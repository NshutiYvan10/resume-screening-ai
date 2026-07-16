"""
Regression tests for the Phase-1 screening engine fixes.

Each test pins a bug that was found empirically in the production audit:
if any of these fail again, the screening quality has regressed.
"""
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from parser import _clean_text
from education_keywords import get_education_level
from extractor import extract_entities, _extract_experience_years
from matcher import find_term
from scorer import compute_score


RESUME = _clean_text("""
Sarah Chen
sarah.chen@email.com

SUMMARY
Full-stack software engineer with 5 years of experience.

EXPERIENCE
Senior Software Engineer, TechCorp — 2021 - Present
- Built ReactJS frontends and Python services on K8s
Software Engineer, WebCo — 2019 - 2021

EDUCATION
Bachelor of Science in Computer Science, State University, 2013-2017
""")


def quals(*items):
    return [{"skill": s, "weight": w, "required": r} for s, w, r in items]


# ---------------------------------------------------------------- parser

def test_clean_text_preserves_lines():
    """Line structure is load-bearing for section detection."""
    assert RESUME.count("\n") > 5


# ------------------------------------------------------------- education

def test_summary_is_not_a_masters_degree():
    """'suMMAry' used to substring-match the 'MA' degree abbreviation."""
    assert get_education_level("summary of qualifications") == "none"


def test_bachelor_detected():
    assert get_education_level("Bachelor of Science in Computer Science") == "bachelors"


def test_resume_education_is_bachelors_not_masters():
    entities = extract_entities(RESUME)
    assert entities["education_level"] == "bachelors"


# --------------------------------------------------------------- matcher

def test_negation_blocks_match():
    text = "Backend developer. No experience with Java. Never used React."
    assert find_term("Java", text)[0] is False
    assert find_term("React", text)[0] is False


def test_alias_matching():
    text = "Built SPAs with ReactJS and NodeJS, deployed on K8s, data in Postgres."
    assert find_term("React", text)[0]
    assert find_term("Node.js", text)[0]
    assert find_term("Kubernetes", text)[0]
    assert find_term("PostgreSQL", text)[0]


def test_no_substring_false_positive():
    assert find_term("Java", "I write JavaScript daily")[0] is False
    assert find_term("R", "Senior developer role")[0] is False


# ------------------------------------------------------------ experience

def test_education_dates_do_not_count_as_experience():
    text = _clean_text("""
Priya Sharma

SUMMARY
Junior developer with 1 year of experience.

EXPERIENCE
Junior Developer, StartUp Inc — 2024 - Present

EDUCATION
Bachelor of Science, University, 2014 - 2018
""")
    years = _extract_experience_years(text)
    assert years <= 3, f"university dates leaked into experience: {years}"


# ----------------------------------------------------------------- scorer

def test_missing_required_skill_earns_nothing():
    """A chef with zero required skills must score 0, not 35."""
    result = compute_score(
        "Executive chef. Menu design and kitchen management.",
        quals(("React", 3, True), ("Python", 2, True)),
        "Software engineer role", {"skills": [], "education_level": "none", "experience_years": 12},
    )
    assert result["skills"] == 0.0
    assert set(result["missing_required"]) == {"React", "Python"}


def test_job_min_years_drives_experience_score():
    entities = {"skills": [], "education_level": "bachelors", "experience_years": 5}
    low = compute_score("x", quals(("Go", 1, True)), "", entities, min_experience_years=10)
    high = compute_score("x", quals(("Go", 1, True)), "", entities, min_experience_years=3)
    assert low["experience"] < high["experience"]


def test_required_education_level_is_relative():
    entities = {"skills": [], "education_level": "bachelors", "experience_years": 5}
    meets = compute_score("x", quals(("Go", 1, True)), "", entities,
                          required_education_level="BACHELORS")
    short = compute_score("x", quals(("Go", 1, True)), "", entities,
                          required_education_level="PHD")
    assert meets["education"] > short["education"]


def test_matched_skills_reported():
    result = compute_score(RESUME, quals(("React", 3, True), ("Python", 2, True), ("Rust", 1, False)),
                           "SWE role", extract_entities(RESUME))
    assert "React" in result["matched_skills"]
    assert "Python" in result["matched_skills"]
    assert "Rust" in result["missing_optional"]
