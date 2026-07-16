"""API-level tests for the screening endpoint."""
import io
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from fastapi.testclient import TestClient
import main

client = TestClient(main.app)
HEADERS = {"X-API-Key": main.SERVICE_API_KEY}


def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "online"


def test_screen_requires_api_key():
    response = client.post("/api/v1/screen", files={"file": ("r.txt", io.BytesIO(b"text"))})
    assert response.status_code == 401


def test_screen_rejects_legacy_doc():
    response = client.post(
        "/api/v1/screen", headers=HEADERS,
        files={"file": ("resume.doc", io.BytesIO(b"old format"))},
    )
    assert response.status_code == 400


def test_screen_end_to_end():
    resume = b"""Jane Doe
jane@example.com

SUMMARY
Engineer with 6 years of experience.

EXPERIENCE
Engineer, Co - 2020 - Present
- Python and React work

EDUCATION
Bachelor of Science in Computer Science, 2012-2016
"""
    response = client.post(
        "/api/v1/screen", headers=HEADERS,
        files={"file": ("resume.txt", io.BytesIO(resume))},
        data={
            "qualifications": '[{"skill":"Python","weight":2,"required":true},'
                              '{"skill":"Go","weight":1,"required":false}]',
            "job_description": "Backend engineer role",
            "job_title": "Backend Engineer",
            "min_experience_years": "3",
            "education_level": "BACHELORS",
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["match_score"] > 0
    assert "Python" in body["matched_skills"]
    assert "Go" in body["missing_optional"]
    assert body["extracted_education"].startswith("Bachelor")
