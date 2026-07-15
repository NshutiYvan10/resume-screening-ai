from scorer import compute_score


def test_scoring_is_deterministic():
    resume_text = "Experienced Python developer with 5 years of experience in machine learning and PostgreSQL."
    qualifications = [
        {"skill": "Python", "weight": 1.0, "required": True},
        {"skill": "PostgreSQL", "weight": 1.0, "required": True},
        {"skill": "Machine Learning", "weight": 1.0, "required": False},
    ]
    job_description = "We need a Python developer with PostgreSQL and machine learning experience."
    entities = {
        "skills": ["Python", "PostgreSQL", "Machine Learning"],
        "education": "Bachelor's in Computer Science",
        "experience_years": 5.0,
        "name": "Jane Doe",
        "email": "jane@example.com",
        "phone": "1234567890",
    }

    first = compute_score(resume_text, qualifications, job_description, entities)
    second = compute_score(resume_text, qualifications, job_description, entities)

    assert first["overall"] == second["overall"]
    assert first["skills"] == second["skills"]
    assert first["experience"] == second["experience"]
    assert first["education"] == second["education"]
    assert 0 <= first["overall"] <= 100
