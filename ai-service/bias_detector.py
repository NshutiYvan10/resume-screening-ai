"""
Bias detection module
Advisory-only detection of potentially biased content in resumes
IMPORTANT: Bias detection NEVER affects scoring - it's purely advisory
"""
import re
import logging
from typing import Dict, Any

logger = logging.getLogger(__name__)


def detect_bias(resume_text: str, candidate_name: str) -> Dict[str, Any]:
    """
    Detect potential bias indicators in resume text
    
    Checks for:
    1. Gender pronouns in personal statements
    2. Age indicators
    3. Marital status
    4. Religion/ethnicity mentions
    
    Args:
        resume_text: Full resume text
        candidate_name: Extracted candidate name
    
    Returns:
        Dict with flagged (bool) and reason (str or None)
    """
    if not resume_text:
        return {"flagged": False, "reason": None}
    
    text_lower = resume_text.lower()
    flags = []

    # Check 1: Gender pronouns in personal statements
    gender_flag = _check_gender_pronouns(text_lower)
    if gender_flag:
        flags.append(gender_flag)

    # Check 2: Age indicators (explicit disclosures - low false-positive risk)
    age_flag = _check_age_indicators(text_lower)
    if age_flag:
        flags.append(age_flag)

    # Checks 3 & 4 use words that legitimately appear in work context
    # ("single sign-on", church as a client/employer), so a mention only
    # counts when it occurs in a personal/profile part of the resume.
    marital_flag = _check_marital_status(text_lower)
    if marital_flag:
        flags.append(marital_flag)

    religion_flag = _check_religion_ethnicity(text_lower)
    if religion_flag:
        flags.append(religion_flag)
    
    if flags:
        # Combine flags into informative but non-alarming message
        reasons = "; ".join(flags)
        return {
            "flagged": True,
            "reason": f"Advisory: Resume contains personal details that may be irrelevant to job evaluation: {reasons}. This is advisory only. The AI score is not affected."
        }
    
    return {"flagged": False, "reason": None}


def _check_gender_pronouns(text: str) -> str:
    """
    Check for gender pronouns in personal statements
    
    Returns flag message if found, empty string otherwise
    """
    # Patterns that indicate gender identification
    gender_patterns = [
        r'\bhe\s+is\b',
        r'\bshe\s+is\b',
        r'\bhis\s+experience\b',
        r'\bher\s+background\b',
        r'\bhis\s+background\b',
        r'\bher\s+experience\b',
        r'\bhimself\b',
        r'\bherself\b',
        r'\bhis\s+career\b',
        r'\bher\s+career\b',
        r'\bhis\s+skills\b',
        r'\bher\s+skills\b'
    ]
    
    for pattern in gender_patterns:
        if re.search(pattern, text):
            return "Gender pronouns detected in personal statement"
    
    return ""


def _check_age_indicators(text: str) -> str:
    """
    Check for age-related information
    
    Returns flag message if found, empty string otherwise
    """
    age_patterns = [
        r'born\s+in\s+19\d{2}',  # "born in 1985"
        r'born\s+in\s+20\d{2}',  # "born in 1990"
        r'aged\s+\d{1,2}',       # "aged 25"
        r'\d{1,2}\s+years\s+old',  # "25 years old"
        r'age\s*:\s*\d{1,2}',    # "Age: 30"
        r'date\s+of\s+birth',    # "Date of Birth"
        r'\bdob\b',              # "DOB"
        r'\bbirthday\b'
    ]
    
    for pattern in age_patterns:
        if re.search(pattern, text):
            return "Age information disclosed"
    
    return ""


def _check_marital_status(text: str) -> str:
    """
    Check for marital status information
    
    Returns flag message if found, empty string otherwise
    """
    marital_patterns = [
        r'\bmarried\b(?!\s+to\s+the)',
        # "single" only as a status word, not "single-handedly" / "single sign-on"
        r'\bsingle\b(?![\s\-](?:handed|sign|page|source|point|click|thread|use|cell|responsibility))(?!-)',
        r'\bdivorced\b',
        r'\bwidowed\b',
        r'marital\s+status',
        r'\bspouse\b',
        r'\bhusband\b',
        r'\bwife\b'
    ]

    for pattern in marital_patterns:
        m = re.search(pattern, text)
        if m and _is_in_personal_section(text, m.start()):
            return "Marital status mentioned"

    return ""


def _check_religion_ethnicity(text: str) -> str:
    """
    Check for religion or ethnicity mentions in personal sections
    
    Returns flag message if found, empty string otherwise
    """
    # Religion indicators
    religion_patterns = [
        r'\bchristian\b',
        r'\bmuslim\b',
        r'\bhindu\b',
        r'\bbuddhist\b',
        r'\bjewish\b',
        r'\bagnostic\b',
        r'\batheist\b',
        r'\breligion\b',
        r'\bfaith\b',
        r'\bchurch\b',
        r'\bmosque\b',
        r'\btemple\b',
        r'\bsynagogue\b'
    ]
    
    # Ethnicity/nationality indicators (in personal context)
    ethnicity_patterns = [
        r'\bethnicity\b',
        r'\brace\b',
        r'\bnationality\b',
        r'\bcountry\s+of\s+origin\b',
        r'\bcultural\s+background\b',
        r'\btribe\b',
        r'\bclan\b'
    ]
    
    for pattern in religion_patterns + ethnicity_patterns:
        m = re.search(pattern, text)
        if m and _is_in_personal_section(text, m.start()):
            return "Personal information (religion/ethnicity) mentioned"

    return ""


def _is_in_personal_section(text: str, match_position: int) -> bool:
    """
    Check if a match is in a personal/profile section (not work experience)
    
    This helps reduce false positives from mentions in work context
    """
    # Look for section headers before the match
    text_before = text[:match_position].lower()
    
    personal_section_keywords = [
        'personal', 'profile', 'about me', 'objective', 'summary',
        'interests', 'hobbies', 'additional information', 'marital', 'date of birth'
    ]

    work_section_keywords = [
        'experience', 'work history', 'employment', 'projects',
        'achievements', 'accomplishments', 'volunteer', 'education',
        'certifications', 'skills'
    ]

    window = text_before[-250:]

    # work context wins when both appear (the nearer header is likelier work)
    for keyword in work_section_keywords:
        if keyword in window:
            return False

    for keyword in personal_section_keywords:
        if keyword in window:
            return True

    # Default to False: an advisory flag must not fire on ambiguous context,
    # or recruiters learn to ignore it.
    return False


def get_bias_severity(flagged: bool, reason: str) -> str:
    """
    Determine severity level of bias flag
    
    Returns:
        'low', 'medium', or 'high'
    """
    if not flagged:
        return "none"
    
    reason_lower = reason.lower()
    
    if "gender" in reason_lower:
        return "medium"
    elif "age" in reason_lower:
        return "medium"
    elif "marital" in reason_lower:
        return "low"
    elif "religion" in reason_lower or "ethnicity" in reason_lower:
        return "high"
    else:
        return "low"


def should_highlight_bias(flagged: bool, reason: str) -> bool:
    """
    Determine if bias flag should be prominently displayed
    
    Returns:
        True if flag should be highlighted, False otherwise
    """
    if not flagged:
        return False
    
    severity = get_bias_severity(flagged, reason)
    return severity in ["medium", "high"]