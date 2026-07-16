"""
Resume scoring module

Weighted scoring against the job's qualification matrix using the SAME
matcher that produces the displayed skills, so score and evidence agree.

Weights: skills 40% | experience 30% | education 30%
"""
import re
import logging
from typing import Dict, List, Any
from collections import Counter

from matcher import find_term, canonicalize

try:
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.metrics.pairwise import cosine_similarity
    _HAS_SKLEARN = True
except ImportError:  # pragma: no cover - environment fallback
    TfidfVectorizer = None
    cosine_similarity = None
    _HAS_SKLEARN = False

logger = logging.getLogger(__name__)

# ordered education levels for relative comparison
_LEVEL_RANK = {"none": 0, "certificate": 1, "diploma": 2, "bachelors": 3, "masters": 4, "phd": 5}

# absolute fallback when the job specifies no education requirement
_ABSOLUTE_EDU_SCORE = {"phd": 100, "masters": 90, "bachelors": 75, "diploma": 55, "certificate": 40, "none": 20}


def compute_score(
    resume_text: str,
    qualifications: List[Dict[str, Any]],
    job_description: str,
    entities: Dict[str, Any],
    min_experience_years: float = 0.0,
    required_education_level: str = "",
) -> Dict[str, Any]:
    """
    Compute overall match score with breakdown and per-qualification evidence.

    Returns dict with overall/skills/experience/education scores (0-100) plus
    matched_skills, missing_required, missing_optional lists.
    """
    if not resume_text:
        return {
            "overall": 0.0, "skills": 0.0, "experience": 0.0, "education": 0.0,
            "matched_skills": [], "missing_required": [], "missing_optional": [],
        }

    skills_score, matched, missing_required, missing_optional = _compute_skills_score(
        resume_text, qualifications)
    experience_score = _compute_experience_score(
        qualifications, job_description, entities, min_experience_years)
    education_score = _compute_education_score(
        resume_text, job_description, entities, required_education_level)

    overall = skills_score * 0.40 + experience_score * 0.30 + education_score * 0.30

    def clamp(v):
        return max(0.0, min(100.0, v))

    return {
        "overall": clamp(overall),
        "skills": clamp(skills_score),
        "experience": clamp(experience_score),
        "education": clamp(education_score),
        "matched_skills": matched,
        "missing_required": missing_required,
        "missing_optional": missing_optional,
    }


def _compute_skills_score(resume_text: str, qualifications: List[Dict[str, Any]]):
    """
    Skills match (40% of total).

    A found qualification earns its full weight; a missing one earns NOTHING —
    required or not. (Previously a missing *required* skill still earned half
    credit, which rewarded candidates for lacking must-haves.) Matching is
    whole-word, alias-aware and negation-aware via the unified matcher.
    """
    if not qualifications:
        return 50.0, [], [], []  # neutral when the job defines no matrix

    total_weight = sum(float(q["weight"]) for q in qualifications)
    if total_weight <= 0:
        return 50.0, [], [], []

    earned = 0.0
    matched, missing_required, missing_optional = [], [], []

    for qual in qualifications:
        skill = str(qual["skill"]).strip()
        weight = float(qual["weight"])
        required = bool(qual.get("required"))

        found, _snippet = find_term(skill, resume_text)
        if found:
            earned += weight * 100
            matched.append(canonicalize(skill))
        elif required:
            missing_required.append(canonicalize(skill))
        else:
            missing_optional.append(canonicalize(skill))

    return (earned / (total_weight * 100)) * 100, matched, missing_required, missing_optional


def _compute_experience_score(
    qualifications: List[Dict[str, Any]],
    job_description: str,
    entities: Dict[str, Any],
    min_experience_years: float,
) -> float:
    """
    Experience match (30% of total).

    Required years come from the job's minExperienceYears field when set;
    otherwise parsed from qualification text / job description; otherwise 2.
    """
    required_years = float(min_experience_years or 0)

    if required_years <= 0:
        sources = [str(q["skill"]) for q in qualifications] + [job_description or ""]
        for source in sources:
            m = re.search(r'(\d{1,2})\s*-\s*(\d{1,2})\s+years?', source, re.IGNORECASE)
            if m:
                required_years = max(required_years, float(m.group(2)))
                continue
            m = re.search(r'(\d{1,2})\+?\s+years?', source, re.IGNORECASE)
            if m:
                required_years = max(required_years, float(m.group(1)))

    if required_years <= 0:
        required_years = 2.0

    candidate_years = float(entities.get("experience_years", 0) or 0)

    if candidate_years >= required_years:
        return 100.0
    if candidate_years >= required_years * 0.7:
        return 75.0
    if candidate_years >= required_years * 0.4:
        return 50.0
    return max(10.0, (candidate_years / required_years) * 100)


def _compute_education_score(
    resume_text: str,
    job_description: str,
    entities: Dict[str, Any],
    required_education_level: str,
) -> float:
    """
    Education match (30% of total): 60% level match + 40% job-content similarity.

    When the job specifies a required level, the candidate is scored RELATIVE
    to it (meeting the bar = 100, one level short = 55, further short = 25).
    Overqualification is never penalized. Without a required level the legacy
    absolute scale applies.
    """
    candidate_level = str(entities.get("education_level", "none") or "none").lower()
    required = (required_education_level or "").strip().lower()

    if required in _LEVEL_RANK and required != "none":
        gap = _LEVEL_RANK.get(candidate_level, 0) - _LEVEL_RANK[required]
        if gap >= 0:
            keyword_score = 100.0
        elif gap == -1:
            keyword_score = 55.0
        elif candidate_level == "none":
            keyword_score = 20.0
        else:
            keyword_score = 25.0
    else:
        keyword_score = float(_ABSOLUTE_EDU_SCORE.get(candidate_level, 20))

    # content similarity between resume and job description (40%)
    tfidf_score = 50.0
    if job_description and resume_text:
        try:
            if _HAS_SKLEARN and TfidfVectorizer is not None:
                vectorizer = TfidfVectorizer(max_features=1000, stop_words='english',
                                             ngram_range=(1, 2))
                tfidf_matrix = vectorizer.fit_transform([resume_text, job_description])
                tfidf_score = float(cosine_similarity(tfidf_matrix[0:1], tfidf_matrix[1:2])[0][0]) * 100
            else:
                tfidf_score = _compute_keyword_similarity(resume_text, job_description)
        except Exception as e:
            logger.warning(f"TF-IDF computation failed: {str(e)}")
            tfidf_score = _compute_keyword_similarity(resume_text, job_description)

    return keyword_score * 0.60 + tfidf_score * 0.40


def _compute_keyword_similarity(text1: str, text2: str) -> float:
    """Fallback similarity using token overlap for environments without scikit-learn."""
    if not text1 or not text2:
        return 0.0

    tokens1 = Counter(re.findall(r"[a-zA-Z0-9]+", text1.lower()))
    tokens2 = Counter(re.findall(r"[a-zA-Z0-9]+", text2.lower()))
    if not tokens1 or not tokens2:
        return 0.0

    common = set(tokens1.keys()) & set(tokens2.keys())
    if not common:
        return 0.0

    numerator = sum(min(tokens1[token], tokens2[token]) for token in common)
    denominator = sum(tokens1.values()) + sum(tokens2.values())
    return (numerator / denominator) * 100 if denominator else 0.0


def get_score_interpretation(score: float) -> str:
    """Human-readable interpretation, aligned with the UI's color thresholds."""
    if score >= 70:
        return "Strong match"
    elif score >= 45:
        return "Possible match"
    else:
        return "Weak match"


def get_score_color(score: float) -> str:
    """Color band, aligned with the frontend (green >= 70, amber >= 45, red below)."""
    if score >= 70:
        return "green"
    elif score >= 45:
        return "yellow"
    else:
        return "red"
