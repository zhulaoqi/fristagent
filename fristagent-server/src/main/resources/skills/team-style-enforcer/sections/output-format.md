## Output Format (STRICT)

Respond ONLY with a valid JSON object. No markdown fences, no text outside the JSON.

```
{
  "score": <integer 0-100>,
  "summary": "<2-3 sentence overall assessment>",
  "issues": [
    {
      "filePath": "<file path>",
      "lineStart": <integer or null>,
      "lineEnd": <integer or null>,
      "issueType": "<BUG|SECURITY|STYLE|PERFORMANCE|SUGGESTION>",
      "severity": "<HIGH|MEDIUM|LOW>",
      "description": "<what the problem is>",
      "suggestion": "<concrete fix recommendation>"
    }
  ]
}
```

---

## Scoring Formula (MANDATORY — follow every step exactly)

### Step 0 — Hard short-circuit
If no code diff provided → score = **0**, stop.

### Step 1 — Per-issue base deductions (start at 100)

| issueType    | HIGH | MEDIUM | LOW |
|--------------|------|--------|-----|
| SECURITY     | -20  | -12    | -6  |
| BUG          | -12  | -7     | -3  |
| PERFORMANCE  | -8   | -4     | -2  |
| STYLE        | -4   | -2     | -1  |
| SUGGESTION   | —    | -2     | -1  |

- SECURITY/BUG HIGH+MEDIUM: every instance deducts in full, no cap.
- BUG LOW: full for first 4, ignore beyond.
- PERFORMANCE: full for first 5, ignore beyond.
- STYLE all combined: total capped at **-20**.
- SUGGESTION all: total capped at **-8**.

### Step 2 — Count-based cascade penalties

**BUG HIGH count:**  1→+0  |  2→-5  |  3→-10  |  4→-15  |  5→-20  |  6+→-25
**SECURITY HIGH count:**  1→+0  |  2→-8  |  3+→-15
**BUG MEDIUM count:**  1–4→+0  |  5–9→-5  |  10+→-12
**STYLE total count:**  1–4→+0  |  5–9→-3  |  10–14→-6  |  15+→-10

### Step 3 — Hard upper-bound caps

| Condition                 | Score must not exceed |
|---------------------------|-----------------------|
| Any SECURITY HIGH         | 55                    |
| BUG HIGH count ≥ 3        | 50                    |
| BUG HIGH count ≥ 5        | 35                    |
| SECURITY HIGH count ≥ 2   | 40                    |
| SECURITY HIGH count ≥ 3   | 25                    |

### Step 4 — Clamp to [0, 100]. Zero issues → score = **100**.

**Score bands:** 90–100 优秀 · 75–89 良好 · 60–74 一般 · 40–59 较差 · 20–39 差 · 0–19 危险
