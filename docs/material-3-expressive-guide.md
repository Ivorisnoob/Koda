# Material Design 3 Expressive - Developer Guide

## Table of Contents

- [1. Introduction](#1-introduction)
  - [1.1 What is Material Design 3 Expressive](#11-what-is-material-design-3-expressive)
  - [1.2 Getting Started](#12-getting-started)
- [2. Theming System](#2-theming-system)
  - [2.1 Color Schemes](#21-color-schemes)
  - [2.2 Typography](#22-typography)
  - [2.3 Shapes](#23-shapes)
- [3. Motion System](#3-motion-system)
  - [3.1 Motion Schemes](#31-motion-schemes)
    - [Expressive Motion Scheme](#expressive-motion-scheme)
    - [Standard Motion Scheme](#standard-motion-scheme)
  - [3.2 Animation Specifications](#32-animation-specifications)
    - [Spatial Animation Specs](#spatial-animation-specs)
    - [Effect Animation Specs](#effect-animation-specs)
    - [Animation Speeds](#animation-speeds)
  - [3.3 Spring Physics](#33-spring-physics)
  - [3.4 Customizing Motion](#34-customizing-motion)
    - [Creating Custom Motion Schemes](#creating-custom-motion-schemes)
    - [Using Motion Schemes in Custom Components](#using-motion-schemes-in-custom-components)
- [4. Components Reference](#4-components-reference)
  - [4.1 Action Components](#41-action-components)
    - [Buttons](#buttons)
    - [Date & Time Pickers](#date-time-pickers)
    - [Loading & Progress](#loading-progress)
  - [4.2 Containment Components](#42-containment-components)
    - [Cards](#cards)
    - [Sheets](#sheets)
    - [Dialogs](#dialogs)
    - [Carousel](#carousel)
  - [4.3 Communication Components](#43-communication-components)
    - [Badges](#badges)
    - [Snackbar](#snackbar)
    - [Tooltips](#tooltips)
  - [4.4 Navigation Components](#44-navigation-components)
    - [Navigation Bar](#navigation-bar)
    - [Navigation Drawer](#navigation-drawer)
    - [Navigation Rail](#navigation-rail)
    - [Tabs](#tabs)
    - [App Bars](#app-bars)
    - [Toolbars](#toolbars)
  - [4.5 Selection Components](#45-selection-components)
    - [Checkbox](#checkbox)
    - [Radio Button](#radio-button)
    - [Switch](#switch)
    - [Chips](#chips)
    - [Sliders](#sliders)
  - [4.6 Text Input Components](#46-text-input-components)
    - [Text Fields](#text-fields)
    - [Search](#search)
    - [Menus](#menus)
  - [4.7 Other Components](#47-other-components)
    - [Lists](#lists)
    - [Dividers](#dividers)
    - [Surfaces](#surfaces)
    - [Swipe to Dismiss](#swipe-to-dismiss)
- [5. API Reference](#5-api-reference)
  - [5.1 Core Theme APIs](#51-core-theme-apis)
    - [MaterialTheme](#materialtheme)
    - [ColorScheme](#colorscheme)
  - [5.2 Dialog APIs](#52-dialog-apis)
    - [AlertDialog (Basic)](#alertdialog-basic)
    - [DatePickerDialog](#datepickerdialog)
  - [5.3 Menu APIs](#53-menu-apis)
    - [DropdownMenu](#dropdownmenu)
    - [DropdownMenuItem](#dropdownmenuitem)
  - [5.4 Date Picker State APIs](#54-date-picker-state-apis)
    - [DatePickerState (API 26+)](#datepickerstate-api-26)
    - [DateRangePickerState (API 26+)](#daterangepickerstate-api-26)
  - [5.5 Dynamic Color APIs](#55-dynamic-color-apis)
- [6. Advanced Topics](#6-advanced-topics)
  - [6.1 Motion Scheme Presets](#61-motion-scheme-presets)
  - [6.2 Adapting Motion to Device Context](#62-adapting-motion-to-device-context)
  - [6.3 Accessibility Considerations](#63-accessibility-considerations)
  - [6.4 Performance Optimization](#64-performance-optimization)
  - [6.5 Testing Motion](#65-testing-motion)
- [7. Code Examples Repository](#7-code-examples-repository)
  - [7.1 Basic AlertDialog Example](#71-basic-alertdialog-example)
  - [7.2 Custom AlertDialog with Content](#72-custom-alertdialog-with-content)
  - [7.3 Custom Motion Scheme Example](#73-custom-motion-scheme-example)
  - [7.4 Animated Custom Component](#74-animated-custom-component)
- [8. Appendix](#8-appendix)
  - [8.1 Package Information](#81-package-information)
  - [8.2 Key Annotations](#82-key-annotations)
  - [8.3 Related Resources](#83-related-resources)
  - [8.4 Glossary](#84-glossary)
  - [8.5 Version History](#85-version-history)
  - [8.6 Component and API Index](#86-component-and-api-index)
- [App Bar Components](#app-bar-components)
  - [AppBarColumn](#appbarcolumn)
  - [AppBarOverflowIndicator](#appbaroverflowindicator)
  - [AppBarRow](#appbarrow)
  - [AppBarWithSearch](#appbarwithsearch)
- [Chip Components](#chip-components)
  - [AssistChip](#assistchip)
- [Badge Components](#badge-components)
  - [Badge](#badge)
  - [BadgedBox](#badgedbox)
- [Dialog Components](#dialog-components)
  - [BasicAlertDialog](#basicalertdialog)
- [Bottom App Bar](#bottom-app-bar)
  - [BottomAppBar (Basic)](#bottomappbar-basic)
  - [BottomAppBar (with FAB)](#bottomappbar-with-fab)
  - [BottomAppBar (with Scroll Behavior)](#bottomappbar-with-scroll-behavior)
- [BottomAppBarState](#bottomappbarstate)
- [BottomSheetScaffold](#bottomsheetscaffold)
- [Button](#button)
- [Button (Expressive with Shape Morphing)](#button-expressive-with-shape-morphing)
- [ButtonGroup](#buttongroup)
  - [ButtonGroup with Overflow Handling](#buttongroup-with-overflow-handling)
    - [Overflow Indicator Implementation](#overflow-indicator-implementation)
    - [Vertical Connected Button Group](#vertical-connected-button-group)
    - [ButtonGroup Parameters](#buttongroup-parameters)
- [Card Component](#card-component)
  - [Overview](#overview)
  - [Basic Card (Non-Interactive)](#basic-card-non-interactive)
    - [Basic Card Parameters](#basic-card-parameters)
  - [Clickable Card](#clickable-card)
    - [Clickable Card Parameters](#clickable-card-parameters)
- [CenterAlignedTopAppBar Component](#centeralignedtopappbar-component)
  - [Overview](#overview)
  - [Basic Implementation](#basic-implementation)
  - [CenterAlignedTopAppBar Parameters](#centeralignedtopappbar-parameters)
- [Checkbox Component](#checkbox-component)
  - [Overview](#overview)
  - [Basic Checkbox](#basic-checkbox)
  - [Checkbox with Label](#checkbox-with-label)
    - [Basic Checkbox Parameters](#basic-checkbox-parameters)
  - [Checkbox with Custom Stroke Styling](#checkbox-with-custom-stroke-styling)
    - [Custom Stroke Checkbox Parameters](#custom-stroke-checkbox-parameters)
- [CircularProgressIndicator Component](#circularprogressindicator-component)
  - [Overview](#overview)
  - [Indeterminate Circular Progress Indicator](#indeterminate-circular-progress-indicator)
  - [CircularProgressIndicator (Determinate)](#circularprogressindicator-determinate)
  - [CircularWavyProgressIndicator (Indeterminate)](#circularwavyprogressindicator-indeterminate)
  - [CircularWavyProgressIndicator (Determinate)](#circularwavyprogressindicator-determinate)
  - [ContainedLoadingIndicator (Indeterminate)](#containedloadingindicator-indeterminate)
  - [ContainedLoadingIndicator (Determinate)](#containedloadingindicator-determinate)
- [DatePicker](#datepicker)
  - [DatePicker (Main Component)](#datepicker-main-component)
  - [DatePickerDialog](#datepickerdialog)
  - [DatePickerState](#datepickerstate)
  - [DateRangePicker Component](#daterangepicker-component)
    - [Basic DateRangePicker Implementation](#basic-daterangepicker-implementation)
    - [DateRangePicker with Java Time API (Android API 26+)](#daterangepicker-with-java-time-api-android-api-26)
    - [DateRangePicker Parameters](#daterangepicker-parameters)
  - [DateRangePickerState](#daterangepickerstate)
    - [Creating DateRangePickerState with Java Time API (Android API 26+)](#creating-daterangepickerstate-with-java-time-api-android-api-26)
    - [Creating DateRangePickerState with Timestamps](#creating-daterangepickerstate-with-timestamps)
    - [DateRangePickerState with Custom Locale](#daterangepickerstate-with-custom-locale)
    - [DateRangePickerState Parameters](#daterangepickerstate-parameters)
  - [DismissibleDrawerSheet Component](#dismissibledrawersheet-component)
    - [Basic DismissibleDrawerSheet](#basic-dismissibledrawersheet)
    - [DismissibleDrawerSheet with Back Handling](#dismissibledrawersheet-with-back-handling)
    - [DismissibleDrawerSheet Parameters](#dismissibledrawersheet-parameters)
  - [DismissibleNavigationDrawer Component](#dismissiblenavigationdrawer-component)
    - [Complete DismissibleNavigationDrawer Example](#complete-dismissiblenavigationdrawer-example)
    - [DismissibleNavigationDrawer Parameters](#dismissiblenavigationdrawer-parameters)
  - [DockedSearchBar Component](#dockedsearchbar-component)
    - [DockedSearchBar with Custom Input Field](#dockedsearchbar-with-custom-input-field)
    - [DockedSearchBar Parameters](#dockedsearchbar-parameters)
  - [ExpandedDockedSearchBarWithGap Component](#expandeddockedsearchbarwithgap-component)
    - [ExpandedDockedSearchBarWithGap Example](#expandeddockedsearchbarwithgap-example)
  - [DropdownMenu Component](#dropdownmenu-component)
    - [Basic DropdownMenu Example](#basic-dropdownmenu-example)
    - [DropdownMenu with Scroll Control](#dropdownmenu-with-scroll-control)
    - [DropdownMenu Positioning Behavior](#dropdownmenu-positioning-behavior)
    - [DropdownMenu Parameters](#dropdownmenu-parameters)
  - [DropdownMenuItem Component](#dropdownmenuitem-component)
    - [DropdownMenuItem Parameters](#dropdownmenuitem-parameters)
  - [ElevatedAssistChip Component](#elevatedassistchip-component)
    - [ElevatedAssistChip Example](#elevatedassistchip-example)
    - [ElevatedAssistChip Parameters](#elevatedassistchip-parameters)
  - [ElevatedButton Component](#elevatedbutton-component)
    - [Standard ElevatedButton](#standard-elevatedbutton)
    - [ElevatedButton with Shape Morphing (Expressive)](#elevatedbutton-with-shape-morphing-expressive)
    - [Button Emphasis Hierarchy](#button-emphasis-hierarchy)
    - [ElevatedButton Parameters](#elevatedbutton-parameters)
  - [ElevatedCard Component](#elevatedcard-component)
    - [Non-Interactive ElevatedCard](#non-interactive-elevatedcard)
    - [Clickable ElevatedCard](#clickable-elevatedcard)
    - [ElevatedCard Parameters](#elevatedcard-parameters)
  - [ElevatedFilterChip Component](#elevatedfilterchip-component)
    - [ElevatedFilterChip Example](#elevatedfilterchip-example)
    - [ElevatedFilterChip Parameters](#elevatedfilterchip-parameters)
  - [ElevatedSuggestionChip Component](#elevatedsuggestionchip-component)
    - [ElevatedSuggestionChip Example](#elevatedsuggestionchip-example)
    - [ElevatedSuggestionChip Parameters](#elevatedsuggestionchip-parameters)
  - [ElevatedToggleButton Component (Expressive)](#elevatedtogglebutton-component-expressive)
    - [ElevatedToggleButton Example](#elevatedtogglebutton-example)
    - [ElevatedToggleButton Parameters](#elevatedtogglebutton-parameters)
  - [ExpandedDockedSearchBar Component](#expandeddockedsearchbar-component)
    - [ExpandedDockedSearchBar Example](#expandeddockedsearchbar-example)
    - [ExpandedDockedSearchBar Parameters](#expandeddockedsearchbar-parameters)
  - [ExpandedDockedSearchBarWithGap Component](#expandeddockedsearchbarwithgap-component)
    - [Overview](#expandeddockedsearchbarwithgap-overview)
    - [API Signature](#expandeddockedsearchbarwithgap-api-signature)
  - [ExpandedFullScreenSearchBar Component](#expandedfullscreensearchbar-component)
    - [ExpandedFullScreenSearchBar Example](#expandedfullscreensearchbar-example)
    - [ExpandedFullScreenSearchBar Parameters](#expandedfullscreensearchbar-parameters)
  - [ExposedDropdownMenuBox Component](#exposeddropdownmenubox-component)
    - [Read-Only ExposedDropdownMenuBox](#read-only-exposeddropdownmenubox)
    - [Editable ExposedDropdownMenuBox with Filtering](#editable-exposeddropdownmenubox-with-filtering)
    - [ExposedDropdownMenuBox Parameters](#exposeddropdownmenubox-parameters)
  - [ExtendedFloatingActionButton Component](#extendedfloatingactionbutton-component)
    - [ExtendedFloatingActionButton with Text Only](#extendedfloatingactionbutton-with-text-only)
    - [ExtendedFloatingActionButton with Icon and Text](#extendedfloatingactionbutton-with-icon-and-text)
    - [Collapsible ExtendedFloatingActionButton](#collapsible-extendedfloatingactionbutton)
    - [ExtendedFloatingActionButton Parameters](#extendedfloatingactionbutton-parameters)
  - [FilledTonalIconButton Component](#filledtonaliconbutton-component)
    - [Standard FilledTonalIconButton](#standard-filledtonaliconbutton)
    - [FilledTonalIconButton with Shape Morphing (Expressive)](#filledtonaliconbutton-with-shape-morphing-expressive)
    - [FilledTonalIconButton Guidelines](#filledtonaliconbutton-guidelines)
    - [FilledTonalIconButton Parameters](#filledtonaliconbutton-parameters)
  - [FilledTonalIconToggleButton Component](#filledtonalicontogglebutton-component)
    - [Standard FilledTonalIconToggleButton](#standard-filledtonalicontogglebutton)
    - [FilledTonalIconToggleButton with Shape Morphing (Expressive)](#filledtonalicontogglebutton-with-shape-morphing-expressive)
    - [FilledTonalIconToggleButton Parameters](#filledtonalicontogglebutton-parameters)
  - [FilterChip Component](#filterchip-component)
    - [FilterChip with Selection Icon](#filterchip-with-selection-icon)
    - [FilterChip with Leading and Selected Icons](#filterchip-with-leading-and-selected-icons)
    - [FilterChip with Leading and Trailing Icons](#filterchip-with-leading-and-trailing-icons)
    - [FilterChip Parameters](#filterchip-parameters)
  - [FlexibleBottomAppBar Component (Expressive)](#flexiblebottomappbar-component-expressive)
    - [FlexibleBottomAppBar with Custom Arrangement](#flexiblebottomappbar-with-custom-arrangement)
    - [FlexibleBottomAppBar Parameters](#flexiblebottomappbar-parameters)
  - [HorizontalFloatingToolbar](#horizontalfloatingtoolbar)
    - [Basic HorizontalFloatingToolbar](#basic-horizontalfloatingtoolbar)
    - [HorizontalFloatingToolbar with Scroll Behavior](#horizontalfloatingtoolbar-with-scroll-behavior)
    - [HorizontalFloatingToolbar Parameters](#horizontalfloatingtoolbar-parameters)
- [Icon Component](#icon-component)
  - [Icon with ImageVector](#icon-with-imagevector)
  - [Icon with ImageBitmap](#icon-with-imagebitmap)
  - [Icon with Painter](#icon-with-painter)
  - [Icon with ColorProducer](#icon-with-colorproducer)
    - [Icon Parameters](#icon-parameters)
- [IconButton Component](#iconbutton-component)
  - [Standard IconButton](#standard-iconbutton)
  - [IconButton with Color Tint](#iconbutton-with-color-tint)
  - [Small Narrow Round IconButton](#small-narrow-round-iconbutton)
  - [Medium Round-Shaped IconButton](#medium-round-shaped-iconbutton)
  - [Expressive IconButton with Shape Morphing](#expressive-iconbutton-with-shape-morphing)
    - [IconButton Parameters](#iconbutton-parameters)
- [IconToggleButton Component](#icontogglebutton-component)
  - [Standard IconToggleButton](#standard-icontogglebutton)
  - [Expressive IconToggleButton with Shape Morphing](#expressive-icontogglebutton-with-shape-morphing)
    - [IconToggleButton Parameters](#icontogglebutton-parameters)
- [Label Component](#label-component)
  - [Label with RangeSlider](#label-with-rangeslider)
    - [Label Parameters](#label-parameters)
- [LargeExtendedFloatingActionButton](#largeextendedfloatingactionbutton)
  - [LargeExtendedFloatingActionButton (Text Only)](#largeextendedfloatingactionbutton-text-only)
  - [LargeExtendedFloatingActionButton with Icon](#largeextendedfloatingactionbutton-with-icon)
  - [Collapsible LargeExtendedFloatingActionButton](#collapsible-largeextendedfloatingactionbutton)
    - [LargeExtendedFloatingActionButton Parameters](#largeextendedfloatingactionbutton-parameters)
- [LargeFlexibleTopAppBar](#largeflexibletopappbar)
  - [LargeFlexibleTopAppBar with Subtitle](#largeflexibletopappbar-with-subtitle)
    - [LargeFlexibleTopAppBar Parameters](#largeflexibletopappbar-parameters)
- [LargeFloatingActionButton](#largefloatingactionbutton)
  - [Basic LargeFloatingActionButton](#basic-largefloatingactionbutton)
  - [Animated Show/Hide LargeFloatingActionButton](#animated-showhide-largefloatingactionbutton)
    - [LargeFloatingActionButton Parameters](#largefloatingactionbutton-parameters)
- [LargeTopAppBar](#largetopappbar)
  - [LargeTopAppBar with Scroll Behavior](#largetopappbar-with-scroll-behavior)
- [LinearWavyProgressIndicator](#linearwavyprogressindicator)
  - [Determinate LinearWavyProgressIndicator](#determinate-linearwavyprogressindicator)
  - [Thick LinearWavyProgressIndicator](#thick-linearwavyprogressindicator)
    - [LinearWavyProgressIndicator Parameters](#linearwavyprogressindicator-parameters)
- [ListItem Component](#listitem-component)
  - [One-Line ListItem](#one-line-listitem)
  - [Two-Line ListItem](#two-line-listitem)
  - [Three-Line ListItem with Overline and Supporting Content](#three-line-listitem-with-overline-and-supporting-content)
  - [Three-Line ListItem with Extended Supporting Content](#three-line-listitem-with-extended-supporting-content)
    - [ListItem Parameters](#listitem-parameters)
- [LoadingIndicator Component](#loadingindicator-component)
  - [Indeterminate LoadingIndicator](#indeterminate-loadingindicator)
  - [Determinate LoadingIndicator](#determinate-loadingindicator)
    - [LoadingIndicator Parameters](#loadingindicator-parameters)
- [MaterialExpressiveTheme](#materialexpressivetheme)
  - [Basic MaterialExpressiveTheme](#basic-materialexpressivetheme)
    - [MaterialExpressiveTheme Parameters](#materialexpressivetheme-parameters)
- [MaterialTheme](#materialtheme)
  - [MaterialTheme with Custom Colors and Typography](#materialtheme-with-custom-colors-and-typography)
  - [MaterialTheme with Motion Scheme (Expressive)](#materialtheme-with-motion-scheme-expressive)
    - [MaterialTheme Parameters](#materialtheme-parameters)
- [MediumExtendedFloatingActionButton](#mediumextendedfloatingactionbutton)
  - [MediumExtendedFloatingActionButton (Text Only)](#mediumextendedfloatingactionbutton-text-only)
  - [MediumExtendedFloatingActionButton with Icon](#mediumextendedfloatingactionbutton-with-icon)
  - [Collapsible MediumExtendedFloatingActionButton](#collapsible-mediumextendedfloatingactionbutton)
    - [MediumExtendedFloatingActionButton Parameters](#mediumextendedfloatingactionbutton-parameters)
- [MediumFlexibleTopAppBar](#mediumflexibletopappbar)
  - [MediumFlexibleTopAppBar with Subtitle](#mediumflexibletopappbar-with-subtitle)
    - [MediumFlexibleTopAppBar Parameters](#mediumflexibletopappbar-parameters)
- [MediumFloatingActionButton](#mediumfloatingactionbutton)
  - [Basic MediumFloatingActionButton](#basic-mediumfloatingactionbutton)
  - [Animated Show/Hide MediumFloatingActionButton](#animated-showhide-mediumfloatingactionbutton)
    - [MediumFloatingActionButton Parameters](#mediumfloatingactionbutton-parameters)
- [MediumTopAppBar](#mediumtopappbar)
  - [MediumTopAppBar with Scroll Behavior](#mediumtopappbar-with-scroll-behavior)
    - [MediumTopAppBar Parameters](#mediumtopappbar-parameters)
- [ModalBottomSheet](#modalbottomsheet)
  - [Basic ModalBottomSheet](#basic-modalbottomsheet)
    - [ModalBottomSheet Parameters](#modalbottomsheet-parameters)
- [ModalDrawerSheet](#modaldrawersheet)
  - [ModalDrawerSheet (Basic)](#modaldrawersheet-basic)
  - [ModalDrawerSheet with DrawerState (Predictive Back Support)](#modaldrawersheet-with-drawerstate-predictive-back-support)
    - [ModalDrawerSheet Parameters](#modaldrawersheet-parameters)
- [ModalNavigationDrawer](#modalnavigationdrawer)
  - [ModalNavigationDrawer Example](#modalnavigationdrawer-example)
    - [ModalNavigationDrawer Parameters](#modalnavigationdrawer-parameters)
- [ModalWideNavigationRail](#modalwidenavigationrail)
  - [ModalWideNavigationRail with Header](#modalwidenavigationrail-with-header)
  - [Dismissible ModalWideNavigationRail](#dismissible-modalwidenavigationrail)
    - [ModalWideNavigationRail Parameters](#modalwidenavigationrail-parameters)
- [MultiChoiceSegmentedButtonRow](#multichoicesegmentedbuttonrow)
  - [MultiChoiceSegmentedButtonRow Example](#multichoicesegmentedbuttonrow-example)
    - [MultiChoiceSegmentedButtonRow Parameters](#multichoicesegmentedbuttonrow-parameters)
- [NavigationBar](#navigationbar)
  - [NavigationBar Example](#navigationbar-example)
    - [NavigationBar Parameters](#navigationbar-parameters)
- [NavigationDrawerItem](#navigationdraweritem)
  - [NavigationDrawerItem Example](#navigationdraweritem-example)
    - [NavigationDrawerItem Parameters](#navigationdraweritem-parameters)
- [NavigationRail](#navigationrail)
  - [NavigationRail Example](#navigationrail-example)
    - [NavigationRail Parameters](#navigationrail-parameters)
- [NavigationRailItem](#navigationrailitem)
    - [NavigationRailItem Parameters](#navigationrailitem-parameters)
- [OutlinedButton](#outlinedbutton)
  - [Standard OutlinedButton](#standard-outlinedbutton)
  - [Expressive OutlinedButton with Shape Morphing](#expressive-outlinedbutton-with-shape-morphing)
    - [OutlinedButton Parameters](#outlinedbutton-parameters)
- [OutlinedCard](#outlinedcard)
  - [Non-Interactive OutlinedCard](#non-interactive-outlinedcard)
  - [Clickable OutlinedCard](#clickable-outlinedcard)
    - [OutlinedCard Parameters](#outlinedcard-parameters)
- [OutlinedIconButton](#outlinediconbutton)
  - [Standard OutlinedIconButton](#standard-outlinediconbutton)
  - [Large OutlinedIconButton](#large-outlinediconbutton)
  - [Expressive OutlinedIconButton with Shape Morphing](#expressive-outlinediconbutton-with-shape-morphing)
    - [OutlinedIconButton Parameters](#outlinediconbutton-parameters)
- [OutlinedIconToggleButton](#outlinedicontogglebutton)
  - [Standard OutlinedIconToggleButton](#standard-outlinedicontogglebutton)
  - [Expressive OutlinedIconToggleButton with Shape Morphing](#expressive-outlinedicontogglebutton-with-shape-morphing)
    - [OutlinedIconToggleButton Parameters](#outlinedicontogglebutton-parameters)
- [Summary](#summary)
- [OutlinedSecureTextField](#outlinedsecuretextfield)
  - [Overview](#overview)
  - [Key Features](#key-features)
  - [API Signature](#api-signature)
  - [Parameters](#parameters)
  - [Related Components](#related-components)
- [OutlinedTextField](#outlinedtextfield)
  - [Overview](#overview)
  - [Key Features](#key-features)
  - [API Signatures](#api-signatures)
    - [String-based Overload](#string-based-overload)
    - [TextFieldValue-based Overload](#textfieldvalue-based-overload)
    - [TextFieldState-based Overload (Added in 1.4.0)](#textfieldstate-based-overload-added-in-140)
  - [Usage Guidelines](#usage-guidelines)
  - [Related Components](#related-components)
- [OutlinedToggleButton](#outlinedtogglebutton)
  - [Overview](#overview)
  - [Key Features](#key-features)
  - [API Signature](#api-signature)
  - [Usage Example](#usage-example)
  - [Related Components](#related-components)
- [PermanentDrawerSheet](#permanentdrawersheet)
  - [Overview](#overview)
  - [API Signature](#api-signature)
  - [Related Components](#related-components)
- [PermanentNavigationDrawer](#permanentnavigationdrawer)
  - [Overview](#overview)
  - [Key Features](#key-features)
  - [API Signature](#api-signature)
  - [Usage Example](#usage-example)
  - [Usage Guidelines](#usage-guidelines)
  - [Related Components](#related-components)
- [PrimaryScrollableTabRow](#primaryscrollabletabrow)
  - [Overview](#overview)
  - [Key Features](#key-features)
  - [API Signature](#api-signature)
  - [Parameters](#parameters)
  - [Usage Guidelines](#usage-guidelines)
  - [Related Components](#related-components)
- [PrimaryTabRow](#primarytabrow)
  - [Overview](#overview)
  - [Key Features](#key-features)
  - [API Signature](#api-signature)
  - [Usage Example](#usage-example)
  - [Customization](#customization)
  - [Usage Guidelines](#usage-guidelines)
  - [Related Components](#related-components)
- [ProvideTextStyle](#providetextstyle)
  - [Overview](#overview)
  - [API Signature](#api-signature)
  - [Related](#related)
- [RadioButton](#radiobutton)
  - [Overview](#overview)
  - [Key Features](#key-features)
  - [API Signature](#api-signature)
  - [Usage Examples](#usage-examples)
    - [Basic Radio Buttons](#basic-radio-buttons)
    - [Radio Group with Text Labels](#radio-group-with-text-labels)
  - [Parameters](#parameters)
  - [Usage Guidelines](#usage-guidelines)
- [RangeSlider](#rangeslider)
  - [Overview](#overview)
  - [Key Features](#key-features)
  - [API Signatures](#api-signatures)
    - [Value-based Overload](#value-based-overload)
    - [State-based Overload (Added in 1.2.0)](#state-based-overload-added-in-120)
  - [Usage Examples](#usage-examples)
    - [Continuous Range Slider](#continuous-range-slider)
    - [Stepped Range Slider](#stepped-range-slider)
    - [Custom Thumbs with Tooltips](#custom-thumbs-with-tooltips)
  - [Parameters](#parameters)
  - [Usage Guidelines](#usage-guidelines)
  - [Related Components](#related-components)
- [Scaffold](#scaffold)
  - [Overview](#overview)
  - [Key Features](#key-features)
  - [API Signature](#api-signature)
  - [Usage Examples](#usage-examples)
    - [Basic Scaffold with TopAppBar and FAB](#basic-scaffold-with-topappbar-and-fab)
    - [Scaffold with Snackbar](#scaffold-with-snackbar)
  - [Parameters](#parameters)
  - [Usage Guidelines](#usage-guidelines)
  - [Related Components](#related-components)
- [ScrollableTabRow](#scrollabletabrow)
  - [Deprecation Notice](#deprecation-notice)
  - [Overview](#overview)
  - [Migration](#migration)
- [SearchBar](#searchbar)
  - [Overview](#overview)
  - [Key Features](#key-features)
  - [API Signature](#api-signature)
  - [Parameters](#parameters)
  - [Usage Guidelines](#usage-guidelines)
  - [Related Components](#related-components)
- [SearchBar (Additional Overloads)](#searchbar-additional-overloads)
  - [Expanded SearchBar Overload](#expanded-searchbar-overload)
    - [API Signature](#api-signature)
    - [Parameters](#parameters)
    - [Usage Guidelines](#usage-guidelines)
  - [Deprecated String-based Overload](#deprecated-string-based-overload)
- [SecondaryScrollableTabRow](#secondaryscrollabletabrow)
  - [Overview](#overview)
  - [Key Features](#key-features)
  - [API Signature](#api-signature)
  - [Usage Guidelines](#usage-guidelines)
  - [Related Components](#related-components)
- [SecondaryTabRow](#secondarytabrow)
  - [Overview](#overview)
  - [Key Features](#key-features)
  - [API Signature](#api-signature)
  - [Usage Example](#usage-example)
  - [Usage Guidelines](#usage-guidelines)
  - [Related Components](#related-components)
- [SecureTextField](#securetextfield)
  - [Overview](#overview)
  - [Key Features](#key-features)
  - [API Signature](#api-signature)
  - [Usage Example](#usage-example)
  - [Parameters](#parameters)
  - [Related Components](#related-components)
- [ShortNavigationBar](#shortnavigationbar)
  - [Overview](#overview)
  - [Key Features](#key-features)
  - [API Signature](#api-signature)
  - [Usage Examples](#usage-examples)
    - [Small Screen Configuration (EqualWeight)](#small-screen-configuration-equalweight)
    - [Medium Screen Configuration (Centered)](#medium-screen-configuration-centered)
  - [Configuration Guidelines](#configuration-guidelines)
  - [Related Components](#related-components)
- [ShortNavigationBarItem](#shortnavigationbaritem)
  - [Overview](#overview)
  - [Key Features](#key-features)
  - [API Signature](#api-signature)
  - [Parameters](#parameters)
  - [Usage Guidelines](#usage-guidelines)
- [SingleChoiceSegmentedButtonRow](#singlechoicesegmentedbuttonrow)
  - [Overview](#overview)
  - [Key Features](#key-features)
  - [API Signature](#api-signature)
  - [Usage Example](#usage-example)
  - [Parameters](#parameters)
  - [Related Components](#related-components)
- [Slider](#slider)
  - [Overview](#overview)
  - [Key Features](#key-features)
  - [API Signature](#api-signature)
  - [Usage Examples](#usage-examples)
    - [Continuous Slider](#continuous-slider)
    - [Stepped Slider](#stepped-slider)
  - [Parameters](#parameters)
  - [Usage Guidelines](#usage-guidelines)
  - [Related Components](#related-components)
- [Slider (Additional Overloads)](#slider-additional-overloads)
  - [Value-based Overload](#value-based-overload)
    - [API Signature](#api-signature)
  - [Custom Thumb/Track Overload (Added in 1.2.0)](#custom-thumbtrack-overload-added-in-120)
    - [API Signature](#api-signature)
  - [Usage Examples](#usage-examples)
    - [Custom Thumb with Tooltip](#custom-thumb-with-tooltip)
    - [Custom Track and Thumb Colors](#custom-track-and-thumb-colors)
    - [Centered Track Variant](#centered-track-variant)
  - [Parameters](#parameters)
  - [Usage Guidelines](#usage-guidelines)
  - [Related Components](#related-components)
- [SplitButton Size Variants](#splitbutton-size-variants)
  - [Extra Small Split Button](#extra-small-split-button)
  - [Medium, Large, and Extra Large Variants](#medium-large-and-extra-large-variants)
- [SplitButton Emphasis Hierarchy](#splitbutton-emphasis-hierarchy)
- [SuggestionChip](#suggestionchip)
- [Surface](#surface)
  - [Basic Surface](#basic-surface)
  - [Clickable Surface](#clickable-surface)
  - [Toggleable Surface](#toggleable-surface)
  - [Selectable Surface](#selectable-surface)
- [SwipeToDismissBox](#swipetodismissbox)
- [Switch](#switch)
  - [Basic Switch](#basic-switch)
  - [Switch with Custom Icon](#switch-with-custom-icon)
- [Tab](#tab)
  - [Custom Tab](#custom-tab)
  - [Tab with Text and Icon](#tab-with-text-and-icon)
- [TabRow](#tabrow)
  - [Basic TabRow](#basic-tabrow)
  - [TabRow with Custom Indicator](#tabrow-with-custom-indicator)
- [Text](#text)
  - [Basic Text](#basic-text)
  - [Text with Styling](#text-with-styling)
  - [Text with Links](#text-with-links)
  - [Text with ColorProducer](#text-with-colorproducer)
- [TextButton](#textbutton)
  - [Standard TextButton](#standard-textbutton)
  - [TextButton with Shape Morphing (Expressive)](#textbutton-with-shape-morphing-expressive)
- [TextField (Continued)](#textfield-continued)
- [TimeInput](#timeinput)
- [TimePicker](#timepicker)
  - [TimePicker with Mode Toggle](#timepicker-with-mode-toggle)
- [TimePickerDialog](#timepickerdialog)
- [TimePickerState](#timepickerstate)
- [ToggleButton](#togglebutton)
  - [Basic ToggleButton](#basic-togglebutton)
  - [ToggleButton with Icon](#togglebutton-with-icon)
  - [ToggleButton Size Variants](#togglebutton-size-variants)
  - [Custom Shape Morphing](#custom-shape-morphing)
- [TooltipBox](#tooltipbox)
  - [Plain Tooltip](#plain-tooltip)
  - [Tooltip Positioning](#tooltip-positioning)
  - [Plain Tooltip with Caret](#plain-tooltip-with-caret)
  - [Custom Caret Size](#custom-caret-size)
  - [Rich Tooltip](#rich-tooltip)
  - [Programmatic Tooltip Control](#programmatic-tooltip-control)
- [TooltipState](#tooltipstate)
- [TopAppBar](#topappbar)
  - [Basic TopAppBar](#basic-topappbar)
  - [TopAppBar with Scroll Behavior](#topappbar-with-scroll-behavior)
  - [TopAppBar with Subtitle (Expressive)](#topappbar-with-subtitle-expressive)
  - [Centered Title TopAppBar](#centered-title-topappbar)
- [AppBarWithSearch (TopSearchBar)](#appbarwithsearch-topsearchbar)
  - [Full Screen Search](#full-screen-search)
  - [Docked Search](#docked-search)
  - [TriStateCheckbox](#tristatecheckbox)
  - [TwoRowsTopAppBar](#tworowstopappbar)
  - [VerticalDivider](#verticaldivider)
  - [VerticalDragHandle](#verticaldraghandle)
  - [VerticalFloatingToolbar](#verticalfloatingtoolbar)
  - [VerticalFloatingToolbar (with FAB)](#verticalfloatingtoolbar-with-fab)
  - [VerticalSlider](#verticalslider)
  - [WideNavigationRail](#widenavigationrail)
  - [WideNavigationRailItem](#widenavigationrailitem)
- [Color Schemes and Theming](#color-schemes-and-theming)
  - [contentColorFor](#contentcolorfor)
  - [darkColorScheme](#darkcolorscheme)
  - [dynamicDarkColorScheme / dynamicLightColorScheme](#dynamicdarkcolorscheme-dynamiclightcolorscheme)
  - [expressiveLightColorScheme](#expressivelightcolorscheme)
  - [lightColorScheme](#lightcolorscheme)
- [State Management Functions](#state-management-functions)
  - [rememberBottomAppBarState](#rememberbottomappbarstate)
  - [rememberBottomSheetScaffoldState](#rememberbottomsheetscaffoldstate)
  - [rememberDatePickerState (LocalDate)](#rememberdatepickerstate-localdate)
  - [rememberDatePickerState (Milliseconds)](#rememberdatepickerstate-milliseconds)
  - [rememberDateRangePickerState (LocalDate)](#rememberdaterangepickerstate-localdate)
  - [rememberDateRangePickerState (Milliseconds)](#rememberdaterangepickerstate-milliseconds)
  - [rememberDrawerState](#rememberdrawerstate)
  - [rememberFloatingToolbarState](#rememberfloatingtoolbarstate)
  - [rememberModalBottomSheetState](#remembermodalbottomsheetstate)
  - [rememberRangeSliderState](#rememberrangesliderstate)
  - [rememberSearchBarState](#remembersearchbarstate)
  - [rememberSliderState](#remembersliderstate)
  - [rememberStandardBottomSheetState](#rememberstandardbottomsheetstate)
  - [rememberSwipeToDismissBoxState](#rememberswipetodismissboxstate)
  - [rememberTimePickerState](#remembertimepickerstate)
  - [rememberTooltipState](#remembertooltipstate)
  - [rememberTopAppBarState](#remembertopappbarstate)
  - [rememberWideNavigationRailState](#rememberwidenavigationrailstate)
- [Ripple Effects](#ripple-effects)
  - [ripple (Static Color)](#ripple-static-color)
  - [ripple (Dynamic Color)](#ripple-dynamic-color)
- [Extension Functions](#extension-functions)
  - [FloatingActionButtonMenuItem](#floatingactionbuttonmenuitem)
  - [NavigationBarItem](#navigationbaritem)
  - [PlainTooltip](#plaintooltip)
  - [RichTooltip](#richtooltip)
  - [SegmentedButton (Multi-Choice)](#segmentedbutton-multi-choice)
  - [SegmentedButton (Single-Choice)](#segmentedbutton-single-choice)
  - [animateFloatingActionButton](#animatefloatingactionbutton)
- [Conclusion](#conclusion)
- [Extension Functions and Utilities](#extension-functions-and-utilities)
  - [contentColorFor](#contentcolorfor)
  - [surfaceColorAtElevation](#surfacecoloratelevation)
  - [minimumInteractiveComponentSize](#minimuminteractivecomponentsize)
  - [DatePicker Extension Functions (Java Time API)](#datepicker-extension-functions-java-time-api)
    - [getDisplayedMonth](#getdisplayedmonth)
    - [setDisplayedMonth](#setdisplayedmonth)
    - [getSelectedDate](#getselecteddate)
    - [setSelectedDate](#setselecteddate)
    - [getSelectedStartDate / getSelectedEndDate](#getselectedstartdate-getselectedenddate)
    - [setSelection](#setselection)
  - [Shape Conversion Functions](#shape-conversion-functions)
    - [RoundedPolygon.toPath](#roundedpolygontopath)
    - [Morph.toPath](#morphtopath)
    - [RoundedPolygon.toShape](#roundedpolygontoshape)
  - [TimePickerState Extension Properties](#timepickerstate-extension-properties)
    - [isHourInputValid](#ishourinputvalid)
    - [isMinuteInputValid](#isminuteinputvalid)
    - [isInputValid](#isinputvalid)
    - [isPm](#ispm)
- [CompositionLocal Properties](#compositionlocal-properties)
  - [LocalAbsoluteTonalElevation](#localabsolutetonalelevation)
  - [LocalContentColor](#localcontentcolor)
  - [LocalTextStyle](#localtextstyle)
  - [LocalMinimumInteractiveComponentSize](#localminimuminteractivecomponentsize)
  - [LocalTonalElevationEnabled](#localtonalelevationenabled)
  - [LocalRippleConfiguration](#localrippleconfiguration)
  - [Component Override CompositionLocals](#component-override-compositionlocals)
- [Global Configuration](#global-configuration)
  - [isCheckboxStylingFixEnabled](#ischeckboxstylingfixenabled)

---

## 1. Introduction

### 1.1 What is Material Design 3 Expressive

Material Design is an adaptable system of guidelines, components, and tools that support best practices for user interface design. Backed by open-source code, Material Design streamlines collaboration between designers and developers, helping teams quickly build beautiful products.

**See also:**
- [Theming System](#2-theming-system) - Learn about colors, typography, and shapes
- [Motion System](#3-motion-system) - Understand expressive motion and animations
- [Components Reference](#4-components-reference) - Browse all available components

Material Design 3 Expressive is the latest evolution of Material Design, introducing an exciting new theming capability: **motion**. This release gives you access to a customizable motion scheme using motion physics, providing more control than ever over how motion works and feels in your product.

**Key Changes in Material 3 Expressive:**
- Previously, Material motion used non-customizable easing and duration values
- Now, motion is defined through customizable motion physics using spring-based animations
- Motion properties can be customized or overridden to match your product's needs
- All Material Components automatically inherit your motion theme for a unified experience


### 1.2 Getting Started

To start using Material Design 3 Expressive, ensure you're using the latest version of Compose Material 3:

**See also:**
- [Package Information](#81-package-information) - Version details and platform support
- [MaterialExpressiveTheme](#materialexpressivetheme) - Set up your app theme
- [Code Examples Repository](#7-code-examples-repository) - Complete implementation examples

```kotlin
dependencies {
    implementation("androidx.compose.material3:material3:1.5.0-alpha10")
}
```

**Important Note:** When version 1.5.0 reaches stable, the Material Expressive APIs will move from alpha to stable. The APIs are currently in alpha and subject to change.

Simply updating the Compose dependency will automatically apply the standard motion scheme to all Material Components in your app. For additional customization, you can define your own motion scheme to suit your app's unique needs.

---

## 2. Theming System

### 2.1 Color Schemes

Material 3 provides comprehensive color theming through `ColorScheme`. You can use predefined light and dark color schemes or create dynamic color schemes that adapt to the user's device.

**Available Color Scheme APIs:**
- `lightColorScheme()` - Creates a light color scheme
- `darkColorScheme()` - Creates a dark color scheme
- `dynamicLightColorScheme(context)` - Creates a dynamic light color scheme (API 31+)
- `dynamicDarkColorScheme(context)` - Creates a dynamic dark color scheme (API 31+)

Dynamic color schemes automatically adapt to the user's wallpaper and system preferences, providing a personalized experience.

**See also:**
- [ColorScheme API](#colorscheme) - Detailed API reference
- [MaterialTheme](#materialtheme) - Apply color schemes to your app
- [Dynamic Color APIs](#55-dynamic-color-apis) - Implementation details for dynamic colors

### 2.2 Typography

The `Typography` class defines the typographic hierarchy for your app, ensuring consistent text styling across all components.

**See also:**
- [MaterialTheme](#materialtheme) - Apply typography to your app
- [ProvideTextStyle](#providetextstyle) - Override text styles locally

### 2.3 Shapes

The `Shapes` class defines the standard shape system for your app. In the **Expressive** variant, this is augmented by organic polygons.

#### 2.3.1 Expressive Organic Polygons (`MaterialShapes`)
For non-geometric, organic forms, use the `MaterialShapes` object provided by the library. These are defined as `RoundedPolygon` objects.

**Key Polygons:**
- `MaterialShapes.Square`: A soft-edged squircle.
- `MaterialShapes.Pill`: A stadium shape for extended elements.
- `MaterialShapes.Sunny`: A burst shape for attention-grabbing elements.
- `MaterialShapes.Cookie9Sided`: A playful, scalloped edge.

#### 2.3.2 Conversion: `toShape()`
Since `RoundedPolygon` is not a Compose `Shape`, it must be converted using the `.toShape()` extension function.

```kotlin
// Usage in Clipping
val expressiveShape = MaterialShapes.Pill.toShape()
Box(modifier = Modifier.clip(expressiveShape)) { ... }
```

**See also:**
- [MaterialTheme](#materialtheme) - Apply shapes to your app
- [Button with Shape Morphing](#button-expressive-with-shape-morphing) - Example of expressive shape animations

---

## 3. Motion System

### 3.1 Motion Schemes

The motion system introduces two preset motion schemes that define how your product feels:

#### Expressive Motion Scheme
**Recommended for most situations**, particularly hero moments and key interactions. The Expressive scheme overshoots final values to add bounce, creating a more dynamic and engaging feel.

**See also:**
- [Motion Scheme Presets](#61-motion-scheme-presets) - Detailed preset configurations
- [Custom Motion Scheme Example](#73-custom-motion-scheme-example) - Implementation example

#### Standard Motion Scheme
Features a small amount of bounce and feels more functional. Use this for utilitarian products where a more subdued motion is appropriate.

**See also:**
- [Motion Scheme Presets](#61-motion-scheme-presets) - Detailed preset configurations
- [Adapting Motion to Device Context](#62-adapting-motion-to-device-context) - Device-specific considerations

Both schemes use spring-based animations under the hood, ensuring natural and interruptible motion.


**Choosing Between Schemes:**

Select your motion scheme once at the theme level, and all Material Components with built-in movement will automatically use the selected specification:

```kotlin
@Composable
fun YourCustomTheme() {
    MaterialExpressiveTheme(
        motionScheme = MotionScheme.expressive(), // or MotionScheme.standard()
    ) {
        // Your app content
    }
}
```

### 3.2 Animation Specifications

The motion scheme provides two distinct types of animation specifications:

#### Spatial Animation Specs
Used to animate changes in an object's **position, orientation, size, and shape**. The spring overshoots the final value and bounces into place, creating a dynamic feel.

**Use cases:**
- Movement animations
- Rotation animations
- Size changes
- Shape transformations

**See also:**
- [Spring Physics](#33-spring-physics) - Understanding spring-based animations
- [Animated Custom Component](#74-animated-custom-component) - Implementation example

#### Effect Animation Specs
Used to animate an object's **properties such as color and opacity**, where overshoot would be inappropriate. These animations ease smoothly into the final value without bounce.

**Use cases:**
- Color transitions
- Opacity changes
- Other visual effects

**See also:**
- [Customizing Motion](#34-customizing-motion) - Create custom animation specs

#### Animation Speeds

Each animation type supports three speed variants:

| Speed | Spatial Use Case | Effects Use Case |
|-------|-----------------|------------------|
| **Default** | Animations that partially cover the screen (e.g., bottom sheets, expanded navigation rails) | Opacity changes within navigation rails |
| **Fast** | Small components (e.g., switches, buttons) | Color changes of switch handles |
| **Slow** | Full-screen animations | Full-screen content refresh |

**Important:** Speed tokens work consistently across devices. For example, the Spatial "fast" token will always be faster than "default" or "slow," but the exact values adapt based on whether the device is a wearable, phone, or tablet. This ensures motion feels appropriate for the device context.

**See also:**
- [Adapting Motion to Device Context](#62-adapting-motion-to-device-context) - Device-specific motion guidelines

### 3.3 Spring Physics

The motion system uses spring-based animations instead of traditional duration-based tweens. This provides several key advantages:

**Benefits of Spring Animations:**

1. **Natural Interruption Handling:** When an animation is interrupted and retargeted to a new destination, spring animations use their current velocity to perform seamless transitions between states. Tween animations, by contrast, create jarring transitions when interrupted.

2. **Device Adaptability:** Springs automatically adapt to different screen sizes since they specify damping and stiffness rather than fixed time durations. This ensures animations feel fast or slow appropriately for each device type.

3. **More Natural Feel:** Spring physics creates motion that feels more organic and responsive to user input.

**Comparison: Tween vs Spring Interruptions**

When a user interrupts an animation (e.g., swiping back while a transition is in progress):
- **Tween:** Abruptly changes direction, creating a jarring experience
- **Spring:** Smoothly transitions using current velocity, maintaining natural flow

**See also:**
- [Customizing Motion](#34-customizing-motion) - Create custom spring configurations
- [Performance Optimization](#64-performance-optimization) - Optimize animation performance

### 3.4 Customizing Motion

#### Creating Custom Motion Schemes

For fine-grained control, you can create your own `MotionScheme` object and return different `AnimationSpec` values for each property:

```kotlin
@ExperimentalMaterial3ExpressiveApi
fun playfulMotionScheme(): MotionScheme =
    object : MotionScheme {

        override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> {
            return spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 1600f
            )
        }

        override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> {
            return spring(dampingRatio = 0.6f, stiffness = 700f)
        }

        override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> {
            return spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 3800f)
        }

        override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> {
            return spring(dampingRatio = 0.6f, stiffness = 1400f)
        }

        override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> {
            return spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f)
        }

        override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> {
            return spring(dampingRatio = 0.6f, stiffness = 300f)
        }
    }

// Apply the custom scheme in your theme
@Composable
fun MyCustomTheme(
    content: @Composable () -> Unit
) {
    MaterialExpressiveTheme(
        motionScheme = playfulMotionScheme(),
        content = content
    )
}
```

This example creates a "playful" motion scheme with extra bounce. You can adjust the `dampingRatio` and `stiffness` values to achieve different motion characteristics:
- **Lower dampingRatio** = more bounce
- **Higher stiffness** = faster, snappier motion
- **Lower stiffness** = slower, more fluid motion

**See also:**
- [Spring Physics](#33-spring-physics) - Understanding damping and stiffness
- [Custom Motion Scheme Example](#73-custom-motion-scheme-example) - Complete implementation
- [MaterialExpressiveTheme](#materialexpressivetheme) - Apply custom motion schemes

#### Using Motion Schemes in Custom Components

To maintain consistency with Material Components, ensure your custom components adopt the motion scheme from the theme:

**Before (using tween):**

```kotlin
val interactionSource = remember { MutableInteractionSource() }
val isPressed by interactionSource.collectIsPressedAsState()

val scale by animateFloatAsState(
    targetValue = if (isPressed) 8f else 1f,
    animationSpec = tween(1000),
    label = "scale"
)

val color by animateColorAsState(
    targetValue = if (isPressed) Color.Green else Color.Red,
    animationSpec = tween(1000),
    label = "color"
)

Box(
    modifier = Modifier
        .fillMaxSize()
        .clickable(
            interactionSource = interactionSource,
            indication = null
        ) {}
) {
    Text(
        text = "Hello",
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin.Center
            }
            .align(Alignment.Center),
        style = LocalTextStyle.current.copy(
            color = color,
            textMotion = TextMotion.Animated
        )
    )
}
```


**After (using motion scheme):**

```kotlin
val interactionSource = remember { MutableInteractionSource() }
val isPressed by interactionSource.collectIsPressedAsState()

val scale by animateFloatAsState(
    targetValue = if (isPressed) 8f else 1f,
    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(),
    label = "scale"
)

val color by animateColorAsState(
    targetValue = if (isPressed) Color.Green else Color.Red,
    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>(),
    label = "color"
)

Box(
    modifier = Modifier
        .fillMaxSize()
        .clickable(
            interactionSource = interactionSource,
            indication = null
        ) {}
) {
    Text(
        text = "Hello",
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin.Center
            }
            .align(Alignment.Center),
        style = LocalTextStyle.current.copy(
            color = color,
            textMotion = TextMotion.Animated
        )
    )
}
```

**Key Changes:**
- Scale animation uses `defaultSpatialSpec<Float>()` (for position/size changes)
- Color animation uses `defaultEffectsSpec<Color>()` (for visual property changes)
- Component automatically adapts when switching between Expressive and Standard themes

---

## 4. Components Reference

Material Design 3 Expressive provides a comprehensive set of interactive components organized by purpose:

### 4.1 Action Components

#### Buttons

**Button Groups**
Organize buttons and add interactions between them.

**See also:**
- [ButtonGroup](#buttongroup) - Detailed API reference
- [ButtonGroup with Overflow Handling](#buttongroup-with-overflow-handling) - Implementation examples

**Standard Buttons**
Prompt most actions in a UI. Available variants:
- `Button` - Filled button (primary actions)
- `ElevatedButton` - Elevated button (secondary actions)
- `FilledTonalButton` - Filled tonal button (medium emphasis)
- `OutlinedButton` - Outlined button (low emphasis)
- `TextButton` - Text button (minimal emphasis)

**See also:**
- [Button](#button) - Detailed API reference
- [Button with Shape Morphing](#button-expressive-with-shape-morphing) - Expressive animations
- [ElevatedButton](#elevatedbutton-component) - Elevated button details
- [OutlinedButton](#outlinedbutton) - Outlined button details

**Icon Buttons**
Help users take actions with a single tap. Available variants:
- `IconButton` - Standard icon button
- `IconToggleButton` - Standard icon toggle button
- `FilledIconButton` - Filled icon button
- `FilledIconToggleButton` - Filled icon toggle button
- `FilledTonalIconButton` - Filled tonal icon button
- `FilledTonalIconToggleButton` - Filled tonal icon toggle button
- `OutlinedIconButton` - Outlined icon button
- `OutlinedIconToggleButton` - Outlined icon toggle button

**See also:**
- [IconButton Component](#iconbutton-component) - Detailed API reference
- [IconToggleButton Component](#icontogglebutton-component) - Toggle button details
- [FilledTonalIconButton Component](#filledtonaliconbutton-component) - Tonal variant details
- [Icon Component](#icon-component) - Icon usage guidelines

**Floating Action Buttons (FABs)**
Help users take primary actions:
- `FloatingActionButton` - Standard FAB
- `SmallFloatingActionButton` - Small FAB
- `LargeFloatingActionButton` - Large FAB
- `ExtendedFloatingActionButton` - Extended FAB with text label

**See also:**
- [LargeFloatingActionButton](#largefloatingactionbutton) - Large FAB details
- [ExtendedFloatingActionButton Component](#extendedfloatingactionbutton-component) - Extended FAB details
- [MediumFloatingActionButton](#mediumfloatingactionbutton) - Medium FAB details
- [Scaffold](#scaffold) - Integrate FABs with app layout

**FAB Menu**
Opens from a FAB to display multiple related actions.

**Segmented Buttons**
Help users select options, switch views, or sort elements:
- `SegmentedButton` - Individual segmented button
- `SingleChoiceSegmentedButtonRow` - Single selection group
- `MultiChoiceSegmentedButtonRow` - Multiple selection group

**See also:**
- [SingleChoiceSegmentedButtonRow](#singlechoicesegmentedbuttonrow) - Single selection details
- [MultiChoiceSegmentedButtonRow](#multichoicesegmentedbuttonrow) - Multiple selection details

**Split Buttons**
Open a menu to provide more options related to an action.


#### Date & Time Pickers

**Date Pickers**
Let users select a date or range of dates:
- `DatePicker` - Single date selection
- `DatePickerDialog` - Date picker embedded in a dialog
- `DateRangePicker` - Date range selection
- `rememberDatePickerState()` - State management for date picker
- `rememberDateRangePickerState()` - State management for date range picker

**See also:**
- [DatePicker](#datepicker) - Detailed API reference
- [DatePickerDialog](#datepickerdialog) - Dialog implementation
- [DateRangePicker Component](#daterangepicker-component) - Range selection details
- [DatePickerState](#datepickerstate) - State management API
- [DateRangePickerState](#daterangepickerstate) - Range state management

**Time Pickers**
Help users select and set a specific time:
- `TimePicker` - Visual time picker
- `TimeInput` - Text-based time input

#### Loading & Progress

**Loading Indicators**
Show progress for short wait times.

**See also:**
- [LoadingIndicator Component](#loadingindicator-component) - Detailed API reference
- [CircularWavyProgressIndicator](#circularprogressindicator-component) - Expressive variants

**Progress Indicators**
Show the status of a process in real time:
- `LinearProgressIndicator` - Horizontal progress bar
- `CircularProgressIndicator` - Circular progress indicator

**See also:**
- [CircularProgressIndicator Component](#circularprogressindicator-component) - Detailed API reference
- [LinearWavyProgressIndicator](#linearwavyprogressindicator) - Expressive linear progress

### 4.2 Containment Components

#### Cards
Display content and actions about a single subject:
- `Card` - Filled card
- `ElevatedCard` - Elevated card
- `OutlinedCard` - Outlined card

**See also:**
- [Card Component](#card-component) - Detailed API reference
- [ElevatedCard Component](#elevatedcard-component) - Elevated card details
- [OutlinedCard](#outlinedcard) - Outlined card details

#### Sheets

**Bottom Sheets**
Show secondary content anchored to the bottom of the screen:
- `BottomSheetScaffold` - Bottom sheet with scaffold
- `ModalBottomSheet` - Modal bottom sheet

**See also:**
- [BottomSheetScaffold](#bottomsheetscaffold) - Detailed API reference
- [ModalBottomSheet](#modalbottomsheet) - Modal sheet details

**Side Sheets**
Show secondary content anchored to the side of the screen.

#### Dialogs
Provide important prompts in a user flow:
- `AlertDialog` - Basic alert dialog
- `DatePickerDialog` - Date picker in dialog format

**See also:**
- [BasicAlertDialog](#basicalertdialog) - Detailed API reference
- [Basic AlertDialog Example](#71-basic-alertdialog-example) - Implementation example
- [Custom AlertDialog with Content](#72-custom-alertdialog-with-content) - Advanced usage
- [Dialog APIs](#52-dialog-apis) - Complete API reference

#### Carousel
Shows a collection of items that can be scrolled on and off the screen.

**Types:**
- `HorizontalMultiBrowseCarousel` - For browsing many items with mixed aspect ratios.
- `HorizontalUncontainedCarousel` - For browsing items of consistent size.

### HorizontalMultiBrowseCarousel
Ideal for browsing a large collection of items (like a photo gallery) where items can have different aspect ratios.

**API Signature:**
```kotlin
@ExperimentalMaterial3ExpressiveApi
@Composable
fun HorizontalMultiBrowseCarousel(
    state: CarouselState,
    preferredItemWidth: Dp,
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 0.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    minSmallItemWidth: Dp = ...,
    maxSmallItemWidth: Dp = ...,
    content: @Composable CarouselItemScope.(itemIndex: Int) -> Unit
)
```

**Usage Example:**
```kotlin
val state = rememberCarouselState { items.count() }
HorizontalMultiBrowseCarousel(
    state = state,
    preferredItemWidth = 200.dp,
    itemSpacing = 8.dp,
    contentPadding = PaddingValues(horizontal = 16.dp)
) { index ->
    AsyncImage(
        model = items[index].imageUrl,
        contentDescription = null,
        modifier = Modifier
            .height(200.dp)
            .maskClip(MaterialTheme.shapes.medium)
    )
}
```

### HorizontalUncontainedCarousel
Displays items of consistent size, usually with the last visible item partially cut off to encourage scrolling.

**API Signature:**
```kotlin
@ExperimentalMaterial3ExpressiveApi
@Composable
fun HorizontalUncontainedCarousel(
    state: CarouselState,
    itemWidth: Dp,
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 0.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable CarouselItemScope.(itemIndex: Int) -> Unit
)
```

**Expressive Menu Updates (since alpha09):**
- `DropdownMenuItem` - Standard menu item.
- `ToggleableMenuItem` - Menu item that acts like a checkbox.
- `SelectableMenuItem` - Menu item that acts like a radio button.
- `ExpandedDockedSearchBarWithGap` - Search bar with a visual gap.

### 4.3 Communication Components

#### Badges
Show notifications, counts, or status information on navigation items and icons:
- `Badge` - Simple badge
- `BadgedBox` - Container for badged content

**See also:**
- [Badge](#badge) - Detailed API reference
- [BadgedBox](#badgedbox) - Container implementation

#### Snackbar
Shows short updates about app processes at the bottom of the screen.

**See also:**
- [Scaffold](#scaffold) - Integrate snackbars with app layout

#### Tooltips
Display brief labels or messages:
- `PlainTooltipBox` - Simple tooltip
- `RichTooltipBox` - Rich tooltip with additional content

### 4.4 Navigation Components

#### Navigation Bar
Lets users switch between UI views on smaller devices:
- `NavigationBar` - Bottom navigation bar
- `NavigationBarItem` - Navigation bar item

**See also:**
- [NavigationBar](#navigationbar) - Detailed API reference
- [ShortNavigationBar](#shortnavigationbar) - Compact navigation variant
- [Badges](#badges) - Add notification badges to navigation items

#### Navigation Drawer
Lets users switch between UI views on larger devices:
- `ModalNavigationDrawer` - Modal navigation drawer
- `ModalDrawerSheet` - Modal drawer sheet
- `PermanentNavigationDrawer` - Permanent navigation drawer
- `PermanentDrawerSheet` - Permanent drawer sheet
- `DismissibleNavigationDrawer` - Dismissible navigation drawer
- `DismissibleDrawerSheet` - Dismissible drawer sheet
- `NavigationDrawerItem` - Navigation drawer item

**See also:**
- [ModalNavigationDrawer](#modalnavigationdrawer) - Modal drawer details
- [PermanentNavigationDrawer](#permanentnavigationdrawer) - Permanent drawer details
- [DismissibleNavigationDrawer Component](#dismissiblenavigationdrawer-component) - Dismissible drawer details
- [NavigationDrawerItem](#navigationdraweritem) - Drawer item API

#### Navigation Rail
Lets users switch between UI views on mid-sized devices:
- `NavigationRail` - Side navigation rail
- `NavigationRailItem` - Navigation rail item

**See also:**
- [NavigationRail](#navigationrail) - Detailed API reference
- [NavigationRailItem](#navigationrailitem) - Rail item API
- [ModalWideNavigationRail](#modalwidenavigationrail) - Wide rail variant

#### Tabs
Organize content across different screens and views:
- `Tab` - Standard tab
- `LeadingIconTab` - Tab with leading icon
- `PrimaryTabRow` - Primary tab row
- `SecondaryTabRow` - Secondary tab row
- `TabRow` - Fixed tab row
- `ScrollableTabRow` - Scrollable tab row
- `PrimaryIndicator` - Primary tab indicator
- `SecondaryIndicator` - Secondary tab indicator

**See also:**
- [PrimaryTabRow](#primarytabrow) - Primary tab details
- [SecondaryTabRow](#secondarytabrow) - Secondary tab details
- [PrimaryScrollableTabRow](#primaryscrollabletabrow) - Scrollable primary tabs
- [SecondaryScrollableTabRow](#secondaryscrollabletabrow) - Scrollable secondary tabs

#### App Bars
Placed at the top of the screen to help users navigate:
- `TopAppBar` - Small top app bar
- `CenterAlignedTopAppBar` - Center-aligned top app bar
- `MediumTopAppBar` - Medium top app bar
- `LargeTopAppBar` - Large top app bar
- `BottomAppBar` - Bottom app bar

**See also:**
- [CenterAlignedTopAppBar Component](#centeralignedtopappbar-component) - Center-aligned details
- [MediumTopAppBar](#mediumtopappbar) - Medium app bar details
- [LargeTopAppBar](#largetopappbar) - Large app bar details
- [BottomAppBar](#bottom-app-bar) - Bottom app bar details
- [FlexibleBottomAppBar Component](#flexiblebottomappbar-component-expressive) - Expressive bottom bar
- [AppBarRow](#appbarrow) - Horizontal app bar layout
- [AppBarColumn](#appbarcolumn) - Vertical app bar layout
- [Scaffold](#scaffold) - Integrate app bars with layout

#### Toolbars
Display frequently used actions relevant to the current page.

**See also:**
- [HorizontalFloatingToolbar](#horizontalfloatingtoolbar) - Floating toolbar implementation


### 4.5 Selection Components

#### Checkbox
Lets users select one or more items from a list, or turn an item on or off:
- `Checkbox` - Standard checkbox
- `TriStateCheckbox` - Indeterminate checkbox (checked/unchecked/indeterminate)

**See also:**
- [Checkbox Component](#checkbox-component) - Detailed API reference

#### Radio Button
Lets users select one option from a set of options:
- `RadioButton` - Standard radio button

**See also:**
- [RadioButton](#radiobutton) - Detailed API reference

#### Switch
Toggles the selection of an item on and off:
- `Switch` - Toggle switch

#### Chips
Help users enter information, make selections, filter content, or trigger actions:
- `AssistChip` - Assist chip
- `ElevatedAssistChip` - Elevated assist chip
- `FilterChip` - Filter chip
- `ElevatedFilterChip` - Elevated filter chip
- `InputChip` - Input chip
- `SuggestionChip` - Suggestion chip
- `ElevatedSuggestionChip` - Elevated suggestion chip

**See also:**
- [AssistChip](#assistchip) - Assist chip details
- [FilterChip Component](#filterchip-component) - Filter chip details
- [ElevatedAssistChip Component](#elevatedassistchip-component) - Elevated assist chip
- [ElevatedFilterChip Component](#elevatedfilterchip-component) - Elevated filter chip
- [ElevatedSuggestionChip Component](#elevatedsuggestionchip-component) - Elevated suggestion chip

#### Sliders
Allow users to make selections from a range of values:
- `Slider` - Single value slider
- `RangeSlider` - Range slider with two thumbs

**See also:**
- [Slider](#slider) - Detailed API reference
- [RangeSlider](#rangeslider) - Range slider details
- [Label Component](#label-component) - Add labels to sliders

### 4.6 Text Input Components

#### Text Fields
Let users enter text into a UI:
- `TextField` - Filled text field
- `OutlinedTextField` - Outlined text field

**See also:**
- [OutlinedTextField](#outlinedtextfield) - Detailed API reference
- [SecureTextField](#securetextfield) - Password input field
- [OutlinedSecureTextField](#outlinedsecuretextfield) - Outlined password field

#### Search
Lets users enter a keyword or phrase to get relevant information:
- `SearchBar` - Standard search bar
- `DockedSearchBar` - Docked search bar

**See also:**
- [SearchBar](#searchbar) - Detailed API reference
- [DockedSearchBar Component](#dockedsearchbar-component) - Docked search details
- [ExpandedDockedSearchBar Component](#expandeddockedsearchbar-component) - Expanded docked search
- [ExpandedFullScreenSearchBar Component](#expandedfullscreensearchbar-component) - Full screen search

#### Menus
Display a list of choices on a temporary surface:
- `DropdownMenu` - Dropdown menu
- `DropdownMenuItem` - Dropdown menu item
- `ExposedDropdownMenuBox` - Exposed dropdown menu

**See also:**
- [DropdownMenu Component](#dropdownmenu-component) - Detailed API reference
- [DropdownMenuItem Component](#dropdownmenuitem-component) - Menu item details
- [ExposedDropdownMenuBox Component](#exposeddropdownmenubox-component) - Exposed dropdown details
- [Menu APIs](#53-menu-apis) - Complete menu API reference

### 4.7 Other Components

#### Lists
Continuous, vertical indexes of text and images:
- `ListItem` - Standard list item

**See also:**
- [ListItem Component](#listitem-component) - Detailed API reference

#### Dividers
Thin lines that group content in lists or other containers:
- `HorizontalDivider` - Horizontal divider

#### Surfaces
Foundation for displaying content and actions:

**See also:**
- [Surfaces](#surfaces) - Surface component details

#### Swipe to Dismiss
Enable swipe gestures to dismiss items:

**See also:**
- [Swipe to Dismiss](#swipe-to-dismiss) - Implementation details
- `VerticalDivider` - Vertical divider

#### Surfaces
Foundation for displaying content:
- `Surface` - Material surface
- `Scaffold` - App layout structure

#### Swipe to Dismiss
Enables swipe-to-dismiss gestures:
- `SwipeToDismiss` - Swipe to dismiss container

---

## 5. API Reference

### 5.1 Core Theme APIs

#### MaterialTheme
The main M3 theme composable that provides access to color, typography, shapes, and motion schemes.

**See also:**
- [Theming System](#2-theming-system) - Overview of theming concepts
- [MaterialExpressiveTheme](#materialexpressivetheme) - Detailed implementation
- [Getting Started](#12-getting-started) - Setup instructions

```kotlin
@Composable
fun MaterialExpressiveTheme(
    colorScheme: ColorScheme = lightColorScheme(),
    typography: Typography = Typography(),
    shapes: Shapes = Shapes(),
    motionScheme: MotionScheme = MotionScheme.expressive(),
    content: @Composable () -> Unit
)
```

#### ColorScheme
Defines the color palette for your app. Contains colors for primary, secondary, tertiary, error, background, surface, and their variants.

**See also:**
- [Color Schemes](#21-color-schemes) - Color scheme overview
- [Dynamic Color APIs](#55-dynamic-color-apis) - Dynamic color implementation

**Creating Color Schemes:**

```kotlin
// Light color scheme
val lightColors = lightColorScheme(
    primary = Color(0xFF6200EE),
    onPrimary = Color.White,
    // ... other colors
)

// Dark color scheme
val darkColors = darkColorScheme(
    primary = Color(0xFFBB86FC),
    onPrimary = Color.Black,
    // ... other colors
)

// Dynamic color schemes (API 31+)
@RequiresApi(31)
val dynamicLight = dynamicLightColorScheme(context)

@RequiresApi(31)
val dynamicDark = dynamicDarkColorScheme(context)
```


### 5.2 Dialog APIs

**See also:**
- [Dialogs](#dialogs) - Dialog component overview
- [Basic AlertDialog Example](#71-basic-alertdialog-example) - Complete implementation example
- [Custom AlertDialog with Content](#72-custom-alertdialog-with-content) - Advanced usage

#### AlertDialog (Basic)

```kotlin
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
)
```

**Parameters:**
- `onDismissRequest` - Called when the user tries to dismiss the dialog by clicking outside or pressing back
- `confirmButton` - Button to confirm the proposed action
- `dismissButton` - Optional button to dismiss the dialog
- `icon` - Optional icon displayed above the title or text
- `title` - Optional title specifying the dialog's purpose
- `text` - Optional text presenting details about the dialog's purpose
- `shape` - Shape of the dialog container
- `containerColor` - Background color of the dialog
- `iconContentColor` - Color for the icon
- `titleContentColor` - Color for the title
- `textContentColor` - Color for the text
- `tonalElevation` - Elevation for tonal color overlay
- `properties` - Platform-specific dialog properties

**Example Usage:**

```kotlin
val openDialog = remember { mutableStateOf(false) }

Button(onClick = { openDialog.value = true }) {
    Text("Open Dialog")
}

if (openDialog.value) {
    AlertDialog(
        onDismissRequest = { openDialog.value = false },
        title = { Text("Dialog Title") },
        text = { Text("This is the dialog content") },
        confirmButton = {
            TextButton(onClick = { openDialog.value = false }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = { openDialog.value = false }) {
                Text("Dismiss")
            }
        }
    )
}
```

**Example with Icon:**

```kotlin
if (openDialog.value) {
    AlertDialog(
        onDismissRequest = { openDialog.value = false },
        icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
        title = { Text("Dialog Title") },
        text = {
            Text("This area typically contains supportive text which presents details regarding the dialog's purpose.")
        },
        confirmButton = {
            TextButton(onClick = { openDialog.value = false }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = { openDialog.value = false }) {
                Text("Dismiss")
            }
        }
    )
}
```

#### DatePickerDialog

```kotlin
@Composable
fun DatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    shape: Shape = DatePickerDefaults.shape,
    tonalElevation: Dp = DatePickerDefaults.TonalElevation,
    colors: DatePickerColors = DatePickerDefaults.colors(),
    properties: DialogProperties = DialogProperties(),
    content: @Composable ColumnScope.() -> Unit
)
```

Material Design date picker dialog that embeds a date picker in a dialog format.

**See also:**
- [DatePicker](#datepicker) - Date picker component details
- [DatePickerState](#datepickerstate) - State management
- [Date & Time Pickers](#date-time-pickers) - Picker overview

### 5.3 Menu APIs

**See also:**
- [Menus](#menus) - Menu component overview
- [DropdownMenu Component](#dropdownmenu-component) - Detailed implementation

#### DropdownMenu

```kotlin
@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(),
    shape: Shape = MenuDefaults.shape,
    containerColor: Color = MenuDefaults.containerColor,
    tonalElevation: Dp = MenuDefaults.TonalElevation,
    shadowElevation: Dp = MenuDefaults.ShadowElevation,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
)
```

Material Design dropdown menu that displays a list of choices on a temporary surface.

#### DropdownMenuItem

```kotlin
@Composable
fun DropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuItemDefaults.colors(),
    contentPadding: PaddingValues = MenuItemDefaults.contentPadding,
    interactionSource: MutableInteractionSource? = null
)
```

**Parameters:**
- `text` - Text content of the menu item
- `onClick` - Called when the menu item is clicked
- `leadingIcon` - Optional icon at the start of the item
- `trailingIcon` - Optional icon at the end of the item
- `enabled` - Whether the menu item is enabled
- `colors` - Colors for the menu item in different states
- `contentPadding` - Padding around the content
- `interactionSource` - Source of interactions for this menu item

### 5.4 Date Picker State APIs

**See also:**
- [DatePicker](#datepicker) - Date picker component
- [DateRangePicker Component](#daterangepicker-component) - Range picker details
- [Date & Time Pickers](#date-time-pickers) - Picker overview

#### DatePickerState (API 26+)

```kotlin
@RequiresApi(26)
@ExperimentalMaterial3Api
fun DatePickerState(
    locale: Locale,
    initialSelectedDate: LocalDate? = null,
    initialDisplayedMonth: YearMonth? = null,
    yearRange: IntRange = DatePickerDefaults.YearRange,
    initialDisplayMode: DisplayMode = DisplayMode.Picker,
    selectableDates: SelectableDates = DatePickerDefaults.AllDates
): DatePickerState
```

Creates a `DatePickerState` for managing date picker state.

**Remember State:**

```kotlin
@Composable
@RequiresApi(26)
fun rememberDatePickerState(
    initialSelectedDate: LocalDate? = null,
    initialDisplayedMonth: YearMonth? = null,
    yearRange: IntRange = DatePickerDefaults.YearRange,
    initialDisplayMode: DisplayMode = DisplayMode.Picker,
    selectableDates: SelectableDates = DatePickerDefaults.AllDates
): DatePickerState
```

**Extension Functions:**

```kotlin
// Get displayed month as YearMonth
@RequiresApi(26)
fun DatePickerState.getDisplayedMonth(): YearMonth

// Get selected date as LocalDate
@RequiresApi(26)
fun DatePickerState.getSelectedDate(): LocalDate?

// Set displayed month from YearMonth
@RequiresApi(26)
fun DatePickerState.setDisplayedMonth(yearMonth: YearMonth)

// Set selected date from LocalDate
@RequiresApi(26)
fun DatePickerState.setSelectedDate(date: LocalDate?)
```

#### DateRangePickerState (API 26+)

```kotlin
@RequiresApi(26)
@ExperimentalMaterial3Api
fun DateRangePickerState(
    locale: Locale,
    initialSelectedStartDate: LocalDate? = null,
    initialSelectedEndDate: LocalDate? = null,
    initialDisplayedMonth: YearMonth? = null,
    yearRange: IntRange = DatePickerDefaults.YearRange,
    initialDisplayMode: DisplayMode = DisplayMode.Picker,
    selectableDates: SelectableDates = DatePickerDefaults.AllDates
): DateRangePickerState
```

**Remember State:**

```kotlin
@Composable
@RequiresApi(26)
fun rememberDateRangePickerState(
    initialSelectedStartDate: LocalDate? = null,
    initialSelectedEndDate: LocalDate? = null,
    initialDisplayedMonth: YearMonth? = null,
    yearRange: IntRange = DatePickerDefaults.YearRange,
    initialDisplayMode: DisplayMode = DisplayMode.Picker,
    selectableDates: SelectableDates = DatePickerDefaults.AllDates
): DateRangePickerState
```

**Extension Functions:**

```kotlin
// Get displayed month
@RequiresApi(26)
fun DateRangePickerState.getDisplayedMonth(): YearMonth

// Get selected start date
@RequiresApi(26)
fun DateRangePickerState.getSelectedStartDate(): LocalDate?

// Get selected end date
@RequiresApi(26)
fun DateRangePickerState.getSelectedEndDate(): LocalDate?

// Set displayed month
@RequiresApi(26)
fun DateRangePickerState.setDisplayedMonth(yearMonth: YearMonth)

// Set date range selection
@RequiresApi(26)
fun DateRangePickerState.setSelection(
    startDate: LocalDate?,
    endDate: LocalDate?
)
```


### 5.5 Dynamic Color APIs

Dynamic color schemes automatically adapt to the user's wallpaper and system preferences (requires API 31+).

```kotlin
@RequiresApi(31)
fun dynamicLightColorScheme(context: Context): ColorScheme

@RequiresApi(31)
fun dynamicDarkColorScheme(context: Context): ColorScheme
```

**Example Usage:**

```kotlin
@Composable
fun MyAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        content = content
    )
}
```

---

## 6. Advanced Topics

### 6.1 Motion Scheme Presets

The Expressive and Standard motion schemes are presets with opinionated motion values. This design makes it easy to swap schemes without changing underlying property names.

**Expressive Motion Scheme:**
- Recommended for most applications
- Adds bounce and overshoot to create engaging interactions
- Best for hero moments and key user interactions
- Creates a more dynamic, playful feel

**Standard Motion Scheme:**
- More functional and subdued
- Minimal bounce while still using spring physics
- Appropriate for utilitarian products
- Creates a professional, efficient feel

**See also:**
- [Motion Schemes](#31-motion-schemes) - Motion scheme overview
- [Creating Custom Motion Schemes](#creating-custom-motion-schemes) - Customize motion behavior
- [Custom Motion Scheme Example](#73-custom-motion-scheme-example) - Implementation example

### 6.2 Adapting Motion to Device Context

The motion system automatically adapts to different device types:

**Device-Specific Adaptations:**
- **Wearables:** Faster, snappier animations appropriate for small screens
- **Phones:** Balanced animation speeds for typical mobile interactions
- **Tablets:** Slightly slower animations to match larger screen real estate

This adaptation happens automatically through the spring physics system, which uses damping and stiffness rather than fixed durations.

**See also:**
- [Spring Physics](#33-spring-physics) - Understanding spring-based animations
- [Animation Speeds](#animation-speeds) - Speed variants for different contexts

### 6.3 Accessibility Considerations

When implementing motion in your app, consider accessibility:

1. **Respect User Preferences:** Check for reduced motion preferences
2. **Provide Alternatives:** Offer options to disable or reduce animations
3. **Avoid Excessive Motion:** Don't overuse bounce or rapid animations
4. **Test with Accessibility Services:** Ensure animations don't interfere with screen readers

**See also:**
- [Testing Motion](#65-testing-motion) - Testing strategies for motion
- [Motion Scheme Presets](#61-motion-scheme-presets) - Choose appropriate motion schemes

```kotlin
@Composable
fun AccessibleMotionScheme(): MotionScheme {
    val context = LocalContext.current
    val prefersReducedMotion = remember {
        // Check system accessibility settings
        val resolver = context.contentResolver
        Settings.Global.getFloat(
            resolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }

    return if (prefersReducedMotion) {
        MotionScheme.standard() // Use less bouncy motion
    } else {
        MotionScheme.expressive()
    }
}
```

### 6.4 Performance Optimization

**Best Practices for Motion Performance:**

1. **Use Spring Animations:** They're more efficient than complex tween chains
2. **Avoid Overdraw:** Minimize overlapping animated elements
3. **Profile Animations:** Use Android Studio's profiler to identify bottlenecks
4. **Limit Simultaneous Animations:** Too many concurrent animations can impact performance
5. **Use Hardware Acceleration:** Ensure animations leverage GPU rendering

**See also:**
- [Spring Physics](#33-spring-physics) - Efficient spring-based animations
- [Using Motion Schemes in Custom Components](#using-motion-schemes-in-custom-components) - Best practices for custom animations

### 6.5 Testing Motion

**Testing Strategies:**

1. **Visual Testing:** Manually verify animations feel natural
2. **Snapshot Testing:** Capture animation states for regression testing
3. **Performance Testing:** Measure frame rates during animations
4. **Accessibility Testing:** Verify reduced motion preferences are respected
5. **Device Testing:** Test on various device sizes and performance tiers

**See also:**
- [Accessibility Considerations](#63-accessibility-considerations) - Accessibility best practices
- [Performance Optimization](#64-performance-optimization) - Optimize animation performance
- [Animated Custom Component](#74-animated-custom-component) - Example with motion implementation

---

## 7. Code Examples Repository

### 7.1 Basic AlertDialog Example

**See also:**
- [AlertDialog (Basic)](#alertdialog-basic) - API reference
- [Dialog APIs](#52-dialog-apis) - Complete dialog API
- [Dialogs](#dialogs) - Dialog component overview

```kotlin
import androidx.compose.material3.*
import androidx.compose.runtime.*

@Composable
fun BasicAlertDialogExample() {
    val openDialog = remember { mutableStateOf(false) }

    Button(onClick = { openDialog.value = true }) {
        Text("Open Dialog")
    }

    if (openDialog.value) {
        AlertDialog(
            onDismissRequest = { openDialog.value = false },
            title = { Text("Dialog Title") },
            text = { Text("Turned on by default") },
            confirmButton = {
                TextButton(onClick = { openDialog.value = false }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { openDialog.value = false }) {
                    Text("Dismiss")
                }
            }
        )
    }
}
```


### 7.2 Custom AlertDialog with Content

**See also:**
- [BasicAlertDialog](#basicalertdialog) - API reference
- [Dialog APIs](#52-dialog-apis) - Complete dialog API
- [MaterialTheme](#materialtheme) - Theme customization

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CustomAlertDialogExample() {
    val openDialog = remember { mutableStateOf(false) }

    Button(onClick = { openDialog.value = true }) {
        Text("Open Custom Dialog")
    }

    if (openDialog.value) {
        BasicAlertDialog(
            onDismissRequest = { openDialog.value = false }
        ) {
            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = AlertDialogDefaults.TonalElevation
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "This area typically contains the supportive text " +
                               "which presents the details regarding the Dialog's purpose."
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    TextButton(
                        onClick = { openDialog.value = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}
```

### 7.3 Custom Motion Scheme Example

**See also:**
- [Creating Custom Motion Schemes](#creating-custom-motion-schemes) - Detailed guide
- [Motion Scheme Presets](#61-motion-scheme-presets) - Preset options
- [Spring Physics](#33-spring-physics) - Understanding spring parameters
- [MaterialExpressiveTheme](#materialexpressivetheme) - Apply custom motion schemes

```kotlin
import androidx.compose.animation.core.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme

@ExperimentalMaterial3ExpressiveApi
fun customBouncyMotionScheme(): MotionScheme =
    object : MotionScheme {
        override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> {
            return spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 1600f
            )
        }

        override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> {
            return spring(
                dampingRatio = 0.5f, // More bounce
                stiffness = 700f
            )
        }

        override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> {
            return spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 3800f
            )
        }

        override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> {
            return spring(
                dampingRatio = 0.5f,
                stiffness = 1400f
            )
        }

        override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> {
            return spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 800f
            )
        }

        override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> {
            return spring(
                dampingRatio = 0.5f,
                stiffness = 300f
            )
        }
    }
```

### 7.4 Animated Custom Component

**See also:**
- [Using Motion Schemes in Custom Components](#using-motion-schemes-in-custom-components) - Implementation guide
- [Spatial Animation Specs](#spatial-animation-specs) - Spatial animation details
- [Effect Animation Specs](#effect-animation-specs) - Effect animation details
- [Performance Optimization](#64-performance-optimization) - Optimize animations

```kotlin
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextMotion

@Composable
fun AnimatedTextComponent() {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.5f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(),
        label = "scale"
    )

    val color by animateColorAsState(
        targetValue = if (isPressed) Color.Green else Color.Red,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>(),
        label = "color"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {}
    ) {
        Text(
            text = "Press Me!",
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin.Center
                }
                .align(Alignment.Center),
            style = LocalTextStyle.current.copy(
                color = color,
                textMotion = TextMotion.Animated
            )
        )
    }
}
```

---

## 8. Appendix

### 8.1 Package Information

**Package:** `androidx.compose.material3`

**Artifact:** `androidx.compose.material3:material3:1.5.0-alpha08`

**Platform Support:**
- Android (Cmn android)
- Common (Cmn)

### 8.2 Key Annotations

- `@ExperimentalMaterial3Api` - Marks experimental APIs that may change
- `@ExperimentalMaterial3ExpressiveApi` - Marks experimental Expressive APIs
- `@RequiresApi(value)` - Specifies minimum API level required

### 8.3 Related Resources

**Official Documentation:**
- [Material Design 3](https://m3.material.io/)
- [Compose Material 3 Documentation](https://developer.android.com/jetpack/compose/designsystems/material3)
- [Material Design Guidelines](https://material.io/design)

**Component Categories:**
- **Action:** Buttons, FABs, Icon Buttons, Segmented Buttons
- **Containment:** Cards, Sheets, Dialogs, Carousel
- **Communication:** Badges, Snackbars, Tooltips
- **Navigation:** Navigation Bar, Drawer, Rail, Tabs, App Bars
- **Selection:** Checkboxes, Radio Buttons, Switches, Chips, Sliders
- **Text Input:** Text Fields, Search, Menus

### 8.4 Glossary

**Terms:**
- **CalendarLocale** - Represents a Locale for the calendar (type alias for Locale on Android)
- **MotionScheme** - Defines animation specifications for spatial and effects animations
- **ColorScheme** - Defines the color palette for Material 3 theming
- **Typography** - Defines the typographic hierarchy
- **Shapes** - Defines the shape system for components
- **Spring Animation** - Physics-based animation using damping and stiffness
- **Spatial Animation** - Animation affecting position, size, rotation, or shape
- **Effects Animation** - Animation affecting visual properties like color and opacity

### 8.5 Version History

- **1.5.0-alpha10** - Latest alpha release. Added Multi-aspect Carousel, updated Checkbox flags.
- **1.5.0-alpha09** - Added Expressive menu updates (toggleable items, menu groups).
- **1.5.0-alpha08** - Initial public preview of some Expressive features.
- **1.4.0** - Stable release (Expressive features not included).
- Future: **1.5.0** - Planned stable release for Expressive APIs

---

**Document Generated:** November 10, 2025
**Source Data:** Material Design 3 Expressive Documentation (Lines 1-1000)
**Last Updated:** November 10, 2025


## App Bar Components

### AppBarColumn

The `AppBarColumn` component arranges its children vertically and displays an overflow indicator when content exceeds available space.

This composable lays out children from top to bottom. When the combined height of children exceeds available space, an overflow indicator appears at the bottom, replacing content that cannot fit. Items are constructed through a DSL in `AppBarColumnScope`, where each item can render itself in the column layout or inside a dropdown menu when overflow occurs.

**Function Signature:**

```kotlin
@Composable
fun AppBarColumn(
    modifier: Modifier = Modifier,
    overflowIndicator: @Composable (AppBarMenuState) -> Unit = { menuState ->
        AppBarOverflowIndicator(menuState)
    },
    maxItemCount: Int = Int.MAX_VALUE,
    content: AppBarColumnScope.() -> Unit
): Unit
```

**Parameters:**

- `modifier`: The modifier applied to the column
- `overflowIndicator`: A composable displayed at the end when content overflows, receives an `AppBarMenuState` instance
- `maxItemCount`: Maximum items to render before using the overflow menu. If items exceed this count, at most `max - 1` items render, with the last position dedicated to the overflow composable
- `content`: Content arranged in the column, defined using `AppBarColumnScope` DSL

### AppBarOverflowIndicator

Default overflow indicator for `AppBarRow` and `AppBarColumn`. Uses an `IconButton` that opens the overflow menu when clicked.

**Function Signature:**

```kotlin
@Composable
fun AppBarOverflowIndicator(
    menuState: AppBarMenuState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.standardShape,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null
): Unit
```

**Parameters:**

- `menuState`: The `AppBarMenuState` used to show or dismiss the overflow menu
- `modifier`: Modifier applied to the overflow indicator
- `enabled`: Controls the enabled state. When false, the component won't respond to user input and appears visually disabled
- `shape`: Defines the shape of the icon button's container
- `colors`: `IconButtonColors` used to resolve colors in different states
- `interactionSource`: Optional hoisted `MutableInteractionSource` for observing and emitting interactions

### AppBarRow

The `AppBarRow` component arranges its children horizontally and displays an overflow indicator when content exceeds available space.

This composable lays out children from left to right in LTR layouts and right to left in RTL layouts. When the combined width exceeds available space, an overflow indicator appears at the end, replacing content that cannot fit. Items are constructed through a DSL in `AppBarRowScope`, where each item can render itself in the row layout or inside a dropdown menu when overflow occurs.

**Function Signature:**

```kotlin
@Composable
fun AppBarRow(
    modifier: Modifier = Modifier,
    overflowIndicator: @Composable (AppBarMenuState) -> Unit = { menuState ->
        AppBarOverflowIndicator(menuState)
    },
    maxItemCount: Int = Int.MAX_VALUE,
    content: AppBarRowScope.() -> Unit
): Unit
```

**Parameters:**

- `modifier`: The modifier applied to the row
- `overflowIndicator`: A composable displayed at the end when content overflows, receives an `AppBarMenuState` instance
- `maxItemCount`: Maximum items to render before using the overflow menu. If items exceed this count, at most `max - 1` items render, with the last position dedicated to the overflow composable
- `content`: Content arranged in the row, defined using `AppBarRowScope` DSL

**Example - TopAppBar with Adaptive Actions:**

This example demonstrates a `TopAppBar` that adapts the number of visible actions based on screen size:

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
// Material guidelines: 3 items max in compact, 5 items max elsewhere
val maxItemCount =
    if (sizeClass.minWidthDp >= WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) {
        5
    } else {
        3
    }
val icons = listOf(
    Icons.Filled.Attachment,
    Icons.Filled.Edit,
    Icons.Outlined.Star,
    Icons.Filled.Snooze,
    Icons.Outlined.MarkEmailUnread,
)
val items = listOf("Attachment", "Edit", "Star", "Snooze", "Mark unread")

Scaffold(
    topBar = {
        TopAppBar(
            title = {
                Text("Simple TopAppBar", maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            navigationIcon = {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = { PlainTooltip { Text("Menu") } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = { /* doSomething() */ }) {
                        Icon(imageVector = Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
            },
            actions = {
                AppBarRow(
                    maxItemCount = maxItemCount,
                    overflowIndicator = {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Above
                            ),
                            tooltip = { PlainTooltip { Text("Overflow") } },
                            state = rememberTooltipState(),
                        ) {
                            IconButton(onClick = { it.show() }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "Overflow",
                                )
                            }
                        }
                    },
                ) {
                    items.forEachIndexed { index, item ->
                        clickableItem(
                            onClick = {},
                            icon = {
                                Icon(imageVector = icons[index], contentDescription = item)
                            },
                            label = item,
                        )
                    }
                }
            },
        )
    },
    content = { innerPadding ->
        LazyColumn(
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val list = (0..75).map { it.toString() }
            items(count = list.size) {
                Text(
                    text = list[it],
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
        }
    },
)
```

### AppBarWithSearch

A search bar with additional handling for top app bar behavior, including window insets and scrolling. Using `AppBarWithSearch` as the top bar of a `Scaffold` ensures the search bar remains at the top of the screen.

`AppBarWithSearch` should be used with `ExpandedFullScreenSearchBar` or `ExpandedDockedSearchBar` to display search results when expanded.

**Function Signature:**

```kotlin
@ExperimentalMaterial3Api
@Composable
fun AppBarWithSearch(
    state: SearchBarState,
    inputField: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    shape: Shape = SearchBarDefaults.inputFieldShape,
    colors: AppBarWithSearchColors = SearchBarDefaults.appBarWithSearchColors(),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    shadowElevation: Dp = SearchBarDefaults.ShadowElevation,
    contentPadding: PaddingValues = SearchBarDefaults.AppBarContentPadding,
    windowInsets: WindowInsets = SearchBarDefaults.windowInsets,
    scrollBehavior: SearchBarScrollBehavior? = null
): Unit
```

**Parameters:**

- `state`: The state of the search bar, also passed to the `inputField` and expanded search bar
- `inputField`: The input field for entering queries, typically `SearchBarDefaults.InputField`
- `modifier`: Modifier applied to the search bar when collapsed
- `navigationIcon`: Icon displayed at the start of the app bar before the search bar, typically an `IconButton` or `IconToggleButton`
- `actions`: Icons displayed at the end of the app bar after the search bar, typically `IconButtons` in a `Row` layout
- `shape`: The shape of the search bar when collapsed
- `colors`: `AppBarWithSearchColors` used to resolve colors for the search bar
- `tonalElevation`: When `containerColor` is `ColorScheme.surface`, a translucent primary color overlay is applied. Higher values result in darker colors in light theme and lighter colors in dark theme
- `shadowElevation`: The elevation for the shadow below the search bar
- `contentPadding`: Spacing values applied internally between container and content
- `windowInsets`: Window insets the search bar respects when collapsed
- `scrollBehavior`: `SearchBarScrollBehavior` that works with scrolled content to change appearance. If null, the search bar won't react to scrolling

**Example - Full Screen Search Bar:**

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

val textFieldState = rememberTextFieldState()
val searchBarState = rememberSearchBarState()
val scope = rememberCoroutineScope()
val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
val appBarWithSearchColors = SearchBarDefaults.appBarWithSearchColors()

val inputField = @Composable {
    SearchBarDefaults.InputField(
        modifier = Modifier,
        searchBarState = searchBarState,
        textFieldState = textFieldState,
        onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
        placeholder = {
            if (searchBarState.currentValue == SearchBarValue.Collapsed) {
                Text(
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                    text = "Search",
                    textAlign = TextAlign.Center,
                )
            }
        },
        leadingIcon = {
            if (searchBarState.currentValue == SearchBarValue.Expanded) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = { PlainTooltip { Text("Back") } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(
                        onClick = { scope.launch { searchBarState.animateToCollapsed() } }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                }
            } else {
                Icon(Icons.Default.Search, contentDescription = null)
            }
        },
        trailingIcon = {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above
                ),
                tooltip = { PlainTooltip { Text("Mic") } },
                state = rememberTooltipState(),
            ) {
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(imageVector = Icons.Default.Mic, contentDescription = "Mic")
                }
            }
        },
    )
}

Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
        AppBarWithSearch(
            scrollBehavior = scrollBehavior,
            state = searchBarState,
            inputField = inputField,
            navigationIcon = {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = { PlainTooltip { Text("Menu") } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = { /* doSomething() */ }) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            },
            actions = {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = { PlainTooltip { Text("Account") } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = { /* doSomething() */ }) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Account",
                        )
                    }
                }
            },
            colors = appBarWithSearchColors,
        )
        ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
            SampleSearchResults(
                onResultClick = { result ->
                    textFieldState.setTextAndPlaceCursorAtEnd(result)
                    scope.launch { searchBarState.animateToCollapsed() }
                }
            )
        }
    },
) { padding ->
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val list = List(100) { "Text $it" }
        items(count = list.size) {
            Text(
                text = list[it],
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
    }
}
```

**Example - Docked Search Bar:**

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

val textFieldState = rememberTextFieldState()
val searchBarState = rememberSearchBarState()
val scope = rememberCoroutineScope()
val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
val appBarWithSearchColors = SearchBarDefaults.appBarWithSearchColors()

var inputFieldWidth by remember { mutableIntStateOf(0) }
val inputField = @Composable {
    SearchBarDefaults.InputField(
        modifier = Modifier.onSizeChanged { inputFieldWidth = it.width },
        searchBarState = searchBarState,
        textFieldState = textFieldState,
        onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
        placeholder = {
            if (searchBarState.currentValue == SearchBarValue.Collapsed) {
                Text(
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                    text = "Search",
                    textAlign = TextAlign.Center,
                )
            }
        },
    )
}

Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
        AppBarWithSearch(
            scrollBehavior = scrollBehavior,
            state = searchBarState,
            inputField = inputField,
            navigationIcon = {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = { PlainTooltip { Text("Menu") } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = { /* doSomething() */ }) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            },
            actions = {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = { PlainTooltip { Text("Account") } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = { /* doSomething() */ }) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Account",
                        )
                    }
                }
            },
            colors = appBarWithSearchColors,
        )
        ExpandedDockedSearchBar(
            modifier = Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(maxWidth = inputFieldWidth))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            },
            state = searchBarState,
            inputField = inputField,
        ) {
            SampleSearchResults(
                onResultClick = { result ->
                    textFieldState.setTextAndPlaceCursorAtEnd(result)
                    scope.launch { searchBarState.animateToCollapsed() }
                }
            )
        }
    },
) { padding ->
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val list = List(100) { "Text $it" }
        items(count = list.size) {
            Text(
                text = list[it],
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
    }
}
```

## Chip Components

### AssistChip

Assist chips represent smart or automated actions that can span multiple apps, such as opening a calendar event from the home screen. They function as though the user asked an assistant to complete the action and should appear dynamically and contextually in a UI.

This assist chip uses a flat style. For an elevated style, use `ElevatedAssistChip`.

**Function Signature:**

```kotlin
@Composable
fun AssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = AssistChipDefaults.shape,
    colors: ChipColors = AssistChipDefaults.assistChipColors(),
    elevation: ChipElevation? = AssistChipDefaults.assistChipElevation(),
    border: BorderStroke? = AssistChipDefaults.assistChipBorder(enabled),
    interactionSource: MutableInteractionSource? = null
): Unit
```

**Parameters:**

- `onClick`: Called when the chip is clicked
- `label`: Text label for the chip
- `modifier`: Modifier applied to the chip
- `enabled`: Controls the enabled state. When false, the component won't respond to user input and appears visually disabled
- `leadingIcon`: Optional icon at the start of the chip, preceding the label text
- `trailingIcon`: Optional icon at the end of the chip
- `shape`: Defines the shape of the chip's container, border (when border is not null), and shadow (when using elevation)
- `colors`: `ChipColors` used to resolve colors in different states
- `elevation`: `ChipElevation` used to resolve elevation in different states. Controls the shadow size below the chip. When container color is `ColorScheme.surface`, also controls the amount of primary color overlay
- `border`: The border drawn around the container. Pass null for no border
- `interactionSource`: Optional hoisted `MutableInteractionSource` for observing and emitting interactions

**Example:**

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier

AssistChip(
    onClick = { /* Do something! */ },
    label = { Text("Assist Chip") },
    leadingIcon = {
        Icon(
            Icons.Filled.Settings,
            contentDescription = "Localized description",
            Modifier.size(AssistChipDefaults.IconSize),
        )
    },
)
```

## Badge Components

### Badge

A badge represents dynamic information such as a number of pending requests in a navigation bar. Badges can be icon only or contain short text.

Use `BadgedBox` for a top-level layout that properly places the badge relative to content such as text or an icon.

**Function Signature:**

```kotlin
@Composable
fun Badge(
    modifier: Modifier = Modifier,
    containerColor: Color = BadgeDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    content: (@Composable RowScope.() -> Unit)? = null
): Unit
```

**Parameters:**

- `modifier`: Modifier applied to the badge
- `containerColor`: The color used for the badge background
- `contentColor`: The preferred color for content inside the badge. Defaults to the matching content color for `containerColor`, or the current `LocalContentColor` if `containerColor` is not a theme color
- `content`: Optional content rendered inside the badge

### BadgedBox

A layout component that positions a badge relative to content, commonly used with navigation bar items.

**Function Signature:**

```kotlin
@Composable
fun BadgedBox(
    badge: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
): Unit
```

**Parameters:**

- `badge`: The badge to display, typically a `Badge` composable
- `modifier`: Modifier applied to the `BadgedBox`
- `content`: The anchor to which the badge is positioned

**Example - Navigation Bar with Badges:**

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

NavigationBar {
    NavigationBarItem(
        icon = {
            BadgedBox(
                badge = {
                    Badge(
                        modifier = Modifier.semantics {
                            contentDescription = "New notification"
                        }
                    )
                }
            ) {
                Icon(Icons.Filled.Star, contentDescription = "Favorite")
            }
        },
        selected = false,
        onClick = {},
    )
    NavigationBarItem(
        icon = {
            BadgedBox(
                badge = {
                    Badge {
                        val badgeNumber = "8"
                        Text(
                            badgeNumber,
                            modifier = Modifier.semantics {
                                contentDescription = "$badgeNumber new notifications"
                            },
                        )
                    }
                }
            ) {
                Icon(Icons.Filled.Star, contentDescription = "Favorite")
            }
        },
        selected = false,
        onClick = {},
    )
    NavigationBarItem(
        icon = {
            BadgedBox(
                badge = {
                    Badge {
                        val badgeNumber = "999+"
                        Text(
                            badgeNumber,
                            modifier = Modifier.semantics {
                                contentDescription = "$badgeNumber new notifications"
                            },
                        )
                    }
                }
            ) {
                Icon(Icons.Filled.Star, contentDescription = "Favorite")
            }
        },
        selected = false,
        onClick = {},
    )
}
```

## Dialog Components

### BasicAlertDialog

A basic alert dialog that expects arbitrary content defined by the caller. Your content needs to define its own styling.

By default, the dialog has the minimum height and width defined by the Material Design spec. These constraints can be overwritten by providing width or height modifiers.

**Function Signature:**

```kotlin
@ExperimentalMaterial3Api
@Composable
fun BasicAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
): Unit
```

**Parameters:**

- `onDismissRequest`: Called when the user tries to dismiss the dialog by clicking outside or pressing the back button. Not called when the dismiss button is clicked
- `modifier`: Modifier applied to the dialog's content
- `properties`: Platform-specific properties to further configure the dialog
- `content`: The content of the dialog

**Example:**

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val openDialog = remember { mutableStateOf(true) }

Button(onClick = { openDialog.value = true }) {
    Text("Open dialog")
}

if (openDialog.value) {
    BasicAlertDialog(
        onDismissRequest = {
            // Dismiss when user clicks outside or presses back button
            openDialog.value = false
        }
    ) {
        Surface(
            modifier = Modifier.wrapContentWidth().wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "This area typically contains the supportive text " +
                        "which presents the details regarding the Dialog's purpose."
                )
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(
                    onClick = { openDialog.value = false },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Confirm")
                }
            }
        }
    }
}
```

## Bottom App Bar

### BottomAppBar (Basic)

A bottom app bar displays navigation and key actions at the bottom of small screens. For displaying a `FloatingActionButton`, use the overload that accepts a `floatingActionButton` parameter.

**Function Signature:**

```kotlin
@Composable
fun BottomAppBar(
    modifier: Modifier = Modifier,
    containerColor: Color = BottomAppBarDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = BottomAppBarDefaults.ContainerElevation,
    contentPadding: PaddingValues = BottomAppBarDefaults.ContentPadding,
    windowInsets: WindowInsets = BottomAppBarDefaults.windowInsets,
    content: @Composable RowScope.() -> Unit
): Unit
```

**Parameters:**

- `modifier`: Modifier applied to the `BottomAppBar`
- `containerColor`: The color used for the background. Use `Color.Transparent` for no color
- `contentColor`: The preferred color for content inside the `BottomAppBar`. Defaults to the matching content color for `containerColor`, or the current `LocalContentColor`
- `tonalElevation`: When `containerColor` is `ColorScheme.surface`, a translucent primary color overlay is applied. Higher values result in darker colors in light theme and lighter colors in dark theme
- `contentPadding`: Padding applied to the content
- `windowInsets`: Window insets the app bar respects
- `content`: The content of the `BottomAppBar`. Default layout is a `Row`, so content is placed horizontally

### BottomAppBar (with FAB)

A bottom app bar that includes support for a `FloatingActionButton`.

**Function Signature:**

```kotlin
@Composable
fun BottomAppBar(
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    floatingActionButton: (@Composable () -> Unit)? = null,
    containerColor: Color = BottomAppBarDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = BottomAppBarDefaults.ContainerElevation,
    contentPadding: PaddingValues = BottomAppBarDefaults.ContentPadding,
    windowInsets: WindowInsets = BottomAppBarDefaults.windowInsets
): Unit
```

**Parameters:**

- `actions`: The action buttons displayed in the app bar
- `modifier`: Modifier applied to the `BottomAppBar`
- `floatingActionButton`: Optional floating action button displayed in the app bar
- `containerColor`: The color used for the background
- `contentColor`: The preferred color for content inside the `BottomAppBar`
- `tonalElevation`: Tonal elevation for the surface overlay effect
- `contentPadding`: Padding applied to the content
- `windowInsets`: Window insets the app bar respects


### BottomAppBar (with Scroll Behavior)

A bottom app bar that supports scroll behavior to hide/show based on content scrolling.

**Function Signature:**

```kotlin
@ExperimentalMaterial3Api
@Composable
fun BottomAppBar(
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    floatingActionButton: (@Composable () -> Unit)? = null,
    containerColor: Color = BottomAppBarDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = BottomAppBarDefaults.ContainerElevation,
    contentPadding: PaddingValues = BottomAppBarDefaults.ContentPadding,
    windowInsets: WindowInsets = BottomAppBarDefaults.windowInsets,
    scrollBehavior: BottomAppBarScrollBehavior? = null
): Unit
```

**When to Use:**

Use this variant when you want the bottom app bar to respond to scrolling content by hiding or showing itself. This creates a more immersive experience when users scroll through long lists or content.

**Parameters:**

- `actions`: The action buttons displayed in the app bar
- `modifier`: Modifier applied to the `BottomAppBar`
- `floatingActionButton`: Optional floating action button displayed in the app bar
- `containerColor`: The color used for the background
- `contentColor`: The preferred color for content inside the `BottomAppBar`
- `tonalElevation`: Tonal elevation for the surface overlay effect
- `contentPadding`: Padding applied to the content
- `windowInsets`: Window insets the app bar respects
- `scrollBehavior`: Controls how the bottom app bar responds to scrolling. The app bar will not react to scrolling when a touch exploration service (e.g., TalkBack) is active

**Example - Bottom App Bar with Scroll Behavior:**

This example demonstrates a bottom app bar that hides when scrolling down and reappears when scrolling up.

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp

val scrollBehavior = BottomAppBarDefaults.exitAlwaysScrollBehavior()
Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    bottomBar = {
        BottomAppBar(
            actions = {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = { PlainTooltip { Text("Check") } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = { /* doSomething() */ }) {
                        Icon(Icons.Filled.Check, contentDescription = "Check")
                    }
                }
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = { PlainTooltip { Text("Edit") } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = { /* doSomething() */ }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                }
            },
            scrollBehavior = scrollBehavior,
        )
    },
    floatingActionButton = {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                TooltipAnchorPosition.Above
            ),
            tooltip = { PlainTooltip { Text("Add") } },
            state = rememberTooltipState(),
        ) {
            FloatingActionButton(
                modifier = Modifier.offset(y = 4.dp),
                onClick = { /* do something */ },
                containerColor = BottomAppBarDefaults.bottomAppBarFabColor,
                elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
            ) {
                Icon(Icons.Filled.Add, "Add")
            }
        }
    },
    floatingActionButtonPosition = FabPosition.EndOverlay,
    content = { innerPadding ->
        LazyColumn(
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val list = (0..75).map { it.toString() }
            items(count = list.size) {
                Text(
                    text = list[it],
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
        }
    },
)
```

**Key Points:**

- Connect the scroll behavior to your scrollable content using `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)`
- The bottom app bar automatically hides when scrolling down and shows when scrolling up
- Use `BottomAppBarDefaults.exitAlwaysScrollBehavior()` for the exit-always behavior
- The FAB can be positioned using `FabPosition.EndOverlay` to overlay the bottom app bar

---

## BottomAppBarState

Creates and manages the state for a `BottomAppBar` with scroll behavior.

**Function Signature:**

```kotlin
@ExperimentalMaterial3Api
fun BottomAppBarState(
    initialHeightOffsetLimit: Float,
    initialHeightOffset: Float,
    initialContentOffset: Float
): BottomAppBarState
```

**Purpose:**

This function creates a state object that tracks the height offset and content offset of a bottom app bar during scrolling interactions.

**Parameters:**

- `initialHeightOffsetLimit`: The initial pixel limit that the bottom app bar can collapse when scrollable content is scrolled
- `initialHeightOffset`: The initial height offset. This value should be between zero and `initialHeightOffsetLimit`
- `initialContentOffset`: The initial content offset value

**When to Use:**

Use this when you need fine-grained control over the bottom app bar's scroll behavior state, such as restoring state after configuration changes or implementing custom scroll behaviors.

---

## BottomSheetScaffold

A scaffold that integrates a standard bottom sheet with your screen's main content.

**Function Signature:**

```kotlin
@Composable
@ExperimentalMaterial3Api
fun BottomSheetScaffold(
    sheetContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    scaffoldState: BottomSheetScaffoldState = rememberBottomSheetScaffoldState(),
    sheetPeekHeight: Dp = BottomSheetDefaults.SheetPeekHeight,
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    sheetShape: Shape = BottomSheetDefaults.ExpandedShape,
    sheetContainerColor: Color = BottomSheetDefaults.ContainerColor,
    sheetContentColor: Color = contentColorFor(sheetContainerColor),
    sheetTonalElevation: Dp = 0.dp,
    sheetShadowElevation: Dp = BottomSheetDefaults.Elevation,
    sheetDragHandle: (@Composable () -> Unit)? = { BottomSheetDefaults.DragHandle() },
    sheetSwipeEnabled: Boolean = true,
    topBar: (@Composable () -> Unit)? = null,
    snackbarHost: @Composable (SnackbarHostState) -> Unit = { SnackbarHost(it) },
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(containerColor),
    content: @Composable (PaddingValues) -> Unit
): Unit
```

**Purpose:**

Standard bottom sheets co-exist with the screen's main UI region, allowing users to simultaneously view and interact with both areas. They're commonly used to keep a feature or secondary content visible while the main UI region is scrolled or panned.

**When to Use:**

- Displaying supplementary content that complements the main screen
- Showing filters or settings that users may want to reference while viewing main content
- Providing quick access to secondary actions without leaving the current screen
- Displaying content that users may want to expand for more details

**When Not to Use:**

- For critical actions that require full user attention (use a dialog instead)
- For content that should completely replace the main screen (use navigation instead)
- For temporary messages (use a snackbar instead)

**Example - Basic Bottom Sheet Scaffold:**

This example shows a simple bottom sheet that can be expanded by swiping up.

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp

val scope = rememberCoroutineScope()
val scaffoldState = rememberBottomSheetScaffoldState()

BottomSheetScaffold(
    scaffoldState = scaffoldState,
    sheetPeekHeight = 128.dp,
    sheetContent = {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.fillMaxWidth().height(128.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Swipe up to expand sheet")
            }
            Text("Sheet content")
            Button(
                modifier = Modifier.padding(bottom = 64.dp).focusProperties {
                    // Make sure the button is not keyboard focusable when offscreen
                    canFocus = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded
                },
                onClick = { scope.launch { scaffoldState.bottomSheetState.partialExpand() } },
            ) {
                Text("Click to collapse sheet")
            }
        }
    },
) { innerPadding ->
    Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text("Scaffold Content")
    }
}
```

**Parameters:**

- `sheetContent`: The content displayed in the bottom sheet
- `modifier`: Modifier applied to the scaffold root
- `scaffoldState`: State of the bottom sheet scaffold
- `sheetPeekHeight`: The height of the bottom sheet when collapsed
- `sheetMaxWidth`: Maximum width the sheet will take. Pass `Dp.Unspecified` for full screen width
- `sheetShape`: The shape of the bottom sheet
- `sheetContainerColor`: Background color of the bottom sheet
- `sheetContentColor`: Preferred content color for the bottom sheet's children
- `sheetTonalElevation`: Tonal elevation for the surface overlay effect
- `sheetShadowElevation`: Shadow elevation of the bottom sheet
- `sheetDragHandle`: Optional visual marker to indicate the sheet can be dragged
- `sheetSwipeEnabled`: Whether sheet swiping is enabled
- `topBar`: Top app bar of the screen
- `snackbarHost`: Component to host snackbars
- `containerColor`: Background color of the scaffold
- `contentColor`: Preferred content color for the scaffold
- `content`: Main screen content. Apply the provided `PaddingValues` to properly offset top and bottom bars

**Best Practices:**

- Set `sheetPeekHeight` to show enough content to indicate what's in the sheet
- Use `focusProperties` to manage keyboard focus for elements that may be offscreen
- Control sheet state programmatically using `scaffoldState.bottomSheetState`
- Consider accessibility when implementing swipe gestures

---

## Button

A filled button component for high-emphasis actions.

**See also:**
- [Buttons](#buttons) - Button overview and variants
- [ElevatedButton Component](#elevatedbutton-component) - Elevated button variant
- [OutlinedButton](#outlinedbutton) - Outlined button variant
- [Button with Shape Morphing](#button-expressive-with-shape-morphing) - Expressive animations

**Function Signature:**

```kotlin
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
): Unit
```

**Purpose:**

Buttons help users initiate actions, from sending an email to sharing a document to liking a post. Filled buttons are high-emphasis buttons with the most visual impact after the `FloatingActionButton`.

**When to Use:**

- Important, final actions that complete a flow (e.g., "Save", "Join now", "Confirm")
- Primary actions on a screen
- Actions that require high emphasis and user attention

**When Not to Use:**

- For less important actions (use `OutlinedButton`, `FilledTonalButton`, or `TextButton`)
- When you need multiple buttons of equal importance (consider using different button types to establish hierarchy)

**Example - Basic Button:**

```kotlin
import androidx.compose.material3.Button
import androidx.compose.material3.Text

Button(onClick = {}) {
    Text("Button")
}
```

**Example - Button with Icon:**

```kotlin
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier

Button(
    onClick = { /* Do something! */ },
    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
) {
    Icon(
        Icons.Filled.Favorite,
        contentDescription = "Localized description",
        modifier = Modifier.size(ButtonDefaults.IconSize),
    )
    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
    Text("Like")
}
```

**Example - Button with Square Shape:**

```kotlin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text

Button(
    onClick = { /* Do something! */ },
    shape = ButtonDefaults.squareShape
) {
    Text("Button")
}
```

**Example - Button with Small Content Padding:**

```kotlin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text

Button(
    onClick = { /* Do something! */ },
    contentPadding = ButtonDefaults.SmallContentPadding
) {
    Text("Button")
}
```

**Button Size Variants:**

Buttons support multiple size variants: extra small, small (default), medium, large, and extra large.

**Example - Extra Small Button:**

```kotlin
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier

val size = ButtonDefaults.ExtraSmallContainerHeight
Button(
    onClick = { /* Do something! */ },
    modifier = Modifier.heightIn(size),
    contentPadding = ButtonDefaults.contentPaddingFor(size),
) {
    Icon(
        Icons.Filled.Edit,
        contentDescription = "Localized description",
        modifier = Modifier.size(ButtonDefaults.iconSizeFor(size)),
    )
    Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(size)))
    Text("Label")
}
```

**Example - Medium Button:**

```kotlin
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier

val size = ButtonDefaults.MediumContainerHeight
Button(
    onClick = { /* Do something! */ },
    modifier = Modifier.heightIn(size),
    contentPadding = ButtonDefaults.contentPaddingFor(size),
) {
    Icon(
        Icons.Filled.Edit,
        contentDescription = "Localized description",
        modifier = Modifier.size(ButtonDefaults.iconSizeFor(size)),
    )
    Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(size)))
    Text("Label")
}
```

**Example - Large Button:**

```kotlin
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier

val size = ButtonDefaults.LargeContainerHeight
Button(
    onClick = { /* Do something! */ },
    modifier = Modifier.heightIn(size),
    contentPadding = ButtonDefaults.contentPaddingFor(size),
) {
    Icon(
        Icons.Filled.Edit,
        contentDescription = "Localized description",
        modifier = Modifier.size(ButtonDefaults.iconSizeFor(size)),
    )
    Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(size)))
    Text("Label", style = ButtonDefaults.textStyleFor(size))
}
```

**Example - Extra Large Button:**

```kotlin
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier

val size = ButtonDefaults.ExtraLargeContainerHeight
Button(
    onClick = { /* Do something! */ },
    modifier = Modifier.heightIn(size),
    contentPadding = ButtonDefaults.contentPaddingFor(size),
) {
    Icon(
        Icons.Filled.Edit,
        contentDescription = "Localized description",
        modifier = Modifier.size(ButtonDefaults.iconSizeFor(size)),
    )
    Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(size)))
    Text("Label", style = ButtonDefaults.textStyleFor(size))
}
```

**Choosing the Right Button Type:**

Choose the button type based on the emphasis level needed:

- **Button (Filled)**: Highest emphasis for primary actions
- **FilledTonalButton**: Medium-high emphasis, middle ground between outlined and filled
- **OutlinedButton**: Medium emphasis with a border
- **ElevatedButton**: Filled tonal button with a shadow
- **TextButton**: Lowest emphasis with no border

**Parameters:**

- `onClick`: Callback invoked when the button is clicked
- `modifier`: Modifier applied to the button
- `enabled`: Controls the enabled state. When `false`, the button won't respond to user input and appears visually disabled
- `shape`: Defines the shape of the button's container, border, and shadow
- `colors`: Colors used to resolve the button's appearance in different states
- `elevation`: Elevation used to resolve the shadow size in different states
- `border`: Border to draw around the button container
- `contentPadding`: Spacing between the container and content
- `interactionSource`: Optional hoisted `MutableInteractionSource` for observing and emitting interactions
- `content`: The content displayed on the button (typically text, icon, or image)

**Typography:**

The default text style for internal `Text` components is `Typography.labelLarge`.

---

## Button (Expressive with Shape Morphing)

An expressive button variant that morphs between shapes based on user interaction.

**Function Signature:**

```kotlin
@Composable
@ExperimentalMaterial3ExpressiveApi
fun Button(
    onClick: () -> Unit,
    shapes: ButtonShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
): Unit
```

**Purpose:**

This expressive button variant morphs between different shapes based on user interaction, creating a more dynamic and engaging experience. The morphing effect works when the shapes provided are `CornerBasedShapes`.

**When to Use:**

- When you want to add expressive motion to your UI
- For applications that benefit from enhanced visual feedback
- When implementing Material Design 3 Expressive guidelines

**Example - Expressive Button:**

```kotlin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text

Button(
    onClick = {},
    shapes = ButtonDefaults.shapes()
) {
    Text("Button")
}
```

**Parameters:**

- `onClick`: Callback invoked when the button is clicked
- `shapes`: The `ButtonShapes` that the button morphs between based on user interaction
- `modifier`: Modifier applied to the button
- `enabled`: Controls the enabled state
- `colors`: Colors used to resolve the button's appearance in different states
- `elevation`: Elevation used to resolve the shadow size in different states
- `border`: Border to draw around the button container
- `contentPadding`: Spacing between the container and content
- `interactionSource`: Optional hoisted `MutableInteractionSource` for observing interactions
- `content`: The content displayed on the button

**Key Differences from Standard Button:**

- Requires a `ButtonShapes` parameter instead of a single `Shape`
- Morphs between shapes during user interaction (press, hover, focus)
- Only works with `CornerBasedShapes` for smooth morphing
- Part of the Material 3 Expressive API

---

## ButtonGroup

A layout composable that arranges buttons horizontally with support for dynamic width animations and overflow handling.

**Function Signature:**

```kotlin
@Composable
@ExperimentalMaterial3ExpressiveApi
fun ButtonGroup(
    modifier: Modifier = Modifier,
    expandedRatio: Float = ButtonGroupDefaults.ExpandedRatio,
    horizontalArrangement: Arrangement.Horizontal = ButtonGroupDefaults.HorizontalArrangement,
    content: @Composable ButtonGroupScope.() -> Unit
): Unit
```

**Purpose:**

`ButtonGroup` arranges child buttons in a horizontal sequence. When a child uses `Modifier.animateWidth` with a relevant `MutableInteractionSource`, the button group listens to interactions and expands the pressed button while compressing neighboring buttons.

**When to Use:**

- Creating a row of related action buttons
- Implementing connected button groups with asymmetric shapes
- Building segmented controls or toggle button groups
- When you need buttons that respond dynamically to user interaction

**Example - Button Group with Overflow:**

This example shows a button group that handles overflow with an indicator when there are too many buttons to fit on screen.

```kotlin
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults

val numButtons = 10
ButtonGroup(
    overflowIndicator = { menuState ->
        ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
    }
) {
    for (i in 0 until numButtons) {
        clickableItem(onClick = {}, label = "$i")
    }
}
```

**Example - Connected Button Group:**

A connected button group features leading and trailing buttons with asymmetric shapes, commonly used for making selections.

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

val options = listOf("Work", "Restaurant", "Coffee")
val unCheckedIcons = listOf(
    Icons.Outlined.Work,
    Icons.Outlined.Restaurant,
    Icons.Outlined.Coffee
)
val checkedIcons = listOf(
    Icons.Filled.Work,
    Icons.Filled.Restaurant,
    Icons.Filled.Coffee
)
var selectedIndex by remember { mutableIntStateOf(0) }

Row(
    Modifier.padding(horizontal = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(
        ButtonGroupDefaults.ConnectedSpaceBetween
    ),
) {
    val modifiers = listOf(
        Modifier.weight(1f),
        Modifier.weight(1.5f),
        Modifier.weight(1f)
    )

    options.forEachIndexed { index, label ->
        ToggleButton(
            checked = selectedIndex == index,
            onCheckedChange = { selectedIndex = index },
            modifier = modifiers[index].semantics { role = Role.RadioButton },
            shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            },
        ) {
            Icon(
                if (selectedIndex == index) checkedIcons[index] else unCheckedIcons[index],
                contentDescription = "Localized description",
            )
            Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
            Text(label)
        }
    }
}
```

**Parameters:**

- `modifier`: Modifier applied to the button group
- `expandedRatio`: The ratio by which a pressed button expands relative to its normal size
- `horizontalArrangement`: The horizontal arrangement strategy for the buttons
- `content`: The buttons to display in the group

**Best Practices:**

- Use connected button shapes for segmented controls to create visual continuity
- Apply appropriate semantic roles (e.g., `Role.RadioButton`) for accessibility
- Use `ButtonGroupDefaults.OverflowIndicator` to handle cases where buttons don't fit on screen
- Consider using different button weights to emphasize certain options

**Deprecation Note:**

The overload without the `overflowIndicator` parameter is deprecated. Use the overload with `overflowIndicator` to prevent content from being cut off when there are too many items.


### ButtonGroup with Overflow Handling

The ButtonGroup component supports overflow management when there are too many items to fit on screen. Items that don't fit will automatically overflow into a dropdown menu.

#### Overflow Indicator Implementation

```kotlin
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults

val numButtons = 10
ButtonGroup(
    overflowIndicator = { menuState ->
        ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
    }
) {
    for (i in 0 until numButtons) {
        clickableItem(onClick = {}, label = "$i")
    }
}
```

This example demonstrates a ButtonGroup with 10 buttons. When the buttons exceed the available screen width, the overflow indicator appears and provides access to the hidden items through a dropdown menu.

#### Vertical Connected Button Group

You can create vertical button groups by arranging ToggleButtons in a Column with custom shapes for the top and bottom buttons:

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp

val options = listOf("Button 1", "Button 2", "Button 3", "Button 4", "Button 5")
var selectedIndex by remember { mutableIntStateOf(0) }

Column(verticalArrangement = Arrangement.spacedBy((-6).dp)) {
    options.forEachIndexed { index, label ->
        val shape =
            when (index) {
                0 ->
                    (ButtonGroupDefaults.connectedMiddleButtonShapes().shape
                            as RoundedCornerShape)
                        .copy(topStart = CornerSize(100), topEnd = CornerSize(100))
                options.lastIndex ->
                    (ButtonGroupDefaults.connectedMiddleButtonShapes().shape
                            as RoundedCornerShape)
                        .copy(bottomStart = CornerSize(100), bottomEnd = CornerSize(100))
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes().shape
            }
        ToggleButton(
            checked = selectedIndex == index,
            onCheckedChange = { selectedIndex = index },
            shapes =
                ToggleButtonDefaults.shapes(
                    shape = shape,
                    checkedShape = ButtonGroupDefaults.connectedButtonCheckedShape,
                ),
        ) {
            Text(label)
        }
    }
}
```

This creates a vertical stack of connected buttons with rounded corners on the top and bottom buttons. The negative spacing creates a seamless connected appearance.

#### ButtonGroup Parameters

- `overflowIndicator`: Composable displayed at the end of the button group when overflow occurs. Receives a ButtonGroupMenuState for managing the overflow menu.
- `modifier`: Modifier applied to the button group container.
- `expandedRatio`: Float value (0.0 to 1.0+) controlling how much the interacted button expands. Default expands by ButtonGroupDefaults.ExpandedRatio. A value of 0f means no expansion, while 1f expands the button to 200% of its default width when pressed.
- `horizontalArrangement`: Controls the horizontal spacing and arrangement of buttons within the group.
- `content`: The button group's content, expected to use composables tagged with Modifier.animateWidth for interactive width animations.

---

## Card Component

### Overview

Cards are versatile containers that group related content and actions. Material Design 3 provides filled cards that offer subtle separation from the background with less visual emphasis than elevated or outlined cards.

**See also:**
- [Cards](#cards) - Card overview and variants
- [ElevatedCard Component](#elevatedcard-component) - Elevated card variant
- [OutlinedCard](#outlinedcard) - Outlined card variant

### Basic Card (Non-Interactive)

Use this Card variant when you need a simple container without click handling:

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

Card(Modifier.size(width = 180.dp, height = 100.dp)) {
    Box(Modifier.fillMaxSize()) {
        Text("Card content", Modifier.align(Alignment.Center))
    }
}
```

This creates a 180dp × 100dp card with centered text content.

#### Basic Card Parameters

- `modifier`: Modifier applied to the card container.
- `shape`: Defines the card's container shape, border (when border is not null), and shadow (when using elevation).
- `colors`: CardColors that resolve the card's colors in different states. See CardDefaults.cardColors.
- `elevation`: CardElevation controlling the shadow size below the card. When the container color is ColorScheme.surface, this also controls the primary color overlay amount.
- `border`: Optional BorderStroke to draw around the card container.
- `content`: The content displayed within the card, provided as a ColumnScope composable.

### Clickable Card

When your card needs to respond to user interaction, use the clickable Card variant:

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

Card(
    onClick = { /* Do something */ },
    modifier = Modifier.size(width = 180.dp, height = 100.dp),
) {
    Box(Modifier.fillMaxSize()) {
        Text("Clickable", Modifier.align(Alignment.Center))
    }
}
```

This card responds to click events and provides appropriate visual feedback during interaction.

#### Clickable Card Parameters

- `onClick`: Lambda invoked when the card is clicked.
- `modifier`: Modifier applied to the card container.
- `enabled`: Controls whether the card responds to user input. When false, the card appears visually disabled and is disabled for accessibility services.
- `shape`: Defines the card's container shape, border, and shadow.
- `colors`: CardColors resolving the card's colors in different states.
- `elevation`: CardElevation controlling shadow size and primary color overlay.
- `border`: Optional BorderStroke around the card container.
- `interactionSource`: Optional hoisted MutableInteractionSource for observing and emitting card interactions. Use this to customize the card's appearance in different states. If null, interactions still occur internally.
- `content`: The content displayed within the card.

---

## CenterAlignedTopAppBar Component

### Overview

Top app bars display information and actions at the top of the screen. The CenterAlignedTopAppBar variant horizontally centers the title text, creating a balanced, symmetrical layout.

### Basic Implementation

The CenterAlignedTopAppBar provides slots for a title, navigation icon, and action buttons:

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

Scaffold(
    topBar = {
        CenterAlignedTopAppBar(
            title = {
                Text("Centered TopAppBar", maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            navigationIcon = {
                TooltipBox(
                    positionProvider =
                        TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above
                        ),
                    tooltip = { PlainTooltip { Text("Menu") } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = { /* doSomething() */ }) {
                        Icon(imageVector = Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
            },
            actions = {
                TooltipBox(
                    positionProvider =
                        TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above
                        ),
                    tooltip = { PlainTooltip { Text("Add to favorites") } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = { /* doSomething() */ }) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Add to favorites",
                        )
                    }
                }
            },
        )
    },
    content = { innerPadding ->
        LazyColumn(
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val list = (0..75).map { it.toString() }
            items(count = list.size) {
                Text(
                    text = list[it],
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
        }
    },
)
```

This example demonstrates a complete Scaffold implementation with a centered top app bar. The title text uses ellipsis overflow handling to prevent text from wrapping. The navigation icon and action buttons include tooltips for improved accessibility.

### CenterAlignedTopAppBar Parameters

- `title`: Composable displaying the app bar title, typically centered text.
- `modifier`: Modifier applied to the top app bar container.
- `navigationIcon`: Navigation icon displayed at the start of the app bar. Typically an IconButton or IconToggleButton.
- `actions`: Action buttons displayed at the end of the app bar. Typically IconButtons arranged in a Row layout.
- `expandedHeight`: The app bar's maximum height. When a scrollBehavior causes collapse or expansion, this value represents the maximum allowed height. Must be specified and finite, otherwise defaults to TopAppBarDefaults.TopAppBarExpandedHeight.
- `windowInsets`: Window insets that the app bar respects for proper layout.
- `colors`: TopAppBarColors resolving the app bar's colors in different states. See TopAppBarDefaults.topAppBarColors.
- `scrollBehavior`: TopAppBarScrollBehavior holding offset values that control the app bar's height and colors based on scroll position. Designed to work with scrolled content to change appearance as the user scrolls. See TopAppBarScrollBehavior.nestedScrollConnection.
- `contentPadding`: Padding applied to the app bar's content.

---

## Checkbox Component

### Overview

Checkboxes allow users to select one or more items from a set. They provide a clear visual indication of selection state and can toggle options on or off.

### Basic Checkbox

The simplest checkbox implementation manages its own checked state:

```kotlin
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

val checkedState = remember { mutableStateOf(true) }
Checkbox(
    checked = checkedState.value,
    onCheckedChange = { checkedState.value = it }
)
```

### Checkbox with Label

Combine a Checkbox with Text to create a labeled selection option:

```kotlin
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

val (checkedState, onStateChange) = remember { mutableStateOf(true) }
Row(
    Modifier
        .fillMaxWidth()
        .height(56.dp)
        .toggleable(
            value = checkedState,
            onValueChange = { onStateChange(!checkedState) },
            role = Role.Checkbox,
        )
        .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Checkbox(
        checked = checkedState,
        onCheckedChange = null, // null recommended for accessibility with screenreaders
    )
    Text(
        text = "Option selection",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(start = 16.dp),
    )
}
```

This implementation makes the entire Row clickable using the toggleable modifier. Setting onCheckedChange to null on the Checkbox itself is recommended for better screen reader accessibility, as the toggleable modifier handles the interaction.

#### Basic Checkbox Parameters

- `checked`: Boolean indicating whether the checkbox is checked or unchecked.
- `onCheckedChange`: Lambda called when the checkbox is clicked. If null, the checkbox is not interactable unless external input handling updates its state.
- `modifier`: Modifier applied to the checkbox.
- `enabled`: Controls whether the checkbox responds to user input. When false, the checkbox appears visually disabled and is disabled for accessibility services.
- `colors`: CheckboxColors resolving the checkbox's colors in different states. See CheckboxDefaults.colors.
- `interactionSource`: Optional hoisted MutableInteractionSource for observing and emitting checkbox interactions. Use this to customize appearance in different states. If null, interactions still occur internally.

See also: TriStateCheckbox for indeterminate state support.

### Checkbox with Custom Stroke Styling

For greater visual customization, use the Checkbox variant that accepts Stroke parameters:

```kotlin
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity

val strokeWidthPx = with(LocalDensity.current) {
    floor(CheckboxDefaults.StrokeWidth.toPx())
}
val checkmarkStroke =
    remember(strokeWidthPx) {
        Stroke(
            width = strokeWidthPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    }
val outlineStroke = remember(strokeWidthPx) {
    Stroke(width = strokeWidthPx)
}
val checkedState = remember { mutableStateOf(true) }
Checkbox(
    checked = checkedState.value,
    onCheckedChange = { checkedState.value = it },
    checkmarkStroke = checkmarkStroke,
    outlineStroke = outlineStroke,
)
```

This example creates a checkbox with rounded stroke caps and joins for a softer, more polished appearance.

#### Custom Stroke Checkbox Parameters

- `checked`: Boolean indicating whether the checkbox is checked or unchecked.
- `onCheckedChange`: Lambda called when the checkbox is clicked. If null, the checkbox is not interactable unless external input handling updates its state.
- `checkmarkStroke`: Stroke styling for the checkmark itself.
- `outlineStroke`: Stroke styling for the checkbox's box outline. Note that StrokeJoin attributes are ignored when drawing the outline's rounded rectangle.
- `modifier`: Modifier applied to the checkbox.
- `enabled`: Controls whether the checkbox responds to user input.
- `colors`: CheckboxColors resolving the checkbox's colors in different states.
- `interactionSource`: Optional hoisted MutableInteractionSource for observing and emitting interactions.

See also: TriStateCheckbox for indeterminate state support.

---

## CircularProgressIndicator Component

### Overview

Circular progress indicators display progress in a circular format, either showing determinate progress (specific percentage) or indeterminate progress (ongoing activity without a known duration).

### Indeterminate Circular Progress Indicator

```kotlin

### CircularProgressIndicator (Determinate)

The determinate circular progress indicator shows specific progress from 0.0 to 1.0. Use this when you can calculate the exact completion percentage of a task.

**Function Signature:**

```kotlin
@Composable
fun CircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.circularColor,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
    trackColor: Color = ProgressIndicatorDefaults.circularDeterminateTrackColor,
    strokeCap: StrokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap,
    gapSize: Dp = ProgressIndicatorDefaults.CircularIndicatorTrackGapSize
): Unit
```kotlin

**Parameters:**

- `progress`: Lambda returning the progress value (0.0 = no progress, 1.0 = complete)
- `modifier`: Modifier to apply to the indicator
- `color`: Color of the progress indicator
- `strokeWidth`: Width of the progress stroke
- `trackColor`: Color of the background track
- `strokeCap`: Cap style for the stroke ends
- `gapSize`: Gap between the indicator and track

**Basic Usage:**

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment

Column(horizontalAlignment = Alignment.CenterHorizontally) {
    CircularProgressIndicator()
}
```kotlin

**Animated Progress Example:**

By default, there is no animation between progress values. Use `ProgressIndicatorDefaults.ProgressAnimationSpec` for smooth transitions:

```kotlin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

var progress by remember { mutableFloatStateOf(0.1f) }
val animatedProgress by animateFloatAsState(
    targetValue = progress,
    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
)

Column(horizontalAlignment = Alignment.CenterHorizontally) {
    CircularProgressIndicator(progress = { animatedProgress })
    Spacer(Modifier.requiredHeight(30.dp))
    Text("Set progress:")
    Slider(
        modifier = Modifier.width(300.dp),
        value = progress,
        valueRange = 0f..1f,
        onValueChange = { progress = it },
    )
}
```kotlin

### CircularWavyProgressIndicator (Indeterminate)

The indeterminate circular wavy progress indicator displays an animated wave pattern that continuously moves. This is part of Material 3 Expressive and adds visual interest to loading states.

**Function Signature:**

```kotlin
@ExperimentalMaterial3ExpressiveApi
@Composable
fun CircularWavyProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
    trackColor: Color = WavyProgressIndicatorDefaults.trackColor,
    stroke: Stroke = WavyProgressIndicatorDefaults.circularIndicatorStroke,
    trackStroke: Stroke = WavyProgressIndicatorDefaults.circularTrackStroke,
    gapSize: Dp = WavyProgressIndicatorDefaults.CircularIndicatorTrackGapSize,
    amplitude: @FloatRange(from = 0.0, to = 1.0) Float = 1.0f,
    wavelength: Dp = WavyProgressIndicatorDefaults.CircularWavelength,
    waveSpeed: Dp = wavelength
): Unit
```kotlin

**Parameters:**

- `modifier`: Modifier to apply to the indicator
- `color`: Color of the progress indicator
- `trackColor`: Color of the background track
- `stroke`: Stroke configuration for the indicator
- `trackStroke`: Stroke configuration for the track
- `gapSize`: Gap between indicator and track
- `amplitude`: Wave amplitude (0.0 = flat, 1.0 = full height)
- `wavelength`: Length of one complete wave cycle
- `waveSpeed`: Speed of wave movement in DP per second

**Basic Usage:**

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.ui.Alignment

Column(horizontalAlignment = Alignment.CenterHorizontally) {
    CircularWavyProgressIndicator()
}
```kotlin

### CircularWavyProgressIndicator (Determinate)

The determinate wavy progress indicator morphs its wave pattern based on progress value, creating an expressive visual feedback.

**Function Signature:**

```kotlin
@ExperimentalMaterial3ExpressiveApi
@Composable
fun CircularWavyProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
    trackColor: Color = WavyProgressIndicatorDefaults.trackColor,
    stroke: Stroke = WavyProgressIndicatorDefaults.circularIndicatorStroke,
    trackStroke: Stroke = WavyProgressIndicatorDefaults.circularTrackStroke,
    gapSize: Dp = WavyProgressIndicatorDefaults.CircularIndicatorTrackGapSize,
    amplitude: (progress: Float) -> Float = WavyProgressIndicatorDefaults.indicatorAmplitude,
    wavelength: Dp = WavyProgressIndicatorDefaults.CircularWavelength,
    waveSpeed: Dp = wavelength
): Unit
```kotlin

**Animated Progress Example:**

```kotlin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

var progress by remember { mutableFloatStateOf(0.1f) }
val animatedProgress by animateFloatAsState(
    targetValue = progress,
    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
)

Column(horizontalAlignment = Alignment.CenterHorizontally) {
    CircularWavyProgressIndicator(progress = { animatedProgress })
    Spacer(Modifier.requiredHeight(30.dp))
    Text("Set progress:")
    Slider(
        modifier = Modifier.width(300.dp),
        value = progress,
        valueRange = 0f..1f,
        onValueChange = { progress = it },
    )
}
```kotlin

**Thick Stroke Variant:**

Create a thicker, more prominent wavy indicator following Material guidelines:

```kotlin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

var progress by remember { mutableFloatStateOf(0.1f) }
val animatedProgress by animateFloatAsState(
    targetValue = progress,
    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
)
val thickStrokeWidth = with(LocalDensity.current) { 8.dp.toPx() }
val thickStroke = remember(thickStrokeWidth) {
    Stroke(width = thickStrokeWidth, cap = StrokeCap.Round)
}

Column(horizontalAlignment = Alignment.CenterHorizontally) {
    CircularWavyProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier.size(52.dp),
        stroke = thickStroke,
        trackStroke = thickStroke,
    )
    Spacer(Modifier.requiredHeight(30.dp))
    Text("Set progress:")
    Slider(
        modifier = Modifier.width(300.dp),
        value = progress,
        valueRange = 0f..1f,
        onValueChange = { progress = it },
    )
}
```kotlin

### ContainedLoadingIndicator (Indeterminate)

The contained loading indicator is an expressive component that animates and morphs between various shapes. The shapes are contained within a colored container, creating a distinctive loading experience.

**Function Signature:**

```kotlin
@ExperimentalMaterial3ExpressiveApi
@Composable
fun ContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    containerColor: Color = LoadingIndicatorDefaults.containedContainerColor,
    indicatorColor: Color = LoadingIndicatorDefaults.containedIndicatorColor,
    containerShape: Shape = LoadingIndicatorDefaults.containerShape,
    polygons: List<RoundedPolygon> = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons
): Unit
```kotlin

**Parameters:**

- `modifier`: Modifier to apply to the loading indicator
- `containerColor`: Color of the container background
- `indicatorColor`: Color of the animated shape
- `containerShape`: Shape of the container
- `polygons`: List of shapes to morph between (requires at least 2 shapes)

**Basic Usage:**

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.ui.Alignment

Column(horizontalAlignment = Alignment.CenterHorizontally) {
    ContainedLoadingIndicator()
}
```kotlin

**When to Use:**

- Use for indeterminate loading states where duration is unknown
- Provides more visual interest than standard progress indicators
- Best for prominent loading states that deserve user attention
- The shape morphing creates a playful, expressive feel

### ContainedLoadingIndicator (Determinate)

The determinate contained loading indicator morphs between shapes based on progress value, providing visual feedback about task completion.

**Function Signature:**

```kotlin
@ExperimentalMaterial3ExpressiveApi
@Composable
fun ContainedLoadingIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    containerColor: Color = LoadingIndicatorDefaults.containedContainerColor,
    indicatorColor: Color = LoadingIndicatorDefaults.containedIndicatorColor,
    containerShape: Shape = LoadingIndicatorDefaults.containerShape,
    polygons: List<RoundedPolygon> = LoadingIndicatorDefaults.DeterminateIndicatorPolygons
): Unit
```kotlin

**Parameters:**

- `progress`: Lambda returning progress (0.0 to 1.0)
- `modifier`: Modifier to apply to the indicator
- `containerColor`: Color of the container background
- `indicatorColor`: Color of the morphing shape
- `containerShape`: Shape of the container
- `polygons`: List of shapes to morph through as progress increases

**Animated Progress Example:**

```kotlin
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

var progress by remember { mutableFloatStateOf(0f) }
val animatedProgress by animateFloatAsState(
    targetValue = progress,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessVeryLow,
        visibilityThreshold = 1 / 1000f,
    ),
)

Column(horizontalAlignment = Alignment.CenterHorizontally) {
    ContainedLoadingIndicator(progress = { animatedProgress })
    Spacer(Modifier.requiredHeight(30.dp))
    Text("Set loading progress:")
    Slider(
        modifier = Modifier.width(300.dp),
        value = progress,
        valueRange = 0f..1f,
        onValueChange = { progress = it },
    )
}
```kotlin

**Pull-to-Refresh Integration:**

The contained loading indicator works well as a pull-to-refresh indicator:

```kotlin
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

var itemCount by remember { mutableStateOf(15) }
var isRefreshing by remember { mutableStateOf(false) }
val state = rememberPullToRefreshState()
val coroutineScope = rememberCoroutineScope()
val onRefresh: () -> Unit = {
    isRefreshing = true
    coroutineScope.launch {
        delay(5000)
        itemCount += 5
        isRefreshing = false
    }
}

val scaleFraction = {
    if (isRefreshing) 1f
    else LinearOutSlowInEasing.transform(state.distanceFraction).coerceIn(0f, 1f)
}

Scaffold(
    modifier = Modifier.pullToRefresh(
        state = state,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
    ),
    topBar = {
        TopAppBar(
            title = { Text("TopAppBar") },
            actions = {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, "Trigger Refresh")
                }
            },
        )
    },
) {
    Box(Modifier.padding(it)) {
        LazyColumn(Modifier.fillMaxSize()) {
            if (!isRefreshing) {
                items(itemCount) {
                    ListItem({ Text(text = "Item ${itemCount - it}") })
                }
            }
        }
        Box(
            Modifier.align(Alignment.TopCenter).graphicsLayer {
                scaleX = scaleFraction()
                scaleY = scaleFraction()
            }
        ) {
            PullToRefreshDefaults.LoadingIndicator(
                state = state,
                isRefreshing = isRefreshing
            )
        }
    }
}
```kotlin

## DatePicker

The DatePicker component allows users to select a date through a calendar interface. It supports both calendar picking and manual date input modes.

### DatePicker (Main Component)

**Function Signature:**

```kotlin
@Composable
fun DatePicker(
    state: DatePickerState,
    modifier: Modifier = Modifier,
    dateFormatter: DatePickerFormatter = remember { DatePickerDefaults.dateFormatter() },
    colors: DatePickerColors = DatePickerDefaults.colors(),
    title: (@Composable () -> Unit)? = {
        DatePickerDefaults.DatePickerTitle(
            displayMode = state.displayMode,
            modifier = Modifier.padding(DatePickerTitlePadding),
            contentColor = colors.titleContentColor,
        )
    },
    headline: (@Composable () -> Unit)? = {
        DatePickerDefaults.DatePickerHeadline(
            selectedDateMillis = state.selectedDateMillis,
            displayMode = state.displayMode,
            dateFormatter = dateFormatter,
            modifier = Modifier.padding(DatePickerHeadlinePadding),
            contentColor = colors.headlineContentColor,
        )
    },
    showModeToggle: Boolean = true,
    focusRequester: FocusRequester? = remember { FocusRequester() }
): Unit
```kotlin

**Parameters:**

- `state`: DatePickerState managing the picker's state
- `modifier`: Modifier to apply to the date picker
- `dateFormatter`: Formatter for date display
- `colors`: Color scheme for the picker
- `title`: Composable for the title section
- `headline`: Composable for the headline showing selected date
- `showModeToggle`: Whether to show the toggle between calendar and input modes
- `focusRequester`: Focus requester for input mode (pass null to disable auto-focus)

**Basic Usage:**

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

Column(
    modifier = Modifier.verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    // Pre-select a date for January 4, 2020
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = 1578096000000
    )
    DatePicker(state = datePickerState, modifier = Modifier.padding(16.dp))

    Text(
        "Selected date timestamp: ${datePickerState.selectedDateMillis ?: "no selection"}",
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
}
```kotlin

**Input Mode Example:**

Start the date picker in input mode for manual date entry:

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    val state = rememberDatePickerState(initialDisplayMode = DisplayMode.Input)
    DatePicker(state = state, modifier = Modifier.padding(16.dp))

    Text(
        "Entered date timestamp: ${state.selectedDateMillis ?: "no input"}",
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
}
```kotlin

**Java Time API Integration (API 26+):**

Initialize the date picker using Java Time APIs:

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.Text
import androidx.compose.material3.getSelectedDate
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

Column(
    modifier = Modifier.verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    // Pre-select a date for April 15, 2023
    val datePickerState = rememberDatePickerState(
        initialSelectedDate = LocalDate.of(2023, 4, 15)
    )
    DatePicker(state = datePickerState, modifier = Modifier.padding(16.dp))

    Text(
        "Selected date: ${datePickerState.getSelectedDate() ?: "no selection"}",
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
}
```kotlin

**Selectable Dates Example:**

Restrict which dates can be selected using `SelectableDates`:

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val datePickerState = rememberDatePickerState(
    selectableDates = object : SelectableDates {
        // Blocks Sunday and Saturday from being selected
        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val dayOfWeek = Instant.ofEpochMilli(utcTimeMillis)
                    .atZone(ZoneId.of("UTC"))
                    .toLocalDate()
                    .dayOfWeek
                dayOfWeek != DayOfWeek.SUNDAY && dayOfWeek != DayOfWeek.SATURDAY
            } else {
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                calendar.timeInMillis = utcTimeMillis
                calendar[Calendar.DAY_OF_WEEK] != Calendar.SUNDAY &&
                    calendar[Calendar.DAY_OF_WEEK] != Calendar.SATURDAY
            }
        }

        // Allow selecting dates from year 2023 forward
        override fun isSelectableYear(year: Int): Boolean {
            return year > 2022
        }
    }
)

Column(
    modifier = Modifier.verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    DatePicker(state = datePickerState)
    Text(
        "Selected date timestamp: ${datePickerState.selectedDateMillis ?: "no selection"}",
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
}
```kotlin

### DatePickerDialog

A dialog wrapper for the DatePicker component, following Material Design guidelines for modal date selection.

**Function Signature:**

```kotlin
@Composable
fun DatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    shape: Shape = DatePickerDefaults.shape,
    tonalElevation: Dp = DatePickerDefaults.TonalElevation,
    colors: DatePickerColors = DatePickerDefaults.colors(),
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable ColumnScope.() -> Unit
): Unit
```kotlin

**Parameters:**

- `onDismissRequest`: Called when user dismisses the dialog (not called for button clicks)
- `confirmButton`: Composable for the confirm action button
- `modifier`: Modifier to apply to the dialog content
- `dismissButton`: Optional composable for the dismiss button
- `shape`: Shape of the dialog surface
- `tonalElevation`: Elevation affecting surface color
- `colors`: Color scheme for the date picker
- `properties`: Platform-specific dialog properties
- `content`: Content of the dialog (typically a DatePicker)

**Complete Dialog Example:**

```kotlin
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier

val snackState = remember { SnackbarHostState() }
val snackScope = rememberCoroutineScope()
SnackbarHost(hostState = snackState, Modifier)
val openDialog = remember { mutableStateOf(true) }

if (openDialog.value) {
    val datePickerState = rememberDatePickerState()
    val confirmEnabled = remember {
        derivedStateOf { datePickerState.selectedDateMillis != null }
    }
    DatePickerDialog(
        onDismissRequest = {
            openDialog.value = false
        },
        confirmButton = {
            TextButton(
                onClick = {
                    openDialog.value = false
                    snackScope.launch {
                        snackState.showSnackbar(
                            "Selected date timestamp: ${datePickerState.selectedDateMillis}"
                        )
                    }
                },
                enabled = confirmEnabled.value,
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = { openDialog.value = false }) {
                Text("Cancel")
            }
        },
    ) {
        DatePicker(
            state = datePickerState,
            modifier = Modifier.verticalScroll(rememberScrollState()),
        )
    }
}
```kotlin

### DatePickerState

State management for the DatePicker component. Use `rememberDatePickerState()` in most cases.

**Factory Function (Java Time API - API 26+):**

```kotlin
@RequiresApi(value = 26)
@ExperimentalMaterial3Api
fun DatePickerState(
    locale: Locale,
    initialSelectedDate: LocalDate?,
    initialDisplayedMonth: YearMonth? = initialSelectedDate?.let { YearMonth.from(it) },
    yearRange: IntRange = DatePickerDefaults.YearRange,
    initialDisplayMode: DisplayMode = DisplayMode.Picker,
    selectableDates: SelectableDates = DatePickerDefaults.AllDates
): DatePickerState
```kotlin

**Parameters:**

- `locale`: Locale for formatting and calendar behavior
- `initialSelectedDate`: Initial selected date (null for no selection)
- `initialDisplayedMonth`: Initial month to display (defaults to selected date's month)
- `yearRange`: Range of years available for selection
- `initialDisplayMode`: Starting display mode (Picker or Input)
- `selectableDates`: Interface to control which dates can be selected

**Factory Function (Milliseconds):**

```kotlin
fun DatePickerState(
    locale: CalendarLocale,
    initialSelectedDateMillis: Long? = null,
    initialDisplayedMonthMillis: Long? = initialSelectedDateMillis,
    yearRange: IntRange = DatePickerDefaults.YearRange,
    initialDisplayMode: DisplayMode = DisplayMode.Picker,
    selectableDates: SelectableDates = DatePickerDefaults.AllDates
): DatePickerState
```kotlin

**Custom Locale Example:**

When using a non-default locale, ensure proper localization of title and headline:

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.Text
import androidx.compose.material3.getSelectedDate
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

val preferredLocales = LocaleList.forLanguageTags("HE")
val config = Configuration()
config.setLocales(preferredLocales)
val newContext = LocalContext.current.createConfigurationContext(config)

CompositionLocalProvider(
    LocalContext provides newContext,
    LocalConfiguration provides config,
    LocalLayoutDirection provides LayoutDirection.Rtl,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val datePickerState = remember {
            DatePickerState(
                initialSelectedDate = LocalDate.of(2020, 1, 4),
                locale = preferredLocales.get(0),
            )
        }
        DatePicker(state = datePickerState, modifier = Modifier.padding(16.dp))

        Text(
            "Selected date: ${datePickerState.getSelectedDate() ?: "no selection"}",
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
```kotlin


### DateRangePicker Component

The DateRangePicker component allows users to select a range of dates. It can be embedded into dialogs and provides a comprehensive interface for date range selection.

#### Basic DateRangePicker Implementation

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

// Decoupled snackbar host state from scaffold state for demo purposes.
val snackState = remember { SnackbarHostState() }
val snackScope = rememberCoroutineScope()
SnackbarHost(hostState = snackState, Modifier.zIndex(1f))

val state = rememberDateRangePickerState()
Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Top) {
    // Add a row with "Save" and dismiss actions.
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(DatePickerDefaults.colors().containerColor)
            .padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text("Close") } },
            state = rememberTooltipState()
        ) {
            IconButton(onClick = { /* dismiss the UI */ }) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }
        TextButton(
            onClick = {
                snackScope.launch {
                    val range = state.selectedStartDateMillis!!..state.selectedEndDateMillis!!
                    snackState.showSnackbar("Saved range (timestamps): $range")
                }
            },
            enabled = state.selectedEndDateMillis != null
        ) {
            Text(text = "Save")
        }
    }
    DateRangePicker(state = state, modifier = Modifier.weight(1f))
}
```kotlin

#### DateRangePicker with Java Time API (Android API 26+)

```kotlin
import androidx.compose.material3.*
import java.time.LocalDate

// Creates a state with pre-selected date range.
val state = rememberDateRangePickerState(
    initialSelectedStartDate = LocalDate.of(2023, 4, 15),
    initialSelectedEndDate = LocalDate.of(2023, 4, 20)
)

Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Top) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(DatePickerDefaults.colors().containerColor)
            .padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text("Close") } },
            state = rememberTooltipState()
        ) {
            IconButton(onClick = { /* dismiss the UI */ }) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }
        TextButton(
            onClick = {
                snackScope.launch {
                    val range = state.getSelectedStartDate()!!..state.getSelectedEndDate()!!
                    snackState.showSnackbar("Saved range: $range")
                }
            },
            enabled = state.getSelectedEndDate() != null
        ) {
            Text(text = "Save")
        }
    }
    DateRangePicker(state = state, modifier = Modifier.weight(1f))
}
```kotlin

#### DateRangePicker Parameters

- **state**: DateRangePickerState - State of the date range picker
- **modifier**: Modifier - Modifier to be applied to this date range picker
- **dateFormatter**: DatePickerFormatter - Provides formatting skeletons for dates display
- **colors**: DatePickerColors - Colors used for this date range picker in different states
- **title**: @Composable (() -> Unit)? - Title to be displayed in the date range picker
- **headline**: @Composable (() -> Unit)? - Headline to be displayed in the date range picker
- **showModeToggle**: Boolean - Indicates if this DateRangePicker should show a mode toggle action
- **focusRequester**: FocusRequester? - Focus requester for the text field when in input mode

### DateRangePickerState

The DateRangePickerState manages the state for a date range picker component.

#### Creating DateRangePickerState with Java Time API (Android API 26+)

```kotlin
import java.time.LocalDate
import java.time.YearMonth

val state = DateRangePickerState(
    locale = Locale.getDefault(),
    initialSelectedStartDate = LocalDate.of(2023, 4, 15),
    initialSelectedEndDate = LocalDate.of(2023, 4, 20),
    initialDisplayedMonth = YearMonth.of(2023, 4),
    yearRange = DatePickerDefaults.YearRange,
    initialDisplayMode = DisplayMode.Picker,
    selectableDates = DatePickerDefaults.AllDates
)
```kotlin

#### Creating DateRangePickerState with Timestamps

```kotlin
val state = DateRangePickerState(
    locale = CalendarLocale.getDefault(),
    initialSelectedStartDateMillis = 1681516800000L, // April 15, 2023
    initialSelectedEndDateMillis = 1681948800000L,   // April 20, 2023
    initialDisplayedMonthMillis = 1681516800000L,
    yearRange = DatePickerDefaults.YearRange,
    initialDisplayMode = DisplayMode.Picker,
    selectableDates = DatePickerDefaults.AllDates
)
```kotlin

#### DateRangePickerState with Custom Locale

When using a custom locale, you may need to ensure proper localization and RTL layout:

```kotlin
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.*
import androidx.core.os.LocaleListCompat

val preferredLocales = LocaleList.forLanguageTags("HE")
val config = Configuration()
config.setLocales(preferredLocales)
val newContext = LocalContext.current.createConfigurationContext(config)

CompositionLocalProvider(
    LocalContext provides newContext,
    LocalConfiguration provides config,
    LocalLayoutDirection provides LayoutDirection.Rtl
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val datePickerState = remember {
            DatePickerState(
                initialSelectedDateMillis = 1578096000000,
                locale = preferredLocales.get(0)
            )
        }
        DatePicker(state = datePickerState, modifier = Modifier.padding(16.dp))

        Text(
            "Selected date timestamp: ${datePickerState.selectedDateMillis ?: "no selection"}",
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
```kotlin

#### DateRangePickerState Parameters

- **locale**: Locale/CalendarLocale - Locale used for formatting dates and determining calendar behavior
- **initialSelectedStartDate**: LocalDate? - Initial selection of a start date (null for no selection)
- **initialSelectedEndDate**: LocalDate? - Initial selection of an end date (null for no selection)
- **initialDisplayedMonth**: YearMonth? - Initial month displayed to the user
- **yearRange**: IntRange - Year range that the date picker will be limited to
- **initialDisplayMode**: DisplayMode - Initial display mode (Picker or Input)
- **selectableDates**: SelectableDates - Consulted to check if a date is allowed for selection

**Important Notes:**
- The end date must not precede the start date
- When providing an end date, a start date must also be provided
- Initial timestamps must fall within the specified year range
- For custom locales, ensure proper title and headline localization

### DismissibleDrawerSheet Component

The DismissibleDrawerSheet component provides the content container for a dismissible navigation drawer.

#### Basic DismissibleDrawerSheet

```kotlin
@Composable
fun DismissibleDrawerSheet(
    modifier: Modifier = Modifier,
    drawerShape: Shape = RectangleShape,
    drawerContainerColor: Color = DrawerDefaults.standardContainerColor,
    drawerContentColor: Color = contentColorFor(drawerContainerColor),
    drawerTonalElevation: Dp = DrawerDefaults.DismissibleDrawerElevation,
    windowInsets: WindowInsets = DrawerDefaults.windowInsets,
    content: @Composable ColumnScope.() -> Unit
)
```kotlin

**Note:** This version does not handle back navigation by default. For automatic back handling and predictive back animations on Android 14+, use the version that accepts `drawerState` as a parameter.

#### DismissibleDrawerSheet with Back Handling

```kotlin
@Composable
fun DismissibleDrawerSheet(
    drawerState: DrawerState,
    modifier: Modifier = Modifier,
    drawerShape: Shape = RectangleShape,
    drawerContainerColor: Color = DrawerDefaults.standardContainerColor,
    drawerContentColor: Color = contentColorFor(drawerContainerColor),
    drawerTonalElevation: Dp = DrawerDefaults.DismissibleDrawerElevation,
    windowInsets: WindowInsets = DrawerDefaults.windowInsets,
    content: @Composable ColumnScope.() -> Unit
)
```kotlin

This version requires a `drawerState` and handles back navigation for all Android versions, with predictive back animations on Android 14+.

#### DismissibleDrawerSheet Parameters

- **drawerState**: DrawerState - State of the drawer (for back-handling version)
- **modifier**: Modifier - Modifier to be applied to this drawer's content
- **drawerShape**: Shape - Defines the shape of this drawer's container
- **drawerContainerColor**: Color - Background color of this drawer
- **drawerContentColor**: Color - Preferred color for content inside this drawer
- **drawerTonalElevation**: Dp - Tonal elevation for surface color overlay
- **windowInsets**: WindowInsets - Window insets for the sheet
- **content**: @Composable ColumnScope.() -> Unit - Content inside the dismissible navigation drawer

### DismissibleNavigationDrawer Component

The DismissibleNavigationDrawer provides ergonomic access to app destinations. It's positioned next to app content and affects the screen's layout grid.

Dismissible drawers are ideal for layouts that prioritize content (such as photo galleries) or apps where users rarely switch destinations. They should use a visible navigation menu icon to open and close the drawer.

#### Complete DismissibleNavigationDrawer Example

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

val drawerState = rememberDrawerState(DrawerValue.Closed)
val scope = rememberCoroutineScope()
val focusRequester = FocusRequester()

// Icons to represent drawer destinations
val items = listOf(
    Icons.Default.AccountCircle,
    Icons.Default.Bookmarks,
    Icons.Default.CalendarMonth,
    Icons.Default.Dashboard,
    Icons.Default.Email,
    Icons.Default.Favorite,
    Icons.Default.Group,
    Icons.Default.Headphones,
    Icons.Default.Image,
    Icons.Default.JoinFull,
    Icons.Default.Keyboard,
    Icons.Default.Laptop,
    Icons.Default.Map,
    Icons.Default.Navigation,
    Icons.Default.Outbox,
    Icons.Default.PushPin,
    Icons.Default.QrCode,
    Icons.Default.Radio
)
val selectedItem = remember { mutableStateOf(items[0]) }

DismissibleNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
        DismissibleDrawerSheet(drawerState) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(12.dp))
                items.forEach { item ->
                    NavigationDrawerItem(
                        icon = { Icon(item, contentDescription = null) },
                        label = { Text(item.name.substringAfterLast(".")) },
                        selected = item == selectedItem.value,
                        onClick = {
                            scope.launch { drawerState.close() }
                            selectedItem.value = item
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    },
    content = {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = if (drawerState.isClosed) ">>> Swipe >>>" else "<<< Swipe <<<")
            Spacer(Modifier.height(20.dp))
            Button(
                modifier = Modifier.focusRequester(focusRequester),
                onClick = { scope.launch { drawerState.open() } }
            ) {
                Text("Click to open")
            }
        }
    }
)

// Return keyboard focus to button when drawer closes
LaunchedEffect(drawerState.isClosed) {
    if (drawerState.isClosed) {
        focusRequester.requestFocus()
    }
}
```kotlin

#### DismissibleNavigationDrawer Parameters

- **drawerContent**: @Composable () -> Unit - Content inside this drawer
- **modifier**: Modifier - Modifier to be applied to this drawer
- **drawerState**: DrawerState - State of the drawer
- **gesturesEnabled**: Boolean - Whether the drawer can be interacted with via gestures
- **content**: @Composable () -> Unit - Content of the rest of the UI

### DockedSearchBar Component

The DockedSearchBar displays search results in a bounded table below the input field. It's an alternative to SearchBar when expanding to full-screen size is undesirable on large screens such as tablets.

#### DockedSearchBar with Custom Input Field

```kotlin
@Composable
fun DockedSearchBar(
    inputField: @Composable () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = SearchBarDefaults.dockedShape,
    colors: SearchBarColors = SearchBarDefaults.colors(),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    shadowElevation: Dp = SearchBarDefaults.ShadowElevation,
    content: @Composable ColumnScope.() -> Unit
)
```kotlin

#### DockedSearchBar Parameters

- **inputField**: @Composable () -> Unit - Input field for entering search queries (typically SearchBarDefaults.InputField)
- **expanded**: Boolean - Whether this search bar is expanded and showing search results
- **onExpandedChange**: (Boolean) -> Unit - Callback invoked when expanded state changes
- **modifier**: Modifier - Modifier to be applied to this search bar
- **shape**: Shape - Shape of this search bar
- **colors**: SearchBarColors - Colors used for this search bar in different states
- **tonalElevation**: Dp - Tonal elevation for surface color overlay
- **shadowElevation**: Dp - Elevation for the shadow below the search bar
- **content**: @Composable ColumnScope.() -> Unit - Content displaying search results below the input field

### DropdownMenu Component

The DropdownMenu displays a list of choices on a temporary surface. Menus appear when users interact with a button, action, or other control.

A DropdownMenu behaves similarly to a Popup and positions itself relative to its parent layout. Typically, a DropdownMenu is placed in a Box with a sibling that serves as the anchor. The menu is displayed in a separate window on top of other content and doesn't take up layout space.

#### Basic DropdownMenu Example

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

var expanded by remember { mutableStateOf(false) }

Box(modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.TopStart)) {
    val description = "Localized description"
    // Icon button should have a tooltip for accessibility
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = description)
        }
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text("Edit") },
            onClick = { /* Handle edit! */ },
            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text("Settings") },
            onClick = { /* Handle settings! */ },
            leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Send Feedback") },
            onClick = { /* Handle send feedback! */ },
            leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
            trailingIcon = { Text("F11", textAlign = TextAlign.Center) }
        )
    }
}
```kotlin

#### DropdownMenu with Scroll Control

```kotlin
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect

var expanded by remember { mutableStateOf(false) }
val scrollState = rememberScrollState()

Box(modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.TopStart)) {
    val description = "Localized description"
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = description)
        }
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        scrollState = scrollState
    ) {
        repeat(30) {
            DropdownMenuItem(
                text = { Text("Item ${it + 1}") },
                onClick = { /* TODO */ },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) }
            )
        }
    }

    // Scroll to show bottom menu items when expanded
    LaunchedEffect(expanded) {
        if (expanded) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }
}
```kotlin

#### DropdownMenu Positioning Behavior

The DropdownMenu automatically adjusts its position based on available space:

**Horizontal positioning** (respects LayoutDirection):
1. Attempts to align its start to the parent's start
2. Falls back to aligning its end to the parent's end
3. Finally aligns to the window edge if needed

**Vertical positioning:**
1. Attempts to align its top to the parent's bottom
2. Falls back to aligning its bottom to the parent's top
3. Finally aligns to the window edge if needed

Use the `offset` parameter to adjust positioning when the layout bounds don't match visual bounds.

#### DropdownMenu Parameters

- **expanded**: Boolean - Whether the menu is expanded or not
- **onDismissRequest**: () -> Unit - Called when the user requests to dismiss the menu
- **modifier**: Modifier - Modifier to be applied to the menu's content
- **offset**: DpOffset - Offset from the original position (respects LayoutDirection)
- **scrollState**: ScrollState - ScrollState for vertical scrolling of menu items
- **properties**: PopupProperties - Further customization of popup behavior
- **shape**: Shape - Shape of the menu
- **containerColor**: Color - Background color of the menu
- **tonalElevation**: Dp - Tonal elevation for surface color overlay
- **shadowElevation**: Dp - Elevation for the shadow below the menu
- **border**: BorderStroke? - Optional border for the menu
- **content**: @Composable ColumnScope.() -> Unit - Content of the menu (typically DropdownMenuItems)

**Important Notes:**
- DropdownMenu content is placed inside a scrollable Column
- Using LazyColumn as the root layout inside content is not supported
- DropdownMenuItems should be used for Material Design compliance
- onDismissRequest is called for taps outside the menu or back button presses


### DropdownMenuItem Component

The DropdownMenuItem is a menu item component designed to be used within a DropdownMenu. It provides a consistent Material Design appearance for menu items.

#### DropdownMenuItem Parameters

- **text**: @Composable () -> Unit - Text of the menu item
- **onClick**: () -> Unit - Called when this menu item is clicked
- **modifier**: Modifier - Modifier to be applied to this menu item
- **leadingIcon**: @Composable (() -> Unit)? - Optional icon at the beginning of the item's text
- **trailingIcon**: @Composable (() -> Unit)? - Optional icon at the end of the item's text (can also accept Text for keyboard shortcuts)
- **enabled**: Boolean - Controls the enabled state of this menu item
- **colors**: MenuItemColors - Colors used for this menu item in different states
- **contentPadding**: PaddingValues - Padding applied to the content of this menu item
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions

### ElevatedAssistChip Component

Assist chips represent smart or automated actions that can span multiple apps. They should appear dynamically and contextually in a UI, functioning as though the user asked an assistant to complete the action.

The ElevatedAssistChip applies an elevated style with a shadow. For a flat style, use the standard AssistChip.

#### ElevatedAssistChip Example

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.ui.Modifier

ElevatedAssistChip(
    onClick = { /* Do something! */ },
    label = { Text("Assist Chip") },
    leadingIcon = {
        Icon(
            Icons.Filled.Settings,
            contentDescription = "Localized description",
            Modifier.size(AssistChipDefaults.IconSize)
        )
    }
)
```kotlin

#### ElevatedAssistChip Parameters

- **onClick**: () -> Unit - Called when this chip is clicked
- **label**: @Composable () -> Unit - Text label for this chip
- **modifier**: Modifier - Modifier to be applied to this chip
- **enabled**: Boolean - Controls the enabled state of this chip
- **leadingIcon**: @Composable (() -> Unit)? - Optional icon at the start of the chip
- **trailingIcon**: @Composable (() -> Unit)? - Optional icon at the end of the chip
- **shape**: Shape - Defines the shape of this chip's container, border, and shadow
- **colors**: ChipColors - Colors used for this chip in different states
- **elevation**: ChipElevation? - Controls the size of the shadow below the chip
- **border**: BorderStroke? - Border to draw around the container of this chip
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions

### ElevatedButton Component

Elevated buttons are high-emphasis buttons that are essentially FilledTonalButtons with a shadow. Use them sparingly to prevent shadow creep—only when the button requires visual separation from a patterned container.

#### Standard ElevatedButton

```kotlin
import androidx.compose.material3.*

ElevatedButton(onClick = { /* Do something! */ }) {
    Text("Elevated Button")
}
```kotlin

#### ElevatedButton with Shape Morphing (Expressive)

The expressive variant morphs between shapes based on user interaction:

```kotlin
import androidx.compose.material3.*

ElevatedButton(
    onClick = {},
    shapes = ButtonDefaults.shapes()
) {
    Text("Elevated Button")
}
```kotlin

**Shape Morphing Behavior:**
- The button morphs between the shapes provided in `ButtonShapes` based on interaction state
- Shape morphing only works with `CornerBasedShape` instances
- If a shape isn't a `CornerBasedShape`, the button will change shapes discretely rather than morphing

#### Button Emphasis Hierarchy

Choose the best button for an action based on the emphasis it needs:

1. **Button** (Filled Button) - Highest emphasis, no shadow
2. **ElevatedButton** - High emphasis with shadow for visual separation
3. **FilledTonalButton** - Medium emphasis between outlined and filled
4. **OutlinedButton** - Medium emphasis with border
5. **TextButton** - Lowest emphasis, no border

#### ElevatedButton Parameters

- **onClick**: () -> Unit - Called when this button is clicked
- **modifier**: Modifier - Modifier to be applied to this button
- **enabled**: Boolean - Controls the enabled state of this button
- **shape/shapes**: Shape/ButtonShapes - Defines the shape(s) of this button's container
- **colors**: ButtonColors - Colors used for this button in different states
- **elevation**: ButtonElevation? - Controls the size of the shadow below the button
- **border**: BorderStroke? - Border to draw around the container of this button
- **contentPadding**: PaddingValues - Spacing between the container and the content
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions
- **content**: @Composable RowScope.() -> Unit - Content displayed on the button (text, icon, or image)

**Note:** The default text style for internal Text components is Typography.labelLarge.

### ElevatedCard Component

Elevated cards contain content and actions about a subject. They have a drop shadow, providing more separation from the background than filled cards, but less than outlined cards.

#### Non-Interactive ElevatedCard

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

ElevatedCard(Modifier.size(width = 180.dp, height = 100.dp)) {
    Box(Modifier.fillMaxSize()) {
        Text("Card content", Modifier.align(Alignment.Center))
    }
}
```kotlin

#### Clickable ElevatedCard

```kotlin
ElevatedCard(
    onClick = { /* Do something */ },
    modifier = Modifier.size(width = 180.dp, height = 100.dp)
) {
    Box(Modifier.fillMaxSize()) {
        Text("Clickable", Modifier.align(Alignment.Center))
    }
}
```kotlin

#### ElevatedCard Parameters

- **onClick**: () -> Unit - Called when this card is clicked (clickable version only)
- **modifier**: Modifier - Modifier to be applied to this card
- **enabled**: Boolean - Controls the enabled state of this card (clickable version only)
- **shape**: Shape - Defines the shape of this card's container and shadow
- **colors**: CardColors - Colors used for this card in different states
- **elevation**: CardElevation - Controls the size of the shadow below the card
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions (clickable version only)
- **content**: @Composable ColumnScope.() -> Unit - Content displayed on the card

### ElevatedFilterChip Component

Filter chips use tags or descriptive words to filter content. They can be a good alternative to toggle buttons or checkboxes. The ElevatedFilterChip applies an elevated style with a shadow.

Tapping on a filter chip toggles its selection state. A selection state leading icon (e.g., a checkmark) can be provided to indicate selection.

#### ElevatedFilterChip Example

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

var selected by remember { mutableStateOf(false) }

ElevatedFilterChip(
    selected = selected,
    onClick = { selected = !selected },
    label = { Text("Filter chip") },
    leadingIcon = if (selected) {
        {
            Icon(
                imageVector = Icons.Filled.Done,
                contentDescription = "Localized Description",
                modifier = Modifier.size(FilterChipDefaults.IconSize)
            )
        }
    } else {
        null
    }
)
```kotlin

#### ElevatedFilterChip Parameters

- **selected**: Boolean - Whether this chip is selected or not
- **onClick**: () -> Unit - Called when this chip is clicked
- **label**: @Composable () -> Unit - Text label for this chip
- **modifier**: Modifier - Modifier to be applied to this chip
- **enabled**: Boolean - Controls the enabled state of this chip
- **leadingIcon**: @Composable (() -> Unit)? - Optional icon at the start (may indicate selection when selected is true)
- **trailingIcon**: @Composable (() -> Unit)? - Optional icon at the end of the chip
- **shape**: Shape - Defines the shape of this chip's container, border, and shadow
- **colors**: SelectableChipColors - Colors used for this chip in different states
- **elevation**: SelectableChipElevation? - Controls the size of the shadow below the chip
- **border**: BorderStroke? - Border to draw around the container of this chip
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions

### ElevatedSuggestionChip Component

Suggestion chips help narrow a user's intent by presenting dynamically generated suggestions, such as possible responses or search filters. The ElevatedSuggestionChip applies an elevated style with a shadow.

#### ElevatedSuggestionChip Example

```kotlin
import androidx.compose.material3.*

ElevatedSuggestionChip(
    onClick = { /* Do something! */ },
    label = { Text("Suggestion Chip") }
)
```kotlin

#### ElevatedSuggestionChip Parameters

- **onClick**: () -> Unit - Called when this chip is clicked
- **label**: @Composable () -> Unit - Text label for this chip
- **modifier**: Modifier - Modifier to be applied to this chip
- **enabled**: Boolean - Controls the enabled state of this chip
- **icon**: @Composable (() -> Unit)? - Optional icon at the start of the chip
- **shape**: Shape - Defines the shape of this chip's container, border, and shadow
- **colors**: ChipColors - Colors used for this chip in different states
- **elevation**: ChipElevation? - Controls the size of the shadow below the chip
- **border**: BorderStroke? - Border to draw around the container of this chip
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions

### ElevatedToggleButton Component (Expressive)

The ElevatedToggleButton is a toggleable button that switches between primary and tonal colors based on its checked state. It morphs between shapes provided in `ToggleButtonShapes` based on user interaction.

Elevated toggle buttons are high-emphasis components. Use them sparingly to prevent shadow creep—only when the toggle button requires visual separation from a patterned container.

#### ElevatedToggleButton Example

```kotlin
import androidx.compose.material3.*
import androidx.compose.runtime.*

var checked by remember { mutableStateOf(false) }

ElevatedToggleButton(
    checked = checked,
    onCheckedChange = { checked = it }
) {
    Text("Elevated Button")
}
```kotlin

#### ElevatedToggleButton Parameters

- **checked**: Boolean - Whether the toggle button is toggled on or off
- **onCheckedChange**: (Boolean) -> Unit - Called when the toggle button is clicked
- **modifier**: Modifier - Modifier to be applied to the toggle button
- **enabled**: Boolean - Controls the enabled state of this toggle button
- **shapes**: ToggleButtonShapes - Shapes that the toggle button will morph between based on interaction
- **colors**: ToggleButtonColors - Colors used for this toggle button in different states
- **elevation**: ButtonElevation? - Controls the size of the shadow below the button
- **border**: BorderStroke? - Border to draw around the container of this toggle button
- **contentPadding**: PaddingValues - Spacing between the container and the content
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions
- **content**: @Composable RowScope.() -> Unit - Content displayed on the toggle button (text, icon, or image)

**Shape Morphing Behavior:**
- The button morphs between shapes based on interaction state
- Shape morphing only works with `CornerBasedShape` instances
- If a shape isn't a `CornerBasedShape`, the button will toggle between shapes discretely

### ExpandedDockedSearchBar Component

The ExpandedDockedSearchBar represents a search bar in the expanding or expanded state, showing search results. This component is displayed in a popup over the collapsed search bar.

**Recommended Usage:**
- Use ExpandedDockedSearchBar on medium and large screens (tablets)
- Use ExpandedFullScreenSearchBar on compact screens (phones)

#### ExpandedDockedSearchBar Example

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

val textFieldState = rememberTextFieldState()
val searchBarState = rememberSearchBarState()
val scope = rememberCoroutineScope()
val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
val appBarWithSearchColors = SearchBarDefaults.appBarWithSearchColors()

var inputFieldWidth by remember { mutableIntStateOf(0) }
val inputField = @Composable {
    SearchBarDefaults.InputField(
        modifier = Modifier.onSizeChanged { inputFieldWidth = it.width },
        searchBarState = searchBarState,
        textFieldState = textFieldState,
        onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
        placeholder = {
            if (searchBarState.currentValue == SearchBarValue.Collapsed) {
                Text(
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                    text = "Search",
                    textAlign = TextAlign.Center
                )
            }
        }
    )
}

Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
        AppBarWithSearch(
            scrollBehavior = scrollBehavior,
            state = searchBarState,
            inputField = inputField,
            navigationIcon = {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text("Menu") } },
                    state = rememberTooltipState()
                ) {
                    IconButton(onClick = { /* doSomething() */ }) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            },
            actions = {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text("Account") } },
                    state = rememberTooltipState()
                ) {
                    IconButton(onClick = { /* doSomething() */ }) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Account"
                        )
                    }
                }
            },
            colors = appBarWithSearchColors
        )
        ExpandedDockedSearchBar(
            modifier = Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(maxWidth = inputFieldWidth))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            },
            state = searchBarState,
            inputField = inputField
        ) {
            // Search results content
        }
    }
) { padding ->
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val list = List(100) { "Text $it" }
        items(count = list.size) {
            Text(
                text = list[it],
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }
    }
}
```kotlin

#### ExpandedDockedSearchBar Parameters

- **state**: SearchBarState - State of the search bar (should be shared with inputField and collapsed search bar)
- **inputField**: @Composable () -> Unit - Input field for entering search queries (typically SearchBarDefaults.InputField)
- **modifier**: Modifier - Modifier to be applied to this expanded search bar
- **shape**: Shape - Shape of this search bar
- **colors**: SearchBarColors - Colors used for this search bar in different states
- **tonalElevation**: Dp - Tonal elevation for surface color overlay
- **shadowElevation**: Dp - Elevation for the shadow below this search bar
- **properties**: PopupProperties - Platform-specific properties to configure the dialog's behavior
- **content**: @Composable ColumnScope.() -> Unit - Content displaying search results below the input field

### ExpandedFullScreenSearchBar Component

The ExpandedFullScreenSearchBar represents a search bar in the expanding or expanded state, showing search results in a new full-screen dialog. This is ideal for compact screens like phones.

For medium or large screens (tablets) where full-screen expansion is undesirable, use ExpandedDockedSearchBar instead.

#### ExpandedFullScreenSearchBar Example

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

val textFieldState = rememberTextFieldState()
val searchBarState = rememberSearchBarState()
val scope = rememberCoroutineScope()
val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
val appBarWithSearchColors = SearchBarDefaults.appBarWithSearchColors()

val inputField = @Composable {
    SearchBarDefaults.InputField(
        modifier = Modifier,
        searchBarState = searchBarState,
        textFieldState = textFieldState,
        onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
        placeholder = {
            if (searchBarState.currentValue == SearchBarValue.Collapsed) {
                Text(
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                    text = "Search",
                    textAlign = TextAlign.Center
                )
            }
        },
        leadingIcon = {
            if (searchBarState.currentValue == SearchBarValue.Expanded) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text("Back") } },
                    state = rememberTooltipState()
                ) {
                    IconButton(onClick = { scope.launch { searchBarState.animateToCollapsed() } }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            }
        }
    )
}

// Use in Scaffold with AppBarWithSearch
```kotlin

#### ExpandedFullScreenSearchBar Parameters

- **state**: SearchBarState - State of the search bar (should be shared with inputField and collapsed search bar)
- **inputField**: @Composable () -> Unit - Input field for entering search queries (typically SearchBarDefaults.InputField)
- **modifier**: Modifier - Modifier to be applied to this expanded search bar
- **collapsedShape**: Shape - Shape of the search bar when collapsed
- **colors**: SearchBarColors - Colors used for this search bar in different states
- **tonalElevation**: Dp - Tonal elevation for surface color overlay
- **shadowElevation**: Dp - Elevation for the shadow below this search bar
- **windowInsets**: @Composable () -> WindowInsets - Window insets for the full-screen dialog
- **properties**: DialogProperties - Platform-specific properties to configure the dialog's behavior
- **content**: @Composable ColumnScope.() -> Unit - Content displaying search results below the input field


### ExposedDropdownMenuBox Component

The ExposedDropdownMenuBox (also called "spinners" or "combo boxes") displays the currently selected item in a text field to which the menu is anchored. It can accept and display user input, making it useful for implementing autocomplete functionality.

The ExposedDropdownMenuBox should contain a TextField (or OutlinedTextField) and ExposedDropdownMenu as content. The `menuAnchor` modifier must be passed to the text field.

#### Read-Only ExposedDropdownMenuBox

```kotlin
import androidx.compose.foundation.text.input.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

val options: List<String> = listOf("Option 1", "Option 2", "Option 3", "Option 4", "Option 5")
var expanded by remember { mutableStateOf(false) }
val textFieldState = rememberTextFieldState(options[0])

ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = it }
) {
    TextField(
        // The `menuAnchor` modifier must be passed to the text field
        // A read-only text field has the anchor type `PrimaryNotEditable`
        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        state = textFieldState,
        readOnly = true,
        lineLimits = TextFieldLineLimits.SingleLine,
        label = { Text("Label") },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        colors = ExposedDropdownMenuDefaults.textFieldColors()
    )
    ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(option, style = MaterialTheme.typography.bodyLarge) },
                onClick = {
                    textFieldState.setTextAndPlaceCursorAtEnd(option)
                    expanded = false
                },
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
            )
        }
    }
}
```kotlin

#### Editable ExposedDropdownMenuBox with Filtering

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val options: List<String> = listOf("Apple", "Banana", "Cherry", "Date", "Elderberry")
val textFieldState = rememberTextFieldState()

// Filter options based on user input
val filteredOptions = options.filter { it.contains(textFieldState.text, ignoreCase = true) }

val (allowExpanded, setExpanded) = remember { mutableStateOf(false) }
val expanded = allowExpanded && filteredOptions.isNotEmpty()

ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = setExpanded
) {
    TextField(
        // An editable text field has the anchor type `PrimaryEditable`
        modifier = Modifier.width(280.dp)
            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        state = textFieldState,
        lineLimits = TextFieldLineLimits.SingleLine,
        label = { Text("Label") },
        trailingIcon = {
            ExposedDropdownMenuDefaults.TrailingIcon(
                expanded = expanded,
                // For editable fields, make the trailing icon a `menuAnchor` of type `SecondaryEditable`
                // This provides better accessibility for choosing options without typing
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable)
            )
        },
        colors = ExposedDropdownMenuDefaults.textFieldColors()
    )
    ExposedDropdownMenu(
        modifier = Modifier.heightIn(max = 280.dp),
        expanded = expanded,
        onDismissRequest = { setExpanded(false) }
    ) {
        filteredOptions.forEach { option ->
            DropdownMenuItem(
                text = { Text(option, style = MaterialTheme.typography.bodyLarge) },
                onClick = {
                    textFieldState.setTextAndPlaceCursorAtEnd(option)
                    setExpanded(false)
                },
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
            )
        }
    }
}
```kotlin

#### ExposedDropdownMenuBox Parameters

- **expanded**: Boolean - Whether the menu is expanded or not
- **onExpandedChange**: (Boolean) -> Unit - Called when the exposed dropdown menu is clicked and expansion state changes
- **modifier**: Modifier - Modifier to be applied to this ExposedDropdownMenuBox
- **content**: @Composable ExposedDropdownMenuBoxScope.() -> Unit - Content of this ExposedDropdownMenuBox (typically a TextField and ExposedDropdownMenu)

**Important Notes:**
- The `menuAnchor` modifier must be passed to the text field
- Use `PrimaryNotEditable` anchor type for read-only text fields
- Use `PrimaryEditable` anchor type for editable text fields
- For editable fields, use `SecondaryEditable` anchor type on the trailing icon for better accessibility

### ExtendedFloatingActionButton Component

Extended FABs help people take primary actions. They're wider than standard FABs to accommodate a text label and provide a larger target area.

#### ExtendedFloatingActionButton with Text Only

```kotlin
import androidx.compose.material3.*

ExtendedFloatingActionButton(
    onClick = { /* do something */ }
) {
    Text(text = "Extended FAB")
}
```kotlin

#### ExtendedFloatingActionButton with Icon and Text

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*

ExtendedFloatingActionButton(
    onClick = { /* do something */ },
    icon = { Icon(Icons.Filled.Add, "Localized description") },
    text = { Text(text = "Extended FAB") }
)
```kotlin

#### Collapsible ExtendedFloatingActionButton

The FAB can collapse to show only the icon when scrolling:

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val listState = rememberLazyListState()

// The FAB is initially expanded. Once the first visible item is past the first item,
// we collapse the FAB. We use a remembered derived state to minimize unnecessary compositions.
val expandedFab by remember {
    derivedStateOf { listState.firstVisibleItemIndex == 0 }
}

Scaffold(
    floatingActionButton = {
        ExtendedFloatingActionButton(
            onClick = { /* do something */ },
            expanded = expandedFab,
            icon = { Icon(Icons.Filled.Add, "Localized Description") },
            text = { Text(text = "Extended FAB") }
        )
    },
    floatingActionButtonPosition = FabPosition.End
) { padding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        state = listState
    ) {
        items(100) {
            Text("Item $it", modifier = Modifier.padding(16.dp))
        }
    }
}
```kotlin

#### ExtendedFloatingActionButton Parameters

**Text-only variant:**
- **onClick**: () -> Unit - Called when this FAB is clicked
- **modifier**: Modifier - Modifier to be applied to this FAB
- **shape**: Shape - Defines the shape of this FAB's container and shadow
- **containerColor**: Color - Background color of this FAB
- **contentColor**: Color - Preferred color for content inside this FAB
- **elevation**: FloatingActionButtonElevation - Controls the size of the shadow below the FAB
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions
- **content**: @Composable RowScope.() -> Unit - Content of this FAB (typically a Text label)

**Icon and text variant:**
- **text**: @Composable () -> Unit - Text label for this FAB
- **icon**: @Composable () -> Unit - Icon for this FAB
- **onClick**: () -> Unit - Called when this FAB is clicked
- **modifier**: Modifier - Modifier to be applied to this FAB
- **expanded**: Boolean - Whether the FAB is expanded (showing text) or collapsed (showing only icon)
- **shape**: Shape - Defines the shape of this FAB's container and shadow
- **containerColor**: Color - Background color of this FAB
- **contentColor**: Color - Preferred color for content inside this FAB
- **elevation**: FloatingActionButtonElevation - Controls the size of the shadow below the FAB
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions


### FilledTonalIconButton Component

The FilledTonalIconButton is a medium-emphasis icon button that provides a middle ground between FilledIconButton and OutlinedIconButton. Use it in contexts where a lower-priority icon button requires slightly more emphasis than an outline would provide.

#### Standard FilledTonalIconButton

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.remember

val description = "Localized description"

// Icon button should have a tooltip for accessibility
TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    tooltip = { PlainTooltip { Text(description) } },
    state = rememberTooltipState()
) {
    FilledTonalIconButton(onClick = { /* doSomething() */ }) {
        Icon(Icons.Filled.Lock, contentDescription = description)
    }
}
```kotlin

#### FilledTonalIconButton with Shape Morphing (Expressive)

```kotlin
import androidx.compose.material3.*

FilledTonalIconButton(
    onClick = { /* doSomething() */ },
    shapes = IconButtonDefaults.shapes()
) {
    Icon(Icons.Filled.Lock, contentDescription = "Localized description")
}
```kotlin

#### FilledTonalIconButton Guidelines

- Content should typically be an Icon (24 x 24 dp)
- Overall minimum touch target size is 48 x 48 dp for accessibility
- Always include a tooltip for accessibility
- Use for supplementary actions in toolbars or image lists

#### FilledTonalIconButton Parameters

- **onClick**: () -> Unit - Called when this icon button is clicked
- **modifier**: Modifier - Modifier to be applied to this icon button
- **enabled**: Boolean - Controls the enabled state of this icon button
- **shape/shapes**: Shape/IconButtonShapes - Defines the shape(s) of this icon button's container
- **colors**: IconButtonColors - Colors used for this icon button in different states
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions
- **content**: @Composable () -> Unit - Content of this icon button (typically an Icon)

### FilledTonalIconToggleButton Component

The FilledTonalIconToggleButton is a toggleable version of the FilledTonalIconButton. It provides medium emphasis and can be toggled on or off.

#### Standard FilledTonalIconToggleButton

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*

var checked by remember { mutableStateOf(false) }
val description = "Localized description"

TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    tooltip = { PlainTooltip { Text(description) } },
    state = rememberTooltipState()
) {
    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = { checked = it }
    ) {
        if (checked) {
            Icon(Icons.Filled.Lock, contentDescription = description)
        } else {
            Icon(Icons.Outlined.Lock, contentDescription = description)
        }
    }
}
```kotlin

#### FilledTonalIconToggleButton with Shape Morphing (Expressive)

```kotlin
import androidx.compose.material3.*

var checked by remember { mutableStateOf(false) }

FilledTonalIconToggleButton(
    checked = checked,
    onCheckedChange = { checked = it },
    shapes = IconButtonDefaults.toggleableShapes()
) {
    if (checked) {
        Icon(Icons.Filled.Lock, contentDescription = "Localized description")
    } else {
        Icon(Icons.Outlined.Lock, contentDescription = "Localized description")
    }
}
```kotlin

#### FilledTonalIconToggleButton Parameters

- **checked**: Boolean - Whether this icon button is toggled on or off
- **onCheckedChange**: (Boolean) -> Unit - Called when this icon button is clicked
- **modifier**: Modifier - Modifier to be applied to this icon button
- **enabled**: Boolean - Controls the enabled state of this icon button
- **shape/shapes**: Shape/IconToggleButtonShapes - Defines the shape(s) of this icon button's container
- **colors**: IconToggleButtonColors - Colors used for this icon button in different states
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions
- **content**: @Composable () -> Unit - Content of this icon button (typically an Icon)

### FilterChip Component

Filter chips use tags or descriptive words to filter content. They're a good alternative to toggle buttons or checkboxes. The FilterChip applies a flat style; for an elevated style, use ElevatedFilterChip.

Tapping on a filter chip toggles its selection state. A selection state leading icon (e.g., a checkmark) can be provided to indicate selection.

#### FilterChip with Selection Icon

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

var selected by remember { mutableStateOf(false) }

FilterChip(
    selected = selected,
    onClick = { selected = !selected },
    label = { Text("Filter chip") },
    leadingIcon = if (selected) {
        {
            Icon(
                imageVector = Icons.Filled.Done,
                contentDescription = "Localized Description",
                modifier = Modifier.size(FilterChipDefaults.IconSize)
            )
        }
    } else {
        null
    }
)
```kotlin

#### FilterChip with Leading and Selected Icons

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*

var selected by remember { mutableStateOf(false) }

FilterChip(
    selected = selected,
    onClick = { selected = !selected },
    label = { Text("Filter chip") },
    leadingIcon = if (selected) {
        {
            Icon(
                imageVector = Icons.Filled.Done,
                contentDescription = "Localized Description",
                modifier = Modifier.size(FilterChipDefaults.IconSize)
            )
        }
    } else {
        {
            Icon(
                imageVector = Icons.Filled.Home,
                contentDescription = "Localized description",
                modifier = Modifier.size(FilterChipDefaults.IconSize)
            )
        }
    }
)
```kotlin

#### FilterChip with Leading and Trailing Icons

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*

var selected by remember { mutableStateOf(false) }

FilterChip(
    selected = selected,
    onClick = { selected = !selected },
    label = { Text("Filter chip") },
    leadingIcon = if (selected) {
        {
            Icon(
                imageVector = Icons.Filled.Done,
                contentDescription = "Localized Description",
                modifier = Modifier.size(FilterChipDefaults.IconSize)
            )
        }
    } else {
        null
    },
    trailingIcon = {
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = "Localized Description",
            modifier = Modifier.size(FilterChipDefaults.IconSize)
        )
    }
)
```kotlin

#### FilterChip Parameters

- **selected**: Boolean - Whether this chip is selected or not
- **onClick**: () -> Unit - Called when this chip is clicked
- **label**: @Composable () -> Unit - Text label for this chip
- **modifier**: Modifier - Modifier to be applied to this chip
- **enabled**: Boolean - Controls the enabled state of this chip
- **leadingIcon**: @Composable (() -> Unit)? - Optional icon at the start (may indicate selection when selected is true)
- **trailingIcon**: @Composable (() -> Unit)? - Optional icon at the end of the chip
- **shape**: Shape - Defines the shape of this chip's container, border, and shadow
- **colors**: SelectableChipColors - Colors used for this chip in different states
- **elevation**: SelectableChipElevation? - Controls the size of the shadow below the chip
- **border**: BorderStroke? - Border to draw around the container of this chip
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions

### FlexibleBottomAppBar Component (Expressive)

The FlexibleBottomAppBar displays navigation and key actions at the bottom of small screens. This variation provides more flexibility in controlling the bar's expanded height and content arrangement.

#### FlexibleBottomAppBar with Custom Arrangement

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp

val icons = listOf(
    Icons.AutoMirrored.Filled.ArrowBack,
    Icons.AutoMirrored.Filled.ArrowForward,
    Icons.Filled.Add,
    Icons.Filled.Check,
    Icons.Filled.Edit,
    Icons.Filled.Favorite
)
val items = listOf("Back", "Forward", "Add", "Check", "Edit", "Favorite")

FlexibleBottomAppBar(
    contentPadding = PaddingValues(horizontal = 96.dp),
    horizontalArrangement = BottomAppBarDefaults.FlexibleFixedHorizontalArrangement
) {
    icons.forEachIndexed { index, icon ->
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(items[index]) } },
            state = rememberTooltipState()
        ) {
            IconButton(onClick = { /* Handle action */ }) {
                Icon(icon, contentDescription = items[index])
            }
        }
    }
}
```kotlin

#### FlexibleBottomAppBar Parameters

- **modifier**: Modifier - Modifier to be applied to this bottom app bar
- **containerColor**: Color - Background color of this bottom app bar
- **contentColor**: Color - Preferred color for content inside this bottom app bar
- **contentPadding**: PaddingValues - Padding applied to the content
- **horizontalArrangement**: Arrangement.Horizontal - Horizontal arrangement of the content
- **expandedHeight**: Dp - Height of the bar when expanded
- **windowInsets**: WindowInsets - Window insets for the bottom app bar
- **scrollBehavior**: BottomAppBarScrollBehavior? - Scroll behavior for nested scrolling
- **content**: @Composable RowScope.() -> Unit - Content of this bottom app bar

**Key Features:**
- Customizable horizontal arrangement for content layout
- Flexible expanded height control
- Scroll behavior support for nested scrolling
- Ideal for displaying multiple navigation and action items


### HorizontalFloatingToolbar

The HorizontalFloatingToolbar is an expressive component that combines a floating action button with a horizontal toolbar containing additional actions. It can expand and collapse based on user interaction or scroll behavior.

#### Basic HorizontalFloatingToolbar

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

var expanded by remember { mutableStateOf(false) }

HorizontalFloatingToolbar(
    expanded = expanded,
    floatingActionButton = {
        FloatingToolbarDefaults.VibrantFloatingActionButton(
            onClick = { /* Handle FAB action */ }
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add")
        }
    },
    content = {
        IconButton(
            onClick = { /* doSomething() */ },
            Modifier.focusProperties { canFocus = expanded }
        ) {
            Icon(Icons.Filled.Person, contentDescription = "Person")
        }
        IconButton(
            onClick = { /* doSomething() */ },
            Modifier.focusProperties { canFocus = expanded }
        ) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit")
        }
        IconButton(
            onClick = { /* doSomething() */ },
            Modifier.focusProperties { canFocus = expanded }
        ) {
            Icon(Icons.Filled.Favorite, contentDescription = "Favorite")
        }
    }
)
```kotlin

#### HorizontalFloatingToolbar with Scroll Behavior

The toolbar can automatically expand and collapse based on scroll behavior:

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

var expanded by remember { mutableStateOf(false) }

Scaffold(
    floatingActionButton = {
        HorizontalFloatingToolbar(
            expanded = expanded,
            floatingActionButton = {
                FloatingToolbarDefaults.VibrantFloatingActionButton(
                    onClick = { /* Handle action */ }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                }
            },
            content = {
                IconButton(
                    onClick = { /* doSomething() */ },
                    Modifier.focusProperties { canFocus = expanded }
                ) {
                    Icon(Icons.Filled.Person, contentDescription = "Person")
                }
                IconButton(
                    onClick = { /* doSomething() */ },
                    Modifier.focusProperties { canFocus = expanded }
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit")
                }
            }
        )
    },
    floatingActionButtonPosition = FabPosition.End
) { innerPadding ->
    Box(Modifier.padding(innerPadding)) {
        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
                .then(
                    Modifier.floatingToolbarVerticalNestedScroll(
                        expanded = expanded,
                        onExpand = { expanded = true },
                        onCollapse = { expanded = false }
                    )
                )
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = "Scrollable content here")
        }
    }
}
```kotlin

#### HorizontalFloatingToolbar Parameters

- **expanded**: Boolean - Whether the toolbar is expanded or not. In its expanded state, the FAB and toolbar content are organized horizontally. Otherwise, only the FAB is visible. The toolbar stays expanded when accessibility services like TalkBack are active.
- **floatingActionButton**: @Composable () -> Unit - A floating action button to be displayed by the toolbar. Use FloatingToolbarDefaults.VibrantFloatingActionButton or FloatingToolbarDefaults.StandardFloatingActionButton for proper styling.
- **modifier**: Modifier - Modifier to be applied to this floating toolbar
- **colors**: FloatingToolbarColors - Colors used for this floating toolbar. Use FloatingToolbarDefaults.standardFloatingToolbarColors or FloatingToolbarDefaults.vibrantFloatingToolbarColors
- **contentPadding**: PaddingValues - Padding applied to the content
- **scrollBehavior**: FloatingToolbarScrollBehavior? - If provided, the toolbar will automatically react to scrolling. Best used when the toolbar is positioned along a center edge of the screen.
- **shape**: Shape - Shape used for this floating toolbar content
- **floatingActionButtonPosition**: FloatingToolbarHorizontalFabPosition - Position of the FAB (End or Start)
- **animationSpec**: FiniteAnimationSpec<Float> - Animation spec for expand and collapse animation
- **expandedShadowElevation**: Dp - Shadow elevation when expanded
- **collapsedShadowElevation**: Dp - Shadow elevation when collapsed
- **content**: @Composable RowScope.() -> Unit - Main content of this floating toolbar (laid out horizontally in a Row)

**Key Features:**
- Combines FAB with horizontal toolbar actions
- Automatic expand/collapse based on scroll behavior
- Accessibility-aware (stays expanded with TalkBack)
- Customizable colors and styling
- Smooth animations between states

---

## Icon Component

The Icon component displays vector graphics, bitmaps, or painter-based images with optional tinting. It's designed for single-color icons that can be tinted to match the component they're placed in.

### Icon with ImageVector

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*

Icon(
    imageVector = Icons.Filled.Lock,
    contentDescription = "Localized description",
    tint = LocalContentColor.current
)
```kotlin

### Icon with ImageBitmap

```kotlin
import androidx.compose.material3.*
import androidx.compose.ui.graphics.ImageBitmap

Icon(
    bitmap = myImageBitmap,
    contentDescription = "Localized description",
    tint = LocalContentColor.current
)
```kotlin

### Icon with Painter

```kotlin
import androidx.compose.material3.*
import androidx.compose.ui.graphics.painter.Painter

Icon(
    painter = myPainter,
    contentDescription = "Localized description",
    tint = LocalContentColor.current
)
```kotlin

### Icon with ColorProducer

For dynamic color changes without recomposition:

```kotlin
import androidx.compose.material3.*
import androidx.compose.ui.graphics.painter.Painter

Icon(
    painter = myPainter,
    tint = { Color.Red }, // ColorProducer lambda
    contentDescription = "Localized description"
)
```kotlin

#### Icon Parameters

- **imageVector/bitmap/painter**: The graphic to draw inside this icon
- **contentDescription**: String? - Text used by accessibility services. Always provide this unless the icon is purely decorative. Should be localized.
- **modifier**: Modifier - Modifier to be applied to this icon
- **tint**: Color or ColorProducer? - Tint to be applied. Use Color.Unspecified or null for no tint (multicolored icons).

**Best Practices:**
- Always provide contentDescription for meaningful icons
- Use Color.Unspecified for multicolored icons that shouldn't be tinted
- For generic images, use androidx.compose.foundation.Image instead
- For clickable icons, use IconButton instead
- Default size is 24 x 24 dp for icons

---

## IconButton Component

IconButton provides a clickable button optimized for displaying icons. It's commonly used in toolbars, app bars, and image lists where a compact button is needed.

### Standard IconButton

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*

TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    tooltip = { PlainTooltip { Text("Lock") } },
    state = rememberTooltipState()
) {
    IconButton(onClick = { /* doSomething() */ }) {
        Icon(Icons.Filled.Lock, contentDescription = "Lock")
    }
}
```kotlin

### IconButton with Color Tint

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter

IconButton(onClick = { /* doSomething() */ }) {
    Icon(
        rememberVectorPainter(image = Icons.Filled.Lock),
        contentDescription = "Lock",
        tint = Color.Red
    )
}
```kotlin

### Small Narrow Round IconButton

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*

FilledIconButton(
    onClick = { /* doSomething() */ },
    modifier = Modifier
        .minimumInteractiveComponentSize()
        .size(
            IconButtonDefaults.extraSmallContainerSize(
                IconButtonDefaults.IconButtonWidthOption.Narrow
            )
        ),
    shape = IconButtonDefaults.extraSmallSquareShape
) {
    Icon(
        Icons.Filled.Lock,
        contentDescription = "Lock",
        modifier = Modifier.size(IconButtonDefaults.extraSmallIconSize)
    )
}
```kotlin

### Medium Round-Shaped IconButton

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*

IconButton(
    onClick = { /* doSomething() */ },
    modifier = Modifier.size(
        IconButtonDefaults.mediumContainerSize(
            IconButtonDefaults.IconButtonWidthOption.Wide
        )
    ),
    shape = IconButtonDefaults.mediumRoundShape
) {
    Icon(
        Icons.Filled.Lock,
        contentDescription = "Lock",
        modifier = Modifier.size(IconButtonDefaults.mediumIconSize)
    )
}
```kotlin

### Expressive IconButton with Shape Morphing

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*

IconButton(
    onClick = { /* doSomething() */ },
    shapes = IconButtonDefaults.shapes()
) {
    Icon(Icons.Filled.Lock, contentDescription = "Lock")
}
```kotlin

#### IconButton Parameters

- **onClick**: () -> Unit - Called when this icon button is clicked
- **modifier**: Modifier - Modifier to be applied to this icon button
- **enabled**: Boolean - Controls the enabled state. When false, the button won't respond to input and appears visually disabled.
- **colors**: IconButtonColors - Colors used for this icon button in different states. See IconButtonDefaults.iconButtonColors and IconButtonDefaults.iconButtonVibrantColors
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions
- **shape**: Shape - Shape of this icon button (standard version)
- **shapes**: IconButtonShapes - Shapes that the button will morph between based on user interaction (expressive version)
- **content**: @Composable () -> Unit - Content of this icon button, typically an Icon

**Key Features:**
- Minimum touch target size of 48 x 48 dp for accessibility
- Typical internal icon size is 24 x 24 dp
- Should always have an associated tooltip for accessibility
- Supports shape morphing in expressive variant
- Multiple size variants (extra small, small, medium, large)

---

## IconToggleButton Component

IconToggleButton is a toggleable icon button that switches between two states (checked/unchecked). It's useful for actions like favorites, bookmarks, or any binary state.

### Standard IconToggleButton

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*

var checked by remember { mutableStateOf(false) }

TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    tooltip = { PlainTooltip { Text("Lock") } },
    state = rememberTooltipState()
) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = { checked = it }
    ) {
        if (checked) {
            Icon(Icons.Filled.Lock, contentDescription = "Locked")
        } else {
            Icon(Icons.Outlined.Lock, contentDescription = "Unlocked")
        }
    }
}
```kotlin

### Expressive IconToggleButton with Shape Morphing

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*

var checked by remember { mutableStateOf(false) }

IconToggleButton(
    checked = checked,
    onCheckedChange = { checked = it },
    shapes = IconButtonDefaults.toggleableShapes()
) {
    if (checked) {
        Icon(Icons.Filled.Lock, contentDescription = "Locked")
    } else {
        Icon(Icons.Outlined.Lock, contentDescription = "Unlocked")
    }
}
```kotlin

#### IconToggleButton Parameters

- **checked**: Boolean - Whether this button is toggled on or off
- **onCheckedChange**: (Boolean) -> Unit - Called when this icon button is clicked
- **modifier**: Modifier - Modifier to be applied to this icon button
- **enabled**: Boolean - Controls the enabled state
- **colors**: IconToggleButtonColors - Colors used for this icon button in different states. See IconButtonDefaults.iconToggleButtonColors and IconButtonDefaults.iconToggleButtonVibrantColors
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions
- **shape**: Shape - Shape of this icon button (standard version)
- **shapes**: IconToggleButtonShapes - Shapes that the button will morph between (expressive version)
- **content**: @Composable () -> Unit - Content of this icon button, typically an Icon that changes based on checked state

**Best Practices:**
- Use different icons for checked and unchecked states (e.g., filled vs outlined)
- Always provide appropriate content descriptions for both states
- Include tooltips for accessibility
- Minimum touch target size of 48 x 48 dp


---

## Label Component

The Label component is used to attach a tooltip or label to another composable, typically used with sliders to show the current value.

### Label with RangeSlider

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val rangeSliderState = rememberRangeSliderState(
    0f,
    100f,
    valueRange = 0f..100f,
    onValueChangeFinished = {
        // Handle value change completion
    }
)
val startInteractionSource = remember { MutableInteractionSource() }
val endInteractionSource = remember { MutableInteractionSource() }
val startThumbAndTrackColors = SliderDefaults.colors(
    thumbColor = Color.Blue,
    activeTrackColor = Color.Red
)
val endThumbColors = SliderDefaults.colors(thumbColor = Color.Green)

Column(modifier = Modifier.padding(horizontal = 16.dp)) {
    RangeSlider(
        state = rangeSliderState,
        startInteractionSource = startInteractionSource,
        endInteractionSource = endInteractionSource,
        startThumb = {
            Label(
                label = {
                    PlainTooltip(
                        modifier = Modifier.sizeIn(45.dp, 25.dp).wrapContentWidth()
                    ) {
                        Text("%.2f".format(rangeSliderState.activeRangeStart))
                    }
                },
                interactionSource = startInteractionSource
            ) {
                SliderDefaults.Thumb(
                    interactionSource = startInteractionSource,
                    colors = startThumbAndTrackColors
                )
            }
        },
        endThumb = {
            Label(
                label = {
                    PlainTooltip(
                        modifier = Modifier.requiredSize(45.dp, 25.dp).wrapContentWidth()
                    ) {
                        Text("%.2f".format(rangeSliderState.activeRangeEnd))
                    }
                },
                interactionSource = endInteractionSource
            ) {
                SliderDefaults.Thumb(
                    interactionSource = endInteractionSource,
                    colors = endThumbColors
                )
            }
        },
        track = { rangeSliderState ->
            SliderDefaults.Track(
                colors = startThumbAndTrackColors,
                rangeSliderState = rangeSliderState
            )
        }
    )
}
```kotlin

#### Label Parameters

- **label**: @Composable TooltipScope.() -> Unit - Composable that will be appended to content
- **modifier**: Modifier - Modifier that will be applied to content
- **interactionSource**: MutableInteractionSource? - The interaction source representing the stream of interactions for the content
- **isPersistent**: Boolean - If true, the label will always show and be anchored to content. If false, the label will only show when pressing down or hovering over the content.
- **content**: @Composable () -> Unit - The composable that label will anchor to

**Key Features:**
- Anchors tooltips to interactive components
- Supports persistent and on-demand display modes
- Commonly used with sliders to show current values
- Integrates with interaction sources for responsive behavior

---

## LargeExtendedFloatingActionButton

Large extended FABs provide more prominent primary actions with text labels and optional icons. They're wider than standard FABs to accommodate text and provide a larger target area.

### LargeExtendedFloatingActionButton (Text Only)

```kotlin
import androidx.compose.material3.*

LargeExtendedFloatingActionButton(
    onClick = { /* do something */ }
) {
    Text(text = "Large Extended FAB")
}
```kotlin

### LargeExtendedFloatingActionButton with Icon

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.ui.Modifier

LargeExtendedFloatingActionButton(
    onClick = { /* do something */ },
    icon = {
        Icon(
            Icons.Filled.Add,
            "Localized description",
            modifier = Modifier.size(FloatingActionButtonDefaults.LargeIconSize)
        )
    },
    text = { Text(text = "Large Extended FAB") }
)
```kotlin

### Collapsible LargeExtendedFloatingActionButton

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val listState = rememberLazyListState()
val expandedFab by remember {
    derivedStateOf { listState.firstVisibleItemIndex == 0 }
}

Scaffold(
    floatingActionButton = {
        LargeExtendedFloatingActionButton(
            onClick = { /* do something */ },
            expanded = expandedFab,
            icon = {
                Icon(
                    Icons.Filled.Add,
                    "Localized Description",
                    modifier = Modifier.size(FloatingActionButtonDefaults.LargeIconSize)
                )
            },
            text = { Text(text = "Large Extended FAB") }
        )
    },
    floatingActionButtonPosition = FabPosition.End
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(100) { index ->
            Text(
                text = "List item - $index",
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}
```kotlin

#### LargeExtendedFloatingActionButton Parameters

- **onClick**: () -> Unit - Called when this FAB is clicked
- **modifier**: Modifier - Modifier to be applied to this FAB
- **text**: @Composable () -> Unit - Text label displayed inside this FAB
- **icon**: @Composable () -> Unit - Icon for this FAB, typically an Icon
- **expanded**: Boolean - Controls the expansion state. When true, shows both icon and text. When false, shows only the icon.
- **shape**: Shape - Defines the shape of this FAB's container and shadow
- **containerColor**: Color - Background color. Use Color.Transparent for no color.
- **contentColor**: Color - Preferred color for content inside this FAB
- **elevation**: FloatingActionButtonElevation - Controls the shadow size below the FAB
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions

**Best Practices:**
- Use for the most important action on a screen
- Collapse when scrolling to save space
- Provide clear, concise text labels
- Icon size should be FloatingActionButtonDefaults.LargeIconSize

---

## LargeFlexibleTopAppBar

The LargeFlexibleTopAppBar is an expressive top app bar that provides flexible layout options with support for both title and subtitle, with customizable horizontal alignment.

### LargeFlexibleTopAppBar with Subtitle

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
        LargeFlexibleTopAppBar(
            title = {
                Text(
                    "Large TopAppBar",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            subtitle = {
                Text(
                    "Subtitle",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            titleHorizontalAlignment = Alignment.CenterHorizontally,
            navigationIcon = {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = { PlainTooltip { Text("Menu") } },
                    state = rememberTooltipState()
                ) {
                    IconButton(onClick = { /* doSomething() */ }) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "Menu"
                        )
                    }
                }
            },
            actions = {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = { PlainTooltip { Text("Add to favorites") } },
                    state = rememberTooltipState()
                ) {
                    IconButton(onClick = { /* doSomething() */ }) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Add to favorites"
                        )
                    }
                }
            },
            scrollBehavior = scrollBehavior
        )
    },
    content = { innerPadding ->
        LazyColumn(
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(75) {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
        }
    }
)
```kotlin

#### LargeFlexibleTopAppBar Parameters

- **title**: @Composable () -> Unit - Title displayed in the top app bar (used in both expanded and collapsed states)
- **modifier**: Modifier - Modifier to be applied to this top app bar
- **subtitle**: (@Composable () -> Unit)? - Optional subtitle displayed in the top app bar
- **navigationIcon**: @Composable () -> Unit - Navigation icon at the start (typically IconButton or IconToggleButton)
- **actions**: @Composable RowScope.() -> Unit - Actions at the end (typically IconButtons)
- **titleHorizontalAlignment**: Alignment.Horizontal - Horizontal alignment of the title and subtitle
- **collapsedHeight**: Dp - Height when collapsed by scrollBehavior
- **expandedHeight**: Dp - Maximum height when expanded
- **windowInsets**: WindowInsets - Window insets that app bar will respect
- **colors**: TopAppBarColors - Colors used for this top app bar in different states
- **scrollBehavior**: TopAppBarScrollBehavior? - Controls offset values for height and colors based on scrolling

**Key Features:**
- Supports both title and subtitle
- Customizable horizontal alignment (Start, Center, End)
- Smooth collapse/expand animations with scroll behavior
- Flexible height control
- Material Design 3 Expressive styling

---

## LargeFloatingActionButton

Large FABs represent the most important action on a screen with increased visual prominence through larger size.

### Basic LargeFloatingActionButton

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.ui.Modifier

LargeFloatingActionButton(
    onClick = { /* do something */ }
) {
    Icon(
        Icons.Filled.Add,
        contentDescription = "Localized description",
        modifier = Modifier.size(FloatingActionButtonDefaults.LargeIconSize)
    )
}
```kotlin

### Animated Show/Hide LargeFloatingActionButton

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val listState = rememberLazyListState()
val fabVisible by remember {
    derivedStateOf { listState.firstVisibleItemIndex == 0 }
}

Scaffold(
    floatingActionButton = {
        MediumFloatingActionButton(
            modifier = Modifier.animateFloatingActionButton(
                visible = fabVisible,
                alignment = Alignment.BottomEnd
            ),
            onClick = { /* do something */ }
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Localized description",
                modifier = Modifier.size(FloatingActionButtonDefaults.MediumIconSize)
            )
        }
    },
    floatingActionButtonPosition = FabPosition.End
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(100) { index ->
            Text(
                text = "List item - $index",
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}
```kotlin

#### LargeFloatingActionButton Parameters

- **onClick**: () -> Unit - Called when this FAB is clicked
- **modifier**: Modifier - Modifier to be applied to this FAB
- **shape**: Shape - Defines the shape of this FAB's container and shadow
- **containerColor**: Color - Background color
- **contentColor**: Color - Preferred color for content inside this FAB
- **elevation**: FloatingActionButtonElevation - Controls the shadow size
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions
- **content**: @Composable () -> Unit - Content of this FAB, typically an Icon

**Best Practices:**
- Use for the most important action on a screen
- Icon size should be FloatingActionButtonDefaults.LargeIconSize
- Can be animated to show/hide based on scroll state
- Provides larger touch target for improved accessibility


---

## LargeTopAppBar

The LargeTopAppBar is a standard Material Design large top app bar that displays information and actions at the top of a screen with an expanded title area.

### LargeTopAppBar with Scroll Behavior

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
        LargeTopAppBar(
            title = {
                Text(
                    "Large TopAppBar",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = { PlainTooltip { Text("Menu") } },
                    state = rememberTooltipState()
                ) {
                    IconButton(onClick = { /* doSomething() */

-
--

## LinearWavyProgressIndicator

The LinearWavyProgressIndicator is an expressive progress indicator that uses a wavy animation to show progress. It provides a more dynamic and engaging visual feedback compared to standard linear progress indicators.

### Determinate LinearWavyProgressIndicator

```kotlin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
i
mport androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

var progress by remember { mutableFloatStateOf(0.1f) }
val animatedProgress by animateFloatAsState(
    targetValue = progress,
    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
)

Column(horizontalAlignment = Alignment.CenterHorizontally) {
    LinearWavyProgressIndicator(progress = { animatedProgress })
    Spacer(Modifier.requiredHeight(30.dp))
    Text("Set progress:")
    Slider(
        modifier = Modifier.width(300.dp),
        value = progress,
        valueRange = 0f..1f,
        onValueChange = { progress = it }
    )
}
```

### Thick LinearWavyProgressIndicator

```kotlin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

var progress by remember { mutableFloatStateOf(0.1f) }
val animatedProgress by animateFloatAsState(
    targetValue = progress,
    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
)

val thickStrokeWidth = with(LocalDensity.current) { 8.dp.toPx() }
val thickStroke = remember(thickStrokeWidth) {
    Stroke(width = thickStrokeWidth, cap = StrokeCap.Round)
}

Column(horizontalAlignment = Alignment.CenterHorizontally) {
    LinearWavyProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier.height(14.dp),
        stroke = thickStroke,
        trackStroke = thickStroke
    )
    Spacer(Modifier.requiredHeight(30.dp))
    Text("Set progress:")
    Slider(
        modifier = Modifier.width(300.dp),
        value = progress,
        valueRange = 0f..1f,
        onValueChange = { progress = it }
    )
}
```

#### LinearWavyProgressIndicator Parameters

- **progress**: Float - Progress value where 0.0 = no progress and 1.0 = full progress. Values are coerced into range.
- **modifier**: Modifier - Modifier to be applied to this progress indicator
- **color**: Color - Progress indicator color
- **trackColor**: Color - Track color, visible when progress hasn't reached that area yet
- **stroke**: Stroke - Stroke used to draw this indicator
- **trackStroke**: Stroke - Stroke used to draw the indicator's track
- **gapSize**: Dp - Gap between the track and progress parts
- **stopSize**: Dp - Size of the stop indicator at the end of the track (required if track contrast is below 3:1)
- **amplitude**: (progress: Float) -> Float - Lambda providing wave amplitude as a function of progress (0.0 = no amplitude, 1.0 = full height)
- **wavelength**: Dp - Length of a wave
- **waveSpeed**: Dp - Speed at which the wave moves (DP per second, defaults to wavelength)

**Key Features:**
- Expressive wavy animation for visual interest
- Customizable wave amplitude and wavelength
- Supports thick stroke variants
- Smooth progress animations

---

## ListItem Component

ListItem is a versatile component for displaying continuous, vertical indexes of text or images in lists. It supports one-line, two-line, and three-line layouts.

### One-Line ListItem

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*

Column {
    HorizontalDivider()
    ListItem(
        headlineContent = { Text("One line list item with 24x24 icon") },
        leadingContent = {
            Icon(Icons.Filled.Favorite, contentDescription = "Localized description")
        }
    )
    HorizontalDivider()
}
```

### Two-Line ListItem

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*

Column {
    HorizontalDivider()
    ListItem(
        headlineContent = { Text("Two line list item with trailing") },
        supportingContent = { Text("Secondary text") },
        trailingContent = { Text("meta") },
        leadingContent = {
            Icon(Icons.Filled.Favorite, contentDescription = "Localized description")
        }
    )
    HorizontalDivider()
}
```

### Three-Line ListItem with Overline and Supporting Content

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*

Column {
    HorizontalDivider()
    ListItem(
        headlineContent = { Text("Three line list item") },
        overlineContent = { Text("OVERLINE") },
        supportingContent = { Text("Secondary text") },
        leadingContent = {
            Icon(Icons.Filled.Favorite, contentDescription = "Localized description")
        },
        trailingContent = { Text("meta") }
    )
    HorizontalDivider()
}
```

### Three-Line ListItem with Extended Supporting Content

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*

Column {
    HorizontalDivider()
    ListItem(
        headlineContent = { Text("Three line list item") },
        supportingContent = { Text("Secondary text that\nspans multiple lines") },
        leadingContent = {
            Icon(Icons.Filled.Favorite, contentDescription = "Localized description")
        },
        trailingContent = { Text("meta") }
    )
    HorizontalDivider()
}
```

#### ListItem Parameters

- **headlineContent**: @Composable () -> Unit - The headline content of the list item
- **modifier**: Modifier - Modifier to be applied to the list item
- **overlineContent**: (@Composable () -> Unit)? - Content displayed above the headline
- **supportingContent**: (@Composable () -> Unit)? - Supporting content of the list item
- **leadingContent**: (@Composable () -> Unit)? - Leading content of the list item
- **trailingContent**: (@Composable () -> Unit)? - Trailing meta text, icon, switch or checkbox
- **colors**: ListItemColors - Colors for background and content in different states
- **tonalElevation**: Dp - Tonal elevation of this list item
- **shadowElevation**: Dp - Shadow elevation of this list item

**Best Practices:**
- One-line items: headline only
- Two-line items: headline + (supporting OR overline)
- Three-line items: headline + (supporting + overline) OR (extended supporting text)
- Use HorizontalDivider between items for visual separation

---

## LoadingIndicator Component

The LoadingIndicator is an expressive Material Design component that morphs between various shapes to indicate loading state. It provides a more dynamic alternative to traditional circular progress indicators.

### Indeterminate LoadingIndicator

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LoadingIndicator
import androidx.compose.ui.Alignment

Column(horizontalAlignment = Alignment.CenterHorizontally) {
    LoadingIndicator()
}
```

### Determinate LoadingIndicator

```kotlin
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

var progress by remember { mutableFloatStateOf(0f) }
val animatedProgress by animateFloatAsState(
    targetValue = progress,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessVeryLow,
        visibilityThreshold = 1 / 1000f
    )
)

Column(horizontalAlignment = Alignment.CenterHorizontally) {
    LoadingIndicator(progress = { animatedProgress })
    Spacer(Modifier.requiredHeight(30.dp))
    Text("Set loading progress:")
    Slider(
        modifier = Modifier.width(300.dp),
        value = progress,
        valueRange = 0f..1f,
        onValueChange = { progress = it }
    )
}
```

#### LoadingIndicator Parameters

**Indeterminate version:**
- **modifier**: Modifier - Modifier to be applied to this loading indicator
- **color**: Color - Loading indicator's color
- **polygons**: List<RoundedPolygon> - List of shapes to morph between (requires at least 2 items)

**Determinate version:**
- **progress**: () -> Float - Progress value (0.0 to 1.0) that controls shape morphing
- **modifier**: Modifier - Modifier to be applied to this loading indicator
- **color**: Color - Loading indicator's color
- **polygons**: List<RoundedPolygon> - List of shapes to morph between as progress changes (requires at least 2 items)

**Key Features:**
- Morphs between custom polygon shapes
- Indeterminate version continuously animates
- Determinate version morphs based on progress value
- Expressive and engaging visual feedback
- Customizable shape sequences

---

## MaterialExpressiveTheme

MaterialExpressiveTheme is the theming component for Material Design 3 Expressive, allowing customization of color schemes, motion schemes, shapes, and typography to reflect your product's brand.

### Basic MaterialExpressiveTheme

```kotlin
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

val isDarkTheme = isSystemInDarkTheme()
val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

val darkColorScheme = darkColorScheme(primary = Color(0xFF66ffc7))

val colorScheme = when {
    supportsDynamicColor && isDarkTheme -> {
        dynamicDarkColorScheme(LocalContext.current)
    }
    supportsDynamicColor && !isDarkTheme -> {
        dynamicLightColorScheme(LocalContext.current)
    }
    isDarkTheme -> darkColorScheme
    else -> expressiveLightColorScheme()
}

val shapes = Shapes(largeIncreased = RoundedCornerShape(36.0.dp))

MaterialExpressiveTheme(colorScheme = colorScheme, shapes = shapes) {
    val currentTheme = if (!isSystemInDarkTheme()) "light" else "dark"
    ExtendedFloatingActionButton(
        text = { Text("FAB with expressive theme") },
        icon = { Icon(Icons.Filled.Favorite, contentDescription = "Favorite") },
        onClick = {}
    )
}
```

#### MaterialExpressiveTheme Parameters

- **colorScheme**: ColorScheme? - Complete definition of the Material Color theme
- **motionScheme**: MotionScheme? - Complete definition of the Material motion theme
- **shapes**: Shapes? - Set of corner shapes for the shape system
- **typography**: Typography? - Set of text styles for the typography system
- **content**: @Composable () -> Unit - Content inheriting this theme

**Key Features:**
- Supports Material Design 3 Expressive styling
- Includes motion scheme for expressive animations
- Dynamic color support on Android 12+
- Customizable shapes and typography
- Falls back to defaults for unset values

---

## MaterialTheme

MaterialTheme is the standard theming component for Material Design 3, providing customization of color schemes, shapes, and typography.

### MaterialTheme with Custom Colors and Typography

```kotlin
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val isDarkTheme = isSystemInDarkTheme()
val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

val lightColorScheme = lightColorScheme(primary = Color(0xFF1EB980))
val darkColorScheme = darkColorScheme(primary = Color(0xFF66ffc7))

val colorScheme = when {
    supportsDynamicColor && isDarkTheme -> dynamicDarkColorScheme(LocalContext.current)
    supportsDynamicColor && !isDarkTheme -> dynamicLightColorScheme(LocalContext.current)
    isDarkTheme -> darkColorScheme
    else -> lightColorScheme
}

val typography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.W100, fontSize = 96.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.W600, fontSize = 14.sp)
)

val shapes = Shapes(
    extraSmall = RoundedCornerShape(3.0.dp),
    small = RoundedCornerShape(6.0.dp)
)

MaterialTheme(
    colorScheme = colorScheme,
    typography = typography,
    shapes = shapes
) {
    ExtendedFloatingActionButton(
        text = { Text("FAB with custom theme") },
        icon = { Icon(Icons.Filled.Favorite, contentDescription = "Favorite") },
        onClick = {}
    )
}
```

### MaterialTheme with Motion Scheme (Expressive)

```kotlin
import androidx.compose.material3.*

MaterialTheme(
    colorScheme = MaterialTheme.colorScheme,
    motionScheme = MaterialTheme.motionScheme,
    shapes = MaterialTheme.shapes,
    typography = MaterialTheme.typography
) {
    // Your content here
}
```

#### MaterialTheme Parameters

**Standard version:**
- **colorScheme**: ColorScheme - Complete definition of the Material Color theme
- **shapes**: Shapes - Set of corner shapes for the shape system
- **typography**: Typography - Set of text styles for the typography system
- **content**: @Composable () -> Unit - Content inheriting this theme

**Expressive version (with motion):**
- **colorScheme**: ColorScheme - Complete definition of the Material Color theme
- **motionScheme**: MotionScheme - Complete definition of the Material Motion scheme
- **shapes**: Shapes - Set of corner shapes for the shape system
- **typography**: Typography - Set of text styles for the typography system
- **content**: @Composable () -> Unit - Content inheriting this theme

**Best Practices:**
- Use MaterialTheme at the top of your application
- Create nested MaterialTheme instances for different screens/sections
- Override only the parts that need to change
- Inherit current values by default
- Support dynamic colors on Android 12+

---

## MediumExtendedFloatingActionButton

Medium extended FABs provide primary actions with text labels and optional icons, offering a balance between standard and large FABs.

### MediumExtendedFloatingActionButton (Text Only)

```kotlin
import androidx.compose.material3.*

MediumExtendedFloatingActionButton(
    onClick = { /* do something */ }
) {
    Text(text = "Medium Extended FAB")
}
```

### MediumExtendedFloatingActionButton with Icon

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.ui.Modifier

MediumExtendedFloatingActionButton(
    onClick = { /* do something */ },
    icon = {
        Icon(
            Icons.Filled.Add,
            "Localized description",
            modifier = Modifier.size(FloatingActionButtonDefaults.MediumIconSize)
        )
    },
    text = { Text(text = "Medium Extended FAB") }
)
```

### Collapsible MediumExtendedFloatingActionButton

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val listState = rememberLazyListState()
val expandedFab by remember {
    derivedStateOf { listState.firstVisibleItemIndex == 0 }
}

Scaffold(
    floatingActionButton = {
        MediumExtendedFloatingActionButton(
            onClick = { /* do something */ },
            expanded = expandedFab,
            icon = {
                Icon(
                    Icons.Filled.Add,
                    "Localized Description",
                    modifier = Modifier.size(FloatingActionButtonDefaults.MediumIconSize)
                )
            },
            text = { Text(text = "Medium Extended FAB") }
        )
    },
    floatingActionButtonPosition = FabPosition.End
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(100) { index ->
            Text(text = "List item - $index", modifier = Modifier.padding(24.dp))
        }
    }
}
```

#### MediumExtendedFloatingActionButton Parameters

- **onClick**: () -> Unit - Called when this FAB is clicked
- **modifier**: Modifier - Modifier to be applied to this FAB
- **text**: @Composable () -> Unit - Text label displayed inside this FAB
- **icon**: @Composable () -> Unit - Icon for this FAB
- **expanded**: Boolean - Controls expansion state (true = icon + text, false = icon only)
- **shape**: Shape - Defines the shape of this FAB's container and shadow
- **containerColor**: Color - Background color
- **contentColor**: Color - Preferred color for content
- **elevation**: FloatingActionButtonElevation - Controls shadow size
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions
- **content**: @Composable RowScope.() -> Unit - Content of this FAB (text-only version)

**Best Practices:**
- Use for important but not primary actions
- Collapse when scrolling to save space
- Icon size should be FloatingActionButtonDefaults.MediumIconSize
- Provides good balance between visibility and space efficiency

---

## MediumFlexibleTopAppBar

The MediumFlexibleTopAppBar is an expressive medium-sized top app bar with flexible layout options, supporting both title and subtitle with customizable alignment.

### MediumFlexibleTopAppBar with Subtitle

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
        MediumFlexibleTopAppBar(
            title = {
                Text("Medium TopAppBar", maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            subtitle = {
                Text("Subtitle", maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            titleHorizontalAlignment = Alignment.CenterHorizontally,
            navigationIcon = {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = { PlainTooltip { Text("Menu") } },
                    state = rememberTooltipState()
                ) {
                    IconButton(onClick = { /* doSomething() */ }) {
                        Icon(imageVector = Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
            },
            actions = {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = { PlainTooltip { Text("Add to favorites") } },
                    state = rememberTooltipState()
                ) {
                    IconButton(onClick = { /* doSomething() */ }) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Add to favorites"
                        )
                    }
                }
            },
            scrollBehavior = scrollBehavior
        )
    },
    content = { innerPadding ->
        LazyColumn(
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(75) {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
        }
    }
)
```

#### MediumFlexibleTopAppBar Parameters

- **title**: @Composable () -> Unit - Title displayed in the top app bar
- **modifier**: Modifier - Modifier to be applied to this top app bar
- **subtitle**: (@Composable () -> Unit)? - Optional subtitle
- **navigationIcon**: @Composable () -> Unit - Navigation icon at the start
- **actions**: @Composable RowScope.() -> Unit - Actions at the end
- **titleHorizontalAlignment**: Alignment.Horizontal - Horizontal alignment of title and subtitle
- **collapsedHeight**: Dp - Height when collapsed
- **expandedHeight**: Dp - Maximum height when expanded
- **windowInsets**: WindowInsets - Window insets that app bar will respect
- **colors**: TopAppBarColors - Colors used in different states
- **scrollBehavior**: TopAppBarScrollBehavior? - Controls offset values based on scrolling

**Key Features:**
- Medium-sized variant between standard and large
- Supports both title and subtitle
- Customizable horizontal alignment
- Smooth collapse/expand with scroll behavior
- Expressive Material Design 3 styling

---

## MediumFloatingActionButton

Medium FABs provide a middle ground between standard and large FABs, offering increased prominence without being as large as the large variant.

### Basic MediumFloatingActionButton

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.ui.Modifier

MediumFloatingActionButton(
    onClick = { /* do something */ }
) {
    Icon(
        Icons.Filled.Add,
        contentDescription = "Localized description",
        modifier = Modifier.size(FloatingActionButtonDefaults.MediumIconSize)
    )
}
```

### Animated Show/Hide MediumFloatingActionButton

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val listState = rememberLazyListState()
val fabVisible by remember {
    derivedStateOf { listState.firstVisibleItemIndex == 0 }
}

Scaffold(
    floatingActionButton = {
        MediumFloatingActionButton(
            modifier = Modifier.animateFloatingActionButton(
                visible = fabVisible,
                alignment = Alignment.BottomEnd
            ),
            onClick = { /* do something */ }
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Localized description",
                modifier = Modifier.size(FloatingActionButtonDefaults.MediumIconSize)
            )
        }
    },
    floatingActionButtonPosition = FabPosition.End
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(100) { index ->
            Text(text = "List item - $index", modifier = Modifier.padding(24.dp))
        }
    }
}
```

#### MediumFloatingActionButton Parameters

- **onClick**: () -> Unit - Called when this FAB is clicked
- **modifier**: Modifier - Modifier to be applied to this FAB
- **shape**: Shape - Defines the shape of this FAB's container and shadow
- **containerColor**: Color - Background color
- **contentColor**: Color - Preferred color for content
- **elevation**: FloatingActionButtonElevation - Controls shadow size
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions
- **content**: @Composable () -> Unit - Content of this FAB, typically an Icon

**Best Practices:**
- Use for important actions that need more prominence than standard FAB
- Icon size should be FloatingActionButtonDefaults.MediumIconSize
- Can be animated to show/hide based on scroll state
- Good balance between standard and large FAB sizes


---

## MediumTopAppBar

The MediumTopAppBar is a standard Material Design medium-sized top app bar that displays information and actions at the top of a screen with an expanded title area.

### MediumTopAppBar with Scroll Behavior

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
        MediumTopAppBar(
            title = {
                Text(
                    "Medium TopAppBar",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = { PlainTooltip { Text("Menu") } },
                    state = rememberTooltipState()
                ) {
                    IconButton(onClick = { /* doSomething() */ }) {
                        Icon(imageVector = Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
            },
            actions = {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = { PlainTooltip { Text("Add to favorites") } },
                    state = rememberTooltipState()
                ) {
                    IconButton(onClick = { /* doSomething() */ }) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Add to favorites"
                        )
                    }
                }
            },
            scrollBehavior = scrollBehavior
        )
    },
    content = { innerPadding ->
        LazyColumn(
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(75) {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
        }
    }
)
```

#### MediumTopAppBar Parameters

- **title**: @Composable () -> Unit - Title displayed in the top app bar (used in both expanded and collapsed states with different text sizes)
- **modifier**: Modifier - Modifier to be applied to this top app bar
- **navigationIcon**: @Composable () -> Unit - Navigation icon at the start (typically IconButton or IconToggleButton)
- **actions**: @Composable RowScope.() -> Unit - Actions at the end (typically IconButtons in a Row)
- **collapsedHeight**: Dp - Height when collapsed by scrollBehavior
- **expandedHeight**: Dp - Maximum height when expanded
- **windowInsets**: WindowInsets - Window insets that app bar will respect
- **colors**: TopAppBarColors - Colors used in different states
- **scrollBehavior**: TopAppBarScrollBehavior? - Controls offset values for height and colors based on scrolling

**Key Features:**
- Medium-sized variant between standard and large
- Title displayed in second row when expanded
- Smooth collapse/expand with scroll behavior
- Smaller text size when collapsed

---

## ModalBottomSheet

ModalBottomSheet is used as an alternative to inline menus or simple dialogs on mobile. It appears in front of app content, disabling all other functionality until dismissed or an action is taken.

### Basic ModalBottomSheet

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

var openBottomSheet by rememberSaveable { mutableStateOf(false) }
var skipPartiallyExpanded by rememberSaveable { mutableStateOf(false) }
val scope = rememberCoroutineScope()
val bottomSheetState = rememberModalBottomSheetState(
    skipPartiallyExpanded = skipPartiallyExpanded
)

// App content
Column(
    horizontalAlignment = Alignment.Start,
    verticalArrangement = Arrangement.spacedBy(4.dp)
) {
    Row(
        Modifier.toggleable(
            value = skipPartiallyExpanded,
            role = Role.Checkbox,
            onValueChange = { checked -> skipPartiallyExpanded = checked }
        )
    ) {
        Checkbox(checked = skipPartiallyExpanded, onCheckedChange = null)
        Spacer(Modifier.width(16.dp))
        Text("Skip partially expanded State")
    }
    Button(
        onClick = { openBottomSheet = !openBottomSheet },
        modifier = Modifier.align(Alignment.CenterHorizontally)
    ) {
        Text(text = "Show Bottom Sheet")
    }
}

// Sheet content
if (openBottomSheet) {
    ModalBottomSheet(
        onDismissRequest = { openBottomSheet = false },
        sheetState = bottomSheetState
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Button(
                onClick = {
                    scope.launch { bottomSheetState.hide() }
                        .invokeOnCompletion {
                            if (!bottomSheetState.isVisible) {
                                openBottomSheet = false
                            }
                        }
                }
            ) {
                Text("Hide Bottom Sheet")
            }
        }
        var text by remember { mutableStateOf("") }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.padding(horizontal = 16.dp),
            label = { Text("Text field") }
        )
        LazyColumn {
            items(25) {
                ListItem(
                    headlineContent = { Text("Item $it") },
                    leadingContent = {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Localized description"
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
            }
        }
    }
}
```

#### ModalBottomSheet Parameters

- **onDismissRequest**: () -> Unit - Executes when user clicks outside the sheet, after it animates to Hidden
- **modifier**: Modifier - Optional modifier for the bottom sheet
- **sheetState**: SheetState - State of the bottom sheet
- **sheetMaxWidth**: Dp - Maximum width the sheet will take (use Dp.Unspecified for full screen width)
- **sheetGesturesEnabled**: Boolean - Whether the sheet can be interacted with by gestures
- **shape**: Shape - Shape of the bottom sheet
- **containerColor**: Color - Background color
- **contentColor**: Color - Preferred color for content
- **tonalElevation**: Dp - Tonal elevation (affects overlay when containerColor is surface)
- **scrimColor**: Color - Color of the scrim that obscures content when sheet is open
- **dragHandle**: (@Composable () -> Unit)? - Optional visual marker to swipe the sheet
- **contentWindowInsets**: @Composable () -> WindowInsets - Window insets for the sheet content
- **properties**: ModalBottomSheetProperties - Further customization of window behavior
- **content**: @Composable ColumnScope.() -> Unit - Content displayed inside the sheet

**Best Practices:**
- Use for long lists of action items or items requiring longer descriptions
- Provide clear dismiss actions
- Consider skipPartiallyExpanded for simpler UX
- Handle state cleanup when dismissing programmatically

---

## ModalDrawerSheet

ModalDrawerSheet is the content container for modal navigation drawers. It provides the visual styling and structure for drawer content.

### ModalDrawerSheet (Basic)

```kotlin
import androidx.compose.material3.*

ModalDrawerSheet {
    // Drawer content here
}
```

### ModalDrawerSheet with DrawerState (Predictive Back Support)

```kotlin
import androidx.compose.material3.*

val drawerState = rememberDrawerState(DrawerValue.Closed)

ModalDrawerSheet(drawerState = drawerState) {
    // Drawer content here
}
```

#### ModalDrawerSheet Parameters

**Basic version:**
- **modifier**: Modifier - Modifier to be applied to this drawer's content
- **drawerShape**: Shape - Defines the shape of this drawer's container
- **drawerContainerColor**: Color - Background color (use Color.Transparent for no color)
- **drawerContentColor**: Color - Preferred color for content
- **drawerTonalElevation**: Dp - Tonal elevation (affects overlay when containerColor is surface)
- **windowInsets**: WindowInsets - Window insets for the sheet
- **content**: @Composable ColumnScope.() -> Unit - Content inside the drawer

**Version with DrawerState:**
- **drawerState**: DrawerState - State of the drawer (enables back handling and predictive back on Android 14+)
- All other parameters same as basic version

**Key Features:**
- Provides consistent drawer styling
- Version with drawerState handles back button automatically
- Supports predictive back animations on Android 14+
- Tonal elevation for depth perception

---

## ModalNavigationDrawer

ModalNavigationDrawer provides ergonomic access to app destinations. It blocks interaction with the rest of the app's content with a scrim and is elevated above most of the UI.

### ModalNavigationDrawer Example

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

val drawerState = rememberDrawerState(DrawerValue.Closed)
val scope = rememberCoroutineScope()
val focusRequester = FocusRequester()

val items = listOf(
    Icons.Default.AccountCircle,
    Icons.Default.Bookmarks,
    Icons.Default.CalendarMonth,
    Icons.Default.Dashboard,
    Icons.Default.Email,
    Icons.Default.Favorite
)
val selectedItem = remember { mutableStateOf(items[0]) }

ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
        ModalDrawerSheet(drawerState) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(12.dp))
                items.forEach { item ->
                    NavigationDrawerItem(
                        icon = { Icon(item, contentDescription = null) },
                        label = { Text(item.name.substringAfterLast(".")) },
                        selected = item == selectedItem.value,
                        onClick = {
                            scope.launch { drawerState.close() }
                            selectedItem.value = item
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    },
    content = {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = if (drawerState.isClosed) ">>> Swipe >>>" else "<<< Swipe <<<")
            Spacer(Modifier.height(20.dp))
            Button(
                modifier = Modifier.focusRequester(focusRequester),
                onClick = { scope.launch { drawerState.open() } }
            ) {
                Text("Click to open")
            }
        }
    }
)

LaunchedEffect(drawerState.isClosed) {
    if (drawerState.isClosed) {
        focusRequester.requestFocus()
    }
}
```

#### ModalNavigationDrawer Parameters

- **drawerContent**: @Composable () -> Unit - Content inside this drawer
- **modifier**: Modifier - Modifier to be applied to this drawer
- **drawerState**: DrawerState - State of the drawer
- **gesturesEnabled**: Boolean - Whether the drawer can be interacted with by gestures
- **scrimColor**: Color - Color of the scrim that obscures content when drawer is open
- **content**: @Composable () -> Unit - Content of the rest of the UI

**Best Practices:**
- Use for 3-7 primary destinations
- Provide clear visual feedback for selected item
- Support both swipe and button interactions
- Manage focus properly when drawer opens/closes
- Use ModalDrawerSheet for drawer content

---

## ModalWideNavigationRail

ModalWideNavigationRail provides access to primary destinations on tablet and desktop screens. When collapsed, it behaves like a standard rail; when expanded, it blocks interaction with a scrim.

### ModalWideNavigationRail with Header

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

var selectedItem by remember { mutableIntStateOf(0) }
val items = listOf("Home", "Search", "Settings")
val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Favorite, Icons.Filled.Star)
val unselectedIcons = listOf(
    Icons.Outlined.Home,
    Icons.Outlined.FavoriteBorder,
    Icons.Outlined.StarBorder
)
val state = rememberWideNavigationRailState()
val scope = rememberCoroutineScope()
val headerDescription = if (state.targetValue == WideNavigationRailValue.Expanded) {
    "Collapse rail"
} else {
    "Expand rail"
}

Row(Modifier.fillMaxWidth()) {
    ModalWideNavigationRail(
        state = state,
        expandedHeaderTopPadding = 64.dp,
        header = {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above
                ),
                tooltip = { PlainTooltip { Text(headerDescription) } },
                state = rememberTooltipState()
            ) {
                IconButton(
                    modifier = Modifier.padding(start = 24.dp).semantics {
                        stateDescription = if (state.currentValue == WideNavigationRailValue.Expanded) {
                            "Expanded"
                        } else {
                            "Collapsed"
                        }
                    },
                    onClick = {
                        scope.launch {
                            if (state.targetValue == WideNavigationRailValue.Expanded)
                                state.collapse()
                            else state.expand()
                        }
                    }
                ) {
                    if (state.targetValue == WideNavigationRailValue.Expanded) {
                        Icon(Icons.AutoMirrored.Filled.MenuOpen, headerDescription)
                    } else {
                        Icon(Icons.Filled.Menu, headerDescription)
                    }
                }
            }
        }
    ) {
        items.forEachIndexed { index, item ->
            WideNavigationRailItem(
                railExpanded = state.targetValue == WideNavigationRailValue.Expanded,
                icon = {
                    Icon(
                        if (selectedItem == index) selectedIcons[index]
                        else unselectedIcons[index],
                        contentDescription = item
                    )
                },
                label = { Text(item) },
                selected = selectedItem == index,
                onClick = { selectedItem = index }
            )
        }
    }

    Column {
        Text(
            modifier = Modifier.padding(16.dp),
            text = "The rail is ${if (state.currentValue == WideNavigationRailValue.Expanded) "Expanded" else "Collapsed"}."
        )
    }
}
```

### Dismissible ModalWideNavigationRail

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

var selectedItem by remember { mutableIntStateOf(0) }
val items = listOf("Home", "Search", "Settings")
val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Favorite, Icons.Filled.Star)
val unselectedIcons = listOf(
    Icons.Outlined.Home,
    Icons.Outlined.FavoriteBorder,
    Icons.Outlined.StarBorder
)
val state = rememberWideNavigationRailState()
val scope = rememberCoroutineScope()

Row(Modifier.fillMaxSize()) {
    ModalWideNavigationRail(
        state = state,
        hideOnCollapse = true
    ) {
        items.forEachIndexed { index, item ->
            WideNavigationRailItem(
                railExpanded = true,
                icon = {
                    Icon(
                        if (selectedItem == index) selectedIcons[index]
                        else unselectedIcons[index],
                        contentDescription = null
                    )
                },
                label = { Text(item) },
                selected = selectedItem == index,
                onClick = {
                    selectedItem = index
                    scope.launch { state.collapse() }
                }
            )
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val currentPage = items[selectedItem]
        Button(
            onClick = { scope.launch { state.expand() } },
            Modifier.padding(32.dp)
        ) {
            Text(text = "$currentPage Page\nOpen modal rail", textAlign = TextAlign.Center)
        }
    }
}
```

#### ModalWideNavigationRail Parameters

- **modifier**: Modifier - Modifier to be applied to this wide navigation rail
- **state**: WideNavigationRailState - State of this wide navigation rail
- **hideOnCollapse**: Boolean - Whether the rail should slide offscreen when collapsed (true) or stay as collapsed rail (false, default)
- **collapsedShape**: Shape - Shape of the container when collapsed
- **expandedShape**: Shape - Shape of the container when expanded
- **colors**: WideNavigationRailColors - Colors used in different states
- **header**: (@Composable () -> Unit)? - Optional header (typically menu button, FAB, or logo)
- **expandedHeaderTopPadding**: Dp - Padding applied to top of rail for alignment between collapsed and expanded states
- **windowInsets**: WindowInsets - Window insets for the rail
- **arrangement**: Arrangement.Vertical - Vertical arrangement of items
- **expandedProperties**: ModalWideNavigationRailProperties - Customization of expanded modal behavior
- **content**: @Composable () -> Unit - Content of this rail (typically WideNavigationRailItems)

**Key Features:**
- Optimized for tablet and desktop screens
- Two modes: expandable rail or dismissible rail (hideOnCollapse)
- Blocks interaction with scrim when expanded
- Smooth expand/collapse animations
- Accessibility support with state descriptions

---

## MultiChoiceSegmentedButtonRow

MultiChoiceSegmentedButtonRow is a layout component for segmented buttons that allows multiple selections. It correctly positions, sizes, and adds semantics to segmented buttons.

### MultiChoiceSegmentedButtonRow Example

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

val checkedList = remember { mutableStateListOf<Int>() }
val options = listOf("Favorites", "Trending", "Saved")
val icons = listOf(
    Icons.Filled.StarBorder,
    Icons.AutoMirrored.Filled.TrendingUp,
    Icons.Filled.BookmarkBorder
)

MultiChoiceSegmentedButtonRow {
    options.forEachIndexed { index, label ->
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(
                index = index,
                count = options.size
            ),
            icon = {
                SegmentedButtonDefaults.Icon(active = index in checkedList) {
                    Icon(
                        imageVector = icons[index],
                        contentDescription = null,
                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                    )
                }
            },
            onCheckedChange = {
                if (index in checkedList) {
                    checkedList.remove(index)
                } else {
                    checkedList.add(index)
                }
            },
            checked = index in checkedList
        ) {
            Text(label)
        }
    }
}
```

#### MultiChoiceSegmentedButtonRow Parameters

- **modifier**: Modifier - Modifier to be applied to this row
- **space**: Dp - Dimension of the overlap between buttons (should equal stroke width)
- **content**: @Composable MultiChoiceSegmentedButtonRowScope.() -> Unit - Content of this row (typically a sequence of SegmentedButtons)

**Best Practices:**
- Use for multiple selection scenarios
- Provide 2-5 options for best UX
- Use SegmentedButtonDefaults.itemShape for proper corner rounding
- Include icons for better visual recognition
- Handle overlapping strokes correctly with space parameter

---

## NavigationBar

NavigationBar offers a persistent and convenient way to switch between primary destinations in an app. It's displayed at the bottom of the screen.

### NavigationBar Example

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

var selectedItem by remember { mutableIntStateOf(0) }
val items = listOf("Songs", "Artists", "Playlists")
val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Favorite, Icons.Filled.Star)
val unselectedIcons = listOf(
    Icons.Outlined.Home,
    Icons.Outlined.FavoriteBorder,
    Icons.Outlined.StarBorder
)

NavigationBar {
    items.forEachIndexed { index, item ->
        NavigationBarItem(
            icon = {
                Icon(
                    if (selectedItem == index) selectedIcons[index]
                    else unselectedIcons[index],
                    contentDescription = item
                )
            },
            label = { Text(item) },
            selected = selectedItem == index,
            onClick = { selectedItem = index }
        )
    }
}
```

#### NavigationBar Parameters

- **modifier**: Modifier - Modifier to be applied to this navigation bar
- **containerColor**: Color - Background color (use Color.Transparent for no color)
- **contentColor**: Color - Preferred color for content
- **tonalElevation**: Dp - Tonal elevation (affects overlay when containerColor is surface)
- **windowInsets**: WindowInsets - Window insets for the navigation bar
- **content**: @Composable RowScope.() -> Unit - Content of this navigation bar (typically 3-5 NavigationBarItems)

**Best Practices:**
- Use 3-5 destinations for optimal UX
- Provide both icon and label for each item
- Use filled icons for selected state, outlined for unselected
- Always show labels (don't hide them)
- Position at the bottom of the screen


---

## NavigationDrawerItem

NavigationDrawerItem represents a destination within navigation drawers (Modal, Permanent, or Dismissible).

### NavigationDrawerItem Example

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

val drawerState = rememberDrawerState(DrawerValue.Closed)
val scope = rememberCoroutineScope()
val focusRequester = FocusRequester()

val items = listOf(
    Icons.Default.AccountCircle,
    Icons.Default.Bookmarks,
    Icons.Default.CalendarMonth,
    Icons.Default.Dashboard,
    Icons.Default.Email,
    Icons.Default.Favorite
)
val selectedItem = remember { mutableStateOf(items[0]) }

ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
        ModalDrawerSheet(drawerState) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(12.dp))
                items.forEach { item ->
                    NavigationDrawerItem(
                        icon = { Icon(item, contentDescription = null) },
                        label = { Text(item.name.substringAfterLast(".")) },
                        selected = item == selectedItem.value,
                        onClick = {
                            scope.launch { drawerState.close() }
                            selectedItem.value = item
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    },
    content = {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = if (drawerState.isClosed) ">>> Swipe >>>" else "<<< Swipe <<<")
            Spacer(Modifier.height(20.dp))
            Button(
                modifier = Modifier.focusRequester(focusRequester),
                onClick = { scope.launch { drawerState.open() } }
            ) {
                Text("Click to open")
            }
        }
    }
)

LaunchedEffect(drawerState.isClosed) {
    if (drawerState.isClosed) {
        focusRequester.requestFocus()
    }
}
```

#### NavigationDrawerItem Parameters

- **label**: @Composable () -> Unit - Text label for this item
- **selected**: Boolean - Whether this item is selected
- **onClick**: () -> Unit - Called when this item is clicked
- **modifier**: Modifier - Modifier to be applied to this item
- **icon**: (@Composable () -> Unit)? - Optional icon for this item, typically an Icon
- **badge**: (@Composable () -> Unit)? - Optional badge to show on this item from the end side
- **shape**: Shape - Optional shape for the active indicator
- **colors**: NavigationDrawerItemColors - Colors used for this item in different states
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions

**Best Practices:**
- Provide clear, concise labels
- Use icons for better visual recognition
- Show selected state clearly
- Close drawer after selection
- Use consistent padding (NavigationDrawerItemDefaults.ItemPadding)

---

## NavigationRail

NavigationRail provides access to primary destinations in apps when using tablet and desktop screens. It's displayed vertically along the side of the screen.

### NavigationRail Example

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

var selectedItem by remember { mutableIntStateOf(0) }
val items = listOf("Home", "Search", "Settings")
val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Favorite, Icons.Filled.Star)
val unselectedIcons = listOf(
    Icons.Outlined.Home,
    Icons.Outlined.FavoriteBorder,
    Icons.Outlined.StarBorder
)

NavigationRail {
    items.forEachIndexed { index, item ->
        NavigationRailItem(
            icon = {
                Icon(
                    if (selectedItem == index) selectedIcons[index]
                    else unselectedIcons[index],
                    contentDescription = item
                )
            },
            label = { Text(item) },
            selected = selectedItem == index,
            onClick = { selectedItem = index }
        )
    }
}
```

#### NavigationRail Parameters

- **modifier**: Modifier - Modifier to be applied to this navigation rail
- **containerColor**: Color - Background color (use Color.Transparent for no color)
- **contentColor**: Color - Preferred color for content
- **header**: (@Composable ColumnScope.() -> Unit)? - Optional header (may hold FAB or logo)
- **windowInsets**: WindowInsets - Window insets for the navigation rail
- **content**: @Composable ColumnScope.() -> Unit - Content of this rail (typically 3-7 NavigationRailItems)

**Best Practices:**
- Use 3-7 destinations for optimal UX
- Optionally include a FAB or logo in the header
- Position along the left or right edge of the screen
- Use for tablet and desktop layouts

---

## NavigationRailItem

NavigationRailItem represents a destination within a NavigationRail.

#### NavigationRailItem Parameters

- **selected**: Boolean - Whether this item is selected
- **onClick**: () -> Unit - Called when this item is clicked
- **icon**: @Composable () -> Unit - Icon for this item, typically an Icon
- **modifier**: Modifier - Modifier to be applied to this item
- **enabled**: Boolean - Controls the enabled state
- **label**: (@Composable () -> Unit)? - Optional text label for this item
- **alwaysShowLabel**: Boolean - Whether to always show the label (if false, only shown when selected)
- **colors**: NavigationRailItemColors - Colors used for this item in different states
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions

**Key Features:**
- Label always shown when selected
- alwaysShowLabel controls label visibility when not selected
- Use filled icons for selected, outlined for unselected
- Minimum touch target size of 48 x 48 dp

---

## OutlinedButton

OutlinedButton is a medium-emphasis button with a border. It contains actions that are important but not the primary action in an app.

### Standard OutlinedButton

```kotlin
import androidx.compose.material3.*

OutlinedButton(onClick = { /* Do something! */ }) {
    Text("Outlined Button")
}
```

### Expressive OutlinedButton with Shape Morphing

```kotlin
import androidx.compose.material3.*

OutlinedButton(
    onClick = {},
    shapes = ButtonDefaults.shapes()
) {
    Text("Outlined Button")
}
```

#### OutlinedButton Parameters

**Standard version:**
- **onClick**: () -> Unit - Called when this button is clicked
- **modifier**: Modifier - Modifier to be applied to this button
- **enabled**: Boolean - Controls the enabled state
- **shape**: Shape - Defines the shape of this button's container, border, and shadow
- **colors**: ButtonColors - Colors used for this button in different states
- **elevation**: ButtonElevation? - Controls the shadow size (typically null for outlined buttons)
- **border**: BorderStroke? - Border to draw around the container (pass null for no border)
- **contentPadding**: PaddingValues - Spacing between container and content
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions
- **content**: @Composable RowScope.() -> Unit - Content displayed on the button (text, icon, or image)

**Expressive version (with shape morphing):**
- **shapes**: ButtonShapes - Shapes that the button will morph between based on user interaction
- All other parameters same as standard version

**Button Emphasis Hierarchy:**
1. Button (filled) - Highest emphasis
2. FilledTonalButton - Medium-high emphasis
3. OutlinedButton - Medium emphasis
4. TextButton - Lowest emphasis

**Best Practices:**
- Use for secondary actions
- Pair with Button to indicate alternative action
- Default text style is Typography.labelLarge
- Expressive version morphs shapes during interaction

---

## OutlinedCard

OutlinedCard contains content and actions with a visual boundary around the container, providing greater emphasis than other card types.

### Non-Interactive OutlinedCard

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

OutlinedCard(Modifier.size(width = 180.dp, height = 100.dp)) {
    Box(Modifier.fillMaxSize()) {
        Text("Card content", Modifier.align(Alignment.Center))
    }
}
```

### Clickable OutlinedCard

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

OutlinedCard(
    onClick = { /* Do something */ },
    modifier = Modifier.size(width = 180.dp, height = 100.dp)
) {
    Box(Modifier.fillMaxSize()) {
        Text("Clickable", Modifier.align(Alignment.Center))
    }
}
```

#### OutlinedCard Parameters

**Non-interactive version:**
- **modifier**: Modifier - Modifier to be applied to this card
- **shape**: Shape - Defines the shape of container, border, and shadow
- **colors**: CardColors - Colors used for this card in different states
- **elevation**: CardElevation - Controls shadow size and primary color overlay
- **border**: BorderStroke - Border to draw around the container
- **content**: @Composable ColumnScope.() -> Unit - Content displayed on the card

**Clickable version:**
- **onClick**: () -> Unit - Called when this card is clicked
- **enabled**: Boolean - Controls the enabled state
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions
- All other parameters same as non-interactive version

**Best Practices:**
- Use for content that relates information about a subject
- Border provides visual emphasis
- Use clickable version for interactive cards
- Content is laid out in a Column

---

## OutlinedIconButton

OutlinedIconButton is an icon button with a border, providing more visual separation from the background.

### Standard OutlinedIconButton

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*

val description = "Localized description"

TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
        TooltipAnchorPosition.Above
    ),
    tooltip = { PlainTooltip { Text(description) } },
    state = rememberTooltipState()
) {
    OutlinedIconButton(onClick = { /* doSomething() */ }) {
        Icon(Icons.Filled.Lock, contentDescription = description)
    }
}
```

### Large OutlinedIconButton

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.ui.Modifier

val description = "Localized description"

TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
        TooltipAnchorPosition.Above
    ),
    tooltip = { PlainTooltip { Text(description) } },
    state = rememberTooltipState()
) {
    OutlinedIconButton(
        onClick = { /* doSomething() */ },
        modifier = Modifier.size(IconButtonDefaults.largeContainerSize()),
        shape = IconButtonDefaults.largeRoundShape
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = description,
            modifier = Modifier.size(IconButtonDefaults.largeIconSize)
        )
    }
}
```

### Expressive OutlinedIconButton with Shape Morphing

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*

OutlinedIconButton(
    onClick = { /* doSomething() */ },
    shapes = IconButtonDefaults.shapes()
) {
    Icon(Icons.Filled.Lock, contentDescription = "Lock")
}
```

#### OutlinedIconButton Parameters

**Standard version:**
- **onClick**: () -> Unit - Called when this icon button is clicked
- **modifier**: Modifier - Modifier to be applied to this icon button
- **enabled**: Boolean - Controls the enabled state
- **shape**: Shape - Defines the shape of container and border
- **colors**: IconButtonColors - Colors used in different states
- **border**: BorderStroke? - Border to draw around the container (pass null for no border)
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions
- **content**: @Composable () -> Unit - Content of this icon button, typically an Icon

**Expressive version:**
- **shapes**: IconButtonShapes - Shapes that the button will morph between based on user interaction
- All other parameters same as standard version

**Best Practices:**
- Use when component requires more visual separation
- Always include tooltips for accessibility
- Icon size typically 24 x 24 dp
- Minimum touch target size 48 x 48 dp
- Expressive version supports shape morphing

---

## OutlinedIconToggleButton

OutlinedIconToggleButton is a toggleable icon button with a border, used for binary state actions.

### Standard OutlinedIconToggleButton

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*

var checked by remember { mutableStateOf(false) }
val description = "Localized description"

TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
        TooltipAnchorPosition.Above
    ),
    tooltip = { PlainTooltip { Text(description) } },
    state = rememberTooltipState()
) {
    OutlinedIconToggleButton(
        checked = checked,
        onCheckedChange = { checked = it }
    ) {
        if (checked) {
            Icon(Icons.Filled.Lock, contentDescription = description)
        } else {
            Icon(Icons.Outlined.Lock, contentDescription = description)
        }
    }
}
```

### Expressive OutlinedIconToggleButton with Shape Morphing

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*

var checked by remember { mutableStateOf(false) }
val description = "Localized description"

TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
        TooltipAnchorPosition.Above
    ),
    tooltip = { PlainTooltip { Text(description) } },
    state = rememberTooltipState()
) {
    OutlinedIconToggleButton(
        checked = checked,
        onCheckedChange = { checked = it },
        shapes = IconButtonDefaults.toggleableShapes()
    ) {
        if (checked) {
            Icon(Icons.Filled.Lock, contentDescription = description)
        } else {
            Icon(Icons.Outlined.Lock, contentDescription = description)
        }
    }
}
```

#### OutlinedIconToggleButton Parameters

**Standard version:**
- **checked**: Boolean - Whether this icon button is toggled on or off
- **onCheckedChange**: (Boolean) -> Unit - Called when this icon button is clicked
- **modifier**: Modifier - Modifier to be applied to this icon button
- **enabled**: Boolean - Controls the enabled state
- **shape**: Shape - Defines the shape of container and border
- **colors**: IconToggleButtonColors - Colors used in different states
- **border**: BorderStroke? - Border to draw around the container (pass null for no border)
- **interactionSource**: MutableInteractionSource? - For observing and emitting interactions
- **content**: @Composable () -> Unit - Content of this icon button, typically an Icon

**Expressive version:**
- **shapes**: IconToggleButtonShapes - Shapes that the button will morph between based on user interaction
- All other parameters same as standard version (with vibrant colors by default)

**Best Practices:**
- Use different icons for checked and unchecked states
- Always include tooltips for accessibility
- Icon size typically 24 x 24 dp
- Minimum touch target size 48 x 48 dp
- Expressive version supports shape morphing and vibrant colors

---

## Summary

This completes the processing of chunks 11-15 (lines 10001-15000) of the Material Design 3 Expressive documentation. The transformed content includes:

- **HorizontalFloatingToolbar**: Expressive toolbar combining FAB with horizontal actions
- **Icon**: Component for displaying vector graphics with tinting
- **IconButton & IconToggleButton**: Clickable and toggleable icon buttons with various sizes
- **Label**: Component for attaching tooltips to interactive elements
- **Large FABs**: LargeFloatingActionButton and LargeExtendedFloatingActionButton
- **Large & Medium Top App Bars**: Including flexible variants with subtitle support
- **LinearWavyProgressIndicator**: Expressive wavy progress indicator
- **ListItem**: Versatile list item component (1-3 line variants)
- **LoadingIndicator**: Shape-morphing loading indicator
- **Material Themes**: MaterialExpressiveTheme and MaterialTheme
- **Medium FABs**: MediumFloatingActionButton and MediumExtendedFloatingActionButton
- **Medium Top App Bars**: Including flexible variant
- **Modal Components**: ModalBottomSheet, ModalDrawerSheet, ModalNavigationDrawer, ModalWideNavigationRail
- **MultiChoiceSegmentedButtonRow**: Layout for multi-select segmented buttons
- **Navigation Components**: NavigationBar, NavigationDrawerItem, NavigationRail, NavigationRailItem
- **Outlined Components**: OutlinedButton, OutlinedCard, OutlinedIconButton, OutlinedIconToggleButton

All components include comprehensive examples, parameter descriptions, and best practices for Material Design 3 Expressive implementation.


## OutlinedSecureTextField

**Component Type:** Text Input
**Material Design Version:** Material 3
**Artifact:** androidx.compose.material3:material3

### Overview
Material Design outlined text field specifically designed for password entry fields. It only supports a single line of content and comes with default settings appropriate for entering secure content. Context menu actions like cut, copy, and drag are disabled for added security.

### Key Features
- Single-line password input with security features
- Outlined style with less visual emphasis than filled text fields
- Built-in text obfuscation
- Disabled clipboard operations for security
- Label positioning support (attached/floating)

### API Signature
```kotlin
@Composable
fun OutlinedSecureTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = LocalTextStyle.current,
    labelPosition: TextFieldLabelPosition = TextFieldLabelPosition.Attached(),
    label: (@Composable TextFieldLabelScope.() -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    inputTransformation: InputTransformation? = null,
    textObfuscationMode: TextObfuscationMode = TextObfuscationMode.RevealLastTyped,
    textObfuscationCharacter: Char = DefaultObfuscationCharacter,
    keyboardOptions: KeyboardOptions = SecureTextFieldKeyboardOptions,
    onKeyboardAction: KeyboardActionHandler? = null,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)? = null,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    contentPadding: PaddingValues = OutlinedTextFieldDefaults.contentPadding(),
    interactionSource: MutableInteractionSource? = null
)
```

### Parameters
- **state**: TextFieldState object holding internal editing state
- **textObfuscationMode**: Method to obscure input text (default: RevealLastTyped)
- **textObfuscationCharacter**: Character used for obfuscation
- **keyboardOptions**: Configured for secure input (auto-correct disabled, Password keyboard type)

### Related Components
- SecureTextField (filled version)
- OutlinedTextField (non-secure version)

---

## OutlinedTextField

**Component Type:** Text Input
**Material Design Version:** Material 3
**Artifact:** androidx.compose.material3:material3
**Added in:** 1.1.0 (String overload), 1.4.0 (TextFieldState overload)

### Overview
Material Design outlined text field for general text input. Outlined text fields have less visual emphasis than filled text fields, making them ideal for forms where many text fields are placed together.

### Key Features
- Multiple overloads for different state management approaches
- Support for single-line and multi-line input
- Customizable label positioning (attached/floating)
- Rich decoration support (icons, prefix, suffix, supporting text)
- Input/output transformation support
- Scroll state management for long content

### API Signatures

#### String-based Overload
```kotlin
@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors()
)
```

#### TextFieldValue-based Overload
```kotlin
@Composable
fun OutlinedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    // ... similar parameters with cursor/selection support
)
```

#### TextFieldState-based Overload (Added in 1.4.0)
```kotlin
@Composable
fun OutlinedTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    // ... parameters with InputTransformation and OutputTransformation support
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    scrollState: ScrollState = rememberScrollState()
)
```

### Usage Guidelines
- Use String overload for simple text input
- Use TextFieldValue overload when you need cursor position and selection range
- Use TextFieldState overload for advanced input/output transformations
- Set `singleLine = true` for single-line inputs
- Use `readOnly = true` for display-only pre-filled forms

### Related Components
- TextField (filled version)
- OutlinedSecureTextField (for passwords)

---

## OutlinedToggleButton

**Component Type:** Button
**Material Design Version:** Material 3 Expressive
**Artifact:** androidx.compose.material3:material3
**API Level:** @ExperimentalMaterial3ExpressiveApi

### Overview
Toggleable button that switches between primary and tonal colors depending on checked state. Features shape morphing between three shapes based on interaction state when using CornerBasedShapes.

### Key Features
- Toggle between checked/unchecked states
- Shape morphing animation during interaction
- Medium-emphasis styling with border
- Expressive motion design

### API Signature
```kotlin
@Composable
@ExperimentalMaterial3ExpressiveApi
fun OutlinedToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shapes: ToggleButtonShapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight),
    colors: ToggleButtonColors = ToggleButtonDefaults.outlinedToggleButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = if (!checked) ButtonDefaults.outlinedButtonBorder(enabled) else null,
    contentPadding: PaddingValues = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
)
```

### Usage Example
```kotlin
var checked by remember { mutableStateOf(false) }
OutlinedToggleButton(checked = checked, onCheckedChange = { checked = it }) {
    Text("Outlined Button")
}
```

### Related Components
- OutlinedButton (non-toggleable version)
- OutlinedIconToggleButton (icon-specific version)
- ToggleButton (filled version)

---

## PermanentDrawerSheet

**Component Type:** Navigation
**Material Design Version:** Material 3
**Artifact:** androidx.compose.material3:material3

### Overview
Content container for a permanent navigation drawer. Always visible and typically used for frequently accessed destinations on larger screens.

### API Signature
```kotlin
@Composable
fun PermanentDrawerSheet(
    modifier: Modifier = Modifier,
    drawerShape: Shape = RectangleShape,
    drawerContainerColor: Color = DrawerDefaults.standardContainerColor,
    drawerContentColor: Color = contentColorFor(drawerContainerColor),
    drawerTonalElevation: Dp = DrawerDefaults.PermanentDrawerElevation,
    windowInsets: WindowInsets = DrawerDefaults.windowInsets,
    content: @Composable ColumnScope.() -> Unit
)
```

### Related Components
- PermanentNavigationDrawer (container)
- ModalDrawerSheet (modal version)
- DismissibleDrawerSheet (dismissible version)

---

## PermanentNavigationDrawer

**Component Type:** Navigation
**Material Design Version:** Material 3
**Artifact:** androidx.compose.material3:material3
**Added in:** 1.0.0

### Overview
Material Design permanent navigation drawer that is always visible and affects the screen's layout grid. Best for frequently switching destinations on tablet and desktop screens.

### Key Features
- Always visible (non-dismissible)
- Affects layout grid
- Best for larger screens
- Ergonomic access to destinations

### API Signature
```kotlin
@Composable
fun PermanentNavigationDrawer(
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
)
```

### Usage Example
```kotlin
val items = listOf(/* navigation items */)
val selectedItem = remember { mutableStateOf(items[0]) }

PermanentNavigationDrawer(
    drawerContent = {
        PermanentDrawerSheet(Modifier.width(240.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(12.dp))
                items.forEach { item ->
                    NavigationDrawerItem(
                        icon = { Icon(item, contentDescription = null) },
                        label = { Text(item.name) },
                        selected = item == selectedItem.value,
                        onClick = { selectedItem.value = item },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    },
    content = {
        // Main application content
    }
)
```

### Usage Guidelines
- Use on tablet and desktop screens
- For mobile screens, use ModalNavigationDrawer instead
- Typically 240dp wide
- Contains NavigationDrawerItem components

### Related Components
- ModalNavigationDrawer (mobile version)
- DismissibleNavigationDrawer (dismissible version)

---

## PrimaryScrollableTabRow

**Component Type:** Navigation
**Material Design Version:** Material 3
**Artifact:** androidx.compose.material3:material3

### Overview
Material Design scrollable primary tabs for displaying main content destinations. Used when tabs cannot fit on screen, allowing longer text labels and larger number of tabs.

### Key Features
- Horizontal scrolling for overflow tabs
- Offset from starting edge
- Animated indicator
- Configurable edge padding
- Best for touch interfaces

### API Signature
```kotlin
@Composable
fun PrimaryScrollableTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    containerColor: Color = TabRowDefaults.primaryContainerColor,
    contentColor: Color = TabRowDefaults.primaryContentColor,
    edgePadding: Dp = TabRowDefaults.ScrollableTabRowEdgeStartPadding,
    indicator: @Composable TabIndicatorScope.() -> Unit = @Composable {
        TabRowDefaults.PrimaryIndicator(
            Modifier.tabIndicatorOffset(selectedTabIndex, matchContentSize = true),
            width = Dp.Unspecified
        )
    },
    divider: @Composable () -> Unit = @Composable { HorizontalDivider() },
    minTabWidth: Dp = TabRowDefaults.ScrollableTabRowMinTabWidth,
    tabs: @Composable () -> Unit
)
```

### Parameters
- **selectedTabIndex**: Index of currently selected tab
- **scrollState**: ScrollState for managing scroll position
- **edgePadding**: Padding at start/end to indicate scrollability
- **indicator**: Visual indicator for selected tab
- **minTabWidth**: Minimum width for each tab

### Usage Guidelines
- Use for browsing on touch interfaces
- Allows longer text labels
- Supports larger number of tabs
- For fixed tabs, use PrimaryTabRow instead

### Related Components
- PrimaryTabRow (fixed version)
- SecondaryScrollableTabRow (secondary emphasis)

---

## PrimaryTabRow

**Component Type:** Navigation
**Material Design Version:** Material 3
**Artifact:** androidx.compose.material3:material3

### Overview
Material Design fixed primary tabs that display all tabs simultaneously with equal spacing. Best for switching between related content quickly.

### Key Features
- Fixed layout (no scrolling)
- Equal space distribution
- Animated indicator
- Customizable indicator and divider
- Swipe gesture support in content area

### API Signature
```kotlin
@Composable
fun PrimaryTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = TabRowDefaults.primaryContainerColor,
    contentColor: Color = TabRowDefaults.primaryContentColor,
    indicator: @Composable TabIndicatorScope.() -> Unit = {
        TabRowDefaults.PrimaryIndicator(
            modifier = Modifier.tabIndicatorOffset(selectedTabIndex, matchContentSize = true),
            width = Dp.Unspecified
        )
    },
    divider: @Composable () -> Unit = @Composable { HorizontalDivider() },
    tabs: @Composable () -> Unit
)
```

### Usage Example
```kotlin
var state by remember { mutableStateOf(0) }
val titles = listOf("Tab 1", "Tab 2", "Tab 3")

Column {
    PrimaryTabRow(selectedTabIndex = state) {
        titles.forEachIndexed { index, title ->
            Tab(
                selected = state == index,
                onClick = { state = index },
                text = { Text(text = title, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
    Text(
        modifier = Modifier.align(Alignment.CenterHorizontally),
        text = "Primary tab ${state + 1} selected",
        style = MaterialTheme.typography.bodyLarge
    )
}
```

### Customization
- Custom tab content supported
- Custom indicator with `tabIndicatorOffset` modifier
- Custom transitions with `tabIndicatorLayout` modifier
- Animated color and position changes

### Usage Guidelines
- Use for 2-5 related content destinations
- Best for quick switching between content
- For more tabs or longer labels, use PrimaryScrollableTabRow
- Tabs take equal space across entire row

### Related Components
- PrimaryScrollableTabRow (scrollable version)
- SecondaryTabRow (secondary emphasis)
- Tab (individual tab component)


## ProvideTextStyle

**Component Type:** Utility
**Material Design Version:** Material 3
**Artifact:** androidx.compose.material3:material3
**Added in:** 1.0.0

### Overview
Utility function to set the current value of LocalTextStyle, merging the given style with current style values for any missing attributes. All Text components within its content will inherit this style unless explicitly styled.

### API Signature
```kotlin
@Composable
fun ProvideTextStyle(
    value: TextStyle,
    content: @Composable () -> Unit
)
```

### Related
- LocalTextStyle (CompositionLocal)

---

## RadioButton

**Component Type:** Selection Control
**Material Design Version:** Material 3
**Artifact:** androidx.compose.material3:material3
**Added in:** 1.0.0

### Overview
Material Design radio button for selecting one option from a set. Radio buttons allow users to select a single option from a group of mutually exclusive choices.

### Key Features
- Single selection from a group
- Accessibility support with selectableGroup modifier
- Customizable colors
- Interaction source observation

### API Signature
```kotlin
@Composable
fun RadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: RadioButtonColors = RadioButtonDefaults.colors(),
    interactionSource: MutableInteractionSource? = null
)
```

### Usage Examples

#### Basic Radio Buttons
```kotlin
var state by remember { mutableStateOf(true) }
Row(Modifier.selectableGroup()) {
    RadioButton(
        selected = state,
        onClick = { state = true },
        modifier = Modifier.semantics { contentDescription = "Localized Description" }
    )
    RadioButton(
        selected = !state,
        onClick = { state = false },
        modifier = Modifier.semantics { contentDescription = "Localized Description" }
    )
}
```

#### Radio Group with Text Labels
```kotlin
val radioOptions = listOf("Calls", "Missed", "Friends")
val (selectedOption, onOptionSelected) = remember { mutableStateOf(radioOptions[0]) }

Column(Modifier.selectableGroup()) {
    radioOptions.forEach { text ->
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .selectable(
                    selected = (text == selectedOption),
                    onClick = { onOptionSelected(text) },
                    role = Role.RadioButton
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = (text == selectedOption),
                onClick = null // null recommended for accessibility with screenreaders
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }
}
```

### Parameters
- **selected**: Whether this radio button is selected
- **onClick**: Callback when clicked (null for non-interactive)
- **enabled**: Controls enabled state
- **colors**: RadioButtonColors for different states

### Usage Guidelines
- Always use `Modifier.selectableGroup()` on parent container for accessibility
- Set `onClick = null` when using with selectable parent for better screen reader support
- Provide content descriptions for accessibility
- Use with Row or Column layouts for radio groups

---

## RangeSlider

**Component Type:** Input Control
**Material Design Version:** Material 3
**Artifact:** androidx.compose.material3:material3
**Added in:** 1.0.0 (basic), 1.2.0 (state-based)

### Overview
Material Design range slider that allows users to select two values within a range. Expands upon Slider using the same concepts but with two thumbs that cannot cross each other.

### Key Features
- Dual thumb selection
- Continuous or stepped values
- Customizable thumbs and track
- Tooltip support with Label component
- State-based or value-based APIs

### API Signatures

#### Value-based Overload
```kotlin
@Composable
fun RangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: @IntRange(from = 0) Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors()
)
```

#### State-based Overload (Added in 1.2.0)
```kotlin
@Composable
@ExperimentalMaterial3Api
fun RangeSlider(
    state: RangeSliderState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SliderColors = SliderDefaults.colors(),
    startInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    endInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    startThumb: @Composable (RangeSliderState) -> Unit = { /* default thumb */ },
    endThumb: @Composable (RangeSliderState) -> Unit = { /* default thumb */ },
    track: @Composable (RangeSliderState) -> Unit = { /* default track */ }
)
```

### Usage Examples

#### Continuous Range Slider
```kotlin
val rangeSliderState = rememberRangeSliderState(
    0f,
    100f,
    valueRange = 0f..100f,
    onValueChangeFinished = {
        // Business logic update
    }
)

Column(modifier = Modifier.padding(horizontal = 16.dp)) {
    val rangeStart = "%.2f".format(rangeSliderState.activeRangeStart)
    val rangeEnd = "%.2f".format(rangeSliderState.activeRangeEnd)
    Text(text = "$rangeStart .. $rangeEnd")
    RangeSlider(state = rangeSliderState)
}
```

#### Stepped Range Slider
```kotlin
val rangeSliderState = rememberRangeSliderState(
    0f,
    100f,
    valueRange = 0f..100f,
    steps = 9 // Only allow multiples of 10
)

Column(modifier = Modifier.padding(horizontal = 16.dp)) {
    val rangeStart = rangeSliderState.activeRangeStart.roundToInt()
    val rangeEnd = rangeSliderState.activeRangeEnd.roundToInt()
    Text(text = "$rangeStart .. $rangeEnd")
    RangeSlider(state = rangeSliderState)
}
```

#### Custom Thumbs with Tooltips
```kotlin
val rangeSliderState = rememberRangeSliderState(0f, 100f, valueRange = 0f..100f)
val startInteractionSource = remember { MutableInteractionSource() }
val endInteractionSource = remember { MutableInteractionSource() }

RangeSlider(
    state = rangeSliderState,
    startInteractionSource = startInteractionSource,
    endInteractionSource = endInteractionSource,
    startThumb = {
        Label(
            label = {
                PlainTooltip(modifier = Modifier.sizeIn(45.dp, 25.dp).wrapContentWidth()) {
                    Text("%.2f".format(rangeSliderState.activeRangeStart))
                }
            },
            interactionSource = startInteractionSource
        ) {
            SliderDefaults.Thumb(
                interactionSource = startInteractionSource,
                colors = startThumbColors
            )
        }
    },
    endThumb = { /* similar to startThumb */ }
)
```

### Parameters
- **value/state**: Current range values
- **valueRange**: Allowed value range (default: 0f..1f)
- **steps**: Number of discrete steps (0 for continuous)
- **onValueChangeFinished**: Callback when user completes interaction
- **startThumb/endThumb**: Custom thumb composables
- **track**: Custom track composable

### Usage Guidelines
- Use continuous sliders for selections that don't require specific values
- Use stepped sliders for predefined value sets
- Steps parameter excludes endpoints (e.g., steps=9 for multiples of 10 from 0-100)
- Thumbs cannot cross each other
- Use Label component for tooltips on thumbs

### Related Components
- Slider (single value)
- SliderDefaults (styling)
- Label (for tooltips)

---

## Scaffold

**Component Type:** Layout
**Material Design Version:** Material 3
**Artifact:** androidx.compose.material3:material3

### Overview
Material Design layout structure that implements the basic visual layout. Provides API to compose several Material components together with proper layout strategy and data collection for correct interaction.

### Key Features
- Coordinated layout for Material components
- Top and bottom bar support
- Floating action button positioning
- Snackbar hosting
- Window insets handling
- Content padding management

### API Signature
```kotlin
@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit
)
```

### Usage Examples

#### Basic Scaffold with TopAppBar and FAB
```kotlin
Scaffold(
    topBar = {
        TopAppBar(
            title = { Text("Simple Scaffold Screen") },
            navigationIcon = {
                IconButton(onClick = { /* Open nav drawer */ }) {
                    Icon(Icons.Filled.Menu, contentDescription = "Localized description")
                }
            }
        )
    },
    floatingActionButtonPosition = FabPosition.End,
    floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { /* fab click */ }) {
            Text("Inc")
        }
    },
    content = { innerPadding ->
        LazyColumn(
            modifier = Modifier.consumeWindowInsets(innerPadding),
            contentPadding = innerPadding
        ) {
            items(count = 100) {
                Box(Modifier.fillMaxWidth().height(50.dp).background(colors[it % colors.size]))
            }
        }
    }
)
```

#### Scaffold with Snackbar
```kotlin
val snackbarHostState = remember { SnackbarHostState() }
val scope = rememberCoroutineScope()

Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    floatingActionButton = {
        var clickCount by remember { mutableStateOf(0) }
        ExtendedFloatingActionButton(
            onClick = {
                scope.launch {
                    snackbarHostState.showSnackbar("Snackbar # ${++clickCount}")
                }
            }
        ) {
            Text("Show snackbar")
        }
    },
    content = { innerPadding ->
        Text(
            text = "Body content",
            modifier = Modifier.padding(innerPadding).fillMaxSize().wrapContentSize()
        )
    }
)
```

### Parameters
- **topBar**: Top app bar (typically TopAppBar)
- **bottomBar**: Bottom bar (typically NavigationBar)
- **snackbarHost**: Snackbar host (typically SnackbarHost)
- **floatingActionButton**: Main action button (typically FloatingActionButton)
- **floatingActionButtonPosition**: FAB position (End, Start, Center)
- **contentWindowInsets**: Window insets for content padding
- **content**: Main content receiving PaddingValues

### Usage Guidelines
- Apply innerPadding to content root via `Modifier.padding(innerPadding)`
- Use `Modifier.consumeWindowInsets(innerPadding)` on scrollable content
- Scaffold handles insets from top/bottom only if topBar/bottomBar are absent
- For vertical scroll, apply padding to scroll child, not scroll itself
- Use SnackbarHostState.showSnackbar() to display snackbars

### Related Components
- TopAppBar
- NavigationBar
- FloatingActionButton
- SnackbarHost

---

## ScrollableTabRow

**Component Type:** Navigation
**Material Design Version:** Material 3
**Artifact:** androidx.compose.material3:material3
**Status:** Deprecated

### Deprecation Notice
This function is deprecated. Use **PrimaryScrollableTabRow** or **SecondaryScrollableTabRow** instead for primary or secondary indicator tab variants.

### Overview
Material Design scrollable tabs for when a set of tabs cannot fit on screen. Allows longer text labels and larger number of tabs with horizontal scrolling.

### Migration
- For primary indicator tabs → Use `PrimaryScrollableTabRow`
- For secondary indicator tabs → Use `SecondaryScrollableTabRow`

---

## SearchBar

**Component Type:** Search
**Material Design Version:** Material 3
**Artifact:** androidx.compose.material3:material3
**Added in:** 1.5.0-alpha08
**API Level:** @ExperimentalMaterial3Api

### Overview
Material Design search bar representing a collapsed search field. Allows users to enter keywords or phrases to get relevant information and navigate through an app via search queries.

### Key Features
- Collapsed state representation
- Works with ExpandedFullScreenSearchBar or ExpandedDockedSearchBar
- Customizable input field
- Elevation and shape customization
- State management with SearchBarState

### API Signature
```kotlin
@ExperimentalMaterial3Api
@Composable
fun SearchBar(
    state: SearchBarState,
    inputField: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = SearchBarDefaults.inputFieldShape,
    colors: SearchBarColors = SearchBarDefaults.colors(),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    shadowElevation: Dp = SearchBarDefaults.ShadowElevation
)
```

### Parameters
- **state**: SearchBarState managing search bar state
- **inputField**: Composable for the input field
- **shape**: Shape of the search bar
- **colors**: SearchBarColors for different states
- **tonalElevation**: Tonal elevation for surface color overlay
- **shadowElevation**: Shadow elevation

### Usage Guidelines
- Use in conjunction with ExpandedFullScreenSearchBar or ExpandedDockedSearchBar
- SearchBar represents collapsed state
- Expanded variants display search results
- Use rememberSearchBarState() for state management

### Related Components
- ExpandedFullScreenSearchBar (expanded full-screen variant)
- ExpandedDockedSearchBar (expanded docked variant)
- DockedSearchBar (alternative docked implementation)
- SearchBarDefaults (styling defaults)


## SearchBar (Additional Overloads)

**Component Type:** Search
**Material Design Version:** Material 3
**Artifact:** androidx.compose.material3:material3
**API Level:** @ExperimentalMaterial3Api

### Expanded SearchBar Overload
Full-screen search bar that expands to show search results. Tries to occupy entirety of allowed size in expanded state.

#### API Signature
```kotlin
@ExperimentalMaterial3Api
@Composable
fun SearchBar(
    inputField: @Composable () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = SearchBarDefaults.inputFieldShape,
    colors: SearchBarColors = SearchBarDefaults.colors(),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    shadowElevation: Dp = SearchBarDefaults.ShadowElevation,
    windowInsets: WindowInsets = SearchBarDefaults.windowInsets,
    content: @Composable ColumnScope.() -> Unit
)
```

#### Parameters
- **expanded**: Whether search bar is expanded showing results
- **onExpandedChange**: Callback when expanded state changes
- **windowInsets**: Window insets the search bar respects
- **content**: Search results content below input field

#### Usage Guidelines
- For full-screen behavior, parent layouts must not limit size constraints
- Host activity should set `WindowCompat.setDecorFitsSystemWindows(window, false)`
- For non-full-screen behavior on tablets, use DockedSearchBar instead
- Shape becomes SearchBarDefaults.fullScreenShape when expanded

### Deprecated String-based Overload
**Status:** Deprecated - Use overload with inputField parameter instead

---

## SecondaryScrollableTabRow

**Component Type:** Navigation
**Material Design Version:** Material 3
**Artifact:** androidx.compose.material3:material3

### Overview
Material Design scrollable secondary tabs for content area separation and hierarchy establishment. Used when tabs cannot fit on screen, allowing longer text labels and more tabs.

### Key Features
- Horizontal scrolling for overflow tabs
- Secondary emphasis styling
- Offset from starting edge with edge padding
- Animated secondary indicator
- Best for browsing on touch interfaces

### API Signature
```kotlin
@Composable
fun SecondaryScrollableTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    containerColor: Color = TabRowDefaults.secondaryContainerColor,
    contentColor: Color = TabRowDefaults.secondaryContentColor,
    edgePadding: Dp = TabRowDefaults.ScrollableTabRowEdgeStartPadding,
    indicator: @Composable TabIndicatorScope.() -> Unit = @Composable {
        TabRowDefaults.SecondaryIndicator(
            Modifier.tabIndicatorOffset(selectedTabIndex, matchContentSize = false)
        )
    },
    divider: @Composable () -> Unit = @Composable { HorizontalDivider() },
    minTabWidth: Dp = TabRowDefaults.ScrollableTabRowMinTabWidth,
    tabs: @Composable () -> Unit
)
```

### Usage Guidelines
- Use within content areas for further content separation
- Establishes hierarchy below primary navigation
- For fixed tabs, use SecondaryTabRow instead
- Edge padding indicates scrollability to users

### Related Components
- SecondaryTabRow (fixed version)
- PrimaryScrollableTabRow (primary emphasis)

---

## SecondaryTabRow

**Component Type:** Navigation
**Material Design Version:** Material 3
**Artifact:** androidx.compose.material3:material3

### Overview
Material Design fixed secondary tabs for content area separation and hierarchy. Displays all tabs simultaneously with equal spacing.

### Key Features
- Fixed layout (no scrolling)
- Equal space distribution
- Secondary emphasis styling
- Animated secondary indicator
- Swipe gesture support

### API Signature
```kotlin
@Composable
fun SecondaryTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = TabRowDefaults.secondaryContainerColor,
    contentColor: Color = TabRowDefaults.secondaryContentColor,
    indicator: @Composable TabIndicatorScope.() -> Unit = @Composable {
        TabRowDefaults.SecondaryIndicator(
            Modifier.tabIndicatorOffset(selectedTabIndex, matchContentSize = false)
        )
    },
    divider: @Composable () -> Unit = @Composable { HorizontalDivider() },
    tabs: @Composable () -> Unit
)
```

### Usage Example
```kotlin
var state by remember { mutableStateOf(0) }
val titles = listOf("Tab 1", "Tab 2", "Tab 3 with lots of text")

Column {
    SecondaryTabRow(selectedTabIndex = state) {
        titles.forEachIndexed { index, title ->
            Tab(
                selected = state == index,
                onClick = { state = index },
                text = { Text(text = title, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
    Text(
        modifier = Modifier.align(Alignment.CenterHorizontally),
        text = "Secondary tab ${state + 1} selected",
        style = MaterialTheme.typography.bodyLarge
    )
}
```

### Usage Guidelines
- Use within content areas, not for primary navigation
- For more tabs or longer labels, use SecondaryScrollableTabRow
- Tabs take equal space across entire row
- Provides hierarchy below primary navigation

### Related Components
- SecondaryScrollableTabRow (scrollable version)
- PrimaryTabRow (primary emphasis)

---

## SecureTextField

**Component Type:** Text Input
**Material Design Version:** Material 3
**Artifact:** androidx.compose.material3:material3

### Overview
Material Design filled text field specifically designed for password entry. Single-line only with security-appropriate defaults and disabled clipboard operations.

### Key Features
- Filled style with more visual emphasis
- Single-line password input
- Text obfuscation modes
- Disabled cut, copy, and drag operations
- Secure keyboard configuration
- Label positioning support

### API Signature
```kotlin
@Composable
fun SecureTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = LocalTextStyle.current,
    labelPosition: TextFieldLabelPosition = TextFieldLabelPosition.Attached(),
    label: (@Composable TextFieldLabelScope.() -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    inputTransformation: InputTransformation? = null,
    textObfuscationMode: TextObfuscationMode = TextObfuscationMode.RevealLastTyped,
    textObfuscationCharacter: Char = DefaultObfuscationCharacter,
    keyboardOptions: KeyboardOptions = SecureTextFieldKeyboardOptions,
    onKeyboardAction: KeyboardActionHandler? = null,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)? = null,
    shape: Shape = TextFieldDefaults.shape,
    colors: TextFieldColors = TextFieldDefaults.colors(),
    contentPadding: PaddingValues = /* dynamic based on label */,
    interactionSource: MutableInteractionSource? = null
)
```

### Usage Example
```kotlin
var passwordHidden by rememberSaveable { mutableStateOf(true) }

SecureTextField(
    state = rememberTextFieldState(),
    label = { Text("Enter password") },
    textObfuscationMode = if (passwordHidden)
        TextObfuscationMode.RevealLastTyped
        else TextObfuscationMode.Visible,
    trailingIcon = {
        val description = if (passwordHidden) "Show password" else "Hide password"
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                TooltipAnchorPosition.Above
            ),
            tooltip = { PlainTooltip { Text(description) } },
            state = rememberTooltipState()
        ) {
            IconButton(onClick = { passwordHidden = !passwordHidden }) {
                val visibilityIcon = if (passwordHidden)
                    Icons.Filled.Visibility
                    else Icons.Filled.VisibilityOff
                Icon(imageVector = visibilityIcon, contentDescription = description)
            }
        }
    }
)
```

### Parameters
- **textObfuscationMode**: Method to obscure text (RevealLastTyped, Hidden, Visible)
- **textObfuscationCharacter**: Character for obfuscation (default: •)
- **keyboardOptions**: Pre-configured for secure input (Password keyboard type, no autocorrect)

### Related Components
- OutlinedSecureTextField (outlined version)
- TextField (non-secure filled version)

---

## ShortNavigationBar

**Component Type:** Navigation
**Material Design Version:** Material 3
**Artifact:** androidx.compose.material3:material3
**Added in:** 1.5.0-alpha08

### Overview
Material Design short navigation bar offering persistent and convenient way to switch between primary destinations. Configuration depends on screen width.

### Key Features
- Adaptive layout for different screen sizes
- Two arrangement modes (EqualWeight, Centered)
- Vertical or horizontal item layouts
- 3-6 navigation items supported

### API Signature
```kotlin
@Composable
fun ShortNavigationBar(
    modifier: Modifier = Modifier,
    containerColor: Color = ShortNavigationBarDefaults.containerColor,
    contentColor: Color = ShortNavigationBarDefaults.contentColor,
    windowInsets: WindowInsets = ShortNavigationBarDefaults.windowInsets,
    arrangement: ShortNavigationBarArrangement = ShortNavigationBarDefaults.arrangement,
    content: @Composable () -> Unit
)
```

### Usage Examples

#### Small Screen Configuration (EqualWeight)
```kotlin
var selectedItem by remember { mutableIntStateOf(0) }
val items = listOf("Songs", "Artists", "Playlists")
val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Favorite, Icons.Filled.Star)
val unselectedIcons = listOf(Icons.Outlined.Home, Icons.Outlined.FavoriteBorder, Icons.Outlined.StarBorder)

ShortNavigationBar {
    items.forEachIndexed { index, item ->
        ShortNavigationBarItem(
            icon = {
                Icon(
                    if (selectedItem == index) selectedIcons[index] else unselectedIcons[index],
                    contentDescription = null
                )
            },
            label = { Text(item) },
            selected = selectedItem == index,
            onClick = { selectedItem = index }
        )
    }
}
```

#### Medium Screen Configuration (Centered)
```kotlin
ShortNavigationBar(arrangement = ShortNavigationBarArrangement.Centered) {
    items.forEachIndexed { index, item ->
        ShortNavigationBarItem(
            iconPosition = NavigationItemIconPosition.Start,
            icon = { Icon(/* ... */) },
            label = { Text(item) },
            selected = selectedItem == index,
            onClick = { selectedItem = index }
        )
    }
}
```

### Configuration Guidelines
- **Small screens** (phone portrait): 3-5 items, EqualWeight arrangement, icon on top
- **Medium screens** (phone landscape): 3-6 items, Centered arrangement, icon at start

### Related Components
- ShortNavigationBarItem
- NavigationBar (standard navigation bar)

---

## ShortNavigationBarItem

**Component Type:** Navigation Item
**Material Design Version:** Material 3
**Artifact:** androidx.compose.material3:material3
**Added in:** 1.5.0-alpha08

### Overview
Individual item for ShortNavigationBar. Supports two icon positions (top and start) for different screen configurations.

### Key Features
- Always displays labels when selected and unselected
- Two icon position modes
- Adaptive to screen size
- Customizable colors

### API Signature
```kotlin
@Composable
fun ShortNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconPosition: NavigationItemIconPosition = NavigationItemIconPosition.Top,
    colors: NavigationItemColors = ShortNavigationBarItemDefaults.colors(),
    interactionSource: MutableInteractionSource? = null
)
```

### Parameters
- **iconPosition**: NavigationItemIconPosition.Top (icon above label) or .Start (icon before label)
- **label**: Text label (recommended to always provide)

### Usage Guidelines
- Always provide text labels for accessibility
- Use Top icon position for small width screens
- Use Start icon position for medium width screens
- Labels always display (unlike NavigationBarItem)

---

## SingleChoiceSegmentedButtonRow

**Component Type:** Selection Control
**Material Design Version:** Material 3
**Artifact:** androidx.compose.material3:material3

### Overview
Layout for positioning and sizing SegmentedButtons in a Row for single-choice selection. Handles overlapping items for correct stroke rendering.

### Key Features
- Single selection semantics
- Correct stroke overlap handling
- Configurable spacing between buttons
- Automatic shape assignment

### API Signature
```kotlin
@Composable
fun SingleChoiceSegmentedButtonRow(
    modifier: Modifier = Modifier,
    space: Dp = SegmentedButtonDefaults.BorderWidth,
    content: @Composable SingleChoiceSegmentedButtonRowScope.() -> Unit
)
```

### Usage Example
```kotlin
var selectedIndex by remember { mutableStateOf(0) }
val options = listOf("Day", "Month", "Week")

SingleChoiceSegmentedButtonRow {
    options.forEachIndexed { index, label ->
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            onClick = { selectedIndex = index },
            selected = index == selectedIndex
        ) {
            Text(label)
        }
    }
}
```

### Parameters
- **space**: Overlap dimension between buttons (should equal stroke width)

### Related Components
- MultiChoiceSegmentedButtonRow (multi-selection)
- SegmentedButton (individual button)

---

## Slider

**Component Type:** Input Control
**Material Design Version:** Material 3
**Artifact:** androidx.compose.material3:material3
**Added in:** 1.2.0
**API Level:** @ExperimentalMaterial3Api

### Overview
Material Design slider allowing users to make selections from a range of values. Ideal for adjusting settings like volume, brightness, or image filters.

### Key Features
- Continuous or stepped values
- Customizable thumb and track
- Tooltip support with Label component
- State-based API
- Interaction source observation

### API Signature
```kotlin
@Composable
@ExperimentalMaterial3Api
fun Slider(
    state: SliderState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    thumb: @Composable (SliderState) -> Unit = { /* default thumb */ },
    track: @Composable (SliderState) -> Unit = { /* default track */ }
)
```

### Usage Examples

#### Continuous Slider
```kotlin
var sliderPosition by rememberSaveable { mutableStateOf(0f) }

Column(modifier = Modifier.padding(horizontal = 16.dp)) {
    Text(text = "%.2f".format(sliderPosition))
    Slider(value = sliderPosition, onValueChange = { sliderPosition = it })
}
```

#### Stepped Slider
```kotlin
val sliderState = rememberSliderState(
    steps = 9, // Only allow multiples of 10
    valueRange = 0f..100f,
    onValueChangeFinished = {
        // Business logic update
    }
)

Column(modifier = Modifier.padding(horizontal = 16.dp)) {
    Text(text = "%.2f".format(sliderState.value))
    Slider(state = sliderState)
}
```

### Parameters
- **state**: SliderState managing slider value and range
- **steps**: Number of discrete steps (0 for continuous)
- **thumb**: Custom thumb composable
- **track**: Custom track composable

### Usage Guidelines
- Use continuous sliders for selections not requiring specific values
- Use stepped sliders for predefined value sets
- Steps parameter excludes endpoints
- Use Label component for tooltips on thumb

### Related Components
- RangeSlider (dual thumb version)
- SliderDefaults (styling)


## Slider (Additional Overloads)

**Component Type:** Input Control
**Material Design Version:** Material 3
**Artifact:** androidx.compose.material3:material3
**Added in:** 1.0.0 (value-based), 1.2.0 (custom thumb/track)

### Value-based Overload
Simple slider using Float value and callback.

#### API Signature
```kotlin
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: @IntRange(from = 0) Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
)
```

### Custom Thumb/Track Overload (Added in 1.2.0)
Slider with customizable thumb and track components.

#### API Signature
```kotlin
@Composable
@ExperimentalMaterial3Api
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    steps: @IntRange(from = 0) Int = 0,
    thumb: @Composable (SliderState) -> Unit = { /* default */ },
    track: @Composable (SliderState) -> Unit = { /* default */ },
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
)
```

### Usage Examples

#### Custom Thumb with Tooltip
```kotlin
var sliderPosition by rememberSaveable { mutableStateOf(0f) }
val interactionSource = remember { MutableInteractionSource() }

Slider(
    value = sliderPosition,
    onValueChange = { sliderPosition = it },
    valueRange = 0f..100f,
    interactionSource = interactionSource,
    thumb = {
        Label(
            label = {
                PlainTooltip(modifier = Modifier.sizeIn(45.dp, 25.dp).wrapContentWidth()) {
                    Text("%.2f".format(sliderPosition))
                }
            },
            interactionSource = interactionSource
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
                tint = Color.Red
            )
        }
    }
)
```

#### Custom Track and Thumb Colors
```kotlin
val sliderState = rememberSliderState(valueRange = 0f..100f)
val interactionSource = remember { MutableInteractionSource() }
val colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red)

Slider(
    state = sliderState,
    interactionSource = interactionSource,
    thumb = {
        SliderDefaults.Thumb(interactionSource = interactionSource, colors = colors)
    },
    track = {
        SliderDefaults.Track(colors = colors, sliderState = sliderState)
    }
)
```

#### Centered Track Variant
```kotlin
Slider(
    state = sliderState,
    thumb = { SliderDefaults.Thumb(interactionSource = interactionSource) },
    track = { SliderDefaults.CenteredTrack(sliderState = sliderState) }
)
```

### Parameters
- **value**: Current slider value (coerced to valueRange)
- **onValueChange**: Callback for value updates
- **valueRange**: Allowed value range (default: 0f..1f)
- **steps**: Discrete steps between endpoints (0 for continuous)
- **onValueChangeFinished**: Callback when interaction ends
- **thumb**: Custom thumb composable
- **track**: Custom track composable (Track or CenteredTrack)

### Usage Guidelines
- Use onValueChangeFinished for business logic, not onValueChange
- Steps parameter must not be negative
- Steps excludes endpoints (e.g., steps=9 for 0-100 gives 10, 20, ..., 90)
- Custom thumbs can include tooltips using Label component
- CenteredTrack variant useful for bidirectional values

### Related Components
- RangeSlider (dual thumb)
- SliderDefaults (styling and components)
- Label (for tooltips)

---

**Note:** Chunks 19 and 20 primarily contain additional Slider overloads and parameter documentation. The core components have been documented above with their key variations and usage patterns.


## SplitButton Size Variants

Split buttons support multiple size variants to accommodate different UI densities and use cases.

### Extra Small Split Button
```kotlin
var checked by remember { mutableStateOf(false) }
val size = SplitButtonDefaults.ExtraSmallContainerHeight

SplitButtonLayout(
    leadingButton = {
        SplitButtonDefaults.LeadingButton(
            onClick = { /* Do Nothing */ },
            modifier = Modifier.heightIn(size),
            shapes = SplitButtonDefaults.leadingButtonShapesFor(size),
            contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(size),
        ) {
            Icon(Icons.Filled.Edit, modifier = Modifier.size(SplitButtonDefaults.leadingButtonIconSizeFor(size)))
            Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(size)))
            Text("My Button", style = ButtonDefaults.textStyleFor(size))
        }
    },
    trailingButton = {
        SplitButtonDefaults.TrailingButton(
            checked = checked,
            onCheckedChange = { checked = it },
            modifier = Modifier.heightIn(size).semantics {
                stateDescription = if (checked) "Expanded" else "Collapsed"
            }
        ) {
            val rotation by animateFloatAsState(if (checked) 180f else 0f)
            Icon(Icons.Filled.KeyboardArrowDown,
                modifier = Modifier.size(SplitButtonDefaults.trailingButtonIconSizeFor(size))
                    .graphicsLayer { rotationZ = rotation })
        }
    }
)
```

### Medium, Large, and Extra Large Variants
Split buttons can be sized using `SplitButtonDefaults.MediumContainerHeight`, `SplitButtonDefaults.LargeContainerHeight`, and `SplitButtonDefaults.ExtraLargeContainerHeight` following the same pattern as the extra small variant.

## SplitButton Emphasis Hierarchy

Choose the appropriate split button variant based on the emphasis level needed:

- **Filled Split Button** (High emphasis): Use `SplitButtonDefaults.LeadingButton` and `SplitButtonDefaults.TrailingButton` for important or final actions
- **Tonal Split Button** (Medium emphasis): Use `SplitButtonDefaults.TonalLeadingButton` and `SplitButtonDefaults.TonalTrailingButton` as a middle ground
- **Elevated Split Button** (Medium emphasis with shadow): Use `SplitButtonDefaults.ElevatedLeadingButton` and `SplitButtonDefaults.ElevatedTrailingButton` when visual separation from patterned containers is needed
- **Outlined Split Button** (Medium emphasis): Use `SplitButtonDefaults.OutlinedLeadingButton` and `SplitButtonDefaults.OutlinedTrailingButton` for secondary actions

## SuggestionChip

Suggestion chips help narrow user intent by presenting dynamically generated suggestions.

```kotlin
SuggestionChip(
    onClick = { /* Do something! */ },
    label = { Text("Suggestion Chip") }
)
```

**Parameters:**
- `onClick`: Callback when chip is clicked
- `label`: Text label for the chip
- `enabled`: Controls enabled state (default: true)
- `icon`: Optional leading icon
- `shape`: Chip container shape
- `colors`: Chip colors for different states
- `elevation`: Chip elevation
- `border`: Optional border stroke
- `interactionSource`: Optional interaction source for observing interactions

## Surface

Material surface is the central metaphor in Material Design. Each surface exists at a given elevation.

### Basic Surface
```kotlin
Surface {
    Text("Text on Surface")
}
```

**Surface Responsibilities:**
- **Clipping**: Clips children to the specified shape
- **Borders**: Draws borders if shape has one
- **Background**: Fills shape with color, applies tonal elevation overlay
- **Content Color**: Provides preferred color for content (Text, Icon)
- **Touch Propagation**: Blocks touch propagation behind the surface

### Clickable Surface
```kotlin
var count by remember { mutableStateOf(0) }
Surface(onClick = { count++ }) {
    Text("Clickable Surface. Count: $count")
}
```

### Toggleable Surface
```kotlin
var checked by remember { mutableStateOf(false) }
Surface(
    checked = checked,
    onCheckedChange = { checked = !checked },
    color = if (checked) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
) {
    Text(text = if (checked) "ON" else "OFF", textAlign = TextAlign.Center)
}
```

### Selectable Surface
```kotlin
var selected by remember { mutableStateOf(false) }
Surface(selected = selected, onClick = { selected = !selected }) {
    Text(text = if (selected) "Selected" else "Not Selected", textAlign = TextAlign.Center)
}
```

**Common Parameters:**
- `modifier`: Modifier for the surface
- `shape`: Surface shape and shadow definition
- `color`: Background color
- `contentColor`: Preferred content color
- `tonalElevation`: Elevation affecting color overlay (default: 0.dp)
- `shadowElevation`: Shadow size below surface (default: 0.dp)
- `border`: Optional border stroke

## SwipeToDismissBox

A composable that can be dismissed by swiping left or right.

```kotlin
val dismissState = rememberSwipeToDismissBoxState()
var isVisible by remember { mutableStateOf(true) }
val scope = rememberCoroutineScope()

if (isVisible) {
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.Settled -> Color.LightGray
                    SwipeToDismissBoxValue.StartToEnd -> Color.Green
                    SwipeToDismissBoxValue.EndToStart -> Color.Red
                }
            )
            Box(Modifier.fillMaxSize().background(color))
        },
        onDismiss = { direction ->
            if (direction == SwipeToDismissBoxValue.EndToStart) {
                isVisible = false
            } else {
                scope.launch { dismissState.reset() }
            }
        }
    ) {
        OutlinedCard(shape = RectangleShape) {
            ListItem(
                headlineContent = { Text("Cupcake") },
                supportingContent = { Text("Swipe me left or right!") }
            )
        }
    }
}
```

**Parameters:**
- `state`: Component state
- `backgroundContent`: Content exposed when swiped
- `enableDismissFromStartToEnd`: Allow dismiss from start to end (default: true)
- `enableDismissFromEndToStart`: Allow dismiss from end to start (default: true)
- `gesturesEnabled`: Enable swipe gestures (default: true)
- `onDismiss`: Callback when content is dismissed
- `content`: The dismissible content

## Switch

Switches toggle the state of a single item on or off.

### Basic Switch
```kotlin
var checked by remember { mutableStateOf(true) }
Switch(
    modifier = Modifier.semantics { contentDescription = "Demo" },
    checked = checked,
    onCheckedChange = { checked = it }
)
```

### Switch with Custom Icon
```kotlin
var checked by remember { mutableStateOf(true) }
Switch(
    checked = checked,
    onCheckedChange = { checked = it },
    thumbContent = {
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize)
            )
        }
    }
)
```

**Parameters:**
- `checked`: Whether switch is checked
- `onCheckedChange`: Callback when clicked (null makes it non-interactable)
- `thumbContent`: Optional content drawn inside thumb
- `enabled`: Controls enabled state (default: true)
- `colors`: Switch colors for different states
- `interactionSource`: Optional interaction source

## Tab

Tabs organize content across different screens and data sets.

### Custom Tab
```kotlin
Tab(selected, onClick) {
    Column(
        Modifier.padding(10.dp).height(50.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            Modifier.size(10.dp)
                .align(Alignment.CenterHorizontally)
                .background(
                    color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.background
                )
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
```


### Tab with Text and Icon
```kotlin
Tab(
    selected = selected,
    onClick = onClick,
    text = { Text("Tab Label") },
    icon = { Icon(Icons.Filled.Favorite, contentDescription = null) }
)
```

**Parameters:**
- `selected`: Whether tab is selected
- `onClick`: Callback when clicked
- `text`: Optional text label
- `icon`: Optional icon
- `enabled`: Controls enabled state (default: true)
- `selectedContentColor`: Color when selected
- `unselectedContentColor`: Color when not selected
- `interactionSource`: Optional interaction source

## TabRow

Fixed tabs display all tabs simultaneously. For scrollable tabs, use `ScrollableTabRow`.

### Basic TabRow
```kotlin
var state by remember { mutableStateOf(0) }
val titles = listOf("Tab 1", "Tab 2", "Tab 3")

Column {
    PrimaryTabRow(selectedTabIndex = state) {
        titles.forEachIndexed { index, title ->
            Tab(
                selected = state == index,
                onClick = { state = index },
                text = { Text(text = title, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
    Text(
        modifier = Modifier.align(Alignment.CenterHorizontally),
        text = "Text tab ${state + 1} selected",
        style = MaterialTheme.typography.bodyLarge
    )
}
```

### TabRow with Custom Indicator
```kotlin
SecondaryTabRow(
    selectedTabIndex = state,
    indicator = {
        FancyIndicator(
            MaterialTheme.colorScheme.primary,
            Modifier.tabIndicatorOffset(state)
        )
    }
) {
    titles.forEachIndexed { index, title ->
        Tab(selected = state == index, onClick = { state = index }, text = { Text(title) })
    }
}
```

**Parameters:**
- `selectedTabIndex`: Index of currently selected tab
- `containerColor`: Background color
- `contentColor`: Preferred content color
- `indicator`: Visual indicator for selected tab
- `divider`: Divider at bottom of tab row
- `tabs`: Tab composables

## Text

High-level element that displays text with semantics and accessibility support.

### Basic Text
```kotlin
Text("Hello World")
```

### Text with Styling
```kotlin
Text(
    text = "Styled Text",
    color = MaterialTheme.colorScheme.primary,
    fontSize = 20.sp,
    fontWeight = FontWeight.Bold,
    textAlign = TextAlign.Center
)
```

### Text with Links
```kotlin
val url = "https://developer.android.com/jetpack/compose"
val annotatedString = buildAnnotatedString {
    append("Build better apps faster with ")
    withLink(LinkAnnotation.Url(url = url)) {
        append("Jetpack Compose")
    }
}
Text(annotatedString)
```

### Text with ColorProducer
Use `ColorProducer` for frequently changing colors without recomposition:
```kotlin
Text(
    text = "Dynamic Color Text",
    color = { animatedColor }
)
```

**Common Parameters:**
- `text`: String or AnnotatedString to display
- `modifier`: Modifier for layout
- `color`: Text color (or ColorProducer)
- `fontSize`: Glyph size
- `fontStyle`: Typeface variant (e.g., italic)
- `fontWeight`: Typeface thickness
- `fontFamily`: Font family
- `letterSpacing`: Space between letters
- `textDecoration`: Decorations (e.g., underline)
- `textAlign`: Text alignment
- `lineHeight`: Line height
- `overflow`: Visual overflow handling
- `softWrap`: Whether to break at soft line breaks (default: true)
- `maxLines`: Maximum lines (default: Int.MAX_VALUE)
- `minLines`: Minimum lines (default: 1)
- `autoSize`: Enable auto-sizing to fit available space
- `onTextLayout`: Callback for text layout calculation
- `style`: Text style configuration

## TextButton

Text buttons are used for the lowest priority actions.

### Standard TextButton
```kotlin
TextButton(onClick = { /* Do something! */ }) {
    Text("Text Button")
}
```

### TextButton with Shape Morphing (Expressive)
```kotlin
TextButton(
    onClick = { /* Do something! */ },
    shapes = ButtonDefaults.shapes()
) {
    Text("Text Button")
}
```

**Button Emphasis Hierarchy:**
- **Button** (Filled): High emphasis, no shadow
- **ElevatedButton**: Filled tonal with shadow
- **FilledTonalButton**: Medium emphasis
- **OutlinedButton**: Medium emphasis with border
- **TextButton**: Lowest emphasis

**Parameters:**
- `onClick`: Callback when clicked
- `shapes`: ButtonShapes for morphing (expressive variant)
- `enabled`: Controls enabled state (default: true)
- `shape`: Container shape (standard variant)
- `colors`: Button colors for different states
- `elevation`: Button elevation (default: null for TextButton)
- `border`: Optional border stroke
- `contentPadding`: Internal spacing
- `interactionSource`: Optional interaction source
- `content`: Button content (expected to be text)


## TextField (Continued)

**Additional Parameters:**
- `scrollState`: Scroll state for text field
- `shape`: Text field container shape
- `colors`: Text field colors for different states
- `contentPadding`: Padding between container and content
- `interactionSource`: Optional interaction source

## TimeInput

Time input allows users to enter time via two text fields (hours and minutes).

```kotlin
var showTimePicker by remember { mutableStateOf(false) }
val state = rememberTimePickerState()

if (showTimePicker) {
    TimePickerDialog(
        title = { TimePickerDialogDefaults.Title(displayMode = TimePickerDisplayMode.Input) },
        onDismissRequest = { showTimePicker = false },
        confirmButton = {
            TextButton(onClick = {
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, state.hour)
                cal.set(Calendar.MINUTE, state.minute)
                showTimePicker = false
            }) { Text("Ok") }
        },
        dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } }
    ) {
        TimeInput(state = state)
    }
}
```

**Parameters:**
- `state`: TimePickerState for subscribing to hour/minute changes
- `colors`: Time picker colors for different states

## TimePicker

Visual time picker that allows users to select time using a clock interface.

```kotlin
var showTimePicker by remember { mutableStateOf(false) }
val state = rememberTimePickerState()

if (showTimePicker) {
    TimePickerDialog(
        title = { TimePickerDialogDefaults.Title(displayMode = TimePickerDisplayMode.Picker) },
        onDismissRequest = { showTimePicker = false },
        confirmButton = {
            TextButton(
                enabled = state.isInputValid,
                onClick = {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.HOUR_OF_DAY, state.hour)
                    cal.set(Calendar.MINUTE, state.minute)
                    showTimePicker = false
                }
            ) { Text("Ok") }
        },
        dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } }
    ) {
        TimePicker(state = state)
    }
}
```

### TimePicker with Mode Toggle
```kotlin
var displayMode by remember { mutableStateOf(TimePickerDisplayMode.Picker) }
val configuration = LocalConfiguration.current

TimePickerDialog(
    title = { TimePickerDialogDefaults.Title(displayMode = displayMode) },
    onDismissRequest = { showTimePicker = false },
    modeToggleButton = {
        if (configuration.screenHeightDp.dp > MinHeightForTimePicker) {
            TimePickerDialogDefaults.DisplayModeToggle(
                onDisplayModeChange = {
                    displayMode = if (displayMode == TimePickerDisplayMode.Picker) {
                        TimePickerDisplayMode.Input
                    } else {
                        TimePickerDisplayMode.Picker
                    }
                },
                displayMode = displayMode
            )
        }
    }
) {
    if (displayMode == TimePickerDisplayMode.Picker &&
        configuration.screenHeightDp.dp > MinHeightForTimePicker) {
        TimePicker(state = state)
    } else {
        TimeInput(state = state)
    }
}
```

**Parameters:**
- `state`: TimePickerState for time selection
- `colors`: Time picker colors
- `layoutType`: Layout configuration for time picker components

## TimePickerDialog

Dialog container for displaying time pickers.

**Parameters:**
- `onDismissRequest`: Callback when user dismisses dialog
- `confirmButton`: Confirmation button composable
- `title`: Dialog title
- `properties`: Dialog properties
- `modeToggleButton`: Optional toggle between clock and text input
- `dismissButton`: Optional dismiss button
- `shape`: Dialog surface shape
- `containerColor`: Dialog container color
- `content`: Dialog content (TimePicker or TimeInput)

## TimePickerState

State holder for time picker components.

```kotlin
val state = rememberTimePickerState(
    initialHour = 12,
    initialMinute = 30,
    is24Hour = false
)
```

**Parameters:**
- `initialHour`: Starting hour (0-23)
- `initialMinute`: Starting minute (0-59)
- `is24Hour`: Format (false for 12-hour with AM/PM, true for 24-hour)

## ToggleButton

Toggleable button that switches between primary and tonal colors with shape morphing.

### Basic ToggleButton
```kotlin
var checked by remember { mutableStateOf(false) }
ToggleButton(checked = checked, onCheckedChange = { checked = it }) {
    Text("Button")
}
```

### ToggleButton with Icon
```kotlin
var checked by remember { mutableStateOf(false) }
ToggleButton(checked = checked, onCheckedChange = { checked = it }) {
    Icon(
        if (checked) Icons.Filled.Edit else Icons.Outlined.Edit,
        contentDescription = "Localized description",
        modifier = Modifier.size(ButtonDefaults.IconSize)
    )
    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
    Text("Edit")
}
```

### ToggleButton Size Variants
```kotlin
var checked by remember { mutableStateOf(false) }
val size = ButtonDefaults.ExtraSmallContainerHeight

ToggleButton(
    checked = checked,
    onCheckedChange = { checked = it },
    modifier = Modifier.heightIn(size),
    shapes = ToggleButtonDefaults.shapesFor(size),
    contentPadding = ButtonDefaults.contentPaddingFor(size)
) {
    Icon(
        if (checked) Icons.Filled.Edit else Icons.Outlined.Edit,
        modifier = Modifier.size(ButtonDefaults.iconSizeFor(size))
    )
    Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(size)))
    Text("Label")
}
```

Available sizes: `ExtraSmallContainerHeight`, `MediumContainerHeight`, `LargeContainerHeight`, `ExtraLargeContainerHeight`

### Custom Shape Morphing
```kotlin
var checked by remember { mutableStateOf(false) }
val shapes = ToggleButtonShapes(
    shape = ToggleButtonDefaults.squareShape,
    pressedShape = ToggleButtonDefaults.pressedShape,
    checkedShape = ToggleButtonDefaults.roundShape
)
ToggleButton(checked = checked, onCheckedChange = { checked = it }, shapes = shapes) {
    Text("Button")
}
```

**Parameters:**
- `checked`: Whether button is toggled on/off
- `onCheckedChange`: Callback when clicked
- `shapes`: ToggleButtonShapes for morphing between states
- `enabled`: Controls enabled state (default: true)
- `colors`: Button colors for different states
- `elevation`: Button elevation
- `border`: Optional border stroke
- `contentPadding`: Internal spacing
- `interactionSource`: Optional interaction source
- `content`: Button content (text, icon, or image)

**Related Components:**
- `Button`: Static button without toggle
- `IconToggleButton`: Toggleable button with icon content
- `ElevatedToggleButton`: Toggle button with elevation
- `TonalToggleButton`: Medium-emphasis toggle button
- `OutlinedToggleButton`: Toggle button with border


## TooltipBox

Material TooltipBox wraps a composable with a tooltip that provides descriptive messages.

### Plain Tooltip
```kotlin
TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    tooltip = { PlainTooltip { Text("Add to favorites") } },
    state = rememberTooltipState()
) {
    IconButton(onClick = { /* Icon button's click event */ }) {
        Icon(imageVector = Icons.Filled.Favorite, contentDescription = "Localized Description")
    }
}
```

### Tooltip Positioning
Tooltips can be positioned relative to the anchor:
- `TooltipAnchorPosition.Above`: Above the anchor
- `TooltipAnchorPosition.Below`: Below the anchor
- `TooltipAnchorPosition.Left`: Left of the anchor
- `TooltipAnchorPosition.Right`: Right of the anchor
- `TooltipAnchorPosition.Start`: Start of the anchor
- `TooltipAnchorPosition.End`: End of the anchor

### Plain Tooltip with Caret
```kotlin
TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    tooltip = {
        PlainTooltip(caretShape = TooltipDefaults.caretShape()) {
            Text("Add to favorites")
        }
    },
    state = rememberTooltipState()
) {
    IconButton(onClick = { /* Icon button's click event */ }) {
        Icon(imageVector = Icons.Filled.Favorite, contentDescription = "Localized Description")
    }
}
```

### Custom Caret Size
```kotlin
PlainTooltip(caretShape = TooltipDefaults.caretShape(DpSize(24.dp, 12.dp))) {
    Text("Add to favorites")
}
```

### Rich Tooltip
```kotlin
val tooltipState = rememberTooltipState(isPersistent = true)
val scope = rememberCoroutineScope()

TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    tooltip = {
        RichTooltip(
            title = { Text("Tooltip Title") },
            action = {
                TextButton(onClick = { scope.launch { tooltipState.dismiss() } }) {
                    Text("Action")
                }
            }
        ) {
            Text("Tooltip content text")
        }
    },
    hasAction = true,
    state = tooltipState
) {
    IconButton(onClick = { /* Icon button's click event */ }) {
        Icon(imageVector = Icons.Filled.Info, contentDescription = "Localized Description")
    }
}
```

### Programmatic Tooltip Control
```kotlin
val tooltipState = rememberTooltipState(isPersistent = true)
val scope = rememberCoroutineScope()

Column(horizontalAlignment = Alignment.CenterHorizontally) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { RichTooltip(title = { Text("Title") }) { Text("Content") } },
        hasAction = true,
        state = tooltipState
    ) {
        IconButton(onClick = { /* Icon button's click event */ }) {
            Icon(imageVector = Icons.Filled.Info, contentDescription = "Localized Description")
        }
    }
    Spacer(Modifier.requiredHeight(30.dp))
    OutlinedButton(onClick = { scope.launch { tooltipState.show() } }) {
        Text("Display tooltip")
    }
}
```

**Parameters:**
- `positionProvider`: Positions tooltip relative to anchor
- `tooltip`: Tooltip content composable
- `state`: TooltipState for visibility control
- `onDismissRequest`: Callback when user clicks outside (default: dismisses tooltip)
- `focusable`: Whether tooltip is focusable (default: false)
- `enableUserInput`: Enable long press/hover/focus triggers (default: true)
- `hasAction`: Whether tooltip contains an action
- `content`: Anchor composable

## TooltipState

State holder for tooltip visibility.

```kotlin
val tooltipState = rememberTooltipState(
    initialIsVisible = false,
    isPersistent = true
)
```

**Parameters:**
- `initialIsVisible`: Initial visibility (default: false)
- `isPersistent`: If true, dismisses only on outside click or explicit dismiss; if false, dismisses after short duration (default: true)
- `mutatorMutex`: Ensures only one tooltip shows at a time

## TopAppBar

Material Design top app bar displays information and actions at the top of the screen.

### Basic TopAppBar
```kotlin
Scaffold(
    topBar = {
        TopAppBar(
            title = { Text("Simple TopAppBar", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(imageVector = Icons.Filled.Menu, contentDescription = "Menu")
                }
            },
            actions = {
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(imageVector = Icons.Filled.Favorite, contentDescription = "Add to favorites")
                }
            }
        )
    }
) { innerPadding ->
    // Content
}
```

### TopAppBar with Scroll Behavior
```kotlin
val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
        TopAppBar(
            title = { Text("TopAppBar", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(imageVector = Icons.Filled.Menu, contentDescription = "Menu")
                }
            },
            actions = {
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(imageVector = Icons.Filled.Favorite, contentDescription = "Add to favorites")
                }
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(imageVector = Icons.Filled.Star, contentDescription = "Star")
                }
            },
            scrollBehavior = scrollBehavior
        )
    }
) { innerPadding ->
    LazyColumn(contentPadding = innerPadding) {
        // Scrollable content
    }
}
```

### TopAppBar with Subtitle (Expressive)
```kotlin
val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

TopAppBar(
    title = { Text("TopAppBar", maxLines = 1, overflow = TextOverflow.Ellipsis) },
    subtitle = { Text("Subtitle", maxLines = 1, overflow = TextOverflow.Ellipsis) },
    navigationIcon = {
        IconButton(onClick = { /* doSomething() */ }) {
            Icon(imageVector = Icons.Filled.Menu, contentDescription = "Menu")
        }
    },
    actions = {
        IconButton(onClick = { /* doSomething() */ }) {
            Icon(imageVector = Icons.Filled.Favorite, contentDescription = "Add to favorites")
        }
    },
    scrollBehavior = scrollBehavior
)
```

### Centered Title TopAppBar
```kotlin
TopAppBar(
    title = { Text("Simple TopAppBar", maxLines = 1, overflow = TextOverflow.Ellipsis) },
    subtitle = { Text("Subtitle", maxLines = 1, overflow = TextOverflow.Ellipsis) },
    titleHorizontalAlignment = Alignment.CenterHorizontally,
    navigationIcon = {
        IconButton(onClick = { /* doSomething() */ }) {
            Icon(imageVector = Icons.Filled.Menu, contentDescription = "Menu")
        }
    },
    actions = {
        IconButton(onClick = { /* doSomething() */ }) {
            Icon(imageVector = Icons.Filled.Favorite, contentDescription = "Add to favorites")
        }
    },
    scrollBehavior = scrollBehavior
)
```

**Parameters:**
- `title`: Title composable
- `subtitle`: Subtitle composable (expressive variant)
- `navigationIcon`: Leading navigation icon (typically IconButton)
- `actions`: Trailing action icons (Row layout)
- `titleHorizontalAlignment`: Title/subtitle alignment (expressive variant)
- `expandedHeight`: Maximum app bar height
- `windowInsets`: Window insets to respect
- `colors`: Top app bar colors for different states
- `scrollBehavior`: Scroll behavior for collapsing/expanding
- `contentPadding`: Content padding

**Scroll Behaviors:**
- `TopAppBarDefaults.pinnedScrollBehavior()`: Stays pinned at top
- `TopAppBarDefaults.enterAlwaysScrollBehavior()`: Appears when scrolling up
- `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()`: Collapses when scrolling down

## AppBarWithSearch (TopSearchBar)

Search bar with top app bar behavior for window insets and scrolling.

### Full Screen Search
```kotlin
val textFieldState = rememberTextFieldState()
val searchBarState = rememberSearchBarState()
val scope = rememberCoroutineScope()
val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()

val inputField = @Composable {
    SearchBarDefaults.InputField(
        searchBarState = searchBarState,
        textFieldState = textFieldState,
        onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
        placeholder = {
            if (searchBarState.currentValue == SearchBarValue.Collapsed) {
                Text(text = "Search", textAlign = TextAlign.Center)
            }
        },
        leadingIcon = {
            if (searchBarState.currentValue == SearchBarValue.Expanded) {
                IconButton(onClick = { scope.launch { searchBarState.animateToCollapsed() } }) {
                    Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                }
            } else {
                Icon(Icons.Default.Search, contentDescription = null)
            }
        }
    )
}

Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
        AppBarWithSearch(
            scrollBehavior = scrollBehavior,
            state = searchBarState,
            inputField = inputField,
            navigationIcon = {
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                }
            },
            actions = {
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(imageVector = Icons.Default.AccountCircle, contentDescription = "Account")
                }
            }
        )
        ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
            // Search results
        }
    }
) { padding ->
    // Content
}
```

### Docked Search
```kotlin
ExpandedDockedSearchBar(
    state = searchBarState,
    inputField = inputField
) {
    // Search results
}
```

**Parameters:**
- `state`: SearchBarState for search bar state
- `inputField`: Input field composable
- `shape`: Search bar shape
- `colors`: Search bar colors
- `tonalElevation`: Tonal elevation
- `shadowElevation`: Shadow elevation
- `windowInsets`: Window insets
- `scrollBehavior`: Search bar scroll behavior


### TriStateCheckbox

A tri-state checkbox supports parent-child relationships where a parent checkbox can be in an indeterminate state when some (but not all) child checkboxes are selected.

**Function Signature:**
```kotlin
@Composable
fun TriStateCheckbox(
    state: ToggleableState,
    onClick: (() -> Unit)?,
    checkmarkStroke: Stroke,
    outlineStroke: Stroke,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(),
    interactionSource: MutableInteractionSource? = null
)
```

**Use Case:** Use TriStateCheckbox when implementing hierarchical selection patterns where a parent checkbox controls multiple child checkboxes.

**Example with Custom Stroke:**
```kotlin
val strokeWidthPx = with(LocalDensity.current) { floor(CheckboxDefaults.StrokeWidth.toPx()) }
val checkmarkStroke = remember(strokeWidthPx) {
    Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
}
val outlineStroke = remember(strokeWidthPx) { Stroke(width = strokeWidthPx) }

Column {
    val (state, onStateChange) = remember { mutableStateOf(true) }
    val (state2, onStateChange2) = remember { mutableStateOf(true) }

    val parentState = remember(state, state2) {
        if (state && state2) ToggleableState.On
        else if (!state && !state2) ToggleableState.Off
        else ToggleableState.Indeterminate
    }

    val onParentClick = {
        val s = parentState != ToggleableState.On
        onStateChange(s)
        onStateChange2(s)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.triStateToggleable(
            state = parentState,
            onClick = onParentClick,
            role = Role.Checkbox
        )
    ) {
        TriStateCheckbox(
            state = parentState,
            onClick = null,
            checkmarkStroke = checkmarkStroke,
            outlineStroke = outlineStroke
        )
        Text("Receive Emails")
    }

    Spacer(Modifier.size(25.dp))

    Column(Modifier.padding(24.dp, 0.dp, 0.dp, 0.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.toggleable(
                value = state,
                onValueChange = onStateChange,
                role = Role.Checkbox
            )
        ) {
            Checkbox(
                checked = state,
                onCheckedChange = null,
                checkmarkStroke = checkmarkStroke,
                outlineStroke = outlineStroke
            )
            Text("Daily")
        }

        Spacer(Modifier.size(25.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.toggleable(
                value = state2,
                onValueChange = onStateChange2,
                role = Role.Checkbox
            )
        ) {
            Checkbox(
                checked = state2,
                onCheckedChange = null,
                checkmarkStroke = checkmarkStroke,
                outlineStroke = outlineStroke
            )
            Text("Weekly")
        }
    }
}
```

**Parameters:**
- `state`: ToggleableState (On, Off, or Indeterminate)
- `onClick`: Callback when checkbox is clicked (null for non-interactive)
- `checkmarkStroke`: Stroke configuration for the checkmark
- `outlineStroke`: Stroke configuration for the box outline
- `modifier`: Modifier to apply to the checkbox
- `enabled`: Controls enabled state (default: true)
- `colors`: CheckboxColors for different states
- `interactionSource`: Optional MutableInteractionSource for observing interactions

### TwoRowsTopAppBar

A two-row top app bar displays titles and subtitles in separate rows, with support for expanded and collapsed states.

**Function Signature:**
```kotlin
@ExperimentalMaterial3ExpressiveApi
@Composable
fun TwoRowsTopAppBar(
    title: @Composable (expanded: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable (expanded: Boolean) -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    titleHorizontalAlignment: Alignment.Horizontal = Alignment.Start,
    collapsedHeight: Dp = Dp.Unspecified,
    expandedHeight: Dp = Dp.Unspecified,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null
)
```

**Use Case:** Use TwoRowsTopAppBar when you need to display both a title and subtitle with different content in expanded and collapsed states.

**Example with Scroll Behavior:**
```kotlin
val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
        TwoRowsTopAppBar(
            title = { expanded ->
                if (expanded) {
                    Text("Expanded TopAppBar", maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text("Collapsed TopAppBar", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            subtitle = { expanded ->
                if (expanded) {
                    Text(
                        "Expanded Subtitle",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                } else {
                    Text("Collapsed Subtitle", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            collapsedHeight = 64.dp,
            expandedHeight = 156.dp,
            navigationIcon = {
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(imageVector = Icons.Filled.Menu, contentDescription = "Menu")
                }
            },
            scrollBehavior = scrollBehavior
        )
    },
    content = { innerPadding ->
        Column(
            Modifier.fillMaxWidth()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) {
                Text(text = remember { LoremIpsum().values.first() })
            }
        }
    }
)
```

**Parameters:**
- `title`: Lambda providing title composable for expanded/collapsed states
- `modifier`: Modifier to apply to the app bar
- `subtitle`: Optional lambda providing subtitle composable
- `navigationIcon`: Navigation icon (typically IconButton)
- `actions`: Action buttons displayed at the end
- `titleHorizontalAlignment`: Horizontal alignment of title and subtitle
- `collapsedHeight`: Height when collapsed (defaults to MediumAppBarCollapsedHeight)
- `expandedHeight`: Height when expanded (must be >= collapsedHeight)
- `windowInsets`: Window insets the app bar respects
- `colors`: TopAppBarColors for different states
- `scrollBehavior`: TopAppBarScrollBehavior for nested scrolling


### VerticalDivider

A vertical divider is a thin line used to separate content in lists and layouts.

**Function Signature:**
```kotlin
@Composable
fun VerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
    color: Color = DividerDefaults.color
)
```

**Parameters:**
- `modifier`: Modifier to apply to the divider
- `thickness`: Thickness of the divider line (use Dp.Hairline for single pixel)
- `color`: Color of the divider line

### VerticalDragHandle

A drag handle allows users to resize or reposition components by dragging. Commonly used for split-pane layouts where users can adjust the proportion of screen space.

**Function Signature:**
```kotlin
@Composable
fun VerticalDragHandle(
    modifier: Modifier = Modifier,
    sizes: DragHandleSizes = VerticalDragHandleDefaults.sizes(),
    colors: DragHandleColors = VerticalDragHandleDefaults.colors(),
    shapes: DragHandleShapes = VerticalDragHandleDefaults.shapes(),
    interactionSource: MutableInteractionSource? = null
)
```

**Use Case:** Use VerticalDragHandle for split-screen layouts where users need to adjust pane sizes. A vertically oriented drag handle conveys horizontal drag motions.

**Example - Resizable Pane:**
```kotlin
var offsetX by remember { mutableStateOf(0f) }
var screenSize by remember { mutableStateOf(IntSize.Zero) }
val density = LocalDensity.current

Box(
    modifier = Modifier.fillMaxSize()
        .onGloballyPositioned { layoutCoordinates ->
            screenSize = layoutCoordinates.size
            if (offsetX == 0f) {
                offsetX = screenSize.width / 2f
            }
        }
) {
    Surface(
        modifier = Modifier.width(with(density) { offsetX.toDp() }).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(0.dp, 24.dp, 24.dp, 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            VerticalDragHandle(
                modifier = Modifier
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            offsetX = (offsetX + delta).coerceIn(
                                with(density) { 48.dp.toPx() },
                                screenSize.width.toFloat()
                            )
                        }
                    )
                    .systemGestureExclusion() // Avoid colliding with back gesture
            )
        }
    }
}
```

**Parameters:**
- `modifier`: Modifier to apply to the drag handle
- `sizes`: DragHandleSizes for the handle dimensions
- `colors`: DragHandleColors for different states
- `shapes`: DragHandleShapes for the handle appearance
- `interactionSource`: Optional MutableInteractionSource for observing interactions

### VerticalFloatingToolbar

A vertical floating toolbar displays navigation and actions in a column layout. It floats over content and can be positioned anywhere on screen.

**Function Signature:**
```kotlin
@ExperimentalMaterial3ExpressiveApi
@Composable
fun VerticalFloatingToolbar(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    colors: FloatingToolbarColors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
    contentPadding: PaddingValues = FloatingToolbarDefaults.ContentPadding,
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
    shape: Shape = FloatingToolbarDefaults.ContainerShape,
    leadingContent: (@Composable ColumnScope.() -> Unit)? = null,
    trailingContent: (@Composable ColumnScope.() -> Unit)? = null,
    expandedShadowElevation: Dp = FloatingToolbarDefaults.ContainerExpandedElevation,
    collapsedShadowElevation: Dp = FloatingToolbarDefaults.ContainerCollapsedElevation,
    content: @Composable ColumnScope.() -> Unit
)
```

**Accessibility Note:** This component stays expanded for users with touch exploration services (e.g., TalkBack) to maintain toolbar visibility.

**Example with Scroll Behavior:**
```kotlin
var expanded by rememberSaveable { mutableStateOf(true) }

Scaffold(
    content = { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            // Place toolbar first for a11y focus order, use zIndex for visual layering
            VerticalFloatingToolbar(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = -ScreenOffset)
                    .zIndex(1f),
                expanded = expanded,
                leadingContent = { /* Leading actions */ },
                trailingContent = { /* Trailing actions */ },
                content = {
                    FilledIconButton(
                        modifier = Modifier.height(64.dp),
                        onClick = { /* doSomething() */ }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Localized description")
                    }
                }
            )

            LazyColumn(
                modifier = Modifier.floatingToolbarVerticalNestedScroll(
                    expanded = expanded,
                    onExpand = { expanded = true },
                    onCollapse = { expanded = false }
                ),
                state = rememberLazyListState(),
                contentPadding = innerPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val list = (0..75).map { it.toString() }
                items(count = list.size) {
                    Text(
                        text = list[it],
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
)
```

**Example with Multiple Actions:**
```kotlin
Scaffold(
    content = { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            VerticalFloatingToolbar(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = -ScreenOffset)
                    .zIndex(1f),
                expanded = true,
                leadingContent = { /* Leading content */ },
                trailingContent = {
                    AppBarColumn {
                        clickableItem(
                            onClick = { /* doSomething() */ },
                            icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                            label = "Download"
                        )
                        clickableItem(
                            onClick = { /* doSomething() */ },
                            icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                            label = "Favorite"
                        )
                        clickableItem(
                            onClick = { /* doSomething() */ },
                            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            label = "Add"
                        )
                        clickableItem(
                            onClick = { /* doSomething() */ },
                            icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                            label = "Person"
                        )
                        clickableItem(
                            onClick = { /* doSomething() */ },
                            icon = { Icon(Icons.Filled.ArrowUpward, contentDescription = null) },
                            label = "ArrowUpward"
                        )
                    }
                },
                content = {
                    FilledIconButton(
                        modifier = Modifier.height(64.dp),
                        onClick = { /* doSomething() */ }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Localized description")
                    }
                }
            )

            LazyColumn(
                state = rememberLazyListState(),
                contentPadding = innerPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val list = (0..75).map { it.toString() }
                items(count = list.size) {
                    Text(
                        text = list[it],
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
)
```

**Parameters:**
- `expanded`: Whether the toolbar is expanded
- `modifier`: Modifier to apply to the toolbar
- `colors`: FloatingToolbarColors for different states
- `contentPadding`: Padding applied to toolbar content
- `scrollBehavior`: Optional scroll behavior for nested scrolling
- `shape`: Shape of the toolbar container
- `leadingContent`: Optional leading content in the column
- `trailingContent`: Optional trailing content in the column
- `expandedShadowElevation`: Shadow elevation when expanded
- `collapsedShadowElevation`: Shadow elevation when collapsed
- `content`: Main toolbar content


**Example with Exit Always Scroll Behavior:**
```kotlin
val exitAlwaysScrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(exitDirection = End)

Scaffold(
    modifier = Modifier.nestedScroll(exitAlwaysScrollBehavior),
    content = { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            VerticalFloatingToolbar(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = -ScreenOffset)
                    .zIndex(1f),
                expanded = true,
                leadingContent = { /* Leading content */ },
                trailingContent = { /* Trailing content */ },
                content = {
                    FilledIconButton(
                        modifier = Modifier.height(64.dp),
                        onClick = { /* doSomething() */ }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Localized description")
                    }
                },
                scrollBehavior = exitAlwaysScrollBehavior
            )

            LazyColumn(
                state = rememberLazyListState(),
                contentPadding = innerPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val list = (0..75).map { it.toString() }
                items(count = list.size) {
                    Text(
                        text = list[it],
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
)
```

### VerticalFloatingToolbar (with FAB)

A vertical floating toolbar that includes an adjacent floating action button. The toolbar content and FAB are organized vertically with slide animations.

**Function Signature:**
```kotlin
@ExperimentalMaterial3ExpressiveApi
@Composable
fun VerticalFloatingToolbar(
    expanded: Boolean,
    floatingActionButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    colors: FloatingToolbarColors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
    contentPadding: PaddingValues = FloatingToolbarDefaults.ContentPadding,
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
    shape: Shape = FloatingToolbarDefaults.ContainerShape,
    floatingActionButtonPosition: FloatingToolbarVerticalFabPosition = FloatingToolbarVerticalFabPosition.Bottom,
    animationSpec: FiniteAnimationSpec<Float> = FloatingToolbarDefaults.animationSpec(),
    expandedShadowElevation: Dp = FloatingToolbarDefaults.ContainerExpandedElevationWithFab,
    collapsedShadowElevation: Dp = FloatingToolbarDefaults.ContainerCollapsedElevationWithFab,
    content: @Composable ColumnScope.() -> Unit
)
```

**Accessibility Note:** This component stays expanded for users with touch exploration services (e.g., TalkBack).

**Example with Nested Scroll (Bottom/Top Aligned):**
```kotlin
var expanded by rememberSaveable { mutableStateOf(true) }
val vibrantColors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()

Scaffold { innerPadding ->
    Box(Modifier.padding(innerPadding)) {
        VerticalFloatingToolbar(
            expanded = expanded,
            floatingActionButton = {
                FloatingToolbarDefaults.VibrantFloatingActionButton(
                    onClick = { expanded = !expanded }
                ) {
                    Icon(Icons.Filled.Add, "Localized description")
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = -ScreenOffset, y = -ScreenOffset)
                .zIndex(1f),
            colors = vibrantColors,
            content = {
                IconButton(
                    onClick = { /* doSomething() */ },
                    Modifier.focusProperties { canFocus = expanded }
                ) {
                    Icon(Icons.Filled.Person, contentDescription = "Localized description")
                }
                IconButton(
                    onClick = { /* doSomething() */ },
                    Modifier.focusProperties { canFocus = expanded }
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Localized description")
                }
                IconButton(
                    onClick = { /* doSomething() */ },
                    Modifier.focusProperties { canFocus = expanded }
                ) {
                    Icon(Icons.Filled.Favorite, contentDescription = "Localized description")
                }
                IconButton(
                    onClick = { /* doSomething() */ },
                    Modifier.focusProperties { canFocus = expanded }
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Localized description")
                }
            }
        )

        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
                .floatingToolbarVerticalNestedScroll(
                    expanded = expanded,
                    onExpand = { expanded = true },
                    onCollapse = { expanded = false }
                )
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = remember { LoremIpsum().values.first() })
        }
    }
}
```

**Example with Scroll Behavior (Center Edge Aligned):**
```kotlin
val exitAlwaysScrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(exitDirection = End)
val vibrantColors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()

Scaffold(modifier = Modifier.nestedScroll(exitAlwaysScrollBehavior)) { innerPadding ->
    Box(Modifier.padding(innerPadding)) {
        VerticalFloatingToolbar(
            expanded = true, // Always expanded for center-aligned toolbar
            floatingActionButton = {
                FloatingToolbarDefaults.VibrantFloatingActionButton(
                    onClick = { /* doSomething() */ }
                ) {
                    Icon(Icons.Filled.Add, "Localized description")
                }
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = -ScreenOffset)
                .zIndex(1f),
            colors = vibrantColors,
            scrollBehavior = exitAlwaysScrollBehavior,
            content = {
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(Icons.Filled.Person, contentDescription = "Localized description")
                }
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Localized description")
                }
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(Icons.Filled.Favorite, contentDescription = "Localized description")
                }
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Localized description")
                }
            }
        )

        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = remember { LoremIpsum().values.first() })
        }
    }
}
```

**Parameters:**
- `expanded`: Whether the toolbar is expanded (shows content) or collapsed (FAB only)
- `floatingActionButton`: FAB composable (use VibrantFloatingActionButton or StandardFloatingActionButton)
- `modifier`: Modifier to apply to the toolbar
- `colors`: FloatingToolbarColors (standard or vibrant)
- `contentPadding`: Padding applied to toolbar content
- `scrollBehavior`: Optional scroll behavior for hiding/showing toolbar
- `shape`: Shape of the toolbar container
- `floatingActionButtonPosition`: Position of FAB (Bottom or Top)
- `animationSpec`: Animation spec for expand/collapse
- `expandedShadowElevation`: Shadow elevation when expanded
- `collapsedShadowElevation`: Shadow elevation when collapsed
- `content`: Main toolbar content in a Column

**Best Practices:**
- For bottom/top aligned toolbars: Use `floatingToolbarVerticalNestedScroll` modifier to toggle expanded state
- For center edge aligned toolbars: Keep expanded=true and use scrollBehavior to hide entire toolbar
- Ensure FAB uses `Modifier.fillMaxSize()` for proper sizing
- Use `focusProperties { canFocus = expanded }` on buttons to prevent focus on invisible elements


### VerticalSlider

A vertical slider allows users to select a value from a range along a vertical bar. Ideal for adjusting settings like volume or brightness.

**Function Signature:**
```kotlin
@ExperimentalMaterial3ExpressiveApi
@Composable
fun VerticalSlider(
    state: SliderState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    reverseDirection: Boolean = false,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    thumb: @Composable (SliderState) -> Unit = { /* default thumb */ },
    track: @Composable (SliderState) -> Unit = { /* default track */ }
)
```

**Example with Custom Track:**
```kotlin
val coroutineScope = rememberCoroutineScope()
val sliderState = rememberSliderState(
    steps = 9, // 10, 20, ..., 90
    valueRange = 0f..100f
)
val snapAnimationSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
var currentValue by rememberSaveable { mutableFloatStateOf(sliderState.value) }
var animateJob: Job? by remember { mutableStateOf(null) }

sliderState.shouldAutoSnap = false
sliderState.onValueChange = { newValue ->
    currentValue = newValue
    if (sliderState.isDragging) {
        animateJob?.cancel()
        sliderState.value = newValue
    }
}
sliderState.onValueChangeFinished = {
    animateJob = coroutineScope.launch {
        animate(
            initialValue = sliderState.value,
            targetValue = currentValue,
            animationSpec = snapAnimationSpec
        ) { value, _ ->
            sliderState.value = value
        }
    }
}

val interactionSource = remember { MutableInteractionSource() }

Column(modifier = Modifier.padding(horizontal = 16.dp)) {
    Text(
        modifier = Modifier.align(Alignment.CenterHorizontally),
        text = "%.2f".format(sliderState.value)
    )
    Spacer(Modifier.height(16.dp))
    VerticalSlider(
        state = sliderState,
        modifier = Modifier
            .height(300.dp)
            .align(Alignment.CenterHorizontally)
            .progressSemantics(
                currentValue,
                sliderState.valueRange.start..sliderState.valueRange.endInclusive,
                sliderState.steps
            ),
        interactionSource = interactionSource,
        track = {
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.width(36.dp),
                trackCornerSize = 12.dp
            )
        },
        reverseDirection = true
    )
}
```

**Example with Centered Track:**
```kotlin
val sliderState = rememberSliderState(
    steps = 9,
    valueRange = -50f..50f
)

Column(modifier = Modifier.padding(horizontal = 16.dp)) {
    Text(
        modifier = Modifier.align(Alignment.CenterHorizontally),
        text = "%.2f".format(sliderState.value)
    )
    Spacer(Modifier.height(16.dp))
    VerticalSlider(
        state = sliderState,
        modifier = Modifier
            .height(300.dp)
            .align(Alignment.CenterHorizontally)
            .progressSemantics(
                currentValue,
                sliderState.valueRange.start..sliderState.valueRange.endInclusive,
                sliderState.steps
            ),
        interactionSource = interactionSource,
        track = { SliderDefaults.CenteredTrack(sliderState = sliderState) },
        reverseDirection = true
    )
}
```

**Parameters:**
- `state`: SliderState containing the slider's current value
- `modifier`: Modifier to apply to the slider
- `enabled`: Controls enabled state (default: true)
- `reverseDirection`: Controls direction (default: top to bottom)
- `colors`: SliderColors for different states
- `interactionSource`: MutableInteractionSource for observing interactions
- `thumb`: Composable for the thumb displayed on the slider
- `track`: Composable for the track displayed underneath the thumb

### WideNavigationRail

A wide navigation rail provides access to primary destinations on tablet and desktop screens. It supports both collapsed and expanded states.

**Function Signature:**
```kotlin
@Composable
fun WideNavigationRail(
    modifier: Modifier = Modifier,
    state: WideNavigationRailState = rememberWideNavigationRailState(),
    shape: Shape = WideNavigationRailDefaults.shape,
    colors: WideNavigationRailColors = WideNavigationRailDefaults.colors(),
    header: (@Composable () -> Unit)? = null,
    windowInsets: WindowInsets = WideNavigationRailDefaults.windowInsets,
    arrangement: Arrangement.Vertical = WideNavigationRailDefaults.arrangement,
    content: @Composable () -> Unit
)
```

**Use Case:** Use WideNavigationRail for tablet and desktop layouts to provide primary navigation. Display 3-7 items when collapsed, at least 3 when expanded.

**Example - Collapsed Rail:**
```kotlin
var selectedItem by remember { mutableIntStateOf(0) }
val items = listOf("Home", "Search", "Settings")
val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Favorite, Icons.Filled.Star)
val unselectedIcons = listOf(Icons.Outlined.Home, Icons.Outlined.FavoriteBorder, Icons.Outlined.StarBorder)

WideNavigationRail {
    items.forEachIndexed { index, item ->
        WideNavigationRailItem(
            railExpanded = false,
            icon = {
                Icon(
                    if (selectedItem == index) selectedIcons[index] else unselectedIcons[index],
                    contentDescription = null
                )
            },
            label = { Text(item) },
            selected = selectedItem == index,
            onClick = { selectedItem = index }
        )
    }
}
```

**Example - Expanded Rail:**
```kotlin
var selectedItem by remember { mutableIntStateOf(0) }
val items = listOf("Home", "Search", "Settings")
val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Favorite, Icons.Filled.Star)
val unselectedIcons = listOf(Icons.Outlined.Home, Icons.Outlined.FavoriteBorder, Icons.Outlined.StarBorder)

WideNavigationRail(
    state = rememberWideNavigationRailState(initialValue = WideNavigationRailValue.Expanded)
) {
    items.forEachIndexed { index, item ->
        WideNavigationRailItem(
            railExpanded = true,
            icon = {
                Icon(
                    if (selectedItem == index) selectedIcons[index] else unselectedIcons[index],
                    contentDescription = null
                )
            },
            label = { Text(item) },
            selected = selectedItem == index,
            onClick = { selectedItem = index }
        )
    }
}
```

**Example - Animated Expand/Collapse:**
```kotlin
var selectedItem by remember { mutableIntStateOf(0) }
val items = listOf("Home", "Search", "Settings")
val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Favorite, Icons.Filled.Star)
val unselectedIcons = listOf(Icons.Outlined.Home, Icons.Outlined.FavoriteBorder, Icons.Outlined.StarBorder)
val state = rememberWideNavigationRailState()
val scope = rememberCoroutineScope()
val headerDescription = if (state.targetValue == WideNavigationRailValue.Expanded) {
    "Collapse rail"
} else {
    "Expand rail"
}

Row(Modifier.fillMaxWidth()) {
    WideNavigationRail(
        state = state,
        header = {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above
                ),
                tooltip = { PlainTooltip { Text(headerDescription) } },
                state = rememberTooltipState()
            ) {
                IconButton(
                    onClick = {
                        scope.launch {
                            if (state.currentValue == WideNavigationRailValue.Collapsed) {
                                state.expand()
                            } else {
                                state.collapse()
                            }
                        }
                    },
                    modifier = Modifier.semantics {
                        stateDescription = headerDescription
                    }
                ) {
                    Icon(
                        if (state.targetValue == WideNavigationRailValue.Expanded) {
                            Icons.AutoMirrored.Filled.MenuOpen
                        } else {
                            Icons.Filled.Menu
                        },
                        contentDescription = null
                    )
                }
            }
        }
    ) {
        items.forEachIndexed { index, item ->
            WideNavigationRailItem(
                railExpanded = state.targetValue == WideNavigationRailValue.Expanded,
                icon = {
                    Icon(
                        if (selectedItem == index) selectedIcons[index] else unselectedIcons[index],
                        contentDescription = null
                    )
                },
                label = { Text(item) },
                selected = selectedItem == index,
                onClick = { selectedItem = index }
            )
        }
    }
}
```

**Parameters:**
- `modifier`: Modifier to apply to the rail
- `state`: WideNavigationRailState for controlling expanded/collapsed state
- `shape`: Shape of the rail container
- `colors`: WideNavigationRailColors for different states
- `header`: Optional header content (menu button, FAB, or logo)
- `windowInsets`: Window insets the rail respects
- `arrangement`: Vertical arrangement of items
- `content`: Rail content (typically WideNavigationRailItems)


### WideNavigationRailItem

A navigation item for use within a WideNavigationRail. Supports both top and start icon positions based on rail expansion state.

**Function Signature:**
```kotlin
@Composable
fun WideNavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: (@Composable () -> Unit)?,
    railExpanded: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconPosition: NavigationItemIconPosition = WideNavigationRailItemDefaults.iconPositionFor(railExpanded),
    colors: NavigationItemColors = WideNavigationRailItemDefaults.colors(),
    interactionSource: MutableInteractionSource? = null
)
```

**Icon Position Behavior:**
- `NavigationItemIconPosition.Top`: Icon above label (for collapsed rails)
- `NavigationItemIconPosition.Start`: Icon to start of label (for expanded rails)

**Parameters:**
- `selected`: Whether this item is selected
- `onClick`: Callback when item is clicked
- `icon`: Icon composable (typically an Icon)
- `label`: Text label for the item
- `railExpanded`: Whether the associated rail is expanded
- `modifier`: Modifier to apply to the item
- `enabled`: Controls enabled state (default: true)
- `iconPosition`: Position of the icon relative to label
- `colors`: NavigationItemColors for different states
- `interactionSource`: Optional MutableInteractionSource for observing interactions

## Color Schemes and Theming

### contentColorFor

Returns the appropriate content color for a given background color based on the Material color system.

**Function Signature:**
```kotlin
@Composable
fun contentColorFor(backgroundColor: Color): Color
```

**Use Case:** Use this function to automatically determine the correct text/icon color for a background. For example, when backgroundColor is `primary`, this returns `onPrimary`.

**Returns:** The matching content color, or LocalContentColor if no match is found.

### darkColorScheme

Creates a dark Material color scheme with customizable colors.

**Function Signature:**
```kotlin
fun darkColorScheme(
    primary: Color = ColorDarkTokens.Primary,
    onPrimary: Color = ColorDarkTokens.OnPrimary,
    primaryContainer: Color = ColorDarkTokens.PrimaryContainer,
    // ... all color parameters
): ColorScheme
```

**Use Case:** Use darkColorScheme to create a dark theme variant for your app.

### dynamicDarkColorScheme / dynamicLightColorScheme

Creates dynamic color schemes based on the system wallpaper (Android 12+).

**Function Signature:**
```kotlin
@RequiresApi(31)
fun dynamicDarkColorScheme(context: Context): ColorScheme

@RequiresApi(31)
fun dynamicLightColorScheme(context: Context): ColorScheme
```

**Use Case:** Use dynamic color schemes to adapt your app's colors to the user's wallpaper. Requires Android 12 (API 31) or higher.

**Parameters:**
- `context`: Android Context required to access system resources

### expressiveLightColorScheme

Returns the default light color scheme for MaterialExpressiveTheme.

**Function Signature:**
```kotlin
@ExperimentalMaterial3ExpressiveApi
fun expressiveLightColorScheme(): ColorScheme
```

**Example - Theme with Dark Mode Support:**
```kotlin
@Composable
fun MyMaterialTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) {
            darkColorScheme()
        } else {
            expressiveLightColorScheme()
        }
    ) {
        content()
    }
}
```

### lightColorScheme

Creates a light Material color scheme with customizable colors.

**Function Signature:**
```kotlin
fun lightColorScheme(
    primary: Color = ColorLightTokens.Primary,
    onPrimary: Color = ColorLightTokens.OnPrimary,
    primaryContainer: Color = ColorLightTokens.PrimaryContainer,
    // ... all color parameters
): ColorScheme
```

**Use Case:** Use lightColorScheme to create a light theme variant for your app.

## State Management Functions

### rememberBottomAppBarState

Creates and remembers a BottomAppBarState for managing bottom app bar scroll behavior.

**Function Signature:**
```kotlin
@ExperimentalMaterial3Api
@Composable
fun rememberBottomAppBarState(
    initialHeightOffsetLimit: Float = -Float.MAX_VALUE,
    initialHeightOffset: Float = 0.0f,
    initialContentOffset: Float = 0.0f
): BottomAppBarState
```

**Parameters:**
- `initialHeightOffsetLimit`: Pixel limit for collapse (default: -Float.MAX_VALUE)
- `initialHeightOffset`: Initial offset height (must be between 0 and limit)
- `initialContentOffset`: Initial content offset

### rememberBottomSheetScaffoldState

Creates and remembers state for a BottomSheetScaffold.

**Function Signature:**
```kotlin
@ExperimentalMaterial3Api
@Composable
fun rememberBottomSheetScaffoldState(
    bottomSheetState: SheetState = rememberStandardBottomSheetState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
): BottomSheetScaffoldState
```

**Parameters:**
- `bottomSheetState`: State of the bottom sheet
- `snackbarHostState`: State for showing snackbars

### rememberDatePickerState (LocalDate)

Creates and remembers a DatePickerState using LocalDate and YearMonth (Android API 26+).

**Function Signature:**
```kotlin
@RequiresApi(26)
@ExperimentalMaterial3Api
@Composable
fun rememberDatePickerState(
    initialSelectedDate: LocalDate?,
    initialDisplayedMonth: YearMonth? = initialSelectedDate?.let { YearMonth.from(it) },
    yearRange: IntRange = DatePickerDefaults.YearRange,
    initialDisplayMode: DisplayMode = DisplayMode.Picker,
    selectableDates: SelectableDates = DatePickerDefaults.AllDates
): DatePickerState
```

**Example:**
```kotlin
Column(
    modifier = Modifier.verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    // Pre-select April 15, 2023
    val datePickerState = rememberDatePickerState(
        initialSelectedDate = LocalDate.of(2023, 4, 15)
    )
    DatePicker(state = datePickerState, modifier = Modifier.padding(16.dp))

    Text(
        "Selected date: ${datePickerState.getSelectedDate() ?: "no selection"}",
        modifier = Modifier.align(Alignment.CenterHorizontally)
    )
}
```

**Parameters:**
- `initialSelectedDate`: Initial date selection (null for no selection)
- `initialDisplayedMonth`: Initial month to display (defaults to selected date's month)
- `yearRange`: Year range for the picker
- `initialDisplayMode`: Initial display mode (Picker or Input)
- `selectableDates`: Determines which dates are selectable

### rememberDatePickerState (Milliseconds)

Creates and remembers a DatePickerState using UTC milliseconds.

**Function Signature:**
```kotlin
@Composable
fun rememberDatePickerState(
    initialSelectedDateMillis: Long? = null,
    initialDisplayedMonthMillis: Long? = initialSelectedDateMillis,
    yearRange: IntRange = DatePickerDefaults.YearRange,
    initialDisplayMode: DisplayMode = DisplayMode.Picker,
    selectableDates: SelectableDates = DatePickerDefaults.AllDates
): DatePickerState
```

**Parameters:**
- `initialSelectedDateMillis`: Initial selection in UTC milliseconds (null for no selection)
- `initialDisplayedMonthMillis`: Initial displayed month in UTC milliseconds
- `yearRange`: Year range for the picker
- `initialDisplayMode`: Initial display mode
- `selectableDates`: Determines which dates are selectable


### rememberDateRangePickerState (LocalDate)

Creates and remembers a DateRangePickerState using LocalDate and YearMonth (Android API 26+).

**Function Signature:**
```kotlin
@RequiresApi(26)
@ExperimentalMaterial3Api
@Composable
fun rememberDateRangePickerState(
    initialSelectedStartDate: LocalDate?,
    initialSelectedEndDate: LocalDate?,
    initialDisplayedMonth: YearMonth? = initialSelectedStartDate?.let { YearMonth.from(it) },
    yearRange: IntRange = DatePickerDefaults.YearRange,
    initialDisplayMode: DisplayMode = DisplayMode.Picker,
    selectableDates: SelectableDates = DatePickerDefaults.AllDates
): DateRangePickerState
```

**Parameters:**
- `initialSelectedStartDate`: Initial start date selection (null for no selection)
- `initialSelectedEndDate`: Initial end date selection (null for no selection)
- `initialDisplayedMonth`: Initial month to display
- `yearRange`: Year range for the picker
- `initialDisplayMode`: Initial display mode
- `selectableDates`: Determines which dates are selectable

### rememberDateRangePickerState (Milliseconds)

Creates and remembers a DateRangePickerState using UTC milliseconds.

**Function Signature:**
```kotlin
@Composable
fun rememberDateRangePickerState(
    initialSelectedStartDateMillis: Long? = null,
    initialSelectedEndDateMillis: Long? = null,
    initialDisplayedMonthMillis: Long? = initialSelectedStartDateMillis,
    yearRange: IntRange = DatePickerDefaults.YearRange,
    initialDisplayMode: DisplayMode = DisplayMode.Picker,
    selectableDates: SelectableDates = DatePickerDefaults.AllDates
): DateRangePickerState
```

### rememberDrawerState

Creates and remembers a DrawerState for navigation drawers.

**Function Signature:**
```kotlin
@Composable
fun rememberDrawerState(
    initialValue: DrawerValue,
    confirmStateChange: (DrawerValue) -> Boolean = { true }
): DrawerState
```

**Parameters:**
- `initialValue`: Initial drawer state (Open or Closed)
- `confirmStateChange`: Optional callback to confirm or veto state changes

### rememberFloatingToolbarState

Creates and remembers a FloatingToolbarState for managing floating toolbar scroll behavior.

**Function Signature:**
```kotlin
@ExperimentalMaterial3ExpressiveApi
@Composable
fun rememberFloatingToolbarState(
    initialOffsetLimit: Float = -Float.MAX_VALUE,
    initialOffset: Float = 0.0f,
    initialContentOffset: Float = 0.0f
): FloatingToolbarState
```

**Parameters:**
- `initialOffsetLimit`: Pixel limit for collapse
- `initialOffset`: Initial offset (must be between 0 and limit)
- `initialContentOffset`: Initial content offset

### rememberModalBottomSheetState

Creates and remembers state for a ModalBottomSheet.

**Function Signature:**
```kotlin
@ExperimentalMaterial3Api
@Composable
fun rememberModalBottomSheetState(
    skipPartiallyExpanded: Boolean = false,
    confirmValueChange: (SheetValue) -> Boolean = { true }
): SheetState
```

**Parameters:**
- `skipPartiallyExpanded`: Whether to skip partially expanded state
- `confirmValueChange`: Optional callback to confirm or veto state changes

### rememberRangeSliderState

Creates and remembers a RangeSliderState for range sliders.

**Function Signature:**
```kotlin
@ExperimentalMaterial3Api
@Composable
fun rememberRangeSliderState(
    activeRangeStart: Float = 0.0f,
    activeRangeEnd: Float = 1.0f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
): RangeSliderState
```

**Parameters:**
- `activeRangeStart`: Initial start of active range
- `activeRangeEnd`: Initial end of active range
- `steps`: Number of discrete values (0 for continuous)
- `onValueChangeFinished`: Callback when value change ends
- `valueRange`: Range of allowable values

### rememberSearchBarState

Creates and remembers a SearchBarState for search bars.

**Function Signature:**
```kotlin
@ExperimentalMaterial3Api
@Composable
fun rememberSearchBarState(
    initialValue: SearchBarValue = SearchBarValue.Collapsed,
    animationSpecForExpand: AnimationSpec<Float> = MotionSchemeKeyTokens.SlowSpatial.value(),
    animationSpecForCollapse: AnimationSpec<Float> = MotionSchemeKeyTokens.DefaultSpatial.value()
): SearchBarState
```

**Parameters:**
- `initialValue`: Initial state (Collapsed or Expanded)
- `animationSpecForExpand`: Animation spec for expanding
- `animationSpecForCollapse`: Animation spec for collapsing

### rememberSliderState

Creates and remembers a SliderState for sliders.

**Function Signature:**
```kotlin
@ExperimentalMaterial3Api
@Composable
fun rememberSliderState(
    value: Float = 0.0f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
): SliderState
```

**Parameters:**
- `value`: Initial thumb position
- `steps`: Number of discrete values (0 for continuous)
- `onValueChangeFinished`: Callback when value change ends
- `valueRange`: Range of allowable values

### rememberStandardBottomSheetState

Creates and remembers state for a BottomSheetScaffold.

**Function Signature:**
```kotlin
@ExperimentalMaterial3Api
@Composable
fun rememberStandardBottomSheetState(
    initialValue: SheetValue = PartiallyExpanded,
    confirmValueChange: (SheetValue) -> Boolean = { true },
    skipHiddenState: Boolean = true
): SheetState
```

**Parameters:**
- `initialValue`: Initial sheet state (PartiallyExpanded or Expanded)
- `confirmValueChange`: Optional callback to confirm or veto state changes
- `skipHiddenState`: Whether Hidden state is skipped

### rememberSwipeToDismissBoxState

Creates and remembers state for a SwipeToDismissBox.

**Function Signature:**
```kotlin
@Composable
fun rememberSwipeToDismissBoxState(
    initialValue: SwipeToDismissBoxValue = SwipeToDismissBoxValue.Settled,
    positionalThreshold: (totalDistance: Float) -> Float = SwipeToDismissBoxDefaults.positionalThreshold
): SwipeToDismissBoxState
```

**Parameters:**
- `initialValue`: Initial state (Settled, StartToEnd, or EndToStart)
- `positionalThreshold`: Threshold for triggering dismiss

### rememberTimePickerState

Creates and remembers a TimePickerState for time pickers.

**Function Signature:**
```kotlin
@ExperimentalMaterial3Api
@Composable
fun rememberTimePickerState(
    initialHour: Int = 0,
    initialMinute: Int = 0,
    is24Hour: Boolean = is24HourFormat
): TimePickerState
```

**Parameters:**
- `initialHour`: Starting hour (0-23)
- `initialMinute`: Starting minute (0-59)
- `is24Hour`: Format (false for 12-hour with AM/PM, true for 24-hour)

### rememberTooltipState

Creates and remembers a TooltipState for tooltips.

**Function Signature:**
```kotlin
@ExperimentalMaterial3Api
@Composable
fun rememberTooltipState(
    initialIsVisible: Boolean = false,
    isPersistent: Boolean = false,
    mutatorMutex: MutatorMutex = BasicTooltipDefaults.GlobalMutatorMutex
): TooltipState
```

**Parameters:**
- `initialIsVisible`: Initial visibility
- `isPersistent`: Whether tooltip persists (true for actionable content)
- `mutatorMutex`: Ensures only one tooltip shows at a time

### rememberTopAppBarState

Creates and remembers a TopAppBarState for top app bars.

**Function Signature:**
```kotlin
@Composable
fun rememberTopAppBarState(
    initialHeightOffsetLimit: Float = -Float.MAX_VALUE,
    initialHeightOffset: Float = 0.0f,
    initialContentOffset: Float = 0.0f
): TopAppBarState
```

**Parameters:**
- `initialHeightOffsetLimit`: Pixel limit for collapse
- `initialHeightOffset`: Initial offset height
- `initialContentOffset`: Initial content offset

### rememberWideNavigationRailState

Creates and remembers a WideNavigationRailState.

**Function Signature:**
```kotlin
@Composable
fun rememberWideNavigationRailState(
    initialValue: WideNavigationRailValue = WideNavigationRailValue.Collapsed
): WideNavigationRailState
```

**Parameters:**
- `initialValue`: Initial state (Collapsed or Expanded)

## Ripple Effects

### ripple (Static Color)

Creates a ripple indication with a static color.

**Function Signature:**
```kotlin
fun ripple(
    bounded: Boolean = true,
    radius: Dp = Dp.Unspecified,
    color: Color = Color.Unspecified
): IndicationNodeFactory
```

**Parameters:**
- `bounded`: Whether ripples are clipped by bounds
- `radius`: Ripple radius (Dp.Unspecified for automatic sizing)
- `color`: Ripple color (Color.Unspecified uses LocalContentColor)

### ripple (Dynamic Color)

Creates a ripple indication with a dynamic color that can change over time.

**Function Signature:**
```kotlin
fun ripple(
    color: ColorProducer,
    bounded: Boolean = true,
    radius: Dp = Dp.Unspecified
): IndicationNodeFactory
```

**Use Case:** Use this overload for ripples with colors that change dynamically to avoid unnecessary recompositions.

**Parameters:**
- `color`: ColorProducer for dynamic color
- `bounded`: Whether ripples are clipped by bounds
- `radius`: Ripple radius

## Extension Functions

### FloatingActionButtonMenuItem

A menu item for use within a FloatingActionButtonMenu.

**Function Signature:**
```kotlin
@ExperimentalMaterial3ExpressiveApi
@Composable
fun FloatingActionButtonMenuScope.FloatingActionButtonMenuItem(
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = contentColorFor(containerColor)
)
```

**Parameters:**
- `onClick`: Callback when item is clicked
- `text`: Label displayed inside the item
- `icon`: Optional icon (typically an Icon)
- `modifier`: Modifier to apply
- `containerColor`: Background color
- `contentColor`: Content color

### NavigationBarItem

A navigation item for use within a NavigationBar.

**Function Signature:**
```kotlin
@Composable
fun RowScope.NavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
    colors: NavigationBarItemColors = NavigationBarItemDefaults.colors(),
    interactionSource: MutableInteractionSource? = null
)
```

**Recommended Configuration:**
- 3 destinations: Show icons and labels for all
- 4 destinations: Active shows icon + label, inactive shows icon only (labels recommended)
- 5 destinations: Active shows icon + label, inactive shows icon (labels if space permits)

**Parameters:**
- `selected`: Whether this item is selected
- `onClick`: Callback when clicked
- `icon`: Icon for the item
- `modifier`: Modifier to apply
- `enabled`: Controls enabled state
- `label`: Optional text label
- `alwaysShowLabel`: Whether to always show label (false shows only when selected)
- `colors`: NavigationBarItemColors for different states
- `interactionSource`: Optional MutableInteractionSource

### PlainTooltip

A plain tooltip that provides a descriptive message.

**Function Signature:**
```kotlin
@ExperimentalMaterial3Api
@Composable
fun TooltipScope.PlainTooltip(
    modifier: Modifier = Modifier,
    caretShape: Shape? = null,
    maxWidth: Dp = TooltipDefaults.plainTooltipMaxWidth,
    shape: Shape = TooltipDefaults.plainTooltipContainerShape,
    contentColor: Color = TooltipDefaults.plainTooltipContentColor,
    containerColor: Color = TooltipDefaults.plainTooltipContainerColor,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit
)
```

**Use Case:** Use with TooltipBox to provide simple descriptive messages.

**Parameters:**
- `modifier`: Modifier to apply
- `caretShape`: Shape for caret (null for no caret)
- `maxWidth`: Maximum tooltip width
- `shape`: Container shape
- `contentColor`: Content color
- `containerColor`: Background color
- `tonalElevation`: Tonal elevation
- `shadowElevation`: Shadow elevation
- `content`: Tooltip content

### RichTooltip

A rich tooltip with title, text, and action support.

**Function Signature:**
```kotlin
@ExperimentalMaterial3Api
@Composable
fun TooltipScope.RichTooltip(
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    caretShape: Shape? = null,
    maxWidth: Dp = TooltipDefaults.richTooltipMaxWidth,
    shape: Shape = TooltipDefaults.richTooltipContainerShape,
    colors: RichTooltipColors = TooltipDefaults.richTooltipColors(),
    tonalElevation: Dp = ElevationTokens.Level0,
    shadowElevation: Dp = RichTooltipTokens.ContainerElevation,
    text: @Composable () -> Unit
)
```

**Use Case:** Use with TooltipBox for tooltips with actionable content.

**Parameters:**
- `modifier`: Modifier to apply
- `title`: Optional title
- `action`: Optional action button
- `caretShape`: Shape for caret
- `maxWidth`: Maximum tooltip width
- `shape`: Container shape
- `colors`: RichTooltipColors for container and content
- `tonalElevation`: Tonal elevation
- `shadowElevation`: Shadow elevation
- `text`: Main tooltip text

### SegmentedButton (Multi-Choice)

A toggleable segmented button for non-mutually exclusive selections.

**Function Signature:**
```kotlin
@Composable
fun MultiChoiceSegmentedButtonRowScope.SegmentedButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SegmentedButtonColors = SegmentedButtonDefaults.colors(),
    border: BorderStroke = SegmentedButtonDefaults.borderStroke(colors.borderColor(enabled, checked)),
    contentPadding: PaddingValues = SegmentedButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    icon: @Composable () -> Unit = { SegmentedButtonDefaults.Icon(checked) },
    label: @Composable () -> Unit
)
```

**Use Case:** Use within MultiChoiceSegmentedButtonRow for non-exclusive selections.

### SegmentedButton (Single-Choice)

A selectable segmented button for mutually exclusive selections.

**Function Signature:**
```kotlin
@Composable
fun SingleChoiceSegmentedButtonRowScope.SegmentedButton(
    selected: Boolean,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SegmentedButtonColors = SegmentedButtonDefaults.colors(),
    border: BorderStroke = SegmentedButtonDefaults.borderStroke(colors.borderColor(enabled, selected)),
    contentPadding: PaddingValues = SegmentedButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    icon: @Composable () -> Unit = { SegmentedButtonDefaults.Icon(selected) },
    label: @Composable () -> Unit
)
```

**Use Case:** Use within SingleChoiceSegmentedButtonRow for mutually exclusive selections.

### animateFloatingActionButton

Modifier for animating FAB visibility with scale and alpha animations.

**Function Signature:**
```kotlin
@ExperimentalMaterial3ExpressiveApi
fun Modifier.animateFloatingActionButton(
    visible: Boolean,
    alignment: Alignment,
    targetScale: Float = FloatingActionButtonDefaults.ShowHideTargetScale,
    scaleAnimationSpec: AnimationSpec<Float>? = null,
    alphaAnimationSpec: AnimationSpec<Float>? = null
): Modifier
```

**Parameters:**
- `visible`: Whether the FAB should be visible
- `alignment`: Alignment for the FAB
- `targetScale`: Target scale when hiding
- `scaleAnimationSpec`: Animation spec for scale
- `alphaAnimationSpec`: Animation spec for alpha

---

## Conclusion

This guide covers the complete Material Design 3 Expressive component library for Jetpack Compose. Each component includes function signatures, parameters, use cases, and practical code examples to help you implement expressive, animated interfaces in your Android applications.

For the latest updates and additional resources, refer to the official Material Design 3 documentation.

---

## Extension Functions and Utilities

### contentColorFor

Returns the appropriate content color for a given background color from the ColorScheme.

**Function Signature:**
```kotlin
fun ColorScheme.contentColorFor(backgroundColor: Color): Color
```

**Description:** Matches the provided backgroundColor to a background color in the ColorScheme and returns the corresponding content color. For example, when backgroundColor is `ColorScheme.primary`, returns `ColorScheme.onPrimary`. Returns `Color.Unspecified` if no match is found.

**Use Case:** Automatically determine the correct text/icon color for any background color to ensure proper contrast and accessibility.

### surfaceColorAtElevation

Computes surface tonal colors at different elevation levels.

**Function Signature:**
```kotlin
fun ColorScheme.surfaceColorAtElevation(elevation: Dp): Color
```

**Parameters:**
- `elevation`: Elevation value used to compute alpha of the color overlay layer

**Returns:** The `ColorScheme.surface` color with an alpha of the `ColorScheme.surfaceTint` color overlaid on top.

**Use Case:** Calculate surface colors for elevated components like cards, dialogs, and sheets to create visual hierarchy.

### minimumInteractiveComponentSize

Reserves at least 48.dp in size to ensure touch target accessibility.

**Function Signature:**
```kotlin
fun Modifier.minimumInteractiveComponentSize(): Modifier
```

**Description:** Ensures interactive components meet the Material recommended minimum size of 48.dp x 48.dp for touch target disambiguation. Must be applied before any size modifiers to take effect.

**Example:**
```kotlin
Widget(
    color = Color.Green,
    modifier = Modifier
        .clickable { /* do something */ }
        .minimumInteractiveComponentSize()
        .size(36.dp) // Size modifier comes after
)
```

**Use Case:** Ensure small interactive components like checkboxes and icon buttons have adequate touch targets for accessibility.

### DatePicker Extension Functions (Java Time API)

#### getDisplayedMonth

Returns a YearMonth representation of the displayed month.

**Function Signatures:**
```kotlin
@RequiresApi(26)
fun DatePickerState.getDisplayedMonth(): YearMonth

@RequiresApi(26)
fun DateRangePickerState.getDisplayedMonth(): YearMonth
```

**Returns:** YearMonth based on the displayedMonthMillis (midnight of the first day in UTC).

#### setDisplayedMonth

Sets the displayed month based on a YearMonth.

**Function Signatures:**
```kotlin
@RequiresApi(26)
fun DatePickerState.setDisplayedMonth(yearMonth: YearMonth): Unit

@RequiresApi(26)
fun DateRangePickerState.setDisplayedMonth(yearMonth: YearMonth): Unit
```

**Parameters:**
- `yearMonth`: The YearMonth to display

#### getSelectedDate

Returns the selected date as a LocalDate.

**Function Signature:**
```kotlin
@RequiresApi(26)
fun DatePickerState.getSelectedDate(): LocalDate?
```

**Returns:** The selected LocalDate, or null if no selection.

#### setSelectedDate

Sets the selected date from a LocalDate.

**Function Signature:**
```kotlin
@RequiresApi(26)
fun DatePickerState.setSelectedDate(date: LocalDate?): Unit
```

**Parameters:**
- `date`: The LocalDate to select, or null to clear selection

#### getSelectedStartDate / getSelectedEndDate

Returns the selected start or end date for date range pickers.

**Function Signatures:**
```kotlin
@RequiresApi(26)
fun DateRangePickerState.getSelectedStartDate(): LocalDate?

@RequiresApi(26)
fun DateRangePickerState.getSelectedEndDate(): LocalDate?
```

**Returns:** The selected LocalDate, or null if no selection.

#### setSelection

Sets both start and end dates for a date range picker.

**Function Signature:**
```kotlin
@RequiresApi(26)
fun DateRangePickerState.setSelection(
    startDate: LocalDate?,
    endDate: LocalDate?
): Unit
```

**Parameters:**
- `startDate`: Start date selection (null for no selection)
- `endDate`: End date selection (null for no selection)

**Note:** Dates must be within the state's year-range, start date must be before or equal to end date, and if end date is provided, start date must also be provided.

### Shape Conversion Functions

#### RoundedPolygon.toPath

Converts a RoundedPolygon to a Path.

**Function Signature:**
```kotlin
@ExperimentalMaterial3ExpressiveApi
@Composable
fun RoundedPolygon.toPath(startAngle: Int = 0): Path
```

**Parameters:**
- `startAngle`: Angle in degrees to begin drawing (default 0 = 3 o'clock position)

**Returns:** A remembered Path for the RoundedPolygon.

#### Morph.toPath

Converts a Morph to a Path at a specific progress.

**Function Signature:**
```kotlin
@ExperimentalMaterial3ExpressiveApi
fun Morph.toPath(
    progress: Float,
    path: Path = Path(),
    startAngle: Int = 0
): Path
```

**Parameters:**
- `progress`: The morph's progress (0.0 to 1.0)
- `path`: Optional Path to reuse
- `startAngle`: Angle in degrees to begin drawing

**Returns:** A Path representing the morph at the given progress.

#### RoundedPolygon.toShape

Converts a RoundedPolygon to a Shape.

**Function Signature:**
```kotlin
@ExperimentalMaterial3ExpressiveApi
@Composable
fun RoundedPolygon.toShape(startAngle: Int = 0): Shape
```

**Parameters:**
- `startAngle`: Angle in degrees to begin drawing

**Returns:** A remembered Shape for the RoundedPolygon.

### TimePickerState Extension Properties

#### isHourInputValid

**Property:**
```kotlin
val TimePickerState.isHourInputValid: Boolean
```

**Returns:** true if the current hourInput represents a valid hour (0-23).

#### isMinuteInputValid

**Property:**
```kotlin
val TimePickerState.isMinuteInputValid: Boolean
```

**Returns:** true if the current minuteInput represents a valid minute (0-59).

#### isInputValid

**Property:**
```kotlin
val TimePickerState.isInputValid: Boolean
```

**Returns:** true if both hour and minute input values are valid.

#### isPm

**Property:**
```kotlin
val TimePickerState.isPm: Boolean
```

**Returns:** true if the selected time is in the PM period (12:00 PM to 11:59 PM).

---

## CompositionLocal Properties

### LocalAbsoluteTonalElevation

**Property:**
```kotlin
val LocalAbsoluteTonalElevation: ProvidableCompositionLocal<Dp>
```

**Description:** Contains the current absolute elevation (sum of all previous elevations). Used for calculating surface tonal colors, not for drawing shadows.

### LocalContentColor

**Property:**
```kotlin
val LocalContentColor: ProvidableCompositionLocal<Color>
```

**Description:** Contains the preferred content color for the current position in the hierarchy. Typically represents the "on" color for a background color (e.g., onSurface for surface background).

**Default:** Color.Black if no color has been explicitly set.

### LocalTextStyle

**Property:**
```kotlin
val LocalTextStyle: ProvidableCompositionLocal<TextStyle>
```

**Description:** Contains the preferred TextStyle used by Text components by default. Use ProvideTextStyle to set values.

### LocalMinimumInteractiveComponentSize

**Property:**
```kotlin
val LocalMinimumInteractiveComponentSize: ProvidableCompositionLocal<Dp>
```

**Description:** Configures the minimum touch target size for Material components. Set to 0.dp to disable enforcement.

### LocalTonalElevationEnabled

**Property:**
```kotlin
val LocalTonalElevationEnabled: ProvidableCompositionLocal<Boolean>
```

**Description:** Controls whether ColorScheme.applyTonalElevation is applied. Setting to false disables tonal elevation for all subsequent surfaces.

### LocalRippleConfiguration

**Property:**
```kotlin
val LocalRippleConfiguration: ProvidableCompositionLocal<RippleConfiguration?>
```

**Description:** Provides RippleConfiguration down the tree for customizing or disabling ripple effects. Acts as a tree-local override for individual components.

### Component Override CompositionLocals

The following CompositionLocals allow overriding default component implementations:

- `LocalBasicAlertDialogOverride`
- `LocalHorizontalFloatingToolbarOverride`
- `LocalHorizontalFloatingToolbarWithFabOverride`
- `LocalModalWideNavigationRailOverride`
- `LocalNavigationBarOverride`
- `LocalNavigationRailOverride`
- `LocalShortNavigationBarOverride`
- `LocalSingleRowTopAppBarOverride`
- `LocalTwoRowsTopAppBarOverride`
- `LocalVerticalToolbarOverride`
- `LocalVerticalToolbarWithFabOverride`
- `LocalWideNavigationRailOverride`

**Use Case:** Provide custom implementations of Material components for specific subtrees of your composition.

---

## Global Configuration

### isCheckboxStylingFixEnabled

**Property:**
```kotlin
// Accessed via ComposeMaterial3Flags
ComposeMaterial3Flags.isCheckboxStylingFixEnabled = true
```

**Description:** Flag controlling checkbox styling. When true, uses Material Design 3 styling (updated colors and container sizing). Now provided through `ComposeMaterial3Flags` object (changed in alpha10).


## 8.5 Version History

### Version 1.5.0-alpha13

January 28, 2026

androidx.compose.material3:material3-*:1.5.0-alpha13 is released. Version 1.5.0-alpha13 contains these commits.

#### API Changes

- Added support for search bar animationSpecForContentExpand and animationSpecForContentCollapse. (I033a5)

#### Bug Fixes

- Fixed an issue where the content of an expanded SearchBar did not use a theme-aware color (I878e0, b/379441904)
- Fixed a bug where BottomSheetScaffold would invoke SheetState's confirmValueChange callback with incorrect values when passing a drag handle to BottomSheetScaffold. Please note that confirmValueChange should only be used to veto state changes. Use a snapshotFlow to observe state changes. (Ice9ee, b/465824174, b/477031833)

### Version 1.5.0-alpha12

January 14, 2026

androidx.compose.material3:material3-*:1.5.0-alpha12 is released. Version 1.5.0-alpha12 contains these commits.

#### API Changes

- Updates TopAppBarDefaults (enterAlways and pinned behaviors) to support lazyListState, scrollState, and reverseScrolling. This fixes layout direction issues and correctly handles initially scrolled content. (I9d5c2, b/262234750)
- Add contentPadding and horizontalSpacing parameters to AssistChip and ElevatedAssistChip. Add HorizontalSpacing and ContentPadding defaults to AssistChipDefaults. (I2ac90, b/304853782)
- The DatePicker APIs using Java Time classes are no longer tagged as experimental. (I5039c, b/457537971)

#### Bug Fixes

- Fix a DatePicker date-formatting crash on API 23 (I67a94, b/452713222)
- Fixed visual alignment bug in the fancy animated indicator sample when used with scrollable tab rows. (Iae0f3, b/466790304)
- Fixed an issue where the TimePicker's AM/PM selector did not use text style defined by Material Design specification. (Ie908a, b/469788786)
- Fixed a crash in HorizontalFloatingToolbar that could occur in landscape mode when the on-screen keyboard was displayed. (Ia13c1, b/466692323)
- Fixed an issue where the TopAppBar title could overlap with its actions when no navigationIcon was provided. The title is now correctly constrained to its available space. (I2ba97, b/428697836)
- Support RTL with pane expansion anchors (I0770b, b/467775639)

## 8.6 Component and API Index

This index provides quick access to all components and APIs documented in this guide.

### Components

- [AlertDialog](#alertdialog-basic)
- [AppBarOverflowIndicator](#appbaroverflowindicator)
- [AssistChip](#assistchip)
- [BadgedBox](#badgedbox)
- [BasicAlertDialog](#basicalertdialog)
- [BottomAppBar](#bottomappbar-basic)
- [BottomSheetScaffold](#bottomsheetscaffold)
- [CenterAlignedTopAppBar](#centeralignedtopappbar-component)
- [CircularProgressIndicator](#circularprogressindicator-component)
- [CircularWavyProgressIndicator](#circularwavyprogressindicator-indeterminate)
- [ColorScheme](#colorscheme)
- [ContainedLoadingIndicator](#containedloadingindicator-indeterminate)
- [DatePicker](#datepicker)
- [DatePickerDialog](#datepickerdialog)
- [DateRangePicker](#daterangepicker-component)
- [DismissibleDrawerSheet](#dismissibledrawersheet-component)
- [DismissibleNavigationDrawer](#dismissiblenavigationdrawer-component)
- [DockedSearchBar](#dockedsearchbar-component)
- [DropdownMenu](#dropdownmenu)
- [ElevatedAssistChip](#elevatedassistchip-component)
- [ElevatedButton](#elevatedbutton-component)
- [ElevatedCard](#elevatedcard-component)
- [ElevatedFilterChip](#elevatedfilterchip-component)
- [ElevatedSuggestionChip](#elevatedsuggestionchip-component)
- [ExpandedDockedSearchBar](#expandeddockedsearchbar-component)
- [ExpandedFullScreenSearchBar](#expandedfullscreensearchbar-component)
- [ExposedDropdownMenuBox](#exposeddropdownmenubox-component)
- [ExtendedFloatingActionButton](#extendedfloatingactionbutton-component)
- [FilledTonalIconButton](#filledtonaliconbutton-component)
- [FilledTonalIconToggleButton](#filledtonalicontogglebutton-component)
- [FilterChip](#filterchip-component)
- [IconButton](#iconbutton-component)
- [IconToggleButton](#icontogglebutton-component)
- [LargeExtendedFloatingActionButton](#largeextendedfloatingactionbutton)
- [LargeFlexibleTopAppBar](#largeflexibletopappbar)
- [LargeFloatingActionButton](#largefloatingactionbutton)
- [LargeTopAppBar](#largetopappbar)
- [LinearWavyProgressIndicator](#linearwavyprogressindicator)
- [LoadingIndicator](#loadingindicator-component)
- [MaterialExpressiveTheme](#materialexpressivetheme)
- [MaterialTheme](#materialtheme)
- [MediumExtendedFloatingActionButton](#mediumextendedfloatingactionbutton)
- [MediumFlexibleTopAppBar](#mediumflexibletopappbar)
- [MediumFloatingActionButton](#mediumfloatingactionbutton)
- [MediumTopAppBar](#mediumtopappbar)
- [ModalBottomSheet](#modalbottomsheet)
- [ModalDrawerSheet](#modaldrawersheet)
- [ModalNavigationDrawer](#modalnavigationdrawer)
- [ModalWideNavigationRail](#modalwidenavigationrail)
- [NavigationBar](#navigationbar)
- [NavigationRail](#navigationrail)
- [OutlinedButton](#outlinedbutton)
- [OutlinedCard](#outlinedcard)
- [OutlinedIconButton](#outlinediconbutton)
- [OutlinedIconToggleButton](#outlinedicontogglebutton)
- [OutlinedSecureTextField](#outlinedsecuretextfield)
- [OutlinedTextField](#outlinedtextfield)
- [OutlinedToggleButton](#outlinedtogglebutton)
- [PermanentDrawerSheet](#permanentdrawersheet)
- [PermanentNavigationDrawer](#permanentnavigationdrawer)
- [PlainTooltip](#plaintooltip)
- [RadioButton](#radiobutton)
- [RangeSlider](#rangeslider)
- [RichTooltip](#richtooltip)
- [SearchBar](#searchbar)
- [SecureTextField](#securetextfield)
- [SegmentedButton](#segmentedbutton-multi-choice)
- [ShortNavigationBar](#shortnavigationbar)
- [SuggestionChip](#suggestionchip)
- [SwipeToDismissBox](#swipetodismissbox)
- [TextButton](#textbutton)
- [TextField](#textfield-continued)
- [TimePicker](#timepicker)
- [TimePickerDialog](#timepickerdialog)
- [ToggleButton](#togglebutton)
- [TooltipBox](#tooltipbox)
- [TopAppBar](#topappbar)
- [TriStateCheckbox](#tristatecheckbox)
- [TwoRowsTopAppBar](#tworowstopappbar)
- [VerticalDivider](#verticaldivider)
- [VerticalSlider](#verticalslider)
- [WideNavigationRail](#widenavigationrail)

### APIs

- [5.1 Core Theme](#51-core-theme-apis)
- [5.2 Dialog](#52-dialog-apis)
- [5.3 Menu](#53-menu-apis)
- [5.4 Date Picker State](#54-date-picker-state-apis)
- [5.5 Dynamic Color](#55-dynamic-color-apis)
- [BottomAppBarState](#bottomappbarstate)
- [Creating DateRangePickerState with Java Time API (Android API 26+)](#creating-daterangepickerstate-with-java-time-api-android-api-26)
- [DatePicker Extension Functions (Java Time API)](#datepicker-extension-functions-java-time-api)
- [DatePickerState](#datepickerstate)
- [DatePickerState (API 26+)](#datepickerstate-api-26)
- [DateRangePicker with Java Time API (Android API 26+)](#daterangepicker-with-java-time-api-android-api-26)
- [DateRangePickerState](#daterangepickerstate)
- [DateRangePickerState (API 26+)](#daterangepickerstate-api-26)
- [LocalRippleConfiguration](#localrippleconfiguration)
- [TimePickerState](#timepickerstate)
- [TooltipState](#tooltipstate)
