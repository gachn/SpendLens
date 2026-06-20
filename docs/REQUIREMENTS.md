# SpendLens — Requirements Specification

> An Android app that reads financial transaction SMS, parses them with a learning
> pattern engine, detects duplicates, and presents a beautiful expense-analysis UI.
> **All processing is on-device. Raw SMS never leaves the phone.**

---

## 1. Product summary

| | |
|---|---|
| **Platform** | Android (native), min SDK 26 (Android 8.0), target SDK 34 |
| **Language / UI** | Kotlin + Jetpack Compose (Material 3) |
| **Data** | Encrypted on-device SQLite (Room + SQLCipher) |
| **Processing** | 100% on-device; background parsing via WorkManager |
| **AI** | Pluggable `PatternGenerator` interface, **stubbed** now (heuristic fallback), real provider wired later |
| **Region** | Generic / multi-region pattern set (no single-bank lock-in) |

---

## 2. Actors

- **User** — installs the app, grants SMS permission, reviews and corrects transactions.
- **System (background)** — receives new SMS, parses, deduplicates, categorizes.
- **Pattern engine** — matches SMS against known patterns.
- **AI pattern generator** (pluggable) — proposes a new pattern for unrecognized SMS formats.

---

## 3. Functional requirements

### FR-1 — Permissions & onboarding
- **FR-1.1** On first launch, show an onboarding screen explaining *why* SMS access is needed and that data stays on-device.
- **FR-1.2** Request `READ_SMS` and `RECEIVE_SMS` at runtime (Android dangerous permissions). On Android 13+, also request `POST_NOTIFICATIONS`.
- **FR-1.3** If permission is denied, the app remains usable in a "no data" state with a clear path to grant it later (deep-link to app settings if "Don't ask again" was selected).
- **FR-1.4** The app must function with **only** SMS read permission — no contacts, location, internet-for-SMS, or account login required.

### FR-2 — Initial inbox import
- **FR-2.1** After permission is granted, read the existing SMS inbox via `ContentResolver` (`content://sms/inbox`).
- **FR-2.2** Filter to **financial** messages using a fast pre-filter (sender heuristics + keyword/amount presence) before running the full parser.
- **FR-2.3** Import runs in the background with visible progress; the UI must stay responsive.
- **FR-2.4** Import is **idempotent** — re-running it never creates duplicates (keyed by SMS id / content hash).

### FR-3 — Real-time ingestion
- **FR-3.1** A `BroadcastReceiver` listens for `SMS_RECEIVED` and enqueues a parse job.
- **FR-3.2** Parsing of new SMS happens in a background worker (WorkManager), never on the receiver's main thread.
- **FR-3.3** Newly parsed transactions appear in the UI reactively (Flow/StateFlow) without a manual refresh.

### FR-4 — Parsing engine (pattern-based)
- **FR-4.1** Each SMS is matched against a store of **patterns**. A pattern is a named-group regex plus metadata.
- **FR-4.2** Extracted fields: `amount`, `currency`, `direction` (debit/credit), `account` (masked a/c or card tail), `counterparty` (merchant/payee), `balance`, `referenceId`, `timestamp`, `channel` (UPI/card/netbanking/ATM/etc. when derivable).
- **FR-4.3** The engine ships with a **built-in** generic pattern set covering common formats (e.g. "debited/credited … by/at …", UPI, card spend, ATM withdrawal).
- **FR-4.4** Patterns have a **priority/specificity** so the most specific match wins; ties broken deterministically.
- **FR-4.5** A message that matches no pattern is recorded as **unparsed** and queued for AI pattern generation (FR-6).

### FR-5 — Pattern store & learning
- **FR-5.1** Patterns are persisted (built-in seeded on first run; learned patterns added over time).
- **FR-5.2** Each pattern tracks: source (`BUILTIN` / `AI` / `HEURISTIC` / `USER`), match count, last-matched time, enabled flag, and a sample SMS.
- **FR-5.3** New SMS are parsed using the **current** pattern set, including patterns learned from earlier messages ("extract that info in subsequent parsing").
- **FR-5.4** The user can view, enable/disable, and delete learned patterns (Settings).

### FR-6 — AI pattern generation (pluggable, stubbed)
- **FR-6.1** Define a `PatternGenerator` interface: input = the SMS (with PII masked) + sender; output = a candidate pattern (regex + field map) or `null`.
- **FR-6.2** The default implementation is a **heuristic** generator (no network) that derives a regex from message structure; a network/AI implementation can be dropped in later via the same interface.
- **FR-6.3** Before any text is handed to an AI generator, **mask sensitive values** (full account numbers, amounts, names) so only the *template* is shared. (Moot for the on-device heuristic; required for any future remote provider.)
- **FR-6.4** A generated pattern is **validated** (must compile, must re-match its source SMS, must extract a plausible amount) before being saved.
- **FR-6.5** Generated patterns are saved to the store and used for all subsequent matching (FR-5.3).

### FR-7 — Duplicate detection
- **FR-7.1** Detect duplicate transactions arising from: identical re-delivered SMS, multiple SMS for one event (e.g. bank + card network), and re-imports.
- **FR-7.2** Two transactions are **duplicates** when they share amount + account + direction and either (a) the same `referenceId`, or (b) timestamps within a configurable window (default ±180 s) and same counterparty.
- **FR-7.3** Exact duplicates (same SMS content hash) are dropped silently; "probable" duplicates are flagged for user review, not deleted automatically.
- **FR-7.4** The user can confirm/merge or reject a flagged duplicate.

### FR-8 — Categorization
- **FR-8.1** Assign a spending category (Food, Transport, Shopping, Bills, etc.) from the counterparty via a rules table.
- **FR-8.2** The user can re-categorize a transaction; the choice is remembered for that counterparty (a user rule).

### FR-9 — Analytics & UI
- **FR-9.1 Dashboard** — current-month spend, income, net, and balance(s); spend-by-category donut; recent transactions.
- **FR-9.2 Transactions** — searchable, filterable (date range, account, category, direction) list; tap for detail.
- **FR-9.3 Analytics** — monthly trend (bar/line), category breakdown, top merchants, debit vs credit.
- **FR-9.4 Review** — queue of unparsed SMS and flagged duplicates needing user attention.
- **FR-9.5 Settings** — accounts, patterns, categories, privacy controls, re-scan, export, delete-all.
- **FR-9.6** Beautiful, modern Material 3 UI with light/dark themes and dynamic color where available.

### FR-10 — Manual correction (feedback loop)
- **FR-10.1** The user can edit any extracted field, mark a message as "not a transaction", or split/merge.
- **FR-10.2** Corrections may create a `USER` pattern or rule that improves future parsing.

### FR-11 — Data lifecycle
- **FR-11.1** Export all data to an encrypted/clearly-labelled file (user-initiated only).
- **FR-11.2** Delete-all wipes the database and keys (factory reset of the app's data).

---

## 4. Non-functional requirements

### NFR-1 — Privacy & security (highest priority)
- **NFR-1.1** No raw SMS, account number, or amount is transmitted off-device by default. No analytics/telemetry SDKs.
- **NFR-1.2** The database is encrypted at rest (SQLCipher). The DB key is generated on-device and stored in the **Android Keystore**-backed `EncryptedSharedPreferences`.
- **NFR-1.3** Cloud backup and device-transfer of app data are disabled (`allowBackup=false`, data-extraction rules exclude all).
- **NFR-1.4** Minimal permission set: SMS read/receive + notifications only. **No `INTERNET` permission** in the stubbed/on-device build — adding a remote AI provider is an explicit, visible change.
- **NFR-1.5** PII masking is mandatory on any code path that could send text to an external service.

### NFR-2 — Performance
- **NFR-2.1** Importing a 5,000-message inbox completes in the background without ANRs; UI stays at 60 fps.
- **NFR-2.2** Pre-filter rejects non-financial SMS cheaply before regex evaluation.
- **NFR-2.3** Pattern matching short-circuits on first specific match.

### NFR-3 — Reliability
- **NFR-3.1** All ingestion is idempotent and crash-safe (WorkManager ret/backoff).
- **NFR-3.2** A single malformed SMS never crashes parsing; failures are logged locally and surfaced in the Review queue.

### NFR-4 — Usability & accessibility
- **NFR-4.1** Clear empty/permission/error states. Full TalkBack/content-description support, dynamic type, AA contrast.

### NFR-5 — Maintainability & testability
- **NFR-5.1** MVVM + repository layering; pure-Kotlin parser and dedup logic are unit-testable without Android.
- **NFR-5.2** Trusted dependencies only (AndroidX, Room, SQLCipher/Zetetic, Accompanist). Versions pinned in a Gradle version catalog.

---

## 5. Out of scope (v1)

- iOS / cross-platform. Email/PDF statement parsing. Multi-device sync / cloud accounts.
- Bank API (Account Aggregator / Plaid) integration. Bill pay or any money movement.
- A bundled large on-device LLM (the AI slot is an interface; default is heuristic).

---

## 6. Assumptions & constraints

- Transaction SMS are plain text with an amount and a debit/credit cue (true for the vast majority of bank/UPI/card alerts).
- SMS read is gated by Play Store policy for the SMS permission group; distribution may require the *Default SMS handler* exemption flow or sideloading. Documented as a release-time concern.
- Currency/locale is inferred from the SMS (symbol/ISO code), not device locale.

---

## 7. Acceptance criteria (representative)

- **AC-1** Granting SMS permission imports existing financial SMS into the encrypted DB with **zero duplicates** on a second import.
- **AC-2** A new transaction SMS appears as a categorized transaction within seconds, parsed off the main thread.
- **AC-3** An SMS in an unseen format is logged as unparsed, a pattern is generated and saved, and a **subsequent** SMS of the same format parses automatically.
- **AC-4** Two SMS describing the same payment (same amount/account/ref) produce **one** transaction (or one flagged pair), never two silent entries.
- **AC-5** With airplane mode on for the whole session, all features (import, parse, learn, analyze) still work — proving on-device operation.
- **AC-6** Static check: the release manifest contains **no `INTERNET` permission**.
