# Kaloria

A calorie and nutrition tracker for Android built on exact data: you set your own targets, and
Kaloria never estimates TDEE or calories burned (steps are shown as a count, nothing more). Foods
come from your own database, built by photographing nutrition labels and reading them on-device,
plus Open Food Facts and USDA FoodData Central. Everything works offline and syncs to your own
PocketBase server.

## Features

- **Today**: calories left, macros, micronutrients, water and steps on one glass dashboard, and a
  timeline of everything logged. Tap an entry to change the amount or time.
- **Label scanning**: ML Kit text recognition, then Gemini Nano on supported phones (Galaxy S24+,
  Pixel 9+), with a rule-based parser for Hungarian, English, Turkish and Azerbaijani labels as
  the fallback. Values land in an editor next to the photo; doubtful fields are highlighted
  (energy that doesn't match the macros, sugars above carbs, AI vs text disagreements, …).
- **Barcodes**: your own foods first, then Open Food Facts; unknown products go straight to a
  label scan.
- **Search**: your foods, Open Food Facts (branded) and USDA (generic) in one list. Online foods
  are copied into your database the first time you log them.
- **Progress**: energy, macros, weight, steps and micronutrients over 7/30/90 days.
- **Widget**: calories left, macros and water, with Scan, +250 ml and Search buttons.
- **Sync**: Room is the source of truth; WorkManager pushes and pulls PocketBase changes with
  last-write-wins on the client timestamp, enforced on the server too.

## Stack

Kotlin 2.4 · AGP 9.5 (built-in Kotlin) · Compose with Material 3 Expressive (fixed ink-blue brand palette) · Liquid Glass via Kyant's Backdrop (AGSL refraction, specular highlights) ·
Navigation 3 · Hilt · Room · DataStore · WorkManager · CameraX · ML Kit (barcode, text, GenAI
Prompt API) · Health Connect · Glance · Ktor · minSdk 34, targetSdk 37.

## Modules

| Module | Contents |
|---|---|
| `app` | Activity, Navigation 3 back stack, glass dock, widget shortcuts |
| `core:model` | Pure-Kotlin domain types and ids |
| `core:database` | Room entities and DAOs, each with `updatedAt` / `deleted` / `dirty` |
| `core:datastore` | Preferences, and the PocketBase session (token encrypted with a Keystore key) |
| `core:network` | Open Food Facts, USDA and PocketBase clients |
| `core:data` | Offline-first repositories and the food catalog |
| `core:sync` | Sync engine, WorkManager worker and scheduler, account |
| `core:scanner` | OCR, Gemini Nano extraction, the rule-based label parser, barcode analysis |
| `core:health` | Health Connect step counts |
| `core:designsystem` | Theme, glass backdrop and surfaces, rings, bars and charts |
| `core:ui` | Shared composables (portion sheet, food row, number fields) |
| `feature:*` | `today`, `foodsearch`, `foods`, `scan`, `progress`, `settings` |
| `widget` | Glance home screen widget |
| `build-logic` | Convention plugins |
| `server/` | PocketBase image, migrations and the last-write-wins hook |

## Setup

`local.properties` (not committed):

```properties
sdk.dir=/Users/you/Library/Android/sdk
usda.apiKey=YOUR_KEY   # free at https://fdc.nal.usda.gov/api-key-signup ; DEMO_KEY otherwise
```

```sh
./gradlew installDebug                     # dev.southstar21.kaloria.debug
./gradlew testDebugUnitTest                # unit tests, incl. label parser and sync engine
./gradlew verifyRoborazziDebug             # screenshot tests (recordRoborazziDebug to update)
```

### PocketBase

```sh
cd server && docker compose up -d          # http://localhost:8090, admin UI at /_/
```

Create your user in the admin UI (public sign-up is disabled). Debug builds default to
`http://10.0.2.2:8090` (the emulator's view of your machine); release builds to
`https://apikaloria.southstar21.dev`. On a phone on your LAN, enter the machine's IP; Android 17
asks once for local network access.

Ids are generated on the device in PocketBase's format (15 × `[a-z0-9]`), so rows created
offline keep the same id on the server. Nutrients are stored as JSON so "not on the label"
stays distinct from zero.
