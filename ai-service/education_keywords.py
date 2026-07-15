"""
Education level keywords and patterns for extraction
Maps education terms to standardized levels and scores
"""

# Education level mappings
EDUCATION_LEVELS = {
    "phd": [
        "PhD", "Ph.D", "Doctor of Philosophy", "Doctorate", "D.Phil",
        "Doctoral Degree", "Post Doctoral", "Postdoctoral"
    ],
    "masters": [
        "Master", "MSc", "M.Sc", "MBA", "MEng", "M.Eng", "MA", "MS",
        "Masters", "Master's", "Master Degree", "MPhil", "M.Phil",
        "MStat", "MRes", "M.B.A", "M.D", "J.D", "LLM", "LL.M"
    ],
    "bachelors": [
        "Bachelor", "BSc", "B.Sc", "BEng", "B.Eng", "BA", "BS", "B.A", "B.S",
        "Bachelors", "Bachelor's", "Bachelor Degree", "BBA", "B.B.A",
        "BCom", "B.Com", "BTech", "B.Tech", "BE", "B.E"
    ],
    "diploma": [
        "Diploma", "HND", "Higher National Diploma", "Advanced Diploma",
        "Graduate Diploma", "Postgraduate Diploma", "PGDip", "Technical Diploma"
    ],
    "certificate": [
        "Certificate", "Certification", "Professional Certificate",
        "Professional Certification", "Course Certificate", "Training Certificate",
        "Diploma Certificate", "Associate Degree", "Associates"
    ]
}

# Score mapping for education levels (0-100)
EDUCATION_SCORE_MAP = {
    "phd": 100,
    "masters": 90,
    "bachelors": 75,
    "diploma": 55,
    "certificate": 40,
    "none": 20
}

# Field of study patterns (common degree suffixes)
FIELD_PATTERNS = [
    "in Computer Science",
    "in Software Engineering",
    "in Information Technology",
    "in Data Science",
    "in Machine Learning",
    "in Artificial Intelligence",
    "in Civil Engineering",
    "in Mechanical Engineering",
    "in Electrical Engineering",
    "in Business Administration",
    "in Finance",
    "in Accounting",
    "in Marketing",
    "in Human Resources",
    "in Agriculture",
    "in Plantation Management",
    "in Structural Engineering",
    "in Project Management",
    "in Economics",
    "in Mathematics",
    "in Statistics",
    "in Physics",
    "in Chemistry",
    "in Biology",
    "in Medicine",
    "in Nursing",
    "in Law",
    "in Education",
    "in Psychology",
    "in Sociology",
    "in Political Science",
    "in International Relations",
    "in Journalism",
    "in Communications",
    "in Design",
    "in Architecture",
    "in Construction Management",
    "in Supply Chain",
    "in Operations Management"
]


def get_education_level(education_text: str) -> str:
    """
    Determine education level from text
    Returns: 'phd', 'masters', 'bachelors', 'diploma', 'certificate', or 'none'
    """
    if not education_text:
        return "none"
    
    text_lower = education_text.lower()
    
    # Check in order of highest to lowest qualification
    for level in ["phd", "masters", "bachelors", "diploma", "certificate"]:
        keywords = EDUCATION_LEVELS[level]
        for keyword in keywords:
            if keyword.lower() in text_lower:
                return level
    
    return "none"


def get_education_score(education_text: str) -> int:
    """
    Get numeric score for education level
    Returns: 0-100 score
    """
    level = get_education_level(education_text)
    return EDUCATION_SCORE_MAP.get(level, EDUCATION_SCORE_MAP["none"])


def extract_field_of_study(education_text: str) -> str:
    """
    Extract field of study from education text
    Example: "Bachelor's in Computer Science" → "Computer Science"
    """
    if not education_text:
        return ""
    
    text_lower = education_text.lower()
    
    for field in FIELD_PATTERNS:
        if field.lower() in text_lower:
            # Extract the field name (remove "in " prefix)
            return field.replace("in ", "").strip()
    
    return ""


def normalize_education(education_text: str) -> str:
    """
    Normalize education text to standard format
    Example: "BSc Computer Science" → "Bachelor's in Computer Science"
    """
    if not education_text:
        return ""
    
    level = get_education_level(education_text)
    field = extract_field_of_study(education_text)
    
    level_names = {
        "phd": "PhD",
        "masters": "Master's",
        "bachelors": "Bachelor's",
        "diploma": "Diploma",
        "certificate": "Certificate",
        "none": "Education"
    }
    
    level_name = level_names.get(level, "Education")
    
    if field:
        return f"{level_name} in {field}"
    else:
        return level_name