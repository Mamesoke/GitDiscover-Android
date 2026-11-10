# GitDiscover - Android Delivery Record

**Updated:** November 10, 2026.

This record lists delivered Android work. See [Architecture](ARCHITECTURE.md),
[Specification](SPEC.md) and [Test Coverage](TEST_PLAN.md) for implementation
details and recorded verification.

## Application

- [x] Validate the checked-in wrapper, JDK 25 and Android SDK/plugin baseline.
- [x] Declare dependencies through the version catalog.
- [x] Implement immutable Domain entities, shared query rules, repository
      contract, typed failures and a cancelable use case.
- [x] Implement encoded GitHub requests, complete DTO mapping and validated URLs.
- [x] Connect cancellation to the HTTP Call and close response resources.
- [x] Disable implicit retries, redirects and disk HTTP cache.
- [x] Implement the shared rate-limit gate, header parsing and maximum deadline.
- [x] Implement the concurrency-safe 180-second cache, normalized quoted keys,
      successful empty hits and cancellation-safe writes.
- [x] Wire a process-owned graph with constructor injection.
- [x] Implement immutable presentation state, 400 ms debounce, immediate
      cancellation and generation protection.
- [x] Implement search/results/loading/empty/error UI and manual Retry with
      lifecycle-aware countdown.
- [x] Implement detail with all fields, selectable text and external link errors.
- [x] Retain search state in ViewModel, use saveable list state and restore the
      selected snapshot without saving all results or fetching detail again.
- [x] Keep fixture selectors/activity in Debug only.
- [x] Provide Android vector/adaptive launcher artwork and English UI resources.

## Automated and artifact checks

- [x] Pass 38 deterministic Domain/Data/Presentation JVM tests.
- [x] Pass 9 instrumented tests on Small Phone API 24 and 9 on Pixel Tablet API 36.
- [x] Run lint and build Debug/Release; inspect Release fixture exclusion.
- [x] Verify Unicode segmentation with ICU4J regression tests on JVM and both devices.
- [x] Document Android setup, behavior, architecture and test evidence.

## Recorded manual checks

- [x] Install and launch the minified Release APK on both devices.
- [x] Inspect live search/detail on both devices, plus tablet portrait/landscape,
      150% text and dark mode with retained search/detail state.
- [x] Verify offline detail restoration after actual process recreation.
