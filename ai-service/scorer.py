"""
Resume scoring module
Implements weighted scoring algorithm using TF-IDF and cosine similarity
"""
import re
import logging
from typing import Dict, List, Any
from collections import Counter
from education_keywords import get_education_score

try:
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.metrics.pairwise import cosine_similarity
    _HAS_SKLEARN = True
except ImportError:  # pragma: no cover - environment fallback
    TfidfVectorizer = None
    cosine_similarity = None
    _HAS_SKLEARN = False

logger = logging.getLogger(__name__)


def compute_score(
    resume_text: str,
    qualifications: List[Dict[str, Any]],
    job_description: str,
    entities: Dict[str, Any]
) -> Dict[str, float]:
    """
    Compute overall match score with breakdown
    
    Scoring weights:
    - Skills: 40%
    - Experience: 30%
    - Education: 30%
    
    Args:
        resume_text: Full resume text
        qualifications: List of {skill, weight, required} dicts
        job_description: Job description text
        entities: Extracted entities from resume
    
    Returns:
        Dict with overall, skills, experience, education scores (0-100)
    """
    if not resume_text:
        return {
            "overall": 0.0,
            "skills": 0.0,
            "experience": 0.0,
            "education": 0.0
        }
    
    # Compute individual scores
    skills_score = _compute_skills_score(resume_text, qualifications, entities)
    experience_score = _compute_experience_score(resume_text, qualifications, entities)
    education_score = _compute_education_score(resume_text, job_description, entities)
    
    # Weighted overall score
    overall = (
        skills_score * 0.40 +
        experience_score * 0.30 +
        education_score * 0.30
    )
    
    # Clamp to [0, 100]
    overall = max(0.0, min(100.0, overall))
    skills_score = max(0.0, min(100.0, skills_score))
    experience_score = max(0.0, min(100.0, experience_score))
    education_score = max(0.0, min(100.0, education_score))
    
    return {
        "overall": overall,
        "skills": skills_score,
        "experience": experience_score,
        "education": education_score
    }


def _compute_skills_score(
    resume_text: str,
    qualifications: List[Dict[str, Any]],
    entities: Dict[str, Any]
) -> float:
    """
    Compute skills match score (40% of total)
    
    Algorithm:
    - For each required qualification:
      - If skill found in resume: contribute weight × 100
      - If skill not found AND required=True: apply 0.5 penalty
    - Normalize to 0-100
    """
    if not qualifications:
        return 50.0  # Neutral score if no qualifications specified
    
    resume_text_lower = resume_text.lower()
    extracted_skills_lower = [s.lower() for s in entities.get("skills", [])]
    
    total_weight = sum(q["weight"] for q in qualifications)
    if total_weight == 0:
        return 50.0
    
    earned_score = 0.0
    
    for qual in qualifications:
        skill = qual["skill"]
        weight = qual["weight"]
        required = qual["required"]
        
        # Check if skill is in extracted skills or in resume text
        skill_found = (
            skill.lower() in extracted_skills_lower or
            skill.lower() in resume_text_lower
        )
        
        if skill_found:
            # Full points for found skill
            earned_score += weight * 100
        elif required:
            # Penalty for missing required skill (50% of weight)
            earned_score += weight * 100 * 0.5
        else:
            # No penalty for missing optional skill
            earned_score += 0
    
    # Normalize to 0-100
    max_possible = total_weight * 100
    if max_possible > 0:
        skills_score = (earned_score / max_possible) * 100
    else:
        skills_score = 50.0
    
    return skills_score


def _compute_experience_score(
    resume_text: str,
    qualifications: List[Dict[str, Any]],
    entities: Dict[str, Any]
) -> float:
    """
    Compute experience match score (30% of total)
    
    Algorithm:
    - Extract required years from qualifications or job description
    - Compare with extracted experience years
    - Scoring:
      * candidate_years >= required_years: 100
      * candidate_years >= required_years * 0.7: 75
      * candidate_years >= required_years * 0.4: 50
      * else: max(10, (candidate_years / required_years) * 100)
    """
    # Extract required years from qualifications
    required_years = 0.0
    
    for qual in qualifications:
        skill = qual["skill"]
        # Look for year requirements in skill description
        # e.g., "3-5 years experience with Python"
        year_match = re.search(r'(\d+)\s*-\s*(\d+)\s+years?', skill, re.IGNORECASE)
        if year_match:
            required_years = max(required_years, float(year_match.group(2)))
        else:
            year_match = re.search(r'(\d+)\+?\s+years?', skill, re.IGNORECASE)
            if year_match:
                required_years = max(required_years, float(year_match.group(1)))
    
    # If not found in qualifications, try job description
    if required_years == 0:
        year_match = re.search(r'(\d+)\s*-\s*(\d+)\s+years?', qualifications[0]["skill"] if qualifications else "", re.IGNORECASE)
        if year_match:
            required_years = float(year_match.group(2))
    
    # Default to 2 years if not specified
    if required_years == 0:
        required_years = 2.0
    
    # Get candidate's experience
    candidate_years = entities.get("experience_years", 0)
    
    # Calculate score
    if candidate_years >= required_years:
        score = 100.0
    elif candidate_years >= required_years * 0.7:
        score = 75.0
    elif candidate_years >= required_years * 0.4:
        score = 50.0
    else:
        if required_years > 0:
            score = max(10.0, (candidate_years / required_years) * 100)
        else:
            score = 50.0
    
    return score


def _compute_education_score(
    resume_text: str,
    job_description: str,
    entities: Dict[str, Any]
) -> float:
    """
    Compute education match score (30% of total)
    
    Algorithm:
    - 60% keyword match (education level)
    - 40% TF-IDF cosine similarity between resume and job description
    
    Education level scores:
    - PhD: 100
    - Master's: 90
    - Bachelor's: 75
    - Diploma: 55
    - Certificate: 40
    - None: 20
    """
    # Keyword match score (60%)
    education_text = entities.get("education", "")
    keyword_score = get_education_score(education_text)
    
    # TF-IDF similarity score (40%)
    tfidf_score = 50.0  # Default

    if job_description and resume_text:
        try:
            if _HAS_SKLEARN and TfidfVectorizer is not None and cosine_similarity is not None:
                # Create TF-IDF vectors
                vectorizer = TfidfVectorizer(
                    max_features=1000,
                    stop_words='english',
                    ngram_range=(1, 2)  # Use both unigrams and bigrams
                )

                # Fit on both documents
                tfidf_matrix = vectorizer.fit_transform([resume_text, job_description])

                # Compute cosine similarity
                similarity = cosine_similarity(tfidf_matrix[0:1], tfidf_matrix[1:2])[0][0]

                # Convert to 0-100 scale
                tfidf_score = similarity * 100
            else:
                tfidf_score = _compute_keyword_similarity(resume_text, job_description)
        except Exception as e:
            logger.warning(f"TF-IDF computation failed: {str(e)}")
            tfidf_score = _compute_keyword_similarity(resume_text, job_description)
    
    # Blend: 60% keyword + 40% TF-IDF
    education_score = (keyword_score * 0.60) + (tfidf_score * 0.40)
    
    return education_score


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


def _compute_tfidf_similarity(text1: str, text2: str) -> float:
    """
    Compute similarity between two texts.

    Returns:
        Similarity score 0-100
    """
    return _compute_keyword_similarity(text1, text2)


def get_score_interpretation(score: float) -> str:
    """
    Interpret score value
    
    Returns:
        Human-readable interpretation
    """
    if score >= 80:
        return "Excellent"
    elif score >= 60:
        return "Good"
    elif score >= 40:
        return "Average"
    else:
        return "Poor"


def get_score_color(score: float) -> str:
    """
    Get color code for score
    
    Returns:
        Color string for UI
    """
    if score >= 60:
        return "green"
    elif score >= 35:
        return "yellow"
    else:
        return "red"