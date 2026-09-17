# Repository Guidelines

## Project Structure & Module Organization

ZeroPay is an offline Android cashier and inventory app using Java 17, native Android views, and SQLite. The single Gradle module is `app/`.

- `app/src/main/java/com/zeropay/store/`: `MainActivity` handles UI, `StoreDb` owns persistence and transactions, and `Product`, `Promotion`, `BundleOffer`, and `Csv` implement domain logic.
- `app/src/main/res/`: themes and launcher artwork; the manifest is in `app/src/main/`.
- `app/src/test/java/com/zeropay/store/`: JVM tests; `app/src/androidTest/` contains device smoke tests.
- `samples/products.csv`: import example. `docs/VALIDATION.md` records verification; `docs/screenshots/` contains UI references.
- `app/build/` and `dist/` contain generated outputs and are ignored.

## Build, Test, and Development Commands

Use JDK 17–25, Android SDK Platform 36, and Build Tools 36.0.0. Configure `sdk.dir` in local `local.properties` or set `ANDROID_HOME`. Use the bundled Gradle wrapper (9.2.1).

- `./gradlew.bat assembleDebug`: build `app/build/outputs/apk/debug/app-debug.apk`.
- `./gradlew.bat testDebugUnitTest`: run JVM tests.
- `./gradlew.bat lintDebug`: run Android Lint.
- `./gradlew.bat installDebug`: install on a connected emulator/device; launch ZeroPay there.
- `./gradlew.bat connectedDebugAndroidTest`: run device tests; requires downloadable UTP dependencies. See README for the direct `adb` alternative.

On Unix, use `./gradlew` instead. Run build, unit tests, and lint before submitting code changes.

## Coding Style & Naming Conventions

Use four-space indentation, `PascalCase` classes, `lowerCamelCase` methods/fields, and `UPPER_SNAKE_CASE` constants. Follow nearby Java formatting; avoid unrelated reformatting of compact existing code. Keep user-facing text in Chinese. No dedicated formatter is configured; Android Lint supplies static checks.

## Testing Guidelines

Tests use JUnit 4 and Robolectric; device tests use AndroidX Test. Name test classes `*Test` and methods by expected behavior. Add regression tests for changed calculations, CSV handling, database migrations, and transaction rollback. No numeric coverage threshold is configured. Device tests create `TEST001/TEST002` products: use dedicated test devices. Manually verify camera scanning, hardware scanners, and file pickers when affected.

## Commit & Pull Request Guidelines

The available history uses `feat: add order promotions`; follow the `type: concise description` pattern. Keep commits focused. PRs should describe behavior changes, link relevant issues, list validation results and limitations, and include screenshots for UI changes. Update README and validation notes when behavior changes.

## Data & Configuration Rules

Store money as integer cents, preserve barcode leading zeros, and keep sales and stock changes transactional. Preserve existing data during schema upgrades. Never commit signing credentials, local SDK paths, or real store data.
