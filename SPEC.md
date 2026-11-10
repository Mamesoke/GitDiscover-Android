# GitDiscover - Android Product and Technical Specification

**Reviewed:** November 10, 2026.
**Status:** implemented Android behavior; recorded verification is in
[Test Coverage](TEST_PLAN.md).

Related documents: [Architecture](ARCHITECTURE.md), [Delivery Record](TASKS.md), [Test Coverage](TEST_PLAN.md).

## 1. Goal

Provide a native Android application for searching and exploring public GitHub repositories, with search, detail, cancellation, an in-memory cache and explicit error handling.

The application follows the [project guidelines](.github/agents/android-specialist.agent.md): Kotlin, Jetpack Compose, Material 3, immutable state, unidirectional data flow, structured concurrency and clear presentation/domain/data responsibilities.

## 2. Current baseline

- Native Compose/Material 3 search and detail screens.
- Data, Domain and Presentation are implemented with constructor injection,
  cancelable OkHttp transport, TTL cache, cooldown, MVVM and saved state.
- [App configuration](app/build.gradle.kts) preserves namespace
  `com.mamesoke.gitdiscover`, minimum SDK 24, compile/target SDK 37 and Java
  source/target compatibility 11. Current daemon criteria and recorded validation use
  JDK 25.
- Automated Domain/Data/Presentation and offline Compose tests.
- The [manifest](app/src/main/AndroidManifest.xml) declares INTERNET and disables
  production cleartext traffic; detail requires no additional request.

Android execution evidence is in [Test Coverage](TEST_PLAN.md).

## 3. Scope

- Public GitHub repository search and result list.
- Selected repository detail and external GitHub link.
- Cancellation, debounce, cache, error states, cooldown and manual retry.
- Android lifecycle handling, state restoration, native Material 3 controls and width-limited layouts.
- Deterministic unit/integration tests and offline UI workflows.
- Android setup, architecture and validation documentation.

## 4. API and data contract

```text
GET https://api.github.com/search/repositories
  ?q=<encoded query>&per_page=30&page=1

Accept: application/vnd.github+json
X-GitHub-Api-Version: 2026-03-10
User-Agent: GitDiscover
```

- Public search requires no authentication. Do not hardcode a token.
- Consume `items`; do not display or depend on `total_count`, `incomplete_results` or `updated_at`.
- Preserve `id`, `name`, `fullName`, `ownerLogin`, nullable `description`/`language`, `stars`, `forks` and `htmlUrl`.
- Repository identifiers and counts use Kotlin `Long`.
- Missing or null optional fields are valid; other required fields must not silently default.
- Repository URLs must use HTTPS, host exactly `github.com` and contain no user information. An invalid URL in any item fails the entire mapping.
- Preserve literal plus signs, percent signs, separators, qualifiers and Unicode through transport. Do not concatenate or double-encode queries.
- GitHub Search is rate-limited. Server responses determine the shared cooldown deadline.

## 5. Functional requirements

### F01 - Query validation

Trim surrounding whitespace/newlines. Fewer than two user-perceived characters returns an empty list without invoking the repository and keeps presentation Idle. Valid expressions retain case, internal whitespace and qualifiers when sent remotely.

Kotlin `String.length` counts UTF-16 units, so QueryRules uses ICU4J to count extended grapheme clusters. Emoji, modifiers, ZWJ sequences and combining characters do not bypass the minimum. The use case and ViewModel share this query policy.

### F02 - Debounce and loading

Wait 400 ms after the most recent text edit before executing search. Enter Loading immediately for valid input, including the debounce interval, and hide results belonging to the previous query.

### F03 - Cancellation and stale-response protection

Every text edit immediately invalidates the previous generation and cancels debounce/search work. Cancellation must reach the HTTP request, not merely hide its result.

Check cancellation around suspended operations and the generation before publishing. Late successes or errors from superseded work must never replace current state, even if a dependency ignores cancellation. Clearing restores Idle and removes old results.

### F04 - Remote search

Use the API contract above, requesting page 1 with `per_page=30` and displaying its returned items. No authentication or automatic pagination. Verify the expression received by transport, not just a local URL round trip.

### F05 - Mapping

Map every domain field and preserve optional absence. Validate repository URLs in Data before creating domain entities. Do not drop invalid items silently or convert decoding errors into empty success.

### F06 - Result list

Show full name, a nonempty description limited to two lines, language or `Unknown language`, and locally formatted stars. Use stable list keys by repository ID.

### F07 - Repository detail

Show name, full name, description, owner, language, stars and forks. Provide selectable text and an `Open on GitHub` action using the validated URL. Use the selected entity without another request.

The description fallback is `No description provided`; the language fallback is `Unknown language`. Apply fallbacks to null, not automatically to empty strings. The list omits an empty description; detail preserves it.

Open the external link through Android's browser intent. If no activity can handle it, show a visible error.

### F08 - Cache key normalization

Use the same key for reads/writes: trim, collapse whitespace outside quoted expressions, preserve quoted whitespace and escaped quotes, then lowercase independently of device locale.

The remote expression remains unchanged except for surrounding trim. For example, `"my  topic"` and `"my topic"` must not collide. Verify non-ASCII and Turkish-locale behavior.

### F09 - TTL cache

Cache successful results only, including empty arrays, in memory for 180 seconds from insertion. An entry is fresh when `now < expiresAt`; equality is expired and removes the entry on lookup.

Errors and results already observed as canceled must not be inserted. Cancellation after a completed insertion cannot retroactively undo it.

The cache is concurrency-safe, uses an injected wall clock, and has no capacity bound, proactive eviction, disk persistence or same-key in-flight coalescing.

### F10 - Errors and UI states

Represent Idle, Loading, Results, Empty and Failed explicitly. Keep distinct domain errors for invalid request, invalid response, access denied, server failure, decoding, connectivity, rate limiting and unexpected failures. Invalid-response and decoding errors share the same UI message.

Cancellation must not appear as a visible failure. A cancellation from the current dependency restores Idle if its generation is still current; superseded work publishes nothing. Never substitute a failure with an empty result.

### F11 - Shared rate-limit cooldown

- HTTP 429 always indicates rate limiting.
- HTTP 403 indicates rate limiting when `X-RateLimit-Remaining` is `0`, a `Retry-After` header is present, or the response message contains `rate limit` or `secondary rate` case-insensitively. Other 403 responses are access denied.
- Valid `Retry-After` seconds or HTTP date takes precedence over `X-RateLimit-Reset`. Invalid/unusable values fall through; no usable deadline means now +60 seconds.
- Reject negative or non-finite numeric values; do not silently truncate fractional values.
- Concurrent responses can extend, but never shorten, the shared deadline.
- Block HTTP calls while `now < deadline`; allow them at equality.
- Fresh cache hits remain available. Do not retroactively cancel already-started requests when another response announces a limit.

### F12 - Retry and countdown

Retry is manual, uses the current valid query and the normal 400 ms debounce, and remains disabled until its deadline. Refresh the visible countdown once per second while active and recompute it after backgrounding. Expiry enables Retry; it does not automatically submit a request.

### F13 - Search controls and native UX

Provide a labeled, single-line search field, a clear action disabled for an empty field, no automatic capitalization/autocorrection, and a search IME action that hides the keyboard. Text edits schedule the debounced search; clearing uses the normal edit/cancellation path.

Use Material 3, system/top-bar Back, edge-to-edge/insets handling, scrolling detail, scalable text and labeled functional icons. Support light/dark themes and dynamic colors on Android 12+. User-facing text belongs in Android resources, with countdown plurals and the current English product copy.

### F14 - Composition and test fixtures

Production launches use the real dependency graph. Offline UI tests explicitly inject fixture repositories; fixture wiring/selectors must be absent from Release. A production query must never activate fixtures.

Debug-only `FixtureScenario.Empty`, `FixtureScenario.Offline` and `FixtureScenario.RateLimit` cover no results, a recoverable first-attempt failure and a rate-limit failure. They do not prove production transport/cache behavior.

## 6. Android lifecycle and UI behavior

- Rotation preserves query/results and does not duplicate debounce or requests.
- Returning from detail in the same process preserves query, results and scroll without another request.
- Saved-state restoration retains the query and selected repository snapshot, not the entire result list or transient Jobs/Loading/errors.
- Restoring detail after process recreation requires no network; restoring search without in-memory results may re-resolve its valid query with debounce. Restored detail must not trigger that search in the background.
- Cache/cooldown survive rotation but are lost with process death, matching the in-memory policy.
- Material 3 controls, labeled functional icons, scrolling detail and scalable text provide the native UI.
- Recorded device verification covers API 24 and API 36. Earlier manual checks cover phone/tablet search and detail, plus tablet portrait/landscape, 150% text and dark mode.

## 7. Implementation invariants

The F01-F14 behaviors are linked to their automated or recorded manual evidence in [Test Coverage](TEST_PLAN.md). The implementation preserves 400 ms debounce, 180-second TTL, the 30-result first-page contract, shared cooldown, real cancellation and generation protection.

Domain must remain free of Android/UI/transport dependencies. No invalid-query requests, silent success fallbacks, automatic retries, additional detail fetches, production fixtures, credentials or unnecessary permissions are allowed.

The verification record distinguishes JVM, instrumented, build and manual checks.
