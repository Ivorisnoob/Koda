# Project Documentation Hub

This directory serves as the centralized repository for the technical specifications, architectural designs, and development guidelines for **The Music App**.

## Core Documentation

Explore the specialized deep-dives for each major subsystem:

-   **[High-Level Architecture](ARCHITECTURE.md)**: System overview, layer interactions, and security model.
-   **[Playback Engine](DEEP_DIVE_PLAYBACK.md)**: ExoPlayer configuration, JIT resolution, prefetching, and crossfade logic.
-   **[Data & YouTube Integration](DEEP_DIVE_YOUTUBE.md)**: Web extraction, InnerTube API signatures, authentication (SAPISIDHASH), and cookie management.
-   **[UI, UX & Design System](DEEP_DIVE_UI_UX.md)**: Material 3 Expressive implementation, dynamic theming, and ambient visual effects.

---

## Technical Guides

### Development & Contribution
-   **[Contribution Guidelines](../CONTRIBUTING.md)**: Branching strategy, PR requirements, and local setup.
-   **[Design Mindset](DesignMindset.md)**: Philosophical approach to designing for Material 3 Expressive.

### Design References
-   **[Material 3 Components](components.md)**: AI-ready reference for M3 Expressive components.
-   **[Shapes & Geometry](shapes.md)**: Definition of expressive shape tokens and morphing.
-   **[Typography System](typography.md)**: Font families and hierarchy definitions.

---

## Technical Stack Summary

| Layer | Technologies |
| :--- | :--- |
| **Language** | Kotlin 2.1.0+ |
| **UI Framework** | Jetpack Compose (Material 3 Expressive) |
| **Playback** | Media3 ExoPlayer |
| **Concurrency** | Kotlin Coroutines & Flow |
| **Network** | OkHttp 4.12.0 |
| **Data Extraction** | NewPipeExtractor |
| **Persistence** | Jetpack DataStore / EncryptedSharedPreferences |
| **Analytics/Stats** | Local JSON Event Sourcing |
