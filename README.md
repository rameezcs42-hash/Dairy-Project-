# DairyFlow — Smart Milk Business Manager

A fully **offline** Android app for Pakistani milk shops (kirana / dairy farm context):
track regular customers, daily morning/evening deliveries, payments, purchases,
stock, expenses and monthly billing — with branded PDF invoices and reports.

**Premium Emerald Green** UI, single-shop inventory, integer-paisa–free billing
(round2 on every money step), zero network permissions.

## Features

- **Dashboard** — outstanding dues hero, today stats (litres/sales/collections/stock),
  low-stock warning, month strip, recent payments, quick actions.
- **Customers** — searchable list (name/phone/area/shop), per-customer detail with
  balance hero (DUE / ADVANCE / SETTLED), contact actions (call / SMS bill),
  running ledger, one-tap monthly invoice PDF.
- **Daily Entry** — per-date desk with morning/evening filter, one tap to mark OFF,
  auto-skip on weekly-off day or one-off holiday, "Fill defaults", guaranteed
  no-duplicate saves (UNIQUE(customer,date,session)).
- **Payments** — cash / Easypaisa / JazzCash / bank, month & today totals,
  edit/delete, advances shown as negative balances.
- **Billing** — month navigation, per-customer statements (opening balance b/f,
  litres, billed, paid, closing), single or bulk PDF invoice generation,
  monthly summary PDF.
- **Reports** — daily sales, monthly summary, per-customer, outstanding dues,
  profit & loss, rate history; every report exports to PDF.
- **Rates** — effective-dated sale/purchase rates (buffalo/cow/mixed);
  historical bills keep historical rates.
- **Inventory** — stock = purchases − sales ± adjustments, purchase log with
  supplier/fat%, spoilage/wastage/return/recount adjustments, low-stock alert.
- **Expenses** — rent, salary, transport, electricity, ice, misc.
- **Backup & Restore** — one-tap `.dairyflow` export via the Storage Access
  Framework, validation (magic header + `PRAGMA integrity_check` + required tables)
  before restore, automatic daily backup (keeps newest 5).
- **PDF invoices** — branded A4, line items, opening balance, totals,
  amount-in-words footer.

## Project layout

```
app/src/main/
├── AndroidManifest.xml
├── java/com/dairyflow/app/
│   ├── DairyFlowApp.java            # Application: default settings, daily auto-backup
│   ├── SplashActivity.java          # branded splash → MainActivity
│   ├── MainActivity.java            # bottom nav host (Home / Customers / Entry / Payments / More)
│   ├── billing/BillingEngine.java   # pure-Java billing math (unit-tested, no Android deps)
│   ├── data/                        # SQLite: DatabaseHelper, models, DAOs
│   │   └── repository/BillingRepository.java
│   ├── pdf/PdfGenerator.java        # branded invoice PDF
│   ├── pdf/ReportPdf.java           # tabular report PDF
│   ├── backup/BackupManager.java    # export / validate / restore / auto-backup
│   ├── provider/ShareProvider.java  # read-only content provider for sharing PDFs/backups
│   └── ui/…                         # screens (dashboard, customers, entry, billing, …)
├── res/                             # layouts, drawables, values (emerald palette)
└── tools/build_apk.sh               # offline build pipeline (see below)
```

## Building

This tree was **not built with Gradle** (no network access to Google/Maven and no
Android SDK on the build machine). `tools/build_apk.sh` reproduces the APK with
plain tools: `aapt2` → Eclipse JDT (`ecj`) → `d8` → `zipalign` → `apksigner`.
Point `TOOLCHAIN` at a local Android SDK + ecj and an ad-hoc debug keystore.

```bash
TOOLCHAIN=/path/to/toolchain ./tools/build_apk.sh   # outputs DairyFlow-debug.apk
```

Continuous integration note: importing this tree into Android Studio will **not**
work as-is because there is no Gradle wrapper — the app sources are plain Java
against the framework SDK only (no androidx), by design, to stay buildable offline.

## Verification status

- Billing engine: **35/35 unit tests pass** (duplicate deliveries, skipped days,
  advance payments, rate changes, month-end statements, opening balance carry).
- Resource linking and signing: **verified** (`aapt2 link`, `apksigner` OK).
- **Not yet run on an emulator/device** — see PR description for the honest state.

## License

Proprietary — all rights reserved by the repository owner.
