# Material 3 Expressive Overview

Material 3 Expressive is an expansion of Material Design 3, introducing research-backed updates to theming, components, motion, typography, and more. It is designed to help developers create engaging and desirable products with a more distinct and personalized feel.

## Dependencies

**Important**: Material 3 Expressive features are currently in active development and are part of the Alpha releases of the Material 3 Compose library.

The documentation here references **version 1.5.0-alpha14** (or later) based on the latest available release notes. Please check [Google's Maven Repository](https://maven.google.com/web/index.html#androidx.compose.material3:material3) for the absolute latest version available for your project.

Add the following dependency to your `build.gradle` (or `build.gradle.kts`) file:

```kotlin
dependencies {
    // Check for the latest alpha version
    implementation("androidx.compose.material3:material3:1.5.0-alpha14")
}
```

> **Note**: If `1.5.0-alpha` is not yet available in your repositories, check for the latest `1.4.x` alpha/beta versions, as some expressive features (like `MotionScheme`) were introduced there.

## Key Features

*   **Expressive Theming**: New ways to customize the look and feel.
*   **Expressive Motion**: A new `MotionScheme` system that replaces the previous easing and duration-based system with a physics-based approach.
*   **Expressive Components**: Updates to existing components and new components like Carousels, Expressive Lists, and Wavy Progress Indicators.
*   **Expressive Shapes**: Expanded shape system with more variety.
