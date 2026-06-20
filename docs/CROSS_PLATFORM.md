# SpendLens — Cross-platform (iOS) notes

## The hard constraint

**iOS does not allow apps to read the SMS/iMessage inbox.** There is no equivalent of
Android's `READ_SMS`. The auto-scan-all-transaction-SMS mechanic is therefore **Android-only**
and cannot be ported to iPhone as-is.

What iOS *does* offer, and why none replace SMS scanning:

| Option | Gives | Limitation |
|---|---|---|
| `IdentityLookup` / `ILMessageFilterExtension` (iOS 16+ "Transactions") | Classify incoming SMS from **unknown senders** at receive time | Sandboxed; no inbox history; can't populate an expense store |
| Share-sheet / manual import | User shares one SMS to the app | Manual, one at a time |
| Email-receipt parsing | Bank alert emails | Needs mail access + backend |
| **Bank aggregation APIs** | Structured transactions, same on iOS & Android | Needs backend + consent; not purely on-device |

## Recommended path if iOS is wanted

The parser core in this repo (`com.spendlens.app.parser.*`, `…ai.*`, `…parser.model.*`) is **pure
Kotlin with no Android imports** — by design. Two options:

1. **Kotlin Multiplatform (KMP) + Compose Multiplatform.** Extract the pure-Kotlin core
   (parser, dedup, categorizer, domain models, even Room via KMP) into a `shared` module. Share UI
   with Compose Multiplatform. Only **ingestion** differs:
   - Android → SMS (as built here).
   - iOS → bank-aggregation API / email / manual import feeding the **same** parser + DB.
2. **Pivot the data source to aggregation** (Plaid / TrueLayer / RBI Account Aggregator) for a
   single product that works on both platforms without SMS — at the cost of a backend and no
   longer being purely on-device.

## What to do now (low cost, keeps the door open)

Keep the parser/model/dedup/ai packages free of Android dependencies (already the case). When iOS
becomes a priority, those packages move into a KMP `shared` module with minimal change; the
Android app keeps SMS ingestion, and an iOS app supplies a different ingestion source behind the
same `SmsProcessor`-style pipeline.

> Bottom line for the immediate question: your wife's iPhone can't get SMS-scanning, but it could
> get the same SpendLens analysis fed by her bank's data via an aggregator — reusing the heavy
> logic already written here.
