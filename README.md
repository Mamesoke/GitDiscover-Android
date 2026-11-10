# GitDiscover for Android

A small native Kotlin application for searching and exploring public GitHub
repositories, built with Jetpack Compose and Material 3. It uses three layers,
request cancellation, an in-memory cache and automated tests.


https://github.com/user-attachments/assets/b747c2ea-6c65-4225-b200-061c8949e921


> **Recorded verification as of November 10, 2026:** 38 JVM tests passed, along
> with 9 instrumented tests on each emulator: Small Phone running Android 7.0 and
> Pixel Tablet running Android 16. Lint and Debug/Release builds completed.
> See [TEST_PLAN.md](TEST_PLAN.md) for the evidence and the checks exercised.

## MVP features

- Search repositories with at least two user-perceived characters after trimming
  surrounding whitespace, using a 400 ms debounce.
- Request the first page of up to 30 results and open repository details.
- Cancel previous searches and reject superseded responses.
- Cache successful results, including empty results, for 180 seconds.
- Display loading, empty, connectivity and rate-limit states.
- Retry manually, with a countdown when GitHub supplies a cooldown deadline.
- Show all detail fields without another request and open the repository URL
  through Android's browser intent.
- Retain state in a ViewModel and restore the query and selected repository
  snapshot through SavedStateHandle.

## Public API

GitHub REST API:

```text
GET https://api.github.com/search/repositories?q=<query>&per_page=30&page=1
```

Public search does not require an account, token or API key. Requests include
`Accept: application/vnd.github+json`, `User-Agent: GitDiscover` and
`X-GitHub-Api-Version: 2026-03-10`.

OkHttp builds structured query parameters to preserve characters such as `+`,
`%`, `&`, quotes and Unicode, including qualifiers such as `C++ language:C++`.
Tests assert what a local server actually receives.

Rate-limit responses establish a shared cooldown across queries using
`Retry-After` or `X-RateLimit-Reset`, with a 60-second fallback. The client does
not retry automatically; fresh cached results remain available during cooldown.

GitHub documentation: [search](https://docs.github.com/en/rest/search/search),
[rate limits](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api)
and [API versions](https://docs.github.com/en/rest/about-the-rest-api/api-versions).

## Requirements

- Android Studio compatible with Android Gradle Plugin 9.4.1.
- JDK 25, specified by the
  [daemon criteria](gradle/gradle-daemon-jvm.properties). Recorded local validation
  used Android Studio's bundled JBR 25.
- Android SDK Platform 37.
- A device or emulator running Android 7.0 (API 24) or later.
- Internet access for live searches and initial dependency resolution.
  Automated suites do not access GitHub.

The project includes the Gradle 9.6.0 wrapper. Dependency versions are managed in
the [version catalog](gradle/libs.versions.toml).

## Android 7.0 compatibility

The configured minimum is API 24; `compileSdk` and `targetSdk` are 37. Compiling
with a recent SDK does not by itself raise the minimum Android version.
Java source and target compatibility are set to 11.

Compose, Lifecycle and Navigation provide the native UI, state handling and
navigation. ICU4J applies consistent user-perceived character rules across
supported runtimes: a single emoji with a modifier or a ZWJ sequence does not
count as two characters. The Unicode corpora have passing results on the JVM,
API 24 and API 36.

## Concurrency and state

The ViewModel exposes immutable state through StateFlow and uses
`viewModelScope`. Each edit cancels the previous Job, including the HTTP call
through `Call.cancel()`. A generation counter prevents late responses from
updating the current state.

The app collects state with `collectAsStateWithLifecycle()`. SavedStateHandle
stores the query and selected repository fields, not the complete result list
or active work. Restored detail does not require network access.

## Run the app

1. Open this directory in Android Studio and sync Gradle.
2. Configure the JDK and SDK listed above. Android Studio sets the SDK path in
   `local.properties`; do not commit that file. For terminal commands, set
   `JAVA_HOME` to the required JDK.
3. Select an API 24 or later device/emulator and run `app`.
4. Search for `kotlin`, select a repository and inspect its details.

From the terminal, with the SDK and a device configured:

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

The Debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

Normal launches use the real API. UI tests inject offline repositories through
a Debug-only Activity. Production queries never activate fixtures, and fixture
classes are excluded from Release.

## Run tests

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleRelease
```

Instrumentation requires a connected device or emulator. Release produces an
optimized APK without a distribution signature.

JUnit and coroutines-test verify Domain and Presentation using fake repositories
and virtual time. Data tests use an injected clock and MockWebServer on loopback,
including real transport cancellation. Compose tests use offline fixtures and
retain the production debounce.

## Architecture

```text
Presentation (Compose + SearchViewModel + StateFlow)
                         |
                         v
Domain (Repository + RepositorySearching + SearchRepositoriesUseCase)
                         ^
                         |
Data (OkHttp + DTOs + cache decorator + cooldown)
```

- **Composition:** one `app` module, constructor injection and an
  Application-owned container with one HTTP client, cache and cooldown per
  process.
- **Cache:** normalized keys without changing the expression sent to GitHub.
  Quoted whitespace is preserved, lowercasing uses Locale.ROOT and Mutex protects
  storage. An entry is fresh while `now < expiresAt`; equality is expired.
  Errors and results observed as canceled are not inserted.
- **Rate limits:** the cache is checked before the shared network gate.
  Concurrent responses can extend the deadline, never shorten it.
- **Detail:** search result fields and a selected repository snapshot provide
  detail rendering and restoration.
- **Android:** Material 3, light/dark themes, dynamic colors on Android 12+,
  English UI resources, locale-formatted counts and Navigation Compose.

Source layout under `app/src/main/java/com/mamesoke/gitdiscover/`:

```text
GitDiscoverApplication.kt   Application-owned dependency container
MainActivity.kt            Android entry point and browser intent
app/                       Container and navigation
domain/                    Entities, contract, errors, rules and use case
data/                      HTTP transport, DTOs, cache and repository decorator
presentation/              State, ViewModel and search/detail screens
ui/theme/                  Material 3 theme
```

JVM tests live in `app/src/test/`, Compose tests in `app/src/androidTest/` and
fixtures in `app/src/debug/`.

## Verification record

| Area | Recorded check |
|---|---|
| JVM tests | 38 passed: 5 Domain, 7 cache, 12 API/transport and 14 Presentation. |
| Instrumented tests | 9 passed on Small Phone API 24 and 9 on Pixel Tablet API 36, with no failures or skips. |
| Lint and builds | Lint reported no errors; Debug and Release were built. Release artifacts were inspected for fixture exclusion. |
| Release startup | An earlier manual check installed and launched the minified APK on API 24 and API 36 with a local development signature. |
| Search and restoration | Earlier manual checks covered live search on both devices and offline detail restoration after process recreation. |
| UI | Earlier visual checks covered phone/tablet search/detail and tablet portrait/landscape, 150% text and dark mode. |

Link tests assert the URL delivered to the injected callback and visible
feedback when no browser is available, without opening an external application.
Manual checks above are recorded separately from automated test results.

See [SPEC.md](SPEC.md), [ARCHITECTURE.md](ARCHITECTURE.md),
[TEST_PLAN.md](TEST_PLAN.md) and [TASKS.md](TASKS.md) for further details.

## License

Distributed under the [MIT License](LICENSE).
