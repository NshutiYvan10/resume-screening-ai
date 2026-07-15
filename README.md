# ResumeAI — AI-Powered Resume Screening System

A production-grade, multi-tenant recruitment platform that parses, scores, and ranks candidate
resumes against job requirements using an AI screening engine. Built with **React + TypeScript**
(frontend), **Spring Boot 3 / Java 21** (backend API), a **FastAPI** NLP microservice (AI), and
**PostgreSQL**.

---

## Table of contents
1. [Architecture](#architecture)
2. [Roles & the end-to-end flow](#roles--the-end-to-end-flow)
3. [Prerequisites](#prerequisites)
4. [Quick start](#quick-start)
5. [Configuration](#configuration)
6. [Email / SMTP](#email--smtp)
7. [Default credentials](#default-credentials)
8. [API overview](#api-overview)
9. [Project layout](#project-layout)

---

## Architecture

```
┌────────────┐      REST/JSON      ┌──────────────────┐   multipart    ┌────────────────┐
│  React SPA │ ──────────────────▶ │  Spring Boot API │ ─────────────▶ │  FastAPI (AI)  │
│  (Vite)    │ ◀────────────────── │  (JWT, JPA)      │ ◀───────────── │  NLP scoring   │
└────────────┘                     └────────┬─────────┘                └────────────────┘
                                            │
                                            ▼
                                     ┌──────────────┐
                                     │  PostgreSQL  │
                                     └──────────────┘
```

- **Frontend** — role-aware SPA (super admin, company admin, recruiter, candidate) with JWT auth,
  silent token refresh, notifications, and analytics dashboards.
- **Backend** — REST API handling auth, invitations, companies, jobs, applications, notifications,
  audit trail, and analytics. Persists domain data and orchestrates AI screening asynchronously.
- **AI service** — stateless FastAPI microservice. Extracts text (PDF/DOCX/DOC/TXT), pulls out
  skills, education and experience, scores a resume against the job's weighted qualification matrix,
  and returns an advisory bias flag. Reused and hardened from the original prototype.
- **Database** — PostgreSQL, schema managed by Flyway migrations.

## Roles & the end-to-end flow

| Role | Created by | Can do |
|------|-----------|--------|
| **Super Admin** | Seeded on first boot | Invite companies, manage/suspend companies, view all users, platform-wide audit trail & analytics |
| **Company Admin** | Accepts a company invitation | Complete company profile, invite team members, post & manage jobs, review applicants, company audit trail & analytics |
| **Recruiter** | Invited by a company admin | Post & manage jobs, review and rank applicants, move candidates through the pipeline |
| **Candidate** | Self-registers (email verification) | Browse published jobs, apply with a resume, track application status |

**The complete flow:**

1. **Super admin invites a company** by email → invitation email with a secure onboarding link.
2. **Company admin accepts** → fills in company details, creates their admin account, is signed in.
3. **Company admin invites team members** (recruiters / more admins) by email → they accept and join.
4. **Recruiter/admin posts a job** with a weighted qualification matrix (skill · weight · required),
   then publishes it.
5. **Candidate registers**, verifies their email, browses jobs, and **applies with a resume**.
6. The backend **asynchronously calls the AI service**, which scores the resume; the result
   (match score, skill/experience/education breakdown, extracted entities, bias flag) is stored.
7. **Applicants are ranked by AI match score.** The recruiter reviews, shortlists, moves candidates
   through the pipeline (under review → shortlisted → interview → offered → hired / rejected).
8. **Candidates are notified** by in-app notification and email on every status change (shortlisted,
   rejected, etc.).
9. Every sensitive action (invites, onboarding, status changes, suspensions, logins) is written to an
   **audit trail** scoped to the platform (super admin) or company (company admin).

## Prerequisites

- **Java 21+** and **Maven 3.9+**
- **Node.js 18+** and **npm**
- **Python 3.10+**
- **PostgreSQL 14+**

## Quick start

Run each service in its own terminal.

### 1. Database

```bash
createdb resume_screening
# or: psql -U postgres -c "CREATE DATABASE resume_screening;"
```

Flyway creates all tables automatically on first backend boot.

### 2. AI service

```bash
cd ai-service
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
AI_SERVICE_API_KEY=change-me-internal-key uvicorn main:app --port 8001
```

Health check: <http://localhost:8001/health>

### 3. Backend

```bash
cd backend
cp .env.example .env      # edit DB_PASSWORD and secrets
# export the variables (or use a tool like direnv / your IDE run config)
export $(grep -v '^#' .env | xargs)
mvn spring-boot:run
```

API: <http://localhost:8080> · Swagger UI: <http://localhost:8080/swagger-ui.html>

### 4. Frontend

```bash
cd frontend
npm install
npm run dev
```

App: <http://localhost:5173> (Vite proxies `/api` to the backend on :8080).

## Configuration

All backend settings are environment variables (see [`backend/.env.example`](backend/.env.example)).
Key ones:

| Variable | Purpose | Default |
|----------|---------|---------|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL connection | localhost/resume_screening |
| `JWT_SECRET` | HS256 signing key (**set a long random value in prod**) | dev placeholder |
| `AI_SERVICE_URL` / `AI_SERVICE_API_KEY` | AI microservice location + shared key | localhost:8001 |
| `FRONTEND_BASE_URL` | Used to build links in emails | http://localhost:5173 |
| `CORS_ORIGINS` | Allowed SPA origins | localhost:5173,4173 |
| `STORAGE_ROOT` | Where uploaded resumes are stored | ./storage |
| `SEED_ADMIN_EMAIL` / `SEED_ADMIN_PASSWORD` | First-boot super admin | see below |
| `MAIL_ENABLED` + `SMTP_*` | Email delivery | see below |

## Email / SMTP

The platform sends transactional email for invitations, email verification, password resets, and
candidate status notifications.

- Point `SMTP_HOST`/`SMTP_PORT` at your provider (or a local dev server like
  [MailHog](https://github.com/mailhog/MailHog) on port 1025).
- For a real provider set `SMTP_AUTH=true`, `SMTP_STARTTLS=true`, and `SMTP_USERNAME`/`SMTP_PASSWORD`.
- **Development without a mail server:** set `MAIL_ENABLED=false`. Email sends are skipped, but every
  action link (invitation, verification, reset) is still logged to the backend console, so you can
  copy it and complete the flow.

## Default credentials

On first boot the backend seeds a platform super admin (only if none exists):

```
Email:    admin@resumeai.local
Password: Admin@12345
```

**Change this immediately** via Account Settings, or override `SEED_ADMIN_EMAIL` /
`SEED_ADMIN_PASSWORD` before the first run.

## API overview

Base path: `/api/v1`. Full interactive docs at `/swagger-ui.html`.

- `auth/*` — login, refresh, logout, register, verify-email, forgot/reset password, profile
- `invitations/*` — company & team invites, public accept endpoints
- `companies/*` — list/manage companies (admin), company profile
- `users/*` — user management (admin & company admin)
- `jobs/*` — company job CRUD + lifecycle, plus `jobs/public/*` job board
- `applications/*` — apply, my applications, per-job applicant lists, status updates, resume download, re-screen
- `notifications/*` — in-app notifications
- `audit` — audit trail (scoped by role)
- `analytics/*` — platform & company analytics

## Project layout

```
resume-screening-ai/
├── ai-service/       # FastAPI NLP screening microservice (Python)
├── backend/          # Spring Boot REST API (Java 21)
│   └── src/main/resources/db/migration/  # Flyway schema
├── frontend/         # React + TypeScript SPA (Vite + Tailwind)
└── README.md
```
