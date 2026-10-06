# API reference

Everything a frontend needs to call this backend: base URL, auth, the response
envelope, every endpoint with its request and response, and the enums.

**Swagger UI** is at `/swagger-ui.html` and needs no token. If this document and
Swagger ever disagree, Swagger is what the server actually serves.

---

## Base URL

```
http://localhost:8080
```

Every endpoint lives under `/api/v1`. There is no other prefix.

---

## Authentication

Two kinds of endpoint:

| | Endpoints | What to send |
| --- | --- | --- |
| Public | `/api/v1/auth/register`, `/login`, `/refresh`, `/logout`, `/me`, `/verify-email`, `/resend-otp`, `/forgot-password`, `/reset-password`, `/api/v1/health` | Nothing |
| Protected | everything under `/api/v1/funds` and `/api/v1/planner` | `Authorization: Bearer <accessToken>` |

Note that all of `/auth` is public, *including* `/me` — it reads the token from the
header and returns `401` if there isn't a valid one, but it does not require a token
to be reachable.

### Tokens

`/login` and `/refresh` return a pair:

| Field | Type | Notes |
| --- | --- | --- |
| `accessToken` | string | Send as `Authorization: Bearer <accessToken>`. Lives 15 minutes. |
| `refreshToken` | string | Single use, 7 days. |
| `tokenType` | string | Always `"Bearer"`. |
| `expiresIn` | integer | Access token lifetime in seconds, i.e. `900`. |

**Send only the access token.** The refresh token goes in the body of `/refresh`
and nowhere else.

**Logging in twice invalidates the earlier login.** Only one refresh token is live
per account, so if a user signs in on a second device the first device's refresh
token stops working. That is intentional.

### Handling expiry

`accessToken` expires after 15 minutes and there is no way to extend it. On a
`401`, call `/auth/refresh` once with the stored refresh token and replay the
original request. If *that* returns `401`, the refresh token is spent or revoked —
send the user back to `/login`.

```ts
async function request(path: string, init: RequestInit = {}) {
  const res = await fetch(`${BASE}/api/v1${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init.headers },
  });

  if (res.status === 401 && refreshToken && !path.startsWith('/auth/refresh')) {
    const refreshed = await fetch(`${BASE}/api/v1/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
    if (!refreshed.ok) {
      localStorage.removeItem('refreshToken');
      location.href = '/login';
    }
    location.reload();
  }
  return res.json();
}
```

### Passwords must be RSA-encrypted

`register`, `login` and `reset-password` do **not** accept a plaintext password.
Encrypt it server-side with the RSA public key (ECB mode, PKCS#1 v1.5 padding),
then Base64-encode the result and send that string.

This is the one thing most likely to be got wrong, so it is worth checking first if
login fails with a `400` while everything else works. Details are in the
[Setup](README.md#setup) section of the README.

---

## Response envelope

Every response, success or failure, has the same four fields:

| Field | Type | Notes |
| --- | --- | --- |
| `success` | boolean | `false` on failure. Redundant with the HTTP status, so a client can branch on the body alone. |
| `message` | string | Human-readable summary. **On failure this is the error reason** — it is the only field with useful detail. |
| `data` | any | The payload. `null` on failure and on endpoints that return no body. |
| `timestamp` | string | Server time, ISO-8601 UTC. |

Success:

```json
{
  "success": true,
  "message": "Success",
  "data": { "...": "endpoint specific" },
  "timestamp": "2026-10-06T09:12:44.318Z"
}
```

Failure:

```json
{
  "success": false,
  "message": "Email already registered",
  "data": null,
  "timestamp": "2026-10-06T09:12:44.318Z"
}
```

**Branch on the HTTP status, not on `message`.** `message` is for humans and its
wording is not a contract.

---

## Errors

| Status | Meaning | What the frontend should do |
| --- | --- | --- |
| `400` | Validation failed. Also returned when a password could not be RSA-decrypted, or a query enum value is unrecognised. | Show `message` against the offending field. Do not retry. |
| `401` | No token, an expired token, or an unknown account. | Refresh once (see [Auth](#authentication)); if that fails, re-login. |
| `404` | The resource does not exist. On plan routes this also covers "not yours". | Treat as empty state. |
| `409` | The fund catalogue is empty — AMFI has not synced yet. | Retry later; this is a server-side data problem. |
| `500` | Unhandled server error. | Show a generic failure. |
| `502` | Gemini replied but the reply could not be repaired into a usable plan. | Offer "try again". The user's inputs were fine. |
| `503` | Gemini is not configured (`GEMINI_API_KEY` unset) or unreachable. | Offer "try again later". Reading existing plans still works. |

Validation failures name the field in `message`, e.g.
`Invalid value for parameter: category`.

---

## Endpoints at a glance

| Method | Path | Auth |
| --- | --- | --- |
| `GET` | `/api/v1/health` | no |
| `POST` | `/api/v1/auth/register` | no |
| `POST` | `/api/v1/auth/login` | no |
| `POST` | `/api/v1/auth/refresh` | no |
| `POST` | `/api/v1/auth/logout` | no |
| `GET` | `/api/v1/auth/me` | token in header |
| `POST` | `/api/v1/auth/verify-email` | no |
| `POST` | `/api/v1/auth/resend-otp` | no |
| `POST` | `/api/v1/auth/forgot-password` | no |
| `POST` | `/api/v1/auth/reset-password` | no |
| `GET` | `/api/v1/funds` | yes |
| `GET` | `/api/v1/funds/{schemeCode}` | yes |
| `POST` | `/api/v1/planner/generate` | yes |
| `GET` | `/api/v1/planner/plans` | yes |
| `GET` | `/api/v1/planner/plans/{planId}` | yes |
| `DELETE` | `/api/v1/planner/plans/{planId}` | yes |
| `GET` | `/api/v1/planner/stats` | yes |

---

## Health

### `GET /api/v1/health`

`200` once the app is serving requests.

```json
{
  "success": true,
  "message": "GOFunds backend is up and running",
  "data": null,
  "timestamp": "2026-10-06T09:12:44.318Z"
}
```

---

## Auth

Registration, login, token refresh, and the password-reset flow.

New accounts start with `emailVerified: false`. Until they consume the emailed OTP
via `/verify-email`, that stays false — gate anything that needs a verified
address on it.

### `POST /api/v1/auth/register`

Creates an account and emails a verification OTP.

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `email` | string | yes | Must be a valid address. |
| `password` | string | yes | RSA-encrypted then Base64-encoded. Max 72 characters — bcrypt's limit, and the check happens *before* encryption. |
| `firstName` | string | yes | |
| `lastName` | string | yes | |
| `phone` | string | no | e.g. `"+919876543210"` |

```json
{
  "email": "asha@example.com",
  "password": "kQd9x0Zr1mA8sT2v3b...",
  "firstName": "Asha",
  "lastName": "Menon",
  "phone": "+919876543210"
}
```

`200` with `data: null`. The account is created but not yet verified.

| Status | When |
| --- | --- |
| `200` | Registered, verification OTP emailed |
| `400` | Validation failed, or the password could not be RSA-decrypted |

### `POST /api/v1/auth/login`

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `email` | string | yes | |
| `password` | string | yes | RSA-encrypted then Base64-encoded, same encoding as `/register`. |

```json
{
  "email": "asha@example.com",
  "password": "kQd9x0Zr1mA8sT2v3b..."
}
```

`200` → [`AuthResponse`](#authresponse).

| Status | When |
| --- | --- |
| `200` | Logged in |
| `400` | Validation failed, or the password could not be RSA-decrypted |
| `401` | Unknown email, or wrong password |

### `POST /api/v1/auth/refresh`

Exchanges a refresh token for a new pair. The token used is invalidated and a new
one is returned, so store the replacement before using the API again.

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `refreshToken` | string | yes | From the previous login or refresh. |

```json
{ "refreshToken": "0a1f2e3d-..." }
```

`200` → [`AuthResponse`](#authresponse).

| Status | When |
| --- | --- |
| `200` | Refreshed |
| `400` | Validation failed |
| `401` | Refresh token unknown, expired, revoked, or already used |

### `POST /api/v1/auth/logout`

Invalidates the caller's refresh token. The access token stays valid until it
expires on its own; there is no way to revoke it early.

No body.

`200` with `data: null`.

| Status | When |
| --- | --- |
| `200` | Logged out |
| `401` | No token, an expired token, or an unknown account |

### `GET /api/v1/auth/me`

The signed-in user's profile. Send the access token in the header.

`200` → [`UserResponse`](#userresponse).

| Status | When |
| --- | --- |
| `200` | Profile returned |
| `401` | No token, an expired token, or an unknown account |

### `POST /api/v1/auth/verify-email`

Consumes the emailed OTP and sets `emailVerified` to true.

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `email` | string | yes | |
| `otp` | string | yes | The 6-digit code from the verification email. |

```json
{
  "email": "asha@example.com",
  "otp": "418207"
}
```

`200` with `data: null`.

| Status | When |
| --- | --- |
| `200` | Email verified |
| `400` | Validation failed, or the OTP was wrong or already consumed |
| `401` | Unknown email |

### `POST /api/v1/auth/resend-otp`

Issues a fresh OTP of the given type and **replaces any earlier one**, so only the
newest code will validate. Anything the user typed from an older email is dead.

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `email` | string | yes | |
| `type` | string | yes | `VERIFICATION` for signup, `PASSWORD_RESET` for a reset. |

```json
{
  "email": "asha@example.com",
  "type": "VERIFICATION"
}
```

`200` with `data: null`.

| Status | When |
| --- | --- |
| `200` | OTP sent |
| `400` | Validation failed |
| `401` | Unknown email |

### `POST /api/v1/auth/forgot-password`

Emails a `PASSWORD_RESET` OTP.

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `email` | string | yes | |

```json
{ "email": "asha@example.com" }
```

`200` with `data: null`.

Returns the same response whether or not the address is registered, so it cannot be
used to find out which emails have accounts. **Do not show a "no such account"
message**, and do not disable the form on an unknown address.

| Status | When |
| --- | --- |
| `200` | Accepted (regardless of whether the email exists) |
| `400` | Validation failed |

### `POST /api/v1/auth/reset-password`

Consumes the `PASSWORD_RESET` OTP and sets the new password.

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `email` | string | yes | |
| `otp` | string | yes | The 6-digit code from the reset email. |
| `newPassword` | string | yes | RSA-encrypted then Base64-encoded, same encoding as `/login`. Max 72 characters. |

```json
{
  "email": "asha@example.com",
  "otp": "418207",
  "newPassword": "kQd9x0Zr1mA8sT2v3b..."
}
```

`200` with `data: null`. Every existing session is invalidated, so the user has to
log in again.

| Status | When |
| --- | --- |
| `200` | Password changed |
| `400` | Validation failed, the password could not be RSA-decrypted, or the OTP was wrong or consumed |
| `401` | Unknown email |

---

## Funds

The mutual fund catalogue, synced daily from AMFI. Only `Growth` (and
dividend-reinvested) options are stored, so every row is directly investable.

`mainCategory`, `subCategory` and `riskLevel` are inferred from the scheme name by
`FundClassifier`, not supplied by AMFI — treat them as a rough hint, not fact.
`returnRate1Year` / `3Year` / `5Year` are usually `null` because AMFI publishes
current NAV only, with no return history.

### `GET /api/v1/funds`

Search and filter the catalogue. All filters are optional and combine with `AND`.

| Param | Type | Required | Notes |
| --- | --- | --- | --- |
| `category` | enum | no | One of `EQUITY`, `DEBT`, `HYBRID`. |
| `riskLevel` | enum | no | One of `LOW`, `LOW_TO_MODERATE`, `MODERATE`, `HIGH`, `VERY_HIGH`. |
| `search` | string | no | Free text over scheme name and fund house. |
| `page` | integer | no | Zero-based, so the first page is `0`. Default `0`. |
| `size` | integer | no | Default `20`, maximum `100`. |
| `sort` | string | no | e.g. `sort=currentNav,desc`. Default `schemeName,asc`. |

Example:

```
GET /api/v1/funds?category=EQUITY&riskLevel=MODERATE&search=hdfc&page=0&size=20&sort=currentNav,desc
```

`200` → a [`Page`](#page) of [`FundResponse`](#fundresponse).

| Status | When |
| --- | --- |
| `200` | Results returned |
| `400` | Validation failed, or an unrecognised `category` / `riskLevel` |
| `401` | No token, an expired token, or an unknown account |

### `GET /api/v1/funds/{schemeCode}`

One fund by AMFI scheme code.

| Param | Type | Required | Notes |
| --- | --- | --- | --- |
| `schemeCode` | string | yes | The AMFI scheme code, e.g. `130565`. **Not the ISIN.** |

`200` → [`FundResponse`](#fundresponse).

| Status | When |
| --- | --- |
| `200` | Fund found |
| `400` | Validation failed |
| `401` | No token, an expired token, or an unknown account |
| `404` | No fund with that scheme code |

---

## Planner

Gemini-powered investment plans. Every route is scoped to the caller's own plans —
one user can never read or delete another's plan, and gets `404` rather than `403`
if they try.

### `POST /api/v1/planner/generate`

Generates a plan and saves it. The backend picks the eligible funds, Gemini chooses
among them and returns allocation percentages, and every rupee figure is computed
in Java and re-validated against the catalogue before saving.

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `monthlyInvestment` | number | yes | The monthly SIP in rupees. This is the **total that gets split across the recommended funds**, not the amount per fund. |
| `goal` | enum | yes | `TAX_SAVING` biases the shortlist towards ELSS funds, which carry a lock-in the other goals do not. |
| `investmentHorizon` | enum | yes | Also caps eligible risk: a short horizon excludes `VERY_HIGH` risk regardless of `riskProfile`. |
| `riskProfile` | enum | no | Omit it and Gemini infers one from the goal and horizon. |

```json
{
  "monthlyInvestment": 25000,
  "goal": "WEALTH_CREATION",
  "investmentHorizon": "FIVE_TO_TEN_YEARS",
  "riskProfile": "MODERATE"
}
```

`201` → [`InvestmentPlanResponse`](#investmentplanresponse).

**This is the slowest endpoint in the API — one LLM round trip.** Expect several
seconds, so show a progress state and disable the submit button rather than letting
the user retry and queue up multiple generations.

| Status | When |
| --- | --- |
| `201` | Plan created |
| `400` | Validation failed |
| `401` | No token, an expired token, or an unknown account |
| `409` | The fund catalogue is empty — AMFI has not synced yet |
| `500` | Unhandled server error |
| `502` | Gemini replied, but the reply could not be repaired into a usable plan |
| `503` | Gemini is not configured (`GEMINI_API_KEY` unset) or unreachable. Reading existing plans still works. |

### `GET /api/v1/planner/plans`

The caller's plans, newest first. Soft-deleted plans are excluded.

`200` → array of [`InvestmentPlanResponse`](#investmentplanresponse). An account with
no plans gets an empty array, not a `404`.

| Status | When |
| --- | --- |
| `200` | Returned |
| `401` | No token, an expired token, or an unknown account |

### `GET /api/v1/planner/plans/{planId}`

| Param | Type | Required | Notes |
| --- | --- | --- | --- |
| `planId` | string (uuid) | yes | From `/plans` or from `/generate`. |

`200` → [`InvestmentPlanResponse`](#investmentplanresponse).

| Status | When |
| --- | --- |
| `200` | Plan found |
| `401` | No token, an expired token, or an unknown account |
| `404` | No such plan for this account |

### `DELETE /api/v1/planner/plans/{planId}`

Soft-deletes a plan. It disappears from `/plans` and from `/stats` immediately, and
`totalInvested` / `projectedValue` / `projectedReturns` stop counting towards the
dashboard totals. `totalMonthlyInvestment` and the other dashboard sums are built
from the caller's remaining plans only, so they drop too.

| Param | Type | Required | Notes |
| --- | --- | --- | --- |
| `planId` | string (uuid) | yes | |

`200` with `data: null`.

| Status | When |
| --- | --- |
| `200` | Deleted |
| `401` | No token, an expired token, or an unknown account |
| `404` | No such plan for this account |

### `GET /api/v1/planner/stats`

Per-account totals across all non-deleted plans.

`200` → [`DashboardStatsResponse`](#dashboardstatsresponse).

| Status | When |
| --- | --- |
| `200` | Returned |
| `401` | No token, an expired token, or an unknown account |

---

## Data types

### Response objects

#### `AuthResponse`

Returned by `/login` and `/refresh`. The user fields are flattened in at the same
level as the token fields, not nested under a `user` key.

| Field | Type | Notes |
| --- | --- | --- |
| `accessToken` | string | Bearer token. Send as `Authorization: Bearer <token>`. Lasts 15 minutes. |
| `refreshToken` | string | Single use, 7 day lifetime. Exchanging it returns a new pair and invalidates itself. |
| `tokenType` | string | Always `"Bearer"`. |
| `expiresIn` | integer | Access token lifetime in seconds, i.e. `900`. |
| `id` | string (uuid) | |
| `email` | string | |
| `firstName` | string | |
| `lastName` | string | |
| `role` | enum | `USER` or `ADMIN`. |
| `emailVerified` | boolean | `false` until the emailed OTP is consumed via `/verify-email`. |

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "0a1f2e3d-4b5c-6d7e-8f90-1a2b3c4d5e6f",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "id": "9c1d8e2f-3a4b-4c5d-8e9f-0a1b2c3d4e5f",
    "email": "asha@example.com",
    "firstName": "Asha",
    "lastName": "Menon",
    "role": "USER",
    "emailVerified": true
  },
  "timestamp": "2026-10-06T09:12:44.318Z"
}
```

#### `UserResponse`

A user's public profile. Never includes the password hash.

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string (uuid) | |
| `email` | string | |
| `firstName` | string | |
| `lastName` | string | |
| `phone` | string | |
| `emailVerified` | boolean | `false` until the emailed OTP is consumed via `/verify-email`. |
| `active` | boolean | `false` for accounts an admin has disabled. |

#### `FundResponse`

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string (uuid) | Internal id. Use `schemeCode` in URLs. |
| `schemeCode` | string | AMFI scheme code — the identifier to use in `/funds/{schemeCode}`. |
| `schemeName` | string | e.g. `"IL&FS Infrastructure Debt Fund Series 1A"` |
| `fundHouse` | string | e.g. `"ICICI Prudential"` |
| `mainCategory` | enum | Inferred from the scheme name, not supplied by AMFI. |
| `subCategory` | enum | Inferred from the scheme name, same caveat. |
| `riskLevel` | enum | Inferred from the scheme name, same caveat. |
| `currentNav` | number | Net asset value per unit, in rupees. Synced daily. |
| `returnRate1Year` | number | Cumulative return, percent. **Usually `null`** — AMFI publishes NAV only, so there is no return history. |
| `returnRate3Year` | number | Cumulative return, percent. Usually `null`. |
| `returnRate5Year` | number | Cumulative return, percent. Usually `null`. |
| `supportsSip` | boolean | Defaults to `true`: AMFI's feed does not say which schemes allow SIP. |
| `supportsLumpSum` | boolean | Defaults to `true`, same caveat. |
| `lastSyncedAt` | string (date-time) | When this row was last refreshed from AMFI. |

Those three nullable return rates need `number | null` in a typed client, not
`number`. A `0` and a `null` mean different things here: `null` is unknown,
`0` genuinely is zero.

#### `PlanFundResponse`

One recommended fund and its slice of the monthly amount. Nested inside
`InvestmentPlanResponse.recommendedFunds`.

| Field | Type | Notes |
| --- | --- | --- |
| `fundId` | string (uuid) | |
| `schemeCode` | string | |
| `schemeName` | string | |
| `fundHouse` | string | |
| `mainCategory` | enum | |
| `subCategory` | enum | |
| `riskLevel` | enum | |
| `allocationPercentage` | number | Share of the monthly amount, 0-100. All allocations always sum to 100. |
| `monthlyAmount` | number | `monthlyInvestment` x `allocationPercentage`, in rupees. |

#### `InvestmentPlanResponse`

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string (uuid) | Pass to `/plans/{planId}`. |
| `goal` | enum | |
| `investmentHorizon` | enum | |
| `monthlyAmount` | number | The monthly amount this plan was built around, in rupees. |
| `riskProfile` | enum | |
| `allocationBreakdown` | object | Asset-class split plus the expected blended annual return. Keys: `equity`, `debt`, `hybrid`, `blendedReturnRate`. |
| `recommendedFunds` | array of [`PlanFundResponse`](#planfundresponse) | Never empty. |
| `totalInvested` | number | `monthlyAmount` x months in the horizon. Recomputed on read, not stored. |
| `projectedValue` | number | Future value of `totalInvested` at the blended rate. Recomputed on read. |
| `projectedReturns` | number | `projectedValue - totalInvested`, in rupees. |
| `explanation` | string | Why Gemini picked this mix, in plain English. |
| `status` | enum | |
| `createdAt` | string (date-time) | |

```json
{
  "success": true,
  "message": "Plan generated successfully",
  "data": {
    "id": "3f7a1c22-8d4e-4a1b-9c3d-5e6f7a8b9c0d",
    "goal": "WEALTH_CREATION",
    "investmentHorizon": "FIVE_TO_TEN_YEARS",
    "monthlyAmount": 25000.0,
    "riskProfile": "MODERATE",
    "allocationBreakdown": {
      "equity": 70.0,
      "debt": 20.0,
      "hybrid": 10.0,
      "blendedReturnRate": 11.5
    },
    "recommendedFunds": [
      {
        "fundId": "5a6b7c8d-9e0f-4a1b-8c2d-3e4f5a6b7c8d",
        "schemeCode": "130565",
        "schemeName": "IL&FS Infrastructure Debt Fund Series 1A",
        "fundHouse": "ICICI Prudential",
        "mainCategory": "DEBT",
        "subCategory": "CORPORATE_BOND",
        "riskLevel": "MODERATE",
        "allocationPercentage": 35.0,
        "monthlyAmount": 8750.0
      }
    ],
    "totalInvested": 1500000.0,
    "projectedValue": 2280000.0,
    "projectedReturns": 780000.0,
    "explanation": "A moderate equity-led mix suits a five to ten year horizon with a MODERATE risk profile.",
    "status": "ACTIVE",
    "createdAt": "2026-10-06T09:12:44.318Z"
  },
  "timestamp": "2026-10-06T09:12:44.318Z"
}
```

#### `DashboardStatsResponse`

| Field | Type | Notes |
| --- | --- | --- |
| `totalPlans` | integer | Non-deleted plans. |
| `activePlans` | integer | Plans still in `ACTIVE` status. |
| `totalMonthlyInvestment` | number | Sum of every plan's monthly amount, in rupees. |
| `totalProjectedReturns` | number | Sum of projected returns, in rupees. |
| `averageProjectedReturns` | number | Rupees per plan, **not** a percentage. Plans without a projection are excluded from the average. |
| `byGoal` | array of [`GoalBreakdown`](#goalbreakdown) | Largest group first. |

#### `GoalBreakdown`

Totals grouped by goal.

| Field | Type | Notes |
| --- | --- | --- |
| `goal` | enum | |
| `planCount` | integer | |
| `monthlyInvestment` | number | Sum of the group's monthly amounts, in rupees. |

#### `Page`

`GET /api/v1/funds` returns a Spring `Page`, so the rows are one level deeper than
every other payload — they live in `data.content`, not `data`.

| Field | Type | Notes |
| --- | --- | --- |
| `content` | array of [`FundResponse`](#fundresponse) | The rows for this page. Empty array when there are none. |
| `totalElements` | integer | Total matching rows across all pages. This is the number to show. |
| `totalPages` | integer | |
| `number` | integer | Zero-based index of this page. |
| `size` | integer | Requested page size. |
| `numberOfElements` | integer | Rows actually in this page. |
| `first` / `last` | boolean | Use these to drive prev/next. |
| `empty` | boolean | |

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "content": [ { "id": "5a6b7c8d-...", "schemeCode": "130565", "currentNav": 2540201.9508 } ],
    "totalElements": 137,
    "totalPages": 7,
    "number": 0,
    "size": 20,
    "numberOfElements": 20,
    "first": true,
    "last": false,
    "empty": false
  },
  "timestamp": "2026-10-06T09:12:44.318Z"
}
```

### Enums

All enums are plain strings on the wire, case-sensitive. Invalid values get a `400`
naming the parameter.

| Values | Used by |
| --- | --- |
| `EQUITY` / `DEBT` / `HYBRID` | `FundResponse.mainCategory`, `category` query param |
| `LOW` / `LOW_TO_MODERATE` / `MODERATE` / `HIGH` / `VERY_HIGH` | `FundResponse.riskLevel`, `riskLevel` query param |
| `LARGE_CAP` / `MID_CAP` / `SMALL_CAP` / `ELSS` / `DIVIDEND_YIELD` / `INDEX` / `SECTORAL` / `OVERNIGHT` / `LIQUID` / `SHORT_DURATION` / `CORPORATE_BOND` / `GOVT_BOND` / `BALANCED` / `CONSERVATIVE` / `AGGRESSIVE_HYBRID` | `FundResponse.subCategory` |
| `WEALTH_CREATION` / `TAX_SAVING` / `RETIREMENT` / `CHILD_EDUCATION` / `HOME_PURCHASE` | `InvestmentPlanRequest.goal`, `InvestmentPlanResponse.goal` |
| `LESS_THAN_3_YEARS` / `THREE_TO_FIVE_YEARS` / `FIVE_TO_TEN_YEARS` / `MORE_THAN_10_YEARS` | `investmentHorizon` |
| `CONSERVATIVE` / `MODERATE` / `AGGRESSIVE` | `riskProfile` |
| `ACTIVE` / `COMPLETED` / `ARCHIVED` | `InvestmentPlanResponse.status` |
| `VERIFICATION` / `PASSWORD_RESET` | `ResendOtpRequest.type` |
| `USER` / `ADMIN` | `AuthResponse.role` |

`InvestmentPlanResponse.allocationBreakdown` is a free-form object keyed by asset
class (`equity`, `debt`, `hybrid`) plus `blendedReturnRate` — it is not an enum and
the backend may add keys, so index it defensively.

---

## Maintenance

If you change an endpoint, update this file in the same commit and check Swagger UI
matches.