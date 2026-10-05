# Planner Module

Generates a mutual fund investment plan for a logged-in user using Gemini, stores
it, and serves it back.

Base package: `com.js.gofunds_backend.planner`

---

## 1. What it does

```
POST /api/v1/planner/generate   → shortlist funds → ask Gemini to pick and split
                                 → validate the answer → compute returns in Java → store
GET  /api/v1/planner/plans      → the user's plans, newest first
GET  /api/v1/planner/plans/{id} → one plan
DELETE /api/v1/planner/plans/{id} → soft delete
GET  /api/v1/planner/stats      → dashboard aggregates
```

All routes require a JWT and are scoped to the caller. Another user's plan id
returns **404**, not 403 — the lookup is filtered by user id in SQL.

Every response is wrapped in `ApiResponse<T>` (`common/dto/ApiResponse.java`).

---

## 2. Folder layout

```
planner/
├── controller/   PlannerController.java      HTTP only, no logic
├── service/      PlannerService.java         owns a user's plans: generate / read / delete
│                 RecommendationService.java  the AI pipeline + the prompt text
│                 FundCatalogService.java     builds the shortlist sent to Gemini
│                 PlannerStatsService.java    dashboard aggregates
│                 InvestmentCalculatorService.java  SIP maths (no LLM involved)
│                 PlanFundMapper.java         JSONB <-> PlanFundResponse conversion
├── ai/           GeminiClient.java           the ONLY place that calls an LLM
│                 RecommendationParser.java   LLM prose -> Recommendation JSON
│                 RecommendationValidator.java repairs / rejects the model's answer
│                 Recommendation.java         the JSON contract asked of Gemini
└── dto/          InvestmentPlanRequest.java  client -> server
                 InvestmentPlanResponse.java server -> client
                 PlanFundResponse.java        one fund inside a plan
                 DashboardStatsResponse.java  aggregates
```

Four folders, matching `auth/` and `funds/`. `ai/` is the exception and is
deliberate — see §3.

**Rule of thumb:** everything in `ai/` deals with untrusted model output.
Everything outside it is deterministic Java.

---

## 3. The two rules that shape the design

**1. The LLM never does arithmetic.**
The prompt asks Gemini for fund ids, allocation percentages, a risk profile and an
explanation. It never asks for projected value. Every rupee figure the user sees
comes from `InvestmentCalculatorService`, so the same inputs always produce the
same output.

**2. The LLM's output is untrusted.**
`RecommendationValidator` re-checks everything in Java before a single value is
stored. See §6.

---

## 4. The generate pipeline

`PlannerService.generate` → `RecommendationService.recommend`:

| # | Step | Class | Failure |
|---|------|-------|---------|
| 1 | Resolve risk profile (default `MODERATE`) | `RecommendationService` | — |
| 2 | Shortlist eligible funds | `FundCatalogService` | **409** if catalogue empty |
| 3 | Render system + user prompt | `RecommendationService` | — |
| 4 | Send to Gemini | `GeminiClient` | **503** |
| 5 | Extract JSON from the reply | `RecommendationParser` | **502** |
| 6 | Repair / validate | `RecommendationValidator` | **502** |
| 7 | Compute returns, store the plan | `PlannerService` + `InvestmentCalculatorService` | — |

Status codes are chosen so the client can tell the three failure modes apart:

- **409** — our problem: the fund catalogue has not been synced.
- **502** — *Gemini's* problem: it answered, but the answer was unusable.
- **503** — Gemini is not configured or unreachable. Plan *reads* still work.

### The shortlist

`FundCatalogService.eligibleFunds` picks at most `planner.catalog.size` funds
(default **30**). Two things to know:

- **Not ranked by return.** The AMFI feed carries no return history, so
  `returnRate1Year` is `null` for every row and ordering by it would be arbitrary.
  Funds are sorted by sub-category then scheme name, so the same request always
  produces the same shortlist.
- **Capped per sub-category** at `catalogSize / distinctSubCategories`. Without a
  cap, any request allowing equity would hand over ~5,660 large caps and blow the
  context window. Dividing by the number of *distinct* sub-categories means a
  single-sleeve catalogue keeps the whole budget instead of being needlessly
  trimmed.

Eligibility filters:

| Filter | Rule |
|---|---|
| Risk level by profile | conservative → low, low-to-moderate · moderate → low-to-moderate, moderate, high · aggressive → moderate, high, very-high |
| Horizon | `< 3 years` additionally removes high and very-high |
| Category | aggressive excludes pure debt funds; the others allow all three |

### The prompt

`RecommendationService.SYSTEM_PROMPT` states the JSON shape and eight hard rules.
The user prompt carries the investor profile and the shortlist, one fund per line:

```
  - fundId: <uuid> | name: <scheme name> | house: <fund house>
    | category: <MAIN/SUB> | risk: <level> | nav: <nav> | 1y return: <n/a>
    | sip: <true|false>
```

A `null` 1y return renders as `n/a` with an explicit instruction not to read it as
zero.

---

## 5. The maths

`InvestmentCalculatorService` — future value of an annuity due, contributions
landing at the start of each month:

```
FV = P × [((1 + r)^n − 1) / r] × (1 + r)      r = annualRate / 12 / 100
```

Horizon bands map to representative years:

| Horizon | Years |
|---|---|
| `LESS_THAN_3_YEARS` | 2 |
| `THREE_TO_FIVE_YEARS` | 4 |
| `FIVE_TO_TEN_YEARS` | 7 |
| `MORE_THAN_10_YEARS` | 15 |

Guards: a `0`/`null` rate short-circuits to `P × n` instead of dividing by zero,
a negative or null monthly amount returns zero, and all money is scaled to 2 dp
with `HALF_UP`.

---

## 6. What the validator repairs vs rejects

`RecommendationValidator` **repairs**:

| Model behaviour | Handling |
|---|---|
| Markdown fences / prose around the JSON | First balanced `{...}` extracted by `RecommendationParser` (brace counting is string- and escape-aware) |
| Invented extra fields | Ignored (`@JsonIgnoreProperties(ignoreUnknown = true)`) |
| Hallucinated `fundId` not in the shortlist | Dropped, and its allocation redistributed across the survivors |
| Duplicate `fundId` | Collapsed, first occurrence wins, so nothing double-counts |
| Allocations totalling 99 or 101 | Rescaled to exactly 100, remainder to the largest holding |
| Wrong `fundName` | Replaced with the database name, never the model's |
| Missing `riskProfile` | Defaults to `MODERATE` |

It **rejects** with **502**: no usable funds, fewer than 3 surviving funds, less
than 10% of the allocation recoverable, an asset split missing 100% by more than 1,
an out-of-range or negative percentage, or a `blendedReturnRate` outside 0–50%.

---

## 7. Storage

Table `investment_plans`:

| Column | Content |
|---|---|
| `allocation_breakdown` (JSONB) | `equity`, `debt`, `hybrid`, `blendedReturnRate` |
| `recommended_funds` (JSONB) | list of maps: `fundId`, `schemeCode`, `schemeName`, `fundHouse`, categories, `riskLevel`, `allocationPercentage`, `monthlyAmount` |
| `projected_returns` | the **only** derived figure persisted |

Funds are stored as plain maps, not a relation, so a plan survives a catalogue
re-sync. `totalInvested` and `projectedValue` are **recomputed on every read**, so
changing the projection maths applies to existing plans too.

`PlanFundMapper` reads defensively — the column is JSON, so a value can be missing,
null or the wrong type, and one malformed entry must not fail the whole listing.

Delete is **soft**: it stamps `deleted_at`, and every query filters on
`deleted_at IS NULL`.

---

## 8. Configuration

| Variable | Default | Notes |
|---|---|---|
| `GEMINI_API_KEY` | *(empty)* | **Optional.** Empty disables Gemini: the app still boots and `POST /generate` returns 503. Do not make it mandatory — that turns a missing key into a startup failure. |
| `GEMINI_MODEL` | `gemini-2.5-flash` | Spring AI 2.x `spring.ai.google.genai.*` prefix. The older `spring.ai.google.gemini.*` prefix is obsolete. |
| `PLANNER_CATALOG_SIZE` | `30` | Funds sent to the model per request. |

There is no failover and no retry: Gemini is the only provider, so there is
nothing to fail over to and a retry loop would just delay the 503.

---

## 9. Tests

68 unit tests, no database and no Spring context needed:

| Class | Covers |
|---|---|
| `GeminiClientTest` | No API key → unavailable + 503, not a boot failure |
| `RecommendationParserTest` | Fences, prose, braces in strings, truncation → 502 |
| `RecommendationValidatorTest` | Every repair and every rejection |
| `FundCatalogServiceTest` | Cap, sub-category balance, horizon ceiling, determinism, no duplicates |
| `InvestmentCalculatorServiceTest` | SIP maths, horizon mapping, 0/null-rate and negative-amount guards |
| `PlanFundMapperTest` | JSONB round-trip, malformed-entry tolerance |
| `PlannerStatsServiceTest` | Aggregates, null projections, goal grouping and ordering |

Run them with:

```bash
./mvnw test -Dtest='!*ApplicationTests*'    # mvnw.cmd on Windows
```

`GofundsBackendApplicationTests#contextLoads` needs real PostgreSQL credentials and
fails without them — that is an environment gap, not a planner issue.
