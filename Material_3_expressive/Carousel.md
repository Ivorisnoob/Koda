# Material 3 Expressive Carousel

Carousels are a key component in Material 3 Expressive, allowing users to browse through a collection of items.

## Types of Carousels

Material 3 provides specific implementations for different carousel strategies.

### HorizontalMultiBrowseCarousel

A carousel optimized for browsing a large collection of items where seeing multiple items at once is important.

```kotlin
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MultiBrowseExample() {
    val state = rememberCarouselState { 10 } // 10 items

    HorizontalMultiBrowseCarousel(
        state = state,
        preferredItemWidth = 120.dp,
        itemSpacing = 8.dp
    ) { i ->
        // Carousel Item Content
        MyCarouselItem(index = i)
    }
}
```

### HorizontalUncontainedCarousel

A carousel that doesn't enforce strict item containment, useful for continuous lists of items.

```kotlin
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel

@Composable
fun UncontainedExample() {
    val state = rememberCarouselState { 10 }

    HorizontalUncontainedCarousel(
        state = state,
        itemWidth = 200.dp,
        itemSpacing = 8.dp
    ) { i ->
        MyCarouselItem(index = i)
    }
}
```

### HorizontalCenteredHeroCarousel

A carousel designed to highlight a single "hero" item while showing previews of previous/next items.

```kotlin
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel

@Composable
fun HeroExample() {
    val state = rememberCarouselState { 5 }

    HorizontalCenteredHeroCarousel(
        state = state
    ) { i ->
        MyHeroItem(index = i)
    }
}
```

## Carousel State

Use `rememberCarouselState` to manage the state (current item, item count) of the carousel.

```kotlin
val state = rememberCarouselState { itemCount }
```
