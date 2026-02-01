### Material 3 Expressive Typography

The biggest change in Material 3 Expressive typography is the move from a static set of styles to a **Dual Type Scale** powered by **Variable Fonts**.

Instead of just "Bold" or "Regular," the system now defines two parallel sets of styles:

1.  **Baseline:** Standard, readable, functional (similar to the classic M3 styles).
2.  **Emphasized:** Expressive, bolder, and often utilizing the **Width** axis of variable fonts to create distinct visual character.

-----

### 1\. Setup: The Variable Font

Expressive typography relies on a variable font that supports custom axes (Weight, Width, Slant). The default standard is **Roboto Flex**.

**Step 1: Add the Font**
Download the full variable version of [Roboto Flex](https://fonts.google.com/specimen/Roboto+Flex) (ttf) and place it in `res/font/roboto_flex.ttf`.

**Step 2: Define the FontFamily with Variations**
In Compose, you don't just load the font; you configure its "axes" using `FontVariation`.

```kotlin
val RobotoFlex = FontFamily(
    Font(
        resId = R.font.roboto_flex,
        // Default variation settings (optional, can be overridden per style)
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400), // Standard Weight
            FontVariation.width(100f), // Standard Width (100 is "Normal")
            FontVariation.slant(0f)    // Upright
        )
    )
)
```

-----

### 2\. Using the "Width" Axis

The **Width** axis (`wdth`) is the star of the Expressive update. It allows you to stretch or condense text without distorting the letterforms.

  * **Standard Width:** `100f`
  * **Condensed:** `< 100f` (e.g., `75f` is narrow)
  * **Expanded:** `> 100f` (e.g., `125f` is wide)

#### Creating an "Emphasized" Style

You can manually create an Expressive style by adjusting the `FontVariation.width`.

```kotlin
val EmphasizedHeadline = TextStyle(
    fontFamily = RobotoFlex,
    fontSize = 32.sp,
    lineHeight = 40.sp,
    // Variable font settings: Bold + Wide
    fontVariationSettings = FontVariation.Settings(
        FontVariation.weight(800), // Extra Bold
        FontVariation.width(115f)  // 115% Width (Slightly wider/expressive)
    )
)

// Usage
Text(
    text = "Expressive Headline",
    style = EmphasizedHeadline
)
```

-----

### 3\. The New Type Scale (Baseline vs. Emphasized)

In `MaterialExpressiveTheme`, the typography system is expanded. While you still have `Display`, `Headline`, `Title`, `Body`, and `Label`, the theme now accounts for "Emphasized" variants intended for high-priority content.

If you are building a custom Typography object, you should define your styles to match this intent.

```kotlin
// Example: A Typography config with Expressive traits
val ExpressiveTypography = Typography(
    // DISPLAY: Large, eccentric, maybe slightly condensed or wide depending on brand
    displayLarge = TextStyle(
        fontFamily = RobotoFlex,
        fontSize = 57.sp,
        fontVariationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.width(110f) // Wide and airy
        )
    ),
    // LABEL: Compact, functional
    labelLarge = TextStyle(
        fontFamily = RobotoFlex,
        fontSize = 14.sp,
        fontVariationSettings = FontVariation.Settings(
            FontVariation.weight(500),
            FontVariation.width(100f) // Standard width for readability
        )
    ),
    // CUSTOM EMPHASIS (e.g., for a special button)
    // You might create a custom style extension or just use a local TextStyle
)
```

**Tip:** In the latest M3 Alpha, `MaterialExpressiveTheme` tries to map these for you, but for full control, defining your own `TextStyle` with `FontVariation` is the most reliable way to get the exact "look" currently.

-----

### 4\. Design Intent: How Google Wants You to Use It

Google's design team has specific intentions for these new tools. It's not just about "making it look different"; it's about **feeling**.

#### A. The "Oscillation" Principle

Do not make everything expressive. The interface should oscillate (move back and forth) between **Standard** and **Expressive** moments.

  * **Standard (Baseline):** Use standard widths and weights for body text, settings menus, and long-form reading. Don't tire the user's eye.
  * **Expressive (Emphasized):** Use wider widths, heavier weights, or unique shapes for "Hero" moments—large headers, success states, or primary action buttons.

#### B. Use Width for Hierarchy, Not Just Size

Previously, designers used **Size** (Big vs Small) and **Weight** (Bold vs Regular) to show importance.
Now, you should use **Width**:

  * **Wider Text (`width(110f+)`)**: Feels slower, more confident, and stable. Use it for short, impactful statements (e.g., "Transaction Complete").
  * **Narrower Text (`width(85f-)`)**: Feels urgent, efficient, or mechanical. Use it for metadata, timestamps, or dense data tables.

#### C. Morphing Typography

Because these are variable fonts, you can **animate** the axes. You can morph a button's text from "Normal" to "Bold and Wide" when the user presses it.

```kotlin
// Animation Example
val isPressed by interactionSource.collectIsPressedAsState()
val targetWidth by animateFloatAsState(if (isPressed) 120f else 100f)

Text(
    text = "Press Me",
    style = LocalTextStyle.current.copy(
        fontFamily = RobotoFlex,
        fontVariationSettings = FontVariation.Settings(
            FontVariation.weight(500),
            FontVariation.width(targetWidth) // Animates the width!
        )
    )
)
```

### Summary Checklist for Designers/Devs

1.  **Stop using static font files** (Regular.ttf, Bold.ttf). Switch to a single Variable Font file.
2.  **Define a "Baseline" set** of styles for 80% of your UI (Utility).
3.  **Define an "Emphasized" set** for the 20% of your UI that needs to pop (Brand moments).
4.  **Experiment with Width:** Try setting your headlines to `width(110f)` and your captions to `width(90f)` to see how it cleans up visual clutter without changing font size.