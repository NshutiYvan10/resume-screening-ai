"""
Screening explainability: parse-quality assessment and a deterministic
"why this score" narrative. Hiring teams must be able to see WHY a candidate
scored what they did, and be warned when the resume text was too poor for the
score to be trusted.
"""
from typing import Any, Dict, List, Tuple


def assess_parse_quality(resume_text: str, entities: Dict[str, Any]) -> Tuple[str, List[str]]:
    """
    Grade how much structured signal we could pull out of the resume.

    Returns (quality, warnings) where quality is 'good' | 'partial' | 'poor'.
    'poor' means the score should not be trusted without a human reading the file.
    """
    warnings: List[str] = []

    length = len(resume_text or "")
    if length < 200:
        warnings.append("Very little text could be extracted from the file "
                        "(possibly a scanned or image-based document)")
    elif length < 600:
        warnings.append("Only a small amount of text was extracted")

    if not entities.get("email"):
        warnings.append("No contact email detected")
    if not entities.get("name"):
        warnings.append("Candidate name could not be detected")
    if (entities.get("education_level") or "none") == "none":
        warnings.append("No education credentials detected")
    if float(entities.get("experience_years") or 0) == 0:
        warnings.append("No work-experience dates or durations detected")
    if not entities.get("skills"):
        warnings.append("No recognizable skills detected")

    if length < 200 or len(warnings) >= 4:
        quality = "poor"
    elif len(warnings) >= 2:
        quality = "partial"
    else:
        quality = "good"
    return quality, warnings


def build_reasoning(
    scores: Dict[str, Any],
    entities: Dict[str, Any],
    qualifications: List[Dict[str, Any]],
    min_experience_years: float,
    required_education_level: str,
) -> str:
    """Plain-English, verifiable explanation of how the score was formed."""
    parts: List[str] = []

    # ---- skills
    total = len(qualifications)
    matched = scores.get("matched_skills", [])
    missing_req = scores.get("missing_required", [])
    missing_opt = scores.get("missing_optional", [])
    if total:
        s = f"Matched {len(matched)} of {total} qualifications"
        if matched:
            s += f" ({', '.join(matched[:6])}{'…' if len(matched) > 6 else ''})"
        s += "."
        if missing_req:
            s += f" Missing REQUIRED: {', '.join(missing_req)}."
        if missing_opt:
            s += f" Missing optional: {', '.join(missing_opt)}."
        parts.append(s)
    else:
        parts.append("The job defined no qualification matrix; skills were scored neutrally.")

    # ---- experience
    years = float(entities.get("experience_years") or 0)
    if min_experience_years and min_experience_years > 0:
        comparison = "meets" if years >= min_experience_years else "is below"
        parts.append(f"Detected ≈{years:g} years of experience, which {comparison} "
                     f"the required {min_experience_years:g}+ years.")
    else:
        parts.append(f"Detected ≈{years:g} years of experience (job specified no minimum).")

    # ---- education
    education = entities.get("education") or "no formal education credentials"
    level_names = {"phd": "PhD", "masters": "Master's", "bachelors": "Bachelor's",
                   "diploma": "Diploma", "certificate": "Certificate"}
    required = (required_education_level or "").strip().lower()
    if required in level_names:
        candidate_level = (entities.get("education_level") or "none").lower()
        rank = {"none": 0, "certificate": 1, "diploma": 2, "bachelors": 3, "masters": 4, "phd": 5}
        comparison = "meets" if rank.get(candidate_level, 0) >= rank[required] else "falls short of"
        parts.append(f"Education: {education}, which {comparison} the "
                     f"{level_names[required]} requirement.")
    else:
        parts.append(f"Education: {education}.")

    return " ".join(parts)
