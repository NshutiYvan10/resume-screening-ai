"""
NLP entity extraction module
Uses spaCy for named entity recognition and custom logic for skills/education/experience
"""
import re
import logging
from typing import Dict, List, Any

try:
    import spacy
except ImportError:  # pragma: no cover - environment fallback
    spacy = None

from skills_dictionary import ALL_SKILLS, SKILLS_LOWER
from education_keywords import get_education_level, extract_field_of_study, EDUCATION_LEVELS

logger = logging.getLogger(__name__)

# Load spaCy model (en_core_web_sm) when available
try:
    nlp = spacy.load("en_core_web_sm") if spacy is not None else None
except Exception as exc:  # pragma: no cover - runtime fallback
    logger.warning("spaCy model unavailable: %s", exc)
    nlp = None


def extract_entities(text: str) -> Dict[str, Any]:
    """
    Extract structured entities from resume text
    
    Returns dict with:
    - skills: list of matched skills
    - education: education level string
    - experience_years: float
    - name: candidate name
    - email: email address
    - phone: phone number
    """
    if not text:
        return {
            "skills": [],
            "education": "",
            "experience_years": 0,
            "name": "",
            "email": "",
            "phone": ""
        }
    
    # Process with spaCy
    doc = nlp(text) if nlp else None
    
    # Extract each entity type
    skills = _extract_skills(text)
    education = _extract_education(text)
    experience_years = _extract_experience_years(text)
    name = _extract_name(text, doc)
    email = _extract_email(text)
    phone = _extract_phone(text)
    
    return {
        "skills": skills,
        "education": education,
        "experience_years": experience_years,
        "name": name,
        "email": email,
        "phone": phone
    }


def _extract_skills(text: str) -> List[str]:
    """
    Extract skills from text by matching against skills dictionary
    
    Uses:
    - Exact keyword match (case-insensitive)
    - Multi-word skill detection
    - Context-aware matching
    """
    text_lower = text.lower()
    found_skills = []
    
    # Sort skills by length (longest first) to match multi-word skills first
    # e.g., "machine learning" before "learning"
    sorted_skills = sorted(ALL_SKILLS, key=len, reverse=True)
    
    for skill in sorted_skills:
        skill_lower = skill.lower()
        
        # Check for exact match (with word boundaries)
        pattern = r'\b' + re.escape(skill_lower) + r'\b'
        if re.search(pattern, text_lower):
            if skill not in found_skills:
                found_skills.append(skill)
    
    return found_skills


def _extract_education(text: str) -> str:
    """
    Extract education level from resume text
    
    Uses regex patterns and education keywords
    """
    text_lower = text.lower()
    
    # Look for education section
    education_section = _extract_section(text, ["education", "academic", "qualifications", "degree"])
    
    if education_section:
        level = get_education_level(education_section)
        field = extract_field_of_study(education_section)
        
        level_names = {
            "phd": "PhD",
            "masters": "Master's",
            "bachelors": "Bachelor's",
            "diploma": "Diploma",
            "certificate": "Certificate",
            "none": ""
        }
        
        level_name = level_names.get(level, "")
        if field and level_name:
            return f"{level_name} in {field}"
        elif level_name:
            return level_name
    
    # Fallback: search entire text
    level = get_education_level(text)
    field = extract_field_of_study(text)
    
    level_names = {
        "phd": "PhD",
        "masters": "Master's",
        "bachelors": "Bachelor's",
        "diploma": "Diploma",
        "certificate": "Certificate",
        "none": ""
    }
    
    level_name = level_names.get(level, "")
    if field and level_name:
        return f"{level_name} in {field}"
    elif level_name:
        return level_name
    
    return ""


def _extract_experience_years(text: str) -> float:
    """
    Extract total years of experience from resume text
    
    Uses multiple patterns:
    - "X years of experience"
    - "X+ years"
    - "X-Y years"
    - Date range calculation
    """
    text_lower = text.lower()
    years_found = []
    
    # Pattern 1: "X years of experience" or "X+ years of experience"
    pattern1 = r'(\d+)\+?\s+years?\s+(?:of\s+)?experience'
    matches1 = re.findall(pattern1, text_lower)
    years_found.extend([float(m) for m in matches1])
    
    # Pattern 2: "X+ years" (standalone)
    pattern2 = r'(\d+)\+?\s+years?\s+(?:in|with|of)'
    matches2 = re.findall(pattern2, text_lower)
    years_found.extend([float(m) for m in matches2])
    
    # Pattern 3: "X-Y years" range (take the higher number)
    pattern3 = r'(\d+)\s*-\s*(\d+)\s+years?'
    matches3 = re.findall(pattern3, text_lower)
    for match in matches3:
        years_found.append(float(match[1]))  # Take upper bound
    
    # Pattern 4: Date range calculation
    # Look for patterns like "2018 - Present" or "2018 to Present"
    date_pattern = r'(\d{4})\s*(?:-|to|–)\s*(?:present|current|(\d{4}))'
    date_matches = re.findall(date_pattern, text_lower)
    
    if date_matches:
        import datetime
        current_year = datetime.datetime.now().year
        for match in date_matches:
            start_year = int(match[0])
            end_year = int(match[1]) if match[1] else current_year
            years_found.append(end_year - start_year)
    
    # Return the maximum years found (most conservative estimate)
    if years_found:
        return max(years_found)
    
    return 0.0


def _extract_name(text: str, doc = None) -> str:
    """
    Extract candidate name from resume text
    
    Uses:
    1. Look for "Name:" label
    2. First line of text (often the name)
    3. spaCy PERSON entity as fallback
    """
    lines = text.strip().split('\n')
    
    # Strategy 1: Look for "Name:" label
    for line in lines[:10]:  # Check first 10 lines
        if re.match(r'^name\s*:\s*(.+)', line, re.IGNORECASE):
            name = re.match(r'^name\s*:\s*(.+)', line, re.IGNORECASE).group(1)
            return name.strip()
    
    # Strategy 2: First non-empty line (often the name)
    for line in lines[:5]:
        line = line.strip()
        if line and len(line.split()) <= 5:  # Name typically 1-5 words
            # Check if it looks like a name (not an email, phone, etc.)
            if not re.search(r'[@\d\(\)]', line):
                return line
    
    # Strategy 3: spaCy PERSON entity
    if doc:
        for ent in doc.ents:
            if ent.label_ == "PERSON":
                return ent.text
    
    return ""


def _extract_email(text: str) -> str:
    """Extract email address from text"""
    email_pattern = r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b'
    matches = re.findall(email_pattern, text)
    return matches[0] if matches else ""


def _extract_phone(text: str) -> str:
    """Extract phone number from text"""
    # Various phone number patterns
    patterns = [
        r'\+?1?[-.\s]?\(?[0-9]{3}\)?[-.\s]?[0-9]{3}[-.\s]?[0-9]{4}',  # US format
        r'\+?[0-9]{1,3}[-.\s]?[0-9]{3,4}[-.\s]?[0-9]{3,4}[-.\s]?[0-9]{3,4}',  # International
        r'\(?[0-9]{3}\)?[-.\s][0-9]{3}[-.\s][0-9]{4}'  # (XXX) XXX-XXXX
    ]
    
    for pattern in patterns:
        matches = re.findall(pattern, text)
        if matches:
            return matches[0]
    
    return ""


def _extract_section(text: str, section_keywords: List[str]) -> str:
    """
    Extract a specific section from resume text
    
    Args:
        text: Full resume text
        section_keywords: List of keywords that indicate section start
    
    Returns:
        Text content of the section
    """
    text_lower = text.lower()
    lines = text.split('\n')
    
    section_start = -1
    section_end = len(lines)
    
    # Find section start
    for i, line in enumerate(lines):
        line_lower = line.lower().strip()
        for keyword in section_keywords:
            if keyword in line_lower and len(line_lower) < 50:  # Section header is usually short
                section_start = i
                break
        if section_start != -1:
            break
    
    if section_start == -1:
        return ""
    
    # Find section end (next section header or end of text)
    next_section_keywords = [
        "experience", "work history", "employment", "projects",
        "skills", "certifications", "awards", "references", "interests"
    ]
    
    for i in range(section_start + 1, len(lines)):
        line_lower = lines[i].lower().strip()
        for keyword in next_section_keywords:
            if keyword in line_lower and len(line_lower) < 50:
                if i > section_start + 1:  # Make sure we have some content
                    section_end = i
                break
        if section_end != len(lines):
            break
    
    # Extract section content
    section_lines = lines[section_start:section_end]
    return '\n'.join(section_lines)