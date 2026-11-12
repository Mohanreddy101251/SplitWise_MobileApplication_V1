# SpliWise — private expense tracker + bill splitter (Android)

Private Android expense tracker + bill splitter: voice entry, bank SMS auto-import, PDF statement gap check.
**100% on-device, encrypted, no internet permission.**

Native **Kotlin + Jetpack Compose**. Every expense, split and statement is processed **on the phone**: no server, no cloud, no AI/LLM, no analytics. The app doesn't even request the Android internet permission, so it **cannot** send your data anywhere.

## Features
| Area | What it does |
|---|---|
| **Login** | Phone number + 4-digit PIN (PBKDF2 hash) or fingerprint. Several profiles per phone, each with its own encrypted database. Auto-locks after 2 minutes in the background. |
| **Add spends** | Tap **+** for a category grid and number pad, then pick the date: Today, Yesterday or any earlier day via 📅. |
| **Voice** | Hold **+** and say “grocery 100 rupees”, “petrol 500”, “swiggy two fifty”. Uses the on-device speech recogniser (Android 13+). |
| **Bank SMS** | SBI / HDFC / Canara alerts are parsed **the moment they arrive** and **re-checked every hour**. Credit-card SMS are detected separately. |
| **11:50 PM** | Optional mini-statement SMS requests to your banks; the replies are read automatically. |
| **Categories** | Food, Grocery, Travel, Petrol, Shopping, Salon, Gym, Friend transfers, Investments, Bills, Rent, **Home**, Health, Entertainment, Education, EMI & Loans, **Card bill & refunds**, Insurance, Gifts, ATM Cash, Income and Others. About 300 Indian merchants are recognised on-device; the app learns each correction you make. |
| **Needs review** | Unknown shops raise a notification with one-tap category buttons. In the review list you can also add a note and use ▲/▼ to jump. |
| **Statement check** | Upload one or more bank or credit-card **PDF statements**. A password is asked for only when needed, **used once and never stored**. Pick a date range (default: last month-end to today). The **RED / AMBER / GREEN** report lists **only the transactions missing** from SpliWise, each with **Add** or **Ignore**. |
| **Credit cards** | Bill payments and refunds credited back to a card are kept out of income and spend. Every row shows *“Debited from HDFC Bank a/c ••4321”* or *“Credited to … credit card ••1234”*. |
| **Splits** | 3 steps: amount + purpose → how many people → names (optional, otherwise Person 1, 2…). Shares are equal and every split stays editable. Includes balances, a WhatsApp reminder and UPI settle-up. Voice works too: “split 100 to 3”, “split 300 with Mohan Ravi Raju”. |
| **Insights** | Today / month / income, “most of today went to…”, category donut with percentages (tap for every transaction), day-by-day bars, spending calendar (tap a day), 6-month trend, top places, budgets. |
| **Income & Spent** | A monthly ledger of money in and out. Any edit updates every screen. |
| **Activity** | Search, month filter and category filters; transactions grouped by day with day totals. CSV export. |

## Privacy & security
- **No internet permission**: the APK has no way to send data off the phone.
- **AES-256 encrypted database** (SQLCipher) per profile. Its random key is protected by the **Android Keystore**.
- PIN stored only as a salted PBKDF2 hash (120,000 rounds). 5 wrong tries locks it for 30 seconds.
- PDF passwords are held only while the file is being opened. The PDF bytes stay in memory and are wiped when you leave the screen.
- Android cloud backup is turned off (`allowBackup=false`).

## Honest limits
- Banks don't send custom-date statements by SMS. Mini statements (SBI `MSTMT`, HDFC `TXN`, Canara `MINI`) show only the last ~5 transactions. The live and hourly SMS import is the main daily source; the **Statement check** finds anything missed.
- Bank SMS and PDF layouts change. The parsers are tested against realistic formats (`app/src/test`).
- Android only, installed as an APK (Google Play doesn't allow apps that read SMS). iOS doesn't let apps read SMS at all.

## Build & install
Needs JDK 17, Android SDK 35 and Gradle 8.11.1. The scripts assume these are in `E:\AndroidDev`; edit the paths in `build.ps1` if yours are elsewhere.
```powershell
# Unit tests + debug APK (output also in build.log)
.\build.ps1
# Build, then install on a USB-connected phone (USB debugging on) and open the app
.\install.ps1
```
Create `local.properties` with `sdk.dir=<path to your Android SDK>` (it's git-ignored).

## Project layout
```
app/src/main/java/com/spliwise/
  App.kt, MainActivity.kt        app + tiny DI container
  core/      Money (paise), Dates, Stats, Crypto (Keystore, PBKDF2)
  auth/      ProfileManager, LoginScreen (PIN pad, fingerprint)
  data/      Entities, Daos, SpliDb (SQLCipher, migrations), Repo
  classify/  Categories (+ merchant dictionary), Categorizer
  voice/     CommandParser (pure rules), VoiceInput (on-device speech)
  split/     SplitEngine, Ledger (balances, simplify debts)
  ingest/    Banks, SmsParser, StatementParser, Reconciler (gap report), Deduper, Importer, BankSettings, Background (SMS receiver, hourly sync, 11:50 PM job)
  notify/    Notifier, CategorizeReceiver
  ui/        Theme, Components, Charts, NumPad, MainApp + Home / Activity / Insights / Splits / Review / Statement / Ledger / Settings screens
app/src/test/java/com/spliwise/   40 unit tests: voice parser, split maths, bank SMS & PDF parsing, gap report, categoriser, de-duplication
```

## Tech
Kotlin 2.0 · Jetpack Compose (Material 3) · Room + SQLCipher · WorkManager · PdfBox-Android · Android SpeechRecognizer · BiometricPrompt · JUnit
