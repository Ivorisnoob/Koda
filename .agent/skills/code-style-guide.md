---
trigger: always_on
---

# Development Rules & Guidelines

This document establishes strict rules for the development of OpenAnime. All AI agents and developers must adhere to these guidelines without exception.

## 1. Communication Style
*   **Professional & Direct:** Use clear, concise, and professional human language.
*   **No Emojis:** Do not use emojis in responses or documentation.
*   **No "Cringe" Talk:** Avoid overly enthusiastic, informal, or robotic phrasing. Speak like a senior engineer.

## 2. Component Usage & Implementation
*   **Strict Priority:**
    1.  **Material 3 Expressive Components:** Always check for an Expressive version first (e.g., `LoadingIndicator` with shapes, `SplitButton`, `FloatingToolbar`).
    2.  **Standard Material 3 Components:** Use standard M3 components if no Expressive version exists.
*   **No Reinventing the Wheel:**
    *   **Do NOT** create custom components that duplicate existing Material 3 functionality.
    *   **Do NOT** create custom shape or animation libraries. Use the `androidx.compose.material3` and `androidx.compose.animation` libraries (especially `MaterialShapes` and `SharedTransitionLayout`).
*   **No Hallucinations:** Never invent components, parameters, or library features that do not exist. Verify their existence in the official Material 3 documentation (1.5.0-alpha13+) before using them.

## 3. Library & Dependency Methodology
*   **Research First:** Before adding any dependency, verify its existence, latest version, and correct artifact ID on Maven Central or Google Maven.
*   **Version Compatibility:** Ensure that all library versions are compatible. For example, do not use a Retrofit converter artifact that didn't exist in the specified Retrofit version.
*   **No Assumptions:** Do not assume a library structure based on past knowledge. Libraries change (e.g., Retrofit 2.9.0 vs 2.11.0). **Verify.**

## 4. Decision Making & Uncertainty
*   **Zero Assumptions:** If a requirement is ambiguous, a design pattern is unclear, or a library feature is undocumented, **STOP and ASK the user for clarification.**
*   **Verify First:** Do not assume a library version supports a feature. Check the `libs.versions.toml` and official release notes.

## 5. Code Quality
*   **Clean Architecture:** Follow the domain/data/presentation layer separation defined in `IMPLEMENTATION_PLAN.md`.
*   **Single Source of Truth:** `libs.versions.toml` is the only place for dependency versions.

---
**Violation of these rules will result in rejected code.**
