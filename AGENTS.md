# Repository Guidelines

## Project Structure & Module Organization

ZeroPay is an offline Android cashier and inventory app using Java 17, native Android views, and SQLite. The single Gradle module is `app/`.

- `app/src/main/java/com/zeropay/store/`: `MainActivity` handles UI, `StoreDb` owns persistence and transactions, and `Product`, `Promotion`, `BundleOffer`, and `Csv` implement domain logic.
- `app/src/main/res/`: themes and launcher artwork; the manifest is in `app/src/main/`.
- `app/src/test/java/com/zeropay/store/`: JVM tests; `app/src/androidTest/` contains device smoke tests.
- `samples/products.csv`: import example. `docs/VALIDATION.md` records verification; `docs/screenshots/` contains UI references.
- `build/` and `dist/` contain generated outputs and are ignored; module build outputs live in `build/app/`.

## Build, Test, and Development Commands

Use JDK 17–25, Android SDK Platform 36, and Build Tools 36.0.0. Configure `sdk.dir` in local `local.properties` or set `ANDROID_HOME`. Use the bundled Gradle wrapper (9.2.1).

- `./gradlew.bat assembleDebug`: build `build/app/outputs/apk/debug/app-debug.apk`.
- `./gradlew.bat testDebugUnitTest`: run JVM tests.
- `./gradlew.bat lintDebug`: run Android Lint.
- `./gradlew.bat installDebug`: install on a connected emulator/device; launch ZeroPay there.
- `./gradlew.bat connectedDebugAndroidTest`: run device tests; requires downloadable UTP dependencies. See README for the direct `adb` alternative.

On Unix, use `./gradlew` instead. Run build, unit tests, and lint before submitting code changes.

## Version Packaging

Use the existing debug-signing and filename conventions unless the user requests another package type.

1. Set `versionName` in `app/build.gradle` to the requested version and increment `versionCode` for a new version; do not increment it again when rebuilding the same version. Version `1.3.0` uses `versionCode 4`.
2. Keep `layout.buildDirectory = rootProject.layout.buildDirectory.dir('app')` in `app/build.gradle` so build files stay under the repository-root `build/app/` directory.
3. Run `./gradlew.bat assembleDebug testDebugUnitTest lintDebug` from the repository root and resolve failures before delivery.
4. Copy `build/app/outputs/apk/debug/app-debug.apk` to `dist/ZeroPay-<version>-debug.apk`, matching the existing flat directory format. Preserve older version packages.
5. Generate `dist/ZeroPay-<version>-debug.apk.sha256` as ASCII text containing the lowercase SHA-256 hash, two spaces, and the APK filename.
6. Verify the delivered APK with Android SDK Build Tools: `apksigner.bat verify --verbose <apk>` and `aapt.exe dump badging <apk>`. Confirm the signature, application ID `com.zeropay.store`, version name, and version code. Locate Build Tools through the configured SDK rather than hard-coding a machine-specific path.
7. Report the APK and checksum paths, debug-signing status, test results, and Lint errors/warnings. Record packaging validation in `docs/VALIDATION.md` and keep README packaging instructions current when they change.

PowerShell delivery example (after a successful build; replace the version as needed):

```powershell
$packageVersion = '1.3.0'
$packageName = "ZeroPay-$packageVersion-debug.apk"
$packagePath = Join-Path 'dist' $packageName
New-Item -ItemType Directory -Path 'dist' -Force | Out-Null
Copy-Item -LiteralPath 'build/app/outputs/apk/debug/app-debug.apk' -Destination $packagePath
$packageHash = (Get-FileHash -LiteralPath $packagePath -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -LiteralPath "$packagePath.sha256" -Value "$packageHash  $packageName" -Encoding ascii
```

## Coding Style & Naming Conventions

Use four-space indentation, `PascalCase` classes, `lowerCamelCase` methods/fields, and `UPPER_SNAKE_CASE` constants. Follow nearby Java formatting; avoid unrelated reformatting of compact existing code. Keep user-facing text in Chinese. No dedicated formatter is configured; Android Lint supplies static checks.

## Testing Guidelines

Tests use JUnit 4 and Robolectric; device tests use AndroidX Test. Name test classes `*Test` and methods by expected behavior. Add regression tests for changed calculations, CSV handling, database migrations, and transaction rollback. No numeric coverage threshold is configured. Device tests create `TEST001/TEST002` products: use dedicated test devices. Manually verify camera scanning, hardware scanners, and file pickers when affected.

## Commit & Pull Request Guidelines

The available history uses `feat: add order promotions`; follow the `type: concise description` pattern. Keep commits focused. PRs should describe behavior changes, link relevant issues, list validation results and limitations, and include screenshots for UI changes. Update README and validation notes when behavior changes.

## Data & Configuration Rules

Store money as integer cents, preserve barcode leading zeros, and keep sales and stock changes transactional. Preserve existing data during schema upgrades. Never commit signing credentials, local SDK paths, or real store data.
