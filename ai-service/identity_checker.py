"""
Identity verification (advisory only).

Compares the applicant's account identity (name / email / phone supplied by the
backend) against what was extracted from the resume, and surfaces mismatch
flags. Like the bias detector, this NEVER affects the match score — it is a
fraud/consistency signal for a human reviewer.

Design notes / false-positive avoidance:
- Names are compared order-insensitively on token sets (so "Yvan Nshuti" vs
  "Nshuti Yvan" matches) with subset + overlap + fuzzy-ratio fallbacks, so
  nicknames/middle names rarely trip it. A mismatch is only raised when a name
  was actually read from the resume.
- Email / phone mismatches are LOW-severity advisories (candidates legitimately
  apply from a different address than the one printed on the resume); they are
  reported but do NOT by themselves mark the identity unverified.
- Only a NAME_MISMATCH (or a duplicate resume, flagged by the backend) marks the
  identity unverified.
"""
import hashlib
import re
from difflib import SequenceMatcher
from typing import Dict, List, Any, Optional

# titles/suffixes stripped before name comparison
_NAME_NOISE = {
    "mr", "mrs", "ms", "miss", "dr", "prof", "sir", "madam",
    "jr", "sr", "ii", "iii", "iv", "phd", "md", "mba", "bsc", "msc",
}

# surname particles that are written joined or split inconsistently
# ("Al Rashid" vs "Alrashid", "De La Cruz" vs "Dela Cruz"); merged with the
# following token so both spellings normalize to the same token.
_NAME_PARTICLES = {
    "al", "el", "bin", "ibn", "van", "von", "de", "del", "della", "di", "da",
    "la", "le", "los", "las", "dos", "das", "san", "st", "mac", "mc", "abu",
}

NAME_MATCH_THRESHOLD = 0.72
_TOKEN_FUZZY_THRESHOLD = 0.82   # a token pair counts as the same name part above this
_COVERAGE_THRESHOLD = 0.6       # fraction of the shorter name's tokens that must match


def _normalize_name(name: str) -> List[str]:
    """Lowercase, drop punctuation/titles, merge surname particles; return tokens."""
    if not name:
        return []
    name = name.strip()
    # "Doe, John" -> "John Doe"
    if "," in name:
        parts = [p.strip() for p in name.split(",", 1)]
        if len(parts) == 2 and parts[0] and parts[1]:
            name = f"{parts[1]} {parts[0]}"
    name = name.lower()
    name = re.sub(r"[^a-z\s\-']", " ", name)          # keep letters, hyphen, apostrophe
    name = name.replace("-", " ").replace("'", "")
    raw = [t for t in name.split() if t and t not in _NAME_NOISE and len(t) > 1]

    # merge a leading particle into the following token so "al rashid" == "alrashid"
    tokens: List[str] = []
    i = 0
    while i < len(raw):
        if raw[i] in _NAME_PARTICLES and i + 1 < len(raw):
            tokens.append(raw[i] + raw[i + 1])
            i += 2
        else:
            tokens.append(raw[i])
            i += 1
    return tokens


def _soundex(token: str) -> str:
    """Lightweight Soundex so transliteration variants (Mohammed/Muhammad) collide."""
    token = re.sub(r"[^a-z]", "", token.lower())
    if not token:
        return ""
    codes = {
        "b": "1", "f": "1", "p": "1", "v": "1",
        "c": "2", "g": "2", "j": "2", "k": "2", "q": "2", "s": "2", "x": "2", "z": "2",
        "d": "3", "t": "3", "l": "4", "m": "5", "n": "5", "r": "6",
    }
    result = token[0].upper()
    prev = codes.get(token[0], "")
    for ch in token[1:]:
        code = codes.get(ch, "")
        if code and code != prev:
            result += code
        # H/W don't reset the previous code; vowels do
        if ch not in ("h", "w"):
            prev = code
        if ch in "aeiouy":
            prev = ""
    return (result + "000")[:4]


def _tokens_match(t: str, u: str) -> bool:
    if t == u:
        return True
    st, su = _soundex(t), _soundex(u)
    if st and st == su:
        return True
    return SequenceMatcher(None, t, u).ratio() >= _TOKEN_FUZZY_THRESHOLD


def _name_similarity(applicant: str, resume: str) -> float:
    """0..1 similarity: order-insensitive, tolerant of extra tokens, transliteration
    variants and joined/split surname particles."""
    a = _normalize_name(applicant)
    r = _normalize_name(resume)
    if not a or not r:
        return 1.0  # nothing to compare -> do not flag
    a_set, r_set = set(a), set(r)

    # full subset either way (middle names, extra tokens) -> strong match
    if a_set <= r_set or r_set <= a_set:
        return 1.0

    shared = a_set & r_set
    # two exactly-shared tokens (first + last) -> match
    if len(shared) >= 2:
        return 1.0

    # per-token best-match pairing across the two names, allowing phonetic/fuzzy hits
    small, big = (a_set, r_set) if len(a_set) <= len(r_set) else (r_set, a_set)
    used, matched = set(), 0
    for t in small:
        for u in big:
            if u not in used and _tokens_match(t, u):
                used.add(u)
                matched += 1
                break
    if matched / len(small) >= _COVERAGE_THRESHOLD:
        return 1.0

    ratio = SequenceMatcher(None, " ".join(sorted(a_set)), " ".join(sorted(r_set))).ratio()
    if len(shared) == 1:
        # one shared token is weak but real evidence -> lean toward not flagging
        return max(ratio, 0.75)
    return ratio


def _digits(phone: str) -> str:
    return re.sub(r"\D", "", phone or "")


def _phone_matches(applicant: str, resume: str) -> bool:
    """Match if the trailing national digits of one number appear within the other.

    Tolerant of country-code prefixes, formatting noise, and the loose resume phone
    extractor grabbing only a partial span (which caused false PHONE_MISMATCH before).
    """
    a, r = _digits(applicant), _digits(resume)
    if len(a) < 7 or len(r) < 7:
        return True  # too little to judge -> do not flag
    short, long_ = (a, r) if len(a) <= len(r) else (r, a)
    return short[-7:] in long_


def resume_fingerprint(resume_text: str) -> str:
    """SHA-256 of normalized resume text (case/whitespace-insensitive).

    Hashing the TEXT (not the file bytes) catches the same resume re-exported to
    a different file format or with trivial formatting changes.
    """
    normalized = re.sub(r"\s+", " ", (resume_text or "").casefold()).strip()
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def check_identity(applicant_name: str, applicant_email: str, applicant_phone: str,
                   entities: Dict[str, Any]) -> Dict[str, Any]:
    """
    Returns {verified: bool, flags: List[str], summary: Optional[str]}.

    verified is False only for strong signals (name mismatch). Email/phone
    mismatches are reported as advisories but keep verified True.
    """
    resume_name = (entities.get("name") or "").strip()
    resume_email = (entities.get("email") or "").strip()
    resume_phone = (entities.get("phone") or "").strip()

    flags: List[str] = []
    notes: List[str] = []
    verified = True

    # ---- name (strong signal)
    if applicant_name and resume_name:
        sim = _name_similarity(applicant_name, resume_name)
        if sim < NAME_MATCH_THRESHOLD:
            flags.append("NAME_MISMATCH")
            verified = False
            notes.append(
                f'The name on the resume ("{resume_name}") does not appear to '
                f'match the applicant account name ("{applicant_name}").'
            )
    elif applicant_name and not resume_name:
        # not a hard fail: parse-quality already surfaces unreadable resumes
        flags.append("NAME_NOT_FOUND")
        notes.append("No candidate name could be read from the resume to verify identity.")

    # ---- email (advisory)
    if applicant_email and resume_email:
        if applicant_email.casefold() != resume_email.casefold():
            flags.append("EMAIL_MISMATCH")
            notes.append(
                f'The resume lists a different email ("{resume_email}") than the '
                f'applicant account ("{applicant_email}").'
            )

    # ---- phone (advisory)
    if applicant_phone and resume_phone and not _phone_matches(applicant_phone, resume_phone):
        flags.append("PHONE_MISMATCH")
        notes.append("The phone number on the resume differs from the applicant account.")

    return {
        "verified": verified,
        "flags": flags,
        "summary": " ".join(notes) if notes else None,
    }
