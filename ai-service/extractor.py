"""
NLP entity extraction module
Uses spaCy for named entity recognition and custom logic for skills/education/experience
"""
import re
import logging
from typing import Dict, List, Any, Optional, Tuple

try:
    import spacy
except ImportError:  # pragma: no cover - environment fallback
    spacy = None

from skills_dictionary import ALL_SKILLS, SKILLS_LOWER
from education_keywords import get_education_level, extract_field_of_study, EDUCATION_LEVELS
from matcher import find_term

logger = logging.getLogger(__name__)

# Load spaCy model (en_core_web_sm) when available
try:
    nlp = spacy.load("en_core_web_sm") if spacy is not None else None
except Exception as exc:  # pragma: no cover - runtime fallback
    logger.warning("spaCy model unavailable: %s", exc)
    nlp = None

_LEVEL_NAMES = {
    "phd": "PhD",
    "masters": "Master's",
    "bachelors": "Bachelor's",
    "diploma": "Diploma",
    "certificate": "Certificate",
    "none": ""
}

EXPERIENCE_SECTION_KEYWORDS = [
    "experience", "work history", "employment", "professional background",
    "career history"
]
EDUCATION_SECTION_KEYWORDS = ["education", "academic", "qualifications", "degree"]


def extract_entities(text: str) -> Dict[str, Any]:
    """
    Extract structured entities from resume text

    Returns dict with:
    - skills: list of matched skills (whole-word, alias- and negation-aware)
    - education: formatted education string ("Bachelor's in Computer Science")
    - education_level: raw level key (phd/masters/bachelors/diploma/certificate/none)
    - experience_years: float (work-history based, education dates excluded)
    - name, email, phone
    """
    if not text:
        return {
            "skills": [],
            "education": "",
            "education_level": "none",
            "experience_years": 0,
            "name": "",
            "email": "",
            "phone": ""
        }

    # Process with spaCy
    doc = nlp(text) if nlp else None

    skills = _extract_skills(text)
    education, education_level = _extract_education(text)
    experience_years = _extract_experience_years(text)
    name = _extract_name(text, doc)
    email = _extract_email(text)
    phone = _extract_phone(text)

    return {
        "skills": skills,
        "education": education,
        "education_level": education_level,
        "experience_years": experience_years,
        "name": name,
        "email": email,
        "phone": phone
    }


def _extract_skills(text: str) -> List[str]:
    """
    Extract skills via the unified matcher (whole-word + aliases + negation),
    so the skills shown to recruiters always agree with the scoring engine.
    """
    found_skills = []
    for skill in sorted(ALL_SKILLS, key=len, reverse=True):
        if skill in found_skills:
            continue
        found, _ = find_term(skill, text)
        if found:
            found_skills.append(skill)
    return found_skills


def _extract_education(text: str) -> Tuple[str, str]:
    """
    Extract (formatted education string, raw level key).
    Prefers the education section; falls back to whole-document scan.
    """
    education_section = _extract_section(text, EDUCATION_SECTION_KEYWORDS)

    for scope in (education_section, text):
        if not scope:
            continue
        level = get_education_level(scope)
        if level != "none":
            field = extract_field_of_study(scope)
            level_name = _LEVEL_NAMES[level]
            formatted = f"{level_name} in {field}" if field else level_name
            return formatted, level

    return "", "none"


def _extract_experience_years(text: str) -> float:
    """
    Estimate years of professional experience.

    Two signals, of which the maximum is used:
    1. Explicitly stated experience ("8 years of experience") anywhere OUTSIDE
       the education section.
    2. Date ranges ("2018 - Present", "since 2019") found ONLY in the work/
       experience section, merged when overlapping and summed. Education date
       ranges (degree years) never count as work experience.
    """
    import datetime
    current_year = datetime.datetime.now().year

    education_section = _extract_section(text, EDUCATION_SECTION_KEYWORDS)
    experience_section = _extract_section(text, EXPERIENCE_SECTION_KEYWORDS)

    # ---- signal 1: stated years, outside the education section
    stated_scope = text
    if education_section:
        stated_scope = text.replace(education_section, " ")
    stated_scope = stated_scope.lower()

    stated = []
    for pattern in (
        r'(\d{1,2})\+?\s+years?\s+(?:of\s+)?experience',
        r'(\d{1,2})\+?\s+years?\s+(?:in|with|of)\b',
    ):
        stated.extend(float(m) for m in re.findall(pattern, stated_scope))
    stated_years = max(stated) if stated else 0.0

    # ---- signal 2: date ranges in the work section only
    if experience_section:
        range_scope = experience_section
    elif education_section:
        range_scope = text.replace(education_section, " ")
    else:
        range_scope = text
    range_scope = range_scope.lower()

    ranges = []
    for m in re.finditer(r'(\d{4})\s*(?:-|–|to)\s*(?:(present|current|now)|(\d{4}))', range_scope):
        start = int(m.group(1))
        end = current_year if m.group(2) else int(m.group(3))
        if 1960 <= start <= current_year and start <= end:
            ranges.append((start, min(end, current_year)))
    for m in re.finditer(r'\bsince\s+(\d{4})\b', range_scope):
        start = int(m.group(1))
        if 1960 <= start <= current_year:
            ranges.append((start, current_year))

    range_years = _merged_range_years(ranges)

    return min(max(stated_years, range_years), 45.0)


def _merged_range_years(ranges: List[Tuple[int, int]]) -> float:
    """Sum of year spans after merging overlaps (parallel jobs don't double-count)."""
    if not ranges:
        return 0.0
    ranges = sorted(ranges)
    merged = [list(ranges[0])]
    for start, end in ranges[1:]:
        if start <= merged[-1][1]:
            merged[-1][1] = max(merged[-1][1], end)
        else:
            merged.append([start, end])
    return float(sum(end - start for start, end in merged))


def _extract_name(text: str, doc=None) -> str:
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
    Extract a specific section from resume text.

    A section starts at a short line containing one of section_keywords and
    ends at the next line that looks like a different section header.
    """
    lines = text.split('\n')

    def looks_like_header(line_lower: str) -> bool:
        # bullets/continuation lines are content, never section headers
        return bool(line_lower) and len(line_lower) < 50 \
            and not line_lower.startswith(('-', '•', '*', '·', '>'))

    section_start = -1
    section_end = len(lines)

    # Find section start
    for i, line in enumerate(lines):
        line_lower = line.lower().strip()
        if not looks_like_header(line_lower):
            continue
        for keyword in section_keywords:
            if keyword in line_lower:
                section_start = i
                break
        if section_start != -1:
            break

    if section_start == -1:
        return ""

    # Find section end (next section header or end of text)
    next_section_keywords = [
        "experience", "work history", "employment", "projects", "education",
        "academic", "skills", "certifications", "awards", "references",
        "interests", "volunteering", "training", "summary"
    ]
    # a keyword that is part of THIS section's header shouldn't end it
    own_keywords = set(section_keywords)

    for i in range(section_start + 1, len(lines)):
        line_lower = lines[i].lower().strip()
        if not looks_like_header(line_lower):
            continue
        for keyword in next_section_keywords:
            if keyword in own_keywords:
                continue
            if keyword in line_lower:
                section_end = i
                break
        if section_end != len(lines):
            break

    # Extract section content
    section_lines = lines[section_start:section_end]
    return '\n'.join(section_lines)
