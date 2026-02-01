<!-- id: task-robust-playback-fix -->
# Task: Robust Playback & Error Handling System

## Status
- [x] Modify `YouTubeRepository.kt` for granular error handling <!-- id: 1 -->
- [x] Upgrade `MusicService.kt` with smart retries and robust fetching logic <!-- id: 2 -->
- [x] Verify functionality (Manual Review) <!-- id: 3 -->

## Context
The current music playback implementation suffers from "load then skip" issues due to aggressive timeouts (10s) and swallowed errors in the repository layer. The goal is to implement a robust system that handles network instability, specific YouTube extraction failures, and provides a smoother user experience.

## Details
1. **Repository Layer (`YouTubeRepository.kt`)**:
   - Change `getStreamUrl` to return a `Result<String>` instead of nullable `String?`.
   - Distinguish between `NetworkError` (retryable) and `ExtractionError` (possibly fatal).
   - Increase internal HTTP connection timeouts if needed to match Service expectations.

2. **Service Layer (`MusicService.kt`)**:
   - Increase `STREAM_TIMEOUT_MS` to **30s** to accommodate slow fetches.
   - Implement **Exponential Backoff** in `resolveStreamUrl` for retryable errors.
   - Add a "No Internet" check before attempting fetch to fail fast or wait.
   - Improve `onPlayerError` to retry specifically on network-related issues rather than blindly clearing cache.
   - Ensure pre-fetching logic (`prefetchUpcomingSongs`) respects the new robust fetching strategy.
