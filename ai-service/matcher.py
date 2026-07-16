"""
Unified skill matcher.

ONE matching engine drives both the score computation and the skills shown to
recruiters, so the evidence displayed always agrees with the number. Features:
- whole-word matching (no substring false positives)
- alias/synonym normalization (ReactJS -> React, K8s -> Kubernetes, ...)
- negation awareness ("no experience with Java" does NOT count as Java)
"""
import re
from typing import List, Optional, Tuple

# variant (lowercase) -> canonical dictionary skill
ALIASES = {
    "reactjs": "React",
    "react.js": "React",
    "nodejs": "Node.js",
    "node js": "Node.js",
    "k8s": "Kubernetes",
    "postgres": "PostgreSQL",
    "psql": "PostgreSQL",
    "tailwindcss": "Tailwind",
    "tailwind css": "Tailwind",
    "js": "JavaScript",
    "ts": "TypeScript",
    "golang": "Go",
    "c sharp": "C#",
    "csharp": "C#",
    "ml": "Machine Learning",
    "scikit learn": "scikit-learn",
    "sklearn": "scikit-learn",
    "vuejs": "Vue",
    "vue.js": "Vue",
    "angularjs": "Angular",
    "expressjs": "Express",
    "express.js": "Express",
    "nextjs": "Next.js",
    "next js": "Next.js",
    "mongo": "MongoDB",
    "emr": "Electronic Medical Records",
    "ehr": "Electronic Medical Records",
    "ci cd": "CI/CD",
    "cicd": "CI/CD",
    "gh actions": "GitHub Actions",
    "amazon web services": "AWS",
    "google cloud platform": "GCP",
    "google cloud": "GCP",
    "ms project": "MS Project",
    "powerpoint": "Microsoft Office",
    "spring boot": "Spring Boot",
}

# words that negate a skill mention when they appear shortly before it
_NEGATION_PATTERN = re.compile(
    r"\b(no|not|never|without|lacking|lacks|don't|do not|doesn't|does not|"
    r"haven't|have not|hasn't|has not|didn't|did not|unfamiliar|limited)\b",
    re.IGNORECASE,
)
_NEGATION_WINDOW = 60  # chars before the match, on the same line


def _term_pattern(term: str) -> str:
    """Whole-word regex for a term; multi-word terms tolerate space/hyphen variation."""
    words = re.split(r"[\s\-]+", term.strip())
    inner = r"[\s\-]+".join(re.escape(w) for w in words if w)
    # word chars, + and # can be part of skill tokens (C++, C#)
    return r"(?<![A-Za-z0-9+#])" + inner + r"(?![A-Za-z0-9+#])"


def _is_negated(text: str, start: int) -> bool:
    """True if a negation word occurs within the window before `start` on the same line."""
    window_start = max(0, start - _NEGATION_WINDOW)
    window = text[window_start:start]
    # only consider the current line/sentence
    for cut in ('\n', '.', ';'):
        idx = window.rfind(cut)
        if idx != -1:
            window = window[idx + 1:]
    return bool(_NEGATION_PATTERN.search(window))


def find_term(term: str, text: str) -> Tuple[bool, Optional[str]]:
    """
    Look for a term (or its aliases) in text.

    Returns (found, evidence_snippet). A mention that is negated does not count;
    if every mention is negated, found is False.
    """
    if not term or not text:
        return False, None

    # normalize the search term itself (job may say "Postgres"), then search the
    # canonical name, the original spelling, and every known variant of it
    canonical = canonicalize(term)
    candidates = list(dict.fromkeys([term, canonical]))
    canonical_lower = canonical.strip().lower()
    for variant, canon in ALIASES.items():
        if canon.lower() == canonical_lower:
            candidates.append(variant)

    for candidate in candidates:
        try:
            pattern = re.compile(_term_pattern(candidate), re.IGNORECASE)
        except re.error:
            continue
        for m in pattern.finditer(text):
            if _is_negated(text, m.start()):
                continue
            line_start = text.rfind('\n', 0, m.start()) + 1
            line_end = text.find('\n', m.end())
            if line_end == -1:
                line_end = len(text)
            snippet = text[line_start:line_end].strip()
            return True, snippet[:160]
    return False, None


def canonicalize(term: str) -> str:
    """Map an alias to its canonical skill name (or return the term unchanged)."""
    return ALIASES.get(term.strip().lower(), term)


def match_terms(terms: List[str], text: str) -> List[str]:
    """Return the subset of terms found (non-negated) in text."""
    return [t for t in terms if find_term(t, text)[0]]
