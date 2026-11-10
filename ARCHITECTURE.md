# GitDiscover - Android Architecture

**Updated:** November 10, 2026.
**Status:** implemented; recorded verification is in
[Test Coverage](TEST_PLAN.md).

## Layers and dependency direction

Lightweight Clean Architecture, MVVM and unidirectional state inside `:app`.
Package boundaries keep the two-screen app simple without extra Gradle modules
or a DI framework.

```text
MainActivity -> GitDiscoverApp / Navigation Compose
                          |
                    SearchViewModel
                          |
                SearchRepositoriesUseCase
                          |
             RepositorySearching (Domain)
                          ^
                          |
        CachedRepositorySearchRepository (Data)
                   |                  |
           SearchResultCache    GitHubApiClient
                                      |
                                 HttpClient
                                      |
                                OkHttpTransport

RepositoryDetailScreen <- selected domain snapshot; no network dependency
```

Dependencies follow `Presentation -> Domain <- Data`. Domain has no Android,
UI, serialization or HTTP imports. `TimeProvider` is a small Domain contract
shared by the cache, cooldown and presentation.

`GitDiscoverApplication` owns a lazily created `AppContainer`: one HTTP client,
cache and cooldown per process. Constructor injection connects the graph to an
Activity-scoped ViewModel using a factory and `createSavedStateHandle()`.
Screens receive immutable state and event callbacks, not repositories.

The remote implementation is `GitHubApiClient` itself.

## Build and libraries

The checked-in Gradle 9.6.0 wrapper, AGP 9.4.1 and Kotlin Compose/serialization
plugins 2.2.10 use JDK 25 according to the current daemon criteria. Device
validation and builds passed with Android Studio's installed JBR 25.
Java source/target compatibility is 11, with minimum SDK 24 and compile/target SDK 37.

Versions live in `gradle/libs.versions.toml`. Compose uses the existing BOM.
Lifecycle 2.8.7, Navigation Compose 2.8.9, coroutines 1.10.2, OkHttp 4.12.0 and
kotlinx.serialization 1.9.0 cover the required responsibilities.
ICU4J 78.1 supplies the same Unicode grapheme rules on every supported runtime.
JUnit/coroutines-test/MockWebServer cover JVM behavior. Instrumentation uses
AndroidX test JUnit 1.3.0 and Espresso 3.7.0.

## Query rules and Domain

`Repository` is immutable, uses Long identifiers/counts and preserves nullable
description/language. `RepositorySearching` exposes a suspending search contract;
`RepositorySearchException` identifies failures without transport types.

The use case checks cancellation before validation and after suspension,
trims only surrounding Unicode whitespace and avoids repository calls for
queries shorter than two extended grapheme clusters. `QueryRules` is shared
with the ViewModel.

The implementation uses ICU4J's character BreakIterator with Locale.ROOT and a
local iterator per call. Its counting function remains injectable. Bundled
Unicode rules keep modifier/ZWJ segmentation consistent without ad-hoc
emoji-merging rules or Android framework dependencies in Domain.
The common and extended emoji/modifier/ZWJ/combining/flag/keycap corpus now passes
JVM, API 24 and API 36 tests. Bundling ICU data increases APK size; this is an
intentional tradeoff for consistent, maintained segmentation across OS versions.

Release uses the standard whole-app
[AGP optimization configuration](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization).
The Release configuration enables optimization without a selective
`packageScope`. Generated R8 artifacts confirm the optimized build. Earlier
manual checks also installed and launched the minified APK on API 24 and API 36.

## Cancellation and presentation

`SearchUiState` contains the text, selected entity and sealed Idle/Loading/
Results/Empty/Failed content. Every edit increments a generation, cancels the
previous Job immediately and hides previous results. Valid input starts one Job
covering the 400 ms debounce and use-case call; invalid input remains Idle.

Cancellation checks and generation comparison guard success and failure
publication, including non-cooperative dependencies. Cancellation is rethrown,
never displayed as connectivity failure; current dependency cancellation
restores Idle. Unexpected exceptions are logged and shown as explicit failures.

Work belongs to `viewModelScope`, not a collector or composition. `GitDiscoverApp`
collects state with `collectAsStateWithLifecycle()`. The countdown recomputes from an absolute
deadline once per second while STARTED and stops at expiry; returning from the
background recalculates it. Expiry only enables manual Retry.

## HTTP and mapping

Structured OkHttp query parameters preserve literal plus signs and Unicode.
The API uses the headers and first-page contract in [Specification](SPEC.md).
Redirects, transparent retries and HTTP disk caching are disabled; the total
call timeout is 30 seconds. The app's source manifest requests INTERNET and
disables cleartext traffic. AndroidX adds its signature-protected
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` to the merged manifest.

`OkHttpTransport` bridges asynchronous callbacks with
`suspendCancellableCoroutine` and `Call.cancel()`. Response/body ownership stays
inside `use`; cancellation while waiting for headers or reading the body closes
the call. Cancellation propagates rather than becoming a visible connectivity
error; transport tests cover cancellation before headers and during body reads.
JSON decoding and mapping run on IO, not the main thread.

DTOs tolerate unknown fields but require all non-optional fields. Missing/null
optional values remain null. Mapping rejects a non-HTTPS/non-`github.com` URL
or user information, failing the whole response rather than dropping items.
All detail fields come from search results.

OkHttp exposes HTTP responses. Unexpected HTTP statuses, missing/malformed payloads,
access denial, server failure and connectivity have explicit error mappings.

## Cache and rate limits

The cache decorator reads before the remote gate, so valid entries remain
available during cooldown. It stores successful lists, including empty lists,
for 180 seconds from insertion using an injected wall clock. Equality is
expired and removes the entry. No LRU, proactive cleanup, request coalescing,
disk persistence or global request serialization is added.

One normalized key is used for reads/writes: trim, collapse unquoted Unicode
whitespace, preserve quoted whitespace/escaped quotes, then lowercase with
Locale.ROOT. The forwarded remote expression is not normalized this way.

Short Mutex sections protect entries and the maximum cooldown deadline, never
the HTTP call. Cache reads check cancellation after lock acquisition and clock
reads; writes check after reading the clock, immediately before insertion.
Already-canceled results cannot populate the cache. `TimeProvider` is synchronous.
Canceling after an insertion has completed does not remove that entry.

429 always indicates rate limiting. A 403 requires `X-RateLimit-Remaining: 0`,
the presence of `Retry-After`, or a message containing `rate limit` or
`secondary rate` case-insensitively. Valid Retry-After seconds/HTTP date precedes the reset
epoch, with a conservative 60-second fallback. Unusable/non-finite/negative
headers fall through. Fractions are retained at millisecond precision, rounded
up to avoid retrying early. Dates use strict Locale.US/GMT parsing compatible
with API 24, without java.time/desugaring.

Overlapping responses can extend, never shorten, the shared deadline.
Requests already started are not canceled retroactively. New calls are blocked
before transport while `now < deadline`; equality permits another attempt.

## Navigation, lifecycle and UI

Navigation Compose has an argument-free search destination and a detail
destination whose only argument is the repository ID.
An independent selected snapshot is retained in the ViewModel and saved as
SavedStateHandle-compatible scalar fields. Neither all results nor transient
Jobs/loading/errors are saved.

- Rotation retains query/results/work without another request.
- Back retains in-process results and the saveable LazyListState.
- Restored detail reconstructs the snapshot without searching in the background.
- Back from restored detail can resolve the query if in-memory results are gone.
- A restored search resolves its valid query with the normal debounce.
- Process death intentionally resets cache and cooldown.

Detail text is selectable and vertically scrollable. ACTION_VIEW opens the
validated URL; an unavailable browser produces a Snackbar.
Material 3 provides light/dark themes, with dynamic colors on Android 12+.
The UI handles system insets, IME behavior and Back, with scalable text, native
touch targets and stable list keys.
User-facing English copy/plurals live in resources; numbers use the locale.
Content is width-limited on large windows. Launcher artwork uses Android vector
and adaptive drawables.

## Test composition

JVM fakes inject repositories, time and controlled completions.
MockWebServer validates production transport/cache integration locally.
`SearchTestActivity` and `FixtureScenario` exist exclusively in `src/debug`.
They explicitly compose the real ViewModel, navigation and screens with an
offline repository; production query text never selects fixtures.
Release manifest and R8 mapping were checked for fixture exclusion.
