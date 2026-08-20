# User Registration — Feature Specification (FinTech / Banking)

**Document type:** Functional & Non-Functional Requirements Specification
**Feature:** New User Registration (Account Opening — Digital Onboarding)
**Audience:** Business Analysts, Product Owners, Backend/Frontend Engineers, QA Engineers, Security Engineers
**Author role:** Senior Business Analyst / FinTech Domain Expert / Application Security Engineer
**Status:** Draft v1.0 — for review

---

## 1. Purpose & Scope

Registration in a financial product is not a UI form — it is the **first
step of onboarding a financial relationship**: creating an identity record,
establishing a credential, assessing risk (fraud/AML), and provisioning the
first financial instrument (an account). This document treats registration
as that full process, not as "collect email + password + submit."

**In scope:** identity data collection, credential creation, verification
(email/phone), consent capture, duplicate/uniqueness handling, abuse
prevention, initial risk/KYC tiering, account provisioning, error handling,
audit logging, and the security controls around all of the above.

**Out of scope (separate specs):** login/authentication flow itself, MFA
enrollment flow, password reset flow, full KYC document-verification
workflow (only the *triggering* of it is covered here), customer support
account-recovery procedures.

---

## 2. Registration Flow — Design Options & Recommendation

There is more than one reasonable way to structure this flow. Below are the
realistic options with trade-offs.

### 2.1 Single-step vs. Multi-step (progressive) registration

| Option | Description | Pros | Cons |
|---|---|---|---|
| **A. Single-step** | One form: name, email, password, submit → account created immediately | Fast, low friction, simple to implement | Weak fraud signal before account exists; hard to insert KYC tiering later; higher abuse risk (bulk account creation) |
| **B. Multi-step (progressive)** *(Recommended)* | Step 1: email/phone + password → credential + unverified identity created. Step 2: verify email/phone (OTP/link). Step 3: minimal profile (name, DOB, country). Step 4 (conditional): identity verification for higher account tiers | Verification gate before any financial capability is granted; natural place to add risk scoring, device fingerprinting, consent capture per step; better abandonment analytics | More engineering complexity; more states to test |

**Recommendation:** **Option B**. A financial account must not be
fully capable (able to hold/move funds) before at least the identity's
contact channel (email or phone) is verified. This is standard practice in
digital banking/neobank onboarding and materially reduces fraud and
mistyped-email account lockout support tickets.

### 2.2 Tiered account capability at registration

| Option | Description | Recommendation |
|---|---|---|
| **A. Full capability immediately** | Account can send/receive money the moment the form is submitted | Not recommended for a product handling real funds |
| **B. Tiered (limited → full)** *(Recommended)* | Account is created in a `PENDING_VERIFICATION` state with **no transactional capability**; gains `LIMITED` capability after email/phone verification; gains `FULL` capability only after identity verification (KYC) passes | Aligns with a risk-based approach; lets the product launch fast (low-friction signup) while still gating real financial risk behind verification |

This document assumes **Option B** for both, and requirements below are
written against that model unless stated otherwise.

### 2.3 Identifier for login: email vs. phone vs. username

| Option | Recommendation |
|---|---|
| Email as primary identifier | **Recommended** — lower cost to implement than phone/SMS OTP infrastructure, globally addressable, no carrier dependency |
| Phone as primary identifier | Viable alternative/addition, common in mobile-first markets; adds SMS cost and carrier deliverability risk |
| Free-text username | **Not recommended** for a financial product — usernames are guessable, complicate uniqueness/PII handling, and provide no verifiable-ownership signal the way email/phone do |

---

## 3. Functional Requirements

Each requirement has a stable ID for traceability into test cases. Priority
uses MoSCoW (**Must / Should / Could / Won't-for-v1**).

### 3.1 Identity Data Collection

| ID | Requirement | Priority |
|---|---|---|
| REQ-01 | The system shall collect, at minimum, full legal name, email address, and password during initial registration. | Must |
| REQ-02 | The system shall collect date of birth during onboarding (before the account reaches `FULL` capability) to support age eligibility checks. | Must |
| REQ-03 | The system shall collect country of residence during onboarding to support jurisdiction-based eligibility and regulatory routing. | Must |
| REQ-04 | The system shall enforce a minimum age eligibility check (e.g., 18+) before granting `FULL` account capability. The exact age threshold is `Regulatory-dependent`. | Must |
| REQ-05 | The system shall NOT collect government ID numbers, document images, or biometric data during the initial credential-creation step; these belong to a dedicated identity-verification (KYC) step, gated separately. | Must |
| REQ-06 | Full name field shall accept Unicode (support for non-Latin scripts, diacritics) and shall be length-bounded (recommended 1–120 characters) with the bound enforced server-side. | Must |
| REQ-07 | The system shall not silently truncate user-submitted text fields; over-length input shall be rejected with a validation error, never truncated and saved. | Must |

### 3.2 Credential Creation (Email & Password)

| ID | Requirement | Priority |
|---|---|---|
| REQ-08 | Email address shall be validated for RFC 5322-compatible format server-side (client-side validation is a UX enhancement only, never trusted). | Must |
| REQ-09 | Email address shall be normalized (lower-cased, leading/trailing whitespace trimmed) before uniqueness checks and storage. | Must |
| REQ-10 | Email uniqueness shall be enforced at the database level (unique constraint), not only at the application level, to remove race-condition windows. | Must |
| REQ-11 | Password shall have a minimum length of 12 characters. *(Rationale: NIST SP 800-63B recommends favoring length over composition rules; 8 is now considered weak for a financial product.)* | Must |
| REQ-12 | Password shall have a maximum length of at least 64 characters (do not artificially cap short); passwords shall not be rejected for containing spaces or most Unicode characters. | Must |
| REQ-13 | Password composition rules (e.g., "must contain uppercase/digit/symbol") shall be limited or avoided per NIST 800-63B guidance; **instead**, the password shall be checked against a breached-password list (e.g., via a k-anonymity API such as Have I Been Pwned or an equivalent internal corpus) and rejected if compromised. | Should |
| REQ-14 | The system shall provide a real-time password strength indicator on the client, computed from an entropy estimate (e.g., zxcvbn), as a UX aid — this is advisory, not a hard gate beyond REQ-11/REQ-13. | Should |
| REQ-15 | Password shall be rejected if it equals or trivially contains the user's email local-part or full name (case-insensitive substring check). | Should |
| REQ-16 | Password confirmation ("repeat password") field is optional UX sugar; if omitted, a "show password" toggle shall be provided instead to reduce mistyped-password lockouts. Product decision — see Open Questions. | Could |
| REQ-17 | Passwords shall be hashed using a memory-hard algorithm (Argon2id recommended; bcrypt with cost factor ≥ 12 acceptable minimum) before storage. Plaintext password shall never be logged, cached, or included in any error message, analytics event, or support tooling. | Must — Security-sensitive |
| REQ-18 | The password field shall never be pre-filled, echoed back, or included in any API response body, including error responses. | Must — Security-sensitive |

### 3.3 Contact Verification (Email / Phone Ownership)

| ID | Requirement | Priority |
|---|---|---|
| REQ-19 | After credential creation, the system shall send a verification email containing either a time-limited signed link or a one-time code (OTP). | Must |
| REQ-20 | The verification token/link shall expire within a bounded window (recommended 15–60 minutes for OTP, up to 24 hours for a link) and shall be single-use. | Must |
| REQ-21 | The account shall remain in `PENDING_VERIFICATION` state — with no ability to hold or move funds — until the contact channel is verified. | Must |
| REQ-22 | The system shall allow the user to request a new verification email/OTP, rate-limited (see 3.7) to prevent spam/abuse of the mail-sending mechanism. | Must |
| REQ-23 | Unverified accounts shall be automatically purged or anonymized after a defined retention window (e.g., 30 days) if never verified, to limit stale-PII exposure. Exact window is a Product Owner decision. | Should |
| REQ-24 | If phone-based verification is offered, OTP codes shall be numeric, minimum 6 digits, single-use, and rate-limited per phone number and per IP independently. | Must (if phone channel offered) |

### 3.4 Consent & Legal Acknowledgment

| ID | Requirement | Priority |
|---|---|---|
| REQ-25 | The system shall require explicit, affirmative acceptance of Terms of Service and Privacy Policy (e.g., unchecked checkbox — no pre-ticked boxes) before an account can be created. | Must |
| REQ-26 | The system shall record which version of the Terms/Privacy Policy was accepted, plus a timestamp, for audit purposes. | Must |
| REQ-27 | Marketing/communications consent shall be a separate, independently-optional checkbox from legal Terms acceptance (never bundled into one checkbox). | Must |
| REQ-28 | The system shall present a plain-language summary of what data is collected and why (privacy notice) at or before the point of data collection, consistent with data-minimization principles. | Should |

### 3.5 Duplicate Detection & Account Uniqueness

| ID | Requirement | Priority |
|---|---|---|
| REQ-29 | One email address shall map to exactly one active account. Registration attempts with an existing verified email shall be rejected. | Must |
| REQ-30 | Registration attempts with an existing but **unverified** email shall not create a second account; the system shall instead offer to resend the verification email (prevents duplicate "ghost" accounts from typos/abandoned signups). | Should |
| REQ-31 | The system shall detect and flag (not necessarily block) duplicate identity signals beyond email — e.g., same device fingerprint or same government-ID-equivalent used across multiple accounts — routed to a fraud-review queue rather than silently allowed. | Should — Security-sensitive |

### 3.6 Initial Account Provisioning

| ID | Requirement | Priority |
|---|---|---|
| REQ-32 | Upon successful registration (pre-verification), the system shall create the user's identity record and a primary account record, but the account shall have `PENDING_VERIFICATION` status and zero transactional capability. | Must |
| REQ-33 | A monetary opening balance, if any (e.g., promotional credit), shall only be credited after the account reaches at least `LIMITED` (contact-verified) status — never on unverified accounts, to prevent bonus-abuse fraud. | Should |
| REQ-34 | Account/reference numbers generated at provisioning shall be non-sequential and non-guessable (avoid auto-increment exposed as the account identifier) to prevent enumeration of customer accounts. | Must — Security-sensitive |

### 3.7 Abuse Prevention & Rate Limiting

| ID | Requirement | Priority |
|---|---|---|
| REQ-35 | Registration endpoint (UI and API) shall be rate-limited per IP address (e.g., N attempts per time window) and shall return `429 Too Many Requests` once exceeded. | Must — Security-sensitive |
| REQ-36 | Registration shall be protected by a bot-mitigation mechanism (CAPTCHA, proof-of-work challenge, or behavioral/device-risk scoring) before account creation, especially on the public web form. | Must — Security-sensitive |
| REQ-37 | The system shall apply device/browser fingerprinting or equivalent signal collection to detect mass automated account creation, feeding a fraud-risk score rather than a hard block, to avoid false-positive rejection of legitimate users. | Should — Security-sensitive |
| REQ-38 | Disposable/temporary email domains shall be detected and either blocked or flagged for manual review, configurable via a maintained deny-list. | Could |
| REQ-39 | The system shall NOT reveal, via response timing, status code, or message content, whether a given email is already registered, beyond what is strictly necessary for UX (see 3.9 — user enumeration). | Must — Security-sensitive |

### 3.8 KYC / AML Triggering (Risk-Based, Regulatory-dependent)

| ID | Requirement | Priority |
|---|---|---|
| REQ-40 | The system shall run a sanctions/watchlist screening check (e.g., against OFAC-equivalent lists) against the provided identity data before granting any account tier above `LIMITED`. Specific mandated lists are `Regulatory-dependent`. | Must — Regulatory-dependent |
| REQ-41 | The system shall apply a risk-based approach: low-value/low-risk account tiers (`LIMITED`) may be granted with only contact verification; higher tiers requiring larger transaction limits shall require identity document verification (KYC) before activation. Specific thresholds are `Regulatory-dependent` and a `Product Owner decision`. | Must — Regulatory-dependent |
| REQ-42 | If sanctions screening produces a potential match, the account shall be placed in a `UNDER_REVIEW` state, invisible to the user as a "rejection" (to avoid tipping off bad actors), and routed to a compliance queue. | Must — Regulatory-dependent, Security-sensitive |
| REQ-43 | All KYC/AML decisions and the data used to make them shall be logged immutably for audit purposes, with retention period per applicable regulation. Retention length is `Regulatory-dependent`. | Must — Regulatory-dependent |

### 3.9 Error Handling & Messaging

| ID | Requirement | Priority |
|---|---|---|
| REQ-44 | Validation error messages shall be field-specific, human-readable, and shall not leak implementation details (e.g., no stack traces, no SQL/ORM error text) to the client. | Must — Security-sensitive |
| REQ-45 | The "email already registered" case shall be communicated in a way that balances UX and anti-enumeration: **recommended approach** — show a generic message ("If this email is available, we'll send you a verification link" is the strictest anti-enumeration pattern) OR show a direct "email already in use" message if the product prioritizes UX over enumeration-resistance. This trade-off is a **Product Owner decision** (see Open Questions) — most retail banking apps in practice choose direct messaging for UX reasons and accept the low residual enumeration risk. | Must — Product Owner decision |
| REQ-46 | All 5xx-class failures during registration shall be logged server-side with a correlation ID, and that correlation ID shall be surfaced to the user for support purposes, without exposing internal error detail. | Should |

### 3.10 Session, Token & Post-Registration State

| ID | Requirement | Priority |
|---|---|---|
| REQ-47 | Successful registration shall NOT automatically grant a fully-privileged, long-lived session; at most, a short-lived, narrowly-scoped token sufficient to complete verification (e.g., "verify-email" scope only) may be issued. | Must — Security-sensitive |
| REQ-48 | A full authenticated session/access token (capable of financial operations) shall only be issued after successful login post-verification, not as a side effect of registration. | Must — Security-sensitive |
| REQ-49 | If registration is performed via API and an access token is returned directly, that token's scope shall be restricted to match the account's actual capability tier (`PENDING_VERIFICATION` → minimal scope), never a full-access token for an unverified identity. | Must — Security-sensitive |

### 3.11 Audit, Logging & Observability

| ID | Requirement | Priority |
|---|---|---|
| REQ-50 | Every registration attempt (success, validation failure, duplicate, rate-limited, fraud-flagged) shall generate an audit log entry including timestamp, IP, user-agent, and outcome — but never the submitted password (plaintext or hash) or full unmasked PII in general application logs. | Must — Security-sensitive |
| REQ-51 | Audit logs for account creation and KYC decisions shall be tamper-evident (e.g., append-only store) and retained per compliance requirements. Retention period is `Regulatory-dependent`. | Must — Regulatory-dependent |
| REQ-52 | Key funnel metrics (start → verified → KYC-passed conversion rates, drop-off per step, time-to-verify) shall be tracked to support product iteration. | Should |

### 3.12 UX / Accessibility

| ID | Requirement | Priority |
|---|---|---|
| REQ-53 | The registration form shall be usable via keyboard alone and shall meet WCAG 2.1 AA for form labeling, error announcement (ARIA live regions), and color-contrast. | Should |
| REQ-54 | Field-level validation errors shall appear inline, adjacent to the relevant field, immediately on blur/submit — not only in a page-level banner. | Must |
| REQ-55 | The multi-step flow (2.1 Option B) shall show clear step/progress indication and shall allow the user to resume an in-progress, unverified registration without re-entering already-submitted data (subject to session/token expiry). | Should |

### 3.13 API-Specific Security Requirements

| ID | Requirement | Priority |
|---|---|---|
| REQ-56 | The registration API endpoint shall require TLS 1.2+ for all traffic; no plaintext HTTP shall be accepted. | Must — Security-sensitive |
| REQ-57 | The API shall enforce request body size limits and strict content-type checking (reject unexpected `Content-Type`) to reduce injection/DoS surface. | Must — Security-sensitive |
| REQ-58 | The API shall perform server-side validation independent of and at least as strict as any client-side validation; client validation shall never be the sole enforcement point. | Must — Security-sensitive |
| REQ-59 | The API shall be protected against mass-assignment vulnerabilities — the registration request DTO shall only accept fields explicitly intended for user input (e.g., a client shall never be able to set `role`, `accountTier`, `isVerified`, or similar privileged fields via the registration payload). | Must — Security-sensitive |
| REQ-60 | API error responses shall follow a consistent, documented error schema (error code, message, correlation ID) and shall not vary in a way that leaks which specific validation rule an attacker's input tripped, where that could aid enumeration or credential-stuffing tooling. | Should — Security-sensitive |

---

## 4. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-01 | Registration endpoint (API) shall respond within 500ms at p95 under expected load, excluding third-party verification (email/SMS provider) latency. |
| NFR-02 | The registration flow shall be available at ≥ 99.9% uptime, consistent with the product's overall SLA. |
| NFR-03 | The system shall support at least [X] concurrent registrations/sec at launch scale — exact figure is a **Product Owner decision** pending capacity planning. |
| NFR-04 | All PII collected during registration shall be encrypted at rest (e.g., AES-256 or provider-managed equivalent) and in transit (TLS). |
| NFR-05 | The registration feature shall be independently deployable/toggleable (feature flag) to support staged rollout and fast rollback. |

---

## 5. Data Classification (PII Sensitivity)

| Field | Classification | Notes |
|---|---|---|
| Full name | PII | Standard protection |
| Email | PII | Also acts as identifier — protect against enumeration |
| Password | Secret / Highly Sensitive | Never stored in plaintext; never logged |
| Date of birth | PII / Sensitive | Used for age & KYC eligibility |
| Country of residence | PII | Drives regulatory routing |
| Government ID (later KYC step) | Highly Sensitive / Regulated | Out of scope here, referenced only as a downstream trigger |
| Device fingerprint / IP | Technical / Pseudonymous | Used for fraud signals; retention should be time-boxed |

---

## 6. Account State Machine (Recommended)

```
REGISTRATION_STARTED
        │
        ▼
PENDING_VERIFICATION  ──(email/phone verified)──▶  LIMITED
        │                                             │
   (never verified,                         (KYC passed / risk-tier upgrade)
    retention window expires)                          │
        │                                              ▼
        ▼                                            FULL
     PURGED                                             │
                                                (sanctions/KYC hit)
                                                          │
                                                          ▼
                                                    UNDER_REVIEW ──▶ REJECTED / FULL
```

---

## 7. Assumptions

1. The product is a retail digital banking / neobank-style application, not
   a business/corporate banking product (which would require additional
   entity-verification requirements).
2. Email is the primary registration identifier; phone-based registration
   is an optional secondary channel, not the sole path.
3. A dedicated, separate KYC/identity-document-verification workflow exists
   or will exist, and this spec only defines *when* it is triggered, not
   *how* document verification itself works.
4. The product operates in at least one jurisdiction requiring AML/KYC
   controls; where it does not, `Regulatory-dependent` items may be
   descoped by Product/Compliance.
5. Passwords are the primary credential at launch; passkeys/WebAuthn are
   assumed out of scope for v1 but are a plausible v2 enhancement.

## 8. Open Questions

1. Should "email already registered" use direct messaging or an
   anti-enumeration-safe generic message (REQ-45)? This materially affects
   both UX copy and API contract and needs a Product Owner ruling.
2. What is the exact unverified-account retention/purge window (REQ-23)?
3. Is phone-number collection mandatory at signup, optional, or entirely
   deferred to a later KYC step?
4. Should password confirmation ("repeat password") be included, or is a
   show/hide toggle sufficient (REQ-16)?
5. What breached-password-check provider (if any) is approved for use,
   given it involves sending password-derived data to a third party, even
   in k-anonymity form (REQ-13)?
6. What are the exact transaction/balance limits per account tier
   (`LIMITED` vs `FULL`)? Needed to make REQ-41 concrete.
7. Is a promotional/bonus opening balance in scope at all for v1 (REQ-33)?

## 9. Regulatory-dependent Requirements

- REQ-04 (minimum age threshold)
- REQ-40, REQ-41, REQ-42, REQ-43 (sanctions screening, KYC tiering, review-state handling, retention)
- REQ-51 (audit log retention period)
- Data residency/localization requirements (not yet captured — likely needed if operating across multiple jurisdictions; flagged for follow-up)

## 10. Security-sensitive Requirements

REQ-10, REQ-17, REQ-18, REQ-31, REQ-34, REQ-35, REQ-36, REQ-37, REQ-39,
REQ-42, REQ-44, REQ-47, REQ-48, REQ-49, REQ-50, REQ-56, REQ-57, REQ-58,
REQ-59, REQ-60 — these should get explicit sign-off from Application
Security/Compliance before implementation, and dedicated abuse-case/pen-test
coverage in QA, not just functional testing.

## 11. Requirements Requiring Product Owner Decision

- REQ-16 (password confirmation field vs. show/hide toggle)
- REQ-23 (unverified-account retention window)
- REQ-33 (whether/when to grant promotional balance)
- REQ-41 (exact tier thresholds)
- REQ-45 (enumeration-resistant vs. direct "email in use" messaging)
- NFR-03 (target concurrent-registration capacity)

---

## 12. Traceability Note

This specification is written to be testable: every `Must`/`Should`
requirement above is written so it can be turned directly into one or more
QA test cases (positive, negative, boundary, and abuse-case) without
further interpretation, with the exception of items explicitly marked as
pending a Product Owner or Regulatory decision — those should not be
tested against a fixed expected result until the decision is made, only
against "system does not crash / fails safely."
