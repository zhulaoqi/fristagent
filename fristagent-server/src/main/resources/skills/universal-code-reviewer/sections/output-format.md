## Output Format (STRICT — no deviation allowed)

Respond ONLY with a valid JSON object. No markdown fences, no explanation text outside the JSON.

```
{
  "score": <integer 0-100>,
  "summary": "<2-3 sentence overall assessment>",
  "issues": [
    {
      "filePath": "<exact file path from the diff>",
      "lineStart": <integer or null>,
      "lineEnd": <integer or null>,
      "issueType": "<BUG|SECURITY|STYLE|PERFORMANCE|SUGGESTION>",
      "severity": "<HIGH|MEDIUM|LOW>",
      "description": "<concise description of the problem>",
      "suggestion": "<concrete, actionable fix>"
    }
  ]
}
```

---

## Scoring Formula (MANDATORY — follow every step exactly)

### Step 0 — Hard short-circuit

If the pull request contains **no code diff** (empty or missing patch), set score = **0** and stop. Do not proceed to other steps.

---

### Step 1 — Per-issue base deductions

Start at **100**. For each issue found, subtract the amount below.

| issueType    | HIGH | MEDIUM | LOW |
|--------------|------|--------|-----|
| SECURITY     | -20  | -12    | -6  |
| BUG          | -12  | -7     | -3  |
| PERFORMANCE  | -8   | -4     | -2  |
| STYLE        | -4   | -2     | -1  |
| SUGGESTION   | —    | -2     | -1  |

**Deduction limits by category:**
- `SECURITY HIGH/MEDIUM`: no cap — every instance deducts in full.
- `BUG HIGH/MEDIUM`: no cap — every instance deducts in full.
- `BUG LOW`: full deduction for first 4, ignore the 5th and beyond.
- `PERFORMANCE` all: full deduction for first 5, ignore beyond.
- `STYLE` all severities combined: total STYLE deduction capped at **-20**.
- `SUGGESTION` all: total SUGGESTION deduction capped at **-8**.

---

### Step 2 — Count-based cascade penalties

After Step 1, apply the following additional deductions based on **how many issues** of each kind were found:

#### BUG HIGH count (memory leaks, NPE, race conditions, etc.)
| Count | Extra penalty |
|-------|--------------|
| 1     | 0            |
| 2     | -5           |
| 3     | -10          |
| 4     | -15          |
| 5     | -20          |
| 6+    | -25 (plateau) |

#### SECURITY HIGH count
| Count | Extra penalty |
|-------|--------------|
| 1     | 0            |
| 2     | -8           |
| 3+    | -15 (plateau) |

#### BUG MEDIUM count
| Count | Extra penalty |
|-------|--------------|
| 1–4   | 0            |
| 5–9   | -5           |
| 10+   | -12          |

#### STYLE total count (all severities combined)
| Count | Extra penalty |
|-------|--------------|
| 1–4   | 0            |
| 5–9   | -3           |
| 10–14 | -6           |
| 15+   | -10          |

---

### Step 3 — Hard upper-bound caps

Apply these caps **after** Steps 1 and 2. Use whichever cap is most restrictive.

| Condition                     | Score must not exceed |
|-------------------------------|-----------------------|
| Any SECURITY HIGH present     | 55                    |
| BUG HIGH count ≥ 3            | 50                    |
| BUG HIGH count ≥ 5            | 35                    |
| SECURITY HIGH count ≥ 2       | 40                    |
| SECURITY HIGH count ≥ 3       | 25                    |

---

### Step 4 — Clamp

Final score = max(0, min(100, computed value)).
If zero issues found → score = **100** (do not assign any other value).

---

### Worked Examples

**Example A — 2 BUG HIGH (NPE + memory leak)**
- Step 1: 2 × -12 = -24 → 76
- Step 2: BUG HIGH count=2, extra -5 → 71
- Step 3: BUG HIGH ≥ 1, cap at 65 → **65**

**Example B — 4 BUG HIGH**
- Step 1: 4 × -12 = -48 → 52
- Step 2: BUG HIGH count=4, extra -15 → 37
- Step 3: BUG HIGH ≥ 3, cap at 50 → **37** (already below cap)

**Example C — 12 STYLE issues (8 LOW + 4 MEDIUM)**
- Step 1 raw: 8×(-1) + 4×(-2) = -16, but STYLE cap = -20, so -16 applies → 84
- Step 2: STYLE count=12, in 10–14 band, extra -6 → 78
- Step 3: no cap triggered → **78**

**Example D — 1 SECURITY HIGH + 2 BUG MEDIUM**
- Step 1: -20 + 2×(-7) = -34 → 66
- Step 2: no count cascade → 66
- Step 3: SECURITY HIGH ≥ 1, cap at 55 → **55**

**Example E — no diff provided**
- Step 0: → **0**

---

### Score Interpretation (for summary text)

| Range   | Label      | Meaning                              |
|---------|------------|--------------------------------------|
| 90–100  | 优秀       | 代码质量高，可放心合并               |
| 75–89   | 良好       | 有少量低风险问题，建议修复后合并     |
| 60–74   | 一般       | 存在若干中等问题，需处理后合并       |
| 40–59   | 较差       | 存在明显缺陷，不建议合并             |
| 20–39   | 差         | 存在高危问题，禁止合并               |
| 0–19    | 危险       | 严重安全漏洞或多个高危BUG，必须拒绝  |
