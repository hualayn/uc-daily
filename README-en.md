# UC Daily Record App

An Android app for recording daily life with ulcerative colitis (UC) — log what you eat, your bowel movements, your medication, and how you feel every day. It makes it easy to look back and answer "what did I eat before the symptoms started?", and easy to share everything with your doctor at follow-up visits.

中文版本：[README.md](./README.md)

## UI Layout (four bottom tabs + a center raised "+" quick-add button)

**Home**
- Welcome card: avatar + time-of-day greeting + a rotating daily encouragement banner; a medication reminder bell in the top-right corner (shows a red dot when a reminder time has passed but the dose is not recorded yet; tap it to jump back to today and open the add-medication panel; a red system notification is posted at the same time)
- Today card: date header (left/right arrows switch week/month) + calendar. Week view by default (swipe left/right to change week, tap to select a date; days with a bowel record are colored by activity level). **Swipe down to expand the full-month view** (swipe up to collapse; in month view, swipe or use the arrows to change month)
- Daily stats: meals / bowel movements / medications (count · total); tapping a stat card filters that day's records by category; tap it again or tap "reset" to clear the filter
- The center "+" button opens the add panel (a global overlay, available from any tab): 🍚 Meal, 💩 Bowel movement, 💊 Medication, 📝 Note
- Today's records: bowel / meal / medication entries mixed in chronological order (today's note pinned on top); on the home tab, tap a card to select it (single selection) to reveal edit/delete buttons

**Tolerance**
- Manage food tolerance states: tolerable (green) / try (yellow) / intolerant (red); foods are added from the "Add Meal" panel (pick the initial state when adding)
- Tap a tag to reveal an X badge for deletion; long-press a tag (hold 400 ms) to drag: release inside the same section = reorder, release in another section = change its tolerance state
- Shows how many meal records reference (tag) each food, making it easy to spot problem foods

**Daily Management**
- Daily management handbook (accordion cards): Diet / Lifestyle / Medication & Medical Care / Psychological Adjustment / Self-Assessment & Symptom Reference — five cards with mutually exclusive expansion (Diet is open by default)
- Self-assessment: typical flare-up vs. remission symptom comparison + a simple activity score (4 questions: bowel frequency, blood in stool, abdominal pain, body temperature; total 0–11 → remission / mild / moderate / severe activity)
- Evidence-based reference information; for self-reference only — it does not replace a doctor's diagnosis

**Profile**
- Avatar (default / male / female) and nickname (editable)
- Menu:
  - **Statistics**: record days / meal count / bowel-movement days / medication count, activity-level distribution, food-tolerance distribution
  - **Export records**: pick a date range + record types (meals / meds / bowel / notes), output as TXT / CSV (CSV also includes all food tolerance), save to clipboard or a file
  - **Restore records**: pick a CSV produced by "Export records" to restore daily records and food tolerance (identical rows on the same day are skipped automatically; daily notes are overwritten by date; food tags are added or updated by name)
  - **Medication settings**: set the daily reminder count and reminder times (drives the home-screen bell + system notification); includes an "on-time reminder (exact alarm)" permission status card that opens the system grant page when not granted
  - **Theme**: light / dark / follow system
  - **Language**: follow system / 简体中文 / English / 日本語 / 한국어 / Français / Deutsch / Italiano / Español / Português / Русский / العربية — switches instantly (see "Multilingual")
  - **App Update**: Google Play Core in-app update (see "Multilingual")
  - **About**: app information

## Features

- **Meal records**: breakfast / lunch / dinner / snack, multiple per day; camera capture or multi-select from the gallery (up to 9 photos at a time), multiple photos per record; text note + food tags (maintained on the "Tolerance" tab, multi-selectable when recording)
- **Bowel movement records**: multiple per day (one entry at a time, sorted by time); record count, nighttime diarrhea, stool consistency (Bristol Stool Scale 1–7), blood in stool, mucus, abdominal pain (0–10 score + location), urgency, other discomfort; backfilling a past date can adjust the recorded time
- **Activity score**: a simplified patient-self-report UCDAI (bowel frequency 0–4 + blood in stool 0–4) automatically computes 0–8, classified as remission / mild / moderate / severe; drives the calendar dots and record cards (for self-monitoring only, not a substitute for diagnosis)
- **Medication records**: medication name (with quick picks for frequent meds; long-press a tag to remove the quick pick) + dose, multiple per day, supports edit / delete
- **Medication reminders**: set the reminder count / times under "Profile → Medication settings"; a dose counts as missed when "number of reminder times already due > number of med records today". When missed: the home bell shows a red dot (tap to go straight to add-medication) + a system notification (persistent red status-bar icon / launcher badge, text "You still have N missed doses, please take your medication soon!"). Sync is triggered at each reminder time by exact alarms (AlarmManager) — reminders fire even when the app is backgrounded or killed by the system, and re-register after reboot; requires the notification permission (runtime prompt on Android 13+) and the exact-alarm permission (Android 12+, grant entry on the med-settings page)
- **Daily note**: one free-form text entry per day (bowel, sleep, mood, discomfort…); also supported when backfilling past dates
- **Record export**: TXT / CSV, filtered by date range and record type, output to clipboard or a file — handy for showing your doctor
- **Edit / delete**: every record card is editable (deletion asks for confirmation)
- **Fullscreen viewer**: tap a photo to view it enlarged (swipe between multiple photos)
- **Date backfill**: tap any date in the home week / full-month calendar to view or backfill that day's records
- **Theme**: light / dark / follow system (switch under "Profile → Theme")
- **Multilingual**: follows the system language by default; you can manually switch between 12 options under "Profile → Language" (follow system / 简体中文 / English / 日本語 / 한국어 / Français / Deutsch / Italiano / Español / Português / Русский / العربية). The UI, the medication-reminder notification and its channel description follow the selected language; Arabic automatically enables RTL layout. See the "Multilingual" section below.
- **In-app updates**: integrated Google Play Core (app-update 2.x) Flexible in-app updates — a silent check at startup, plus a manual check under "Profile → App Update"; when a new version is found: "Update now" → background download → "Restart now" to apply. See the "Multilingual" section below.

## Tech Stack

- **Kotlin** 2.2.10 / **AGP** 9.3.0 / **Gradle** 9.5.0
- **Jetpack Compose** + Material3 (bottom NavigationBar with four tabs + center quick-add; record panels are global overlays, openable from any tab)
- **Room** 2.7.1 (local SQLite, fresh database schema, version 1)
- **Coil** 2.7.0 (image loading)
- **MVVM** architecture (ViewModel + StateFlow)
- **Google Play Core** (`com.google.android.play:app-update:2.1.0`): Flexible in-app updates (delivers new versions of the multilingual app)

## Multilingual

- **String resources**: all UI copy lives in `app/src/main/res/values*/strings.xml` (Simplified Chinese by default + 10 locale directories: `values-en` / `values-ja` / `values-ko` / `values-fr` / `values-de` / `values-it` / `values-es` / `values-pt` / `values-ru` / `values-ar`). Code reads them via `stringResource(R.string.x)` / `context.getString(...)`; enum/list labels (meal types, tolerance states, Bristol scale, bleeding, pain location, activity level, theme, font size, weekdays, etc.) are defined as `@StringRes` resource ids.
- **Language switching**: `AppLocale` (`app/src/main/java/com/ucdaily/AppLocale.kt`) owns the language options and persistence (SharedPreferences `app_prefs / app_language`, default "follow system"); `UcDailyApp` and `MainActivity` wrap the base context with the selected locale in `attachBaseContext`, and `Activity.recreate()` applies the change instantly. The application-level locale ensures even background reminders (alarm / boot receiver / service) use the selected language.
- **Delivery via Google Play Core**: language resources ship inside the App Bundle (AAB) and Google Play delivers only the languages relevant to each user ("deliver languages relevant to the user" is enabled in Play Console), so users never download extra language packs. New versions (with new languages / features) are pushed through Google Play and can be installed in-app via Play Core (silent check at startup + manual check under "Profile → App Update" → background download → restart to apply). Note: in-app updates only work for builds installed from Google Play; sideloaded builds degrade silently.

## Requirements

- Android SDK 26+ (Android 8.0)
- JDK 17+ (the Gradle toolchain downloads the required JDK automatically at build time)
- Android Studio Iguana or later

## Build

```bash
# from the repo root
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
```

The APK is produced at `app/build/outputs/apk/debug/`.

## Project Structure

```
app/src/main/java/com/ucdaily/
├── UcDailyApp.kt   # Application: fixes the zh-CN locale
├── MainActivity.kt      # Entry: bottom tab layout, camera/gallery activity launchers, global panel layer
├── MedReminder.kt       # Med reminders: post/cancel system notification + exact-alarm scheduling + alarm/boot receivers (works in background)
├── ui/
│   ├── MealLogViewModel.kt  # ViewModel: state management (single MealUiState), drafts, data operations
│   ├── HomeScreen.kt        # Home: welcome card (med bell), calendar (week/full-month), daily stats, today's records
│   ├── ToleranceScreen.kt   # Tolerance tab: food tolerance management (tap to delete / drag to reorder / drag across sections to change state)
│   ├── DailyManagementScreen.kt # Daily Management tab: management handbook (accordion cards) + self-assessment score
│   ├── ProfileScreen.kt     # Profile tab: avatar/nickname + menu (stats / export / restore / med settings / theme / about)
│   ├── StatsScreen.kt       # Stats page: record volume, activity-level distribution, food-tolerance distribution
│   ├── MedSettingsScreen.kt # Med settings page: reminder count, reminder times, exact-alarm permission card
│   ├── RecordPanels.kt      # Global record panels: meal/bowel/med/note panels, today's record list, fullscreen photos
│   ├── MealLogScreen.kt     # Shared components: meal/bowel record cards, export dialog, weekday/activity helpers
│   └── Theme.kt             # Blue color theme (light/dark)
└── data/
    ├── MealRecord.kt      # Meal record entity + meal-type enum (food tags JSON codec)
    ├── MealRecordDao.kt   # Meal record DAO
    ├── DailySymptom.kt    # Bowel/symptom entity + activity score (simplified UCDAI)
    ├── DailySymptomDao.kt # Bowel record DAO
    ├── MedRecord.kt       # Medication record entity
    ├── MedRecordDao.kt    # Medication record DAO
    ├── DailyNote.kt       # Daily note entity (unique per date)
    ├── DailyNoteDao.kt    # Daily note DAO
    ├── FoodTag.kt         # Food tag entity + tolerance enum (tolerable/try/intolerant) + sort key
    ├── FoodTagDao.kt      # Food tag DAO
    └── AppDatabase.kt     # Database config (v1, fresh schema, no legacy migrations)
```

## Data Storage

- Records are stored in the local Room database `uc_daily_db` (tables: `meal_records` meals, `daily_symptoms` bowel, `med_records` meds, `daily_notes` notes, `food_tags` food tags; meals / bowel / meds allow multiple entries per day and bowel records carry a time; notes are unique per date)
- Photos are stored in the app-private directory (`getExternalFilesDir`) and are cleaned up automatically on uninstall
- Photos picked from the gallery are copied into the app-private directory, so they stay viewable until the app is uninstalled
- Preferences (nickname / avatar / theme / frequent meds / med reminder times) are stored in SharedPreferences
- Med reminders: the notification uses the system channel `med_reminder` (ongoing, tintable); on-time triggering uses system exact alarms (AlarmManager, one slot per reminder time, up to 6), re-registered on app launch / reminder-time change / boot complete

## About the Activity Score

Based on the patient-self-reportable parts of the UCDAI (Ulcerative Colitis Disease Activity Index) — used for daily records and calendar coloring:

- Bowel frequency score: ≤4/day = 0, 5–6 = 1, 7–10 = 2, 11–14 = 3, ≥15 = 4
- Blood in stool score: none = 0, slight = 1, obvious = 3, clots = 4
- Total 0–8: 0 = remission (green), 1–3 = mild (yellow), 4–5 = moderate (orange), 6–8 = severe (red)

> This score is for self-monitoring and medical reference only — it does not replace a doctor's diagnosis. The full UCDAI also requires physician assessment (general condition + disease extent).
>
> Note: the "Daily Management" tab provides an independent quick self-assessment (4 questions: bowel frequency / blood in stool / abdominal pain / body temperature, total 0–11) for a fast stage comparison. It uses a different scale from the UCDAI-based score above — do not mix the two.
