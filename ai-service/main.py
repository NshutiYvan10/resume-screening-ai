"""
Resume Screening AI Service
FastAPI microservice for NLP-powered resume analysis.

Receives the resume file as a multipart upload together with the job's
qualification matrix, and returns the match score, breakdown, extracted
entities and an advisory bias flag.
"""
import json
import logging
import os
import tempfile
from typing import List, Optional

from fastapi import FastAPI, File, Form, Header, HTTPException, UploadFile
from pydantic import BaseModel, ValidationError

from parser import extract_text
from extractor import extract_entities
from scorer import compute_score
from bias_detector import detect_bias
from explainer import assess_parse_quality, build_reasoning
from identity_checker import check_identity, resume_fingerprint

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

SERVICE_API_KEY = os.environ.get("AI_SERVICE_API_KEY", "change-me-internal-key")
# legacy binary .doc is NOT supported (python-docx can only read .docx)
ALLOWED_EXTENSIONS = {".pdf", ".docx", ".txt"}

app = FastAPI(
    title="Resume Screening AI Service",
    description="NLP-powered resume analysis microservice",
    version="2.0.0",
)


class Qualification(BaseModel):
    """Individual skill/requirement with weight and required flag"""
    skill: str
    weight: float
    required: bool


class ScreenResponse(BaseModel):
    """Response payload with screening results"""
    match_score: float
    score_breakdown: dict
    extracted_skills: List[str]
    extracted_education: str
    extracted_experience_years: float
    extracted_name: str
    extracted_email: str
    extracted_phone: str = ""
    bias_flag: bool
    bias_flag_reason: Optional[str]
    # per-qualification evidence (score and display come from the same matcher)
    matched_skills: List[str] = []
    missing_required: List[str] = []
    missing_optional: List[str] = []
    # explainability
    reasoning: str = ""
    parse_quality: str = "good"          # good | partial | poor
    parse_warnings: List[str] = []
    # identity verification (advisory only — never affects the score)
    identity_verified: bool = True
    identity_flags: List[str] = []
    identity_summary: Optional[str] = None
    # normalized-text fingerprint for duplicate-resume detection (backend compares)
    resume_fingerprint: str = ""


def _check_api_key(x_api_key: Optional[str]):
    if x_api_key != SERVICE_API_KEY:
        raise HTTPException(status_code=401, detail="Invalid or missing X-API-Key")


@app.get("/health")
def health():
    return {"status": "online", "service": "Resume Screening AI", "version": "2.0.0"}


@app.post("/api/v1/screen", response_model=ScreenResponse)
async def screen_resume(
    file: UploadFile = File(...),
    qualifications: str = Form("[]"),
    job_description: str = Form(""),
    job_title: str = Form(""),
    min_experience_years: float = Form(0),
    education_level: str = Form(""),
    applicant_name: str = Form(""),
    applicant_email: str = Form(""),
    applicant_phone: str = Form(""),
    x_api_key: Optional[str] = Header(default=None),
):
    """
    Screening pipeline:
    1. Persist upload to a temp file and extract raw text
    2. Extract NLP entities (skills, education, experience, contact info)
    3. Compute weighted match scores against the job's qualifications
    4. Detect advisory-only bias flags (never affects the score)
    """
    _check_api_key(x_api_key)

    ext = os.path.splitext(file.filename or "")[1].lower()
    if ext not in ALLOWED_EXTENSIONS:
        raise HTTPException(status_code=400, detail=f"Unsupported file type: {ext}")

    try:
        quals = [Qualification(**q) for q in json.loads(qualifications)]
    except (json.JSONDecodeError, ValidationError, TypeError) as e:
        raise HTTPException(status_code=400, detail=f"Invalid qualifications payload: {e}")

    tmp_path = None
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=ext) as tmp:
            tmp.write(await file.read())
            tmp_path = tmp.name

        resume_text = extract_text(tmp_path)
        if not resume_text:
            raise HTTPException(status_code=422, detail="Could not extract text from resume")

        entities = extract_entities(resume_text)
        scores = compute_score(resume_text, [q.dict() for q in quals], job_description, entities,
                               min_experience_years=min_experience_years,
                               required_education_level=education_level)
        bias_result = detect_bias(resume_text, entities.get("name", ""))

        # advisory identity verification (never affects the score)
        identity = check_identity(applicant_name, applicant_email, applicant_phone, entities)
        fingerprint = resume_fingerprint(resume_text)

        # displayed skills = dictionary hits UNION matched job qualifications, so
        # custom qualification terms (any industry) always show as evidence
        displayed_skills = list(entities.get("skills", []))
        for skill in scores.get("matched_skills", []):
            if skill not in displayed_skills:
                displayed_skills.append(skill)

        parse_quality, parse_warnings = assess_parse_quality(resume_text, entities)
        reasoning = build_reasoning(scores, entities, [q.dict() for q in quals],
                                    min_experience_years, education_level)

        logger.info(
            "Screened '%s' for job '%s': overall=%.2f skills=%d bias=%s",
            file.filename, job_title, scores["overall"],
            len(entities.get("skills", [])), bias_result["flagged"],
        )

        return ScreenResponse(
            match_score=round(scores["overall"], 2),
            score_breakdown={
                "skills_match": round(scores["skills"], 2),
                "experience_match": round(scores["experience"], 2),
                "education_match": round(scores["education"], 2),
            },
            extracted_skills=displayed_skills,
            extracted_education=entities.get("education", ""),
            extracted_experience_years=entities.get("experience_years", 0),
            extracted_name=entities.get("name", ""),
            extracted_email=entities.get("email", ""),
            extracted_phone=entities.get("phone", ""),
            bias_flag=bias_result["flagged"],
            bias_flag_reason=bias_result["reason"],
            matched_skills=scores.get("matched_skills", []),
            missing_required=scores.get("missing_required", []),
            missing_optional=scores.get("missing_optional", []),
            reasoning=reasoning,
            parse_quality=parse_quality,
            parse_warnings=parse_warnings,
            identity_verified=identity["verified"],
            identity_flags=identity["flags"],
            identity_summary=identity["summary"],
            resume_fingerprint=fingerprint,
        )
    except HTTPException:
        raise
    except Exception as e:
        logger.error("Screening error: %s", e, exc_info=True)
        raise HTTPException(status_code=500, detail=f"Screening failed: {e}")
    finally:
        if tmp_path and os.path.exists(tmp_path):
            os.unlink(tmp_path)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8001)
