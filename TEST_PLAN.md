# GitDiscover - Android Test Coverage

**Reviewed:** November 10, 2026.

This document records implemented tests and recorded Android verification.
Related documents: [Specification](SPEC.md), [Architecture](ARCHITECTURE.md),
[Delivery Record](TASKS.md).

## 1. Test foundations

Automated suites run without GitHub access:

- JUnit and `runTest` with virtual scheduling and a test Main dispatcher.
- Fake repositories with controlled completions for ordering and cancellation.
- Injected clocks for cache TTL and rate-limit deadlines.
- MockWebServer on loopback for HTTP integration and real transport cancellation.
- Compose instrumentation with Debug-only offline fixtures and the production
  400 ms debounce.

UI fixtures exercise presentation and navigation. Separate Data tests verify
the production HTTP/cache integration.

## 2. Suite inventory

| Suite | Tests | Implemented coverage |
|---|---|---|
| [Domain](app/src/test/java/com/mamesoke/gitdiscover/domain/SearchRepositoriesUseCaseTest.kt) | 5 | Unicode trimming and grapheme validation, preserved expressions/fields, typed errors and cancellation. |
| [Cache](app/src/test/java/com/mamesoke/gitdiscover/data/SearchResultCacheTest.kt) | 7 | Hits at 30/179 seconds, expiry at exactly 180 seconds, replacement values, empty hits, errors, quoted keys, locale independence, cancellation-safe writes and concurrent misses. |
| [API/transport](app/src/test/java/com/mamesoke/gitdiscover/data/GitHubApiClientTest.kt) | 12 | DTOs/URLs, HTTP and connectivity errors, exact encoded queries/headers, cooldown deadlines and precedence, overlapping responses, cache bypass, late responses and real HTTP cancellation. |
| [Presentation](app/src/test/java/com/mamesoke/gitdiscover/presentation/SearchViewModelTest.kt) | 14 | Debounce boundaries, immediate cancellation, stale-response rejection, clear/retry/empty/error states, cache use, saved-state reconstruction and ViewModel clearing. |
| [Compose/device](app/src/androidTest/java/com/mamesoke/gitdiscover/GitDiscoverUiTest.kt) | 9 per device | Search/detail/Back/clear, null fallbacks, empty results, manual retry, immediate rate-limit blocking, retained content after Activity recreation, unavailable-browser feedback and two Unicode corpora. |

The JVM inventory totals **38 tests**. The instrumented inventory totals
**18 executions** across the two devices recorded below.

## 3. Requirement traceability

Each row describes the evidence actually exercised for the corresponding
[functional requirement](SPEC.md), rather than certifying every variation of
its implementation behavior.

| Requirement | Implemented evidence |
|---|---|
| F01 | [Domain tests](app/src/test/java/com/mamesoke/gitdiscover/domain/SearchRepositoriesUseCaseTest.kt): `trimsOnlySurroundingsAndReturnsAllFields`, `invalidGraphemesNeverCallRepository`, `twoGraphemesAreValid`, `extendedEmojiSequencesCannotBypassMinimumLength`; [device corpus](app/src/androidTest/java/com/mamesoke/gitdiscover/GitDiscoverUiTest.kt): `platformGraphemePolicyMatchesDomainCorpus`, `extendedGraphemeSequencesDoNotBypassMinimum`, passed on API 24 and API 36. |
| F02 | [Presentation tests](app/src/test/java/com/mamesoke/gitdiscover/presentation/SearchViewModelTest.kt): `initialIdleInvalidQueriesAndExactDebounceBoundary`, `editAt399RestartsDebounceAndHidesOldResultsImmediately`. |
| F03 | [API tests](app/src/test/java/com/mamesoke/gitdiscover/data/GitHubApiClientTest.kt): `cancelStartedRequestReachesCallAndTerminatesTransport`, `cancelWhileReadingBodyTerminatesCallbackWithoutConnectivityFailure`, `lateNonCooperativeResponseIsRejected`; [Presentation tests](app/src/test/java/com/mamesoke/gitdiscover/presentation/SearchViewModelTest.kt): `lateSuccessOrFailureCannotOverwriteNewGeneration`, `newEditCancelsInFlightBeforeNextDebounceFinishes`, clear/current-cancellation/ViewModel-clear cases. |
| F04 | [API tests](app/src/test/java/com/mamesoke/gitdiscover/data/GitHubApiClientTest.kt): `realTransportReceivesExactQueriesHeadersAndOnlyOneAttempt`; live `kotlin` searches on API 24 and API 36. |
| F05 | [API tests](app/src/test/java/com/mamesoke/gitdiscover/data/GitHubApiClientTest.kt): `mapsAllFieldsAndPreservesAbsentVersusEmptyOptionalFields`, `failsWholeResponseForInvalidUrlsOrMissingRequiredFields`. |
| F06 | [UI tests](app/src/androidTest/java/com/mamesoke/gitdiscover/GitDiscoverUiTest.kt): `searchDetailBackAndClearPreserveStateAndAvoidDetailFetch`; live result rows visually inspected on both devices. |
| F07 | [UI tests](app/src/androidTest/java/com/mamesoke/gitdiscover/GitDiscoverUiTest.kt): selected detail fields and URL callback, `nullFieldsHaveDetailFallbacks`, `unavailableBrowserShowsVisibleError`; actual offline process-restored detail inspected. |
| F08 | [Cache tests](app/src/test/java/com/mamesoke/gitdiscover/data/SearchResultCacheTest.kt): `normalizesKeysWithoutChangingRemoteExpression`, `preservesQuotedWhitespaceEscapesAndLocaleIndependence`. |
| F09 | [Cache tests](app/src/test/java/com/mamesoke/gitdiscover/data/SearchResultCacheTest.kt): `hitsBeforeExpiryRefreshesAtExactBoundaryAndCachesReplacement`, `cachesEmptySuccessButNotErrors`, `canceledLateSuccessCannotPopulateCache`, `cancellationInsideInsertionClockPreventsMutation`, `concurrentMissesAreNotCoalescedAndRemainSafe`. |
| F10 | [Domain tests](app/src/test/java/com/mamesoke/gitdiscover/domain/SearchRepositoriesUseCaseTest.kt): `typedErrorsAndCancellationPropagate`; [API tests](app/src/test/java/com/mamesoke/gitdiscover/data/GitHubApiClientTest.kt): status/connectivity/decoding cases; [Presentation tests](app/src/test/java/com/mamesoke/gitdiscover/presentation/SearchViewModelTest.kt): `unexpectedErrorsAreReportedAndVisible`, current-cancellation and empty/retry cases. |
| F11 | [API tests](app/src/test/java/com/mamesoke/gitdiscover/data/GitHubApiClientTest.kt): rate classifications, fractional/date/invalid-header/equality cases, cache bypass, both overlap orders and `overlappingHttpResponsesDoNotShortenCooldownOrCancelStartedCalls`. |
| F12 | [Presentation tests](app/src/test/java/com/mamesoke/gitdiscover/presentation/SearchViewModelTest.kt): `rateLimitExpiryOnlyEnablesManualRetry`, `failureRetryAndEmptySuccessUseCurrentQueryAndDebounce`; [UI tests](app/src/androidTest/java/com/mamesoke/gitdiscover/GitDiscoverUiTest.kt): `connectivityFailureHasManualRetry`, `rateLimitDisablesImmediateRetry`. |
| F13 | [UI suite](app/src/androidTest/java/com/mamesoke/gitdiscover/GitDiscoverUiTest.kt): clear/Back/field semantics and retained results/detail content after Activity recreation; live phone/tablet search/detail and tablet portrait/landscape, 150% text and dark-mode visual checks. |
| F14 | Five offline UI workflows in the [UI suite](app/src/androidTest/java/com/mamesoke/gitdiscover/GitDiscoverUiTest.kt), local HTTP/cache integration, Release manifest and R8 mapping inspection excluding `testing` classes. |

Activity recreation coverage checks retained results/detail content.
SavedStateHandle reconstruction is tested separately in Presentation, and actual
process restoration was checked manually as recorded below. Browser tests
assert the URL delivered to the injected callback and visible no-handler feedback;
they do not launch an external browser.

## 4. Commands

Use the checked-in wrapper from the project root:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleRelease
```

Instrumentation requires a connected emulator or device. The configured daemon
uses JDK 25. For the latest JVM-only verification, cached dependencies were used:

```bash
./gradlew --offline :app:testDebugUnitTest --rerun
```

## 5. Execution record

Checks recorded on November 10, 2026. Automated results are backed by local
test/lint reports and build artifacts. Manual rows retain the earlier execution
record and are not part of the automated suites.

| Check | Recorded result |
|---|---|
| JVM tests | 38 passed: 5 Domain, 7 cache, 12 API/transport and 14 Presentation. Zero failures/errors/skips, including the latest offline rerun. |
| Lint | Zero errors and 21 warnings in the recorded Debug report; no failure baseline or suppression was added. |
| Debug/Release | Both builds passed. Release produced an unsigned optimized APK; the manifest and R8 mapping excluded fixture activity/classes. |
| Release startup (manual record) | A copy signed with the local development key was installed and launched on API 24 and API 36. |
| Small Phone | Android 7.0 / API 24, arm64, 360x640 dp: 9 instrumented tests passed, zero failures/errors/skips. |
| Pixel Tablet | Android 16 / API 36, arm64, 1280x800 dp landscape: 9 instrumented tests passed, zero failures/errors/skips. |
| Live search/detail (manual record) | A production `kotlin` search on each device returned repositories; the result list and `JetBrains/kotlin` detail were inspected. |
| Process restoration (manual record) | After backgrounding, the app process was killed and a different process restored the selected detail with Wi-Fi/mobile data disabled. Back retained the query and showed an explicit connectivity error with Retry while offline. |
| Tablet layout (manual record) | Portrait/landscape, 150% text and dark mode were visually inspected; detail fields/link remained visible and Back retained query/results. |

The final connected run generated XML reports for both named devices. JVM
results are under `app/build/test-results/testDebugUnitTest/`; device results are
under `app/build/outputs/androidTest-results/connected/debug/`.

## 6. Runtime and build checks

- ICU4J 78.1 supplies consistent grapheme segmentation. Shared Domain/device
  corpora have passing results on the JVM, API 24 and API 36.
- The small-screen Retry workflow scrolls the enabled button into view while
  the keyboard occupies the display.
- Release enables whole-app optimization without selective `packageScope`;
  the generated R8 mapping contains no Debug fixture classes.
- The version catalog declares AndroidX JUnit 1.3.0 and Espresso 3.7.0 for
  instrumentation, matching the recorded device suites.
