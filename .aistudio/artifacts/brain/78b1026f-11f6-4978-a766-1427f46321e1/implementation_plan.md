# Story Test - Automated Accessibility Test Tool

Story Test is an Android automation and testing tool powered by an `AccessibilityService` that executes UI interaction scripts on third-party applications (specifically designed to verify that a Facebook story with a link can be posted completely hands-free). It includes an inspector overlay, step builder, JSON import/export, batch runner, security check safety guards, and full capture sharing.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions were clarified and confirmed during Phase 1:

- **Confirmed Decision 1 (Script Template)**: Include a preloaded, ready-to-test Facebook Story script template with customizable variable slots (`{{PAGE_NAME}}`, `{{LINK}}`) and sensible fallbacks.
- **Confirmed Decision 2 (Floating Overlay Style)**: Draggable floating pill overlay (`TYPE_ACCESSIBILITY_OVERLAY`) with dual primary buttons: quick inspect capture and an immediate abort/stop button.
- **Confirmed Decision 3 (Safety Interruption)**: When security check or login verification text is detected ("Confirm it's you", "security check", "captcha", "Log in", "Enter the code"), execution pauses immediately, rings an alert, and displays a prominent "Needs a person" banner.

---

## 1. Overview & Core Concept

- **What It Does**: Automates complex multi-step mobile user flows on Android using gesture dispatch, node hierarchy traversal, and text entry through an Android `AccessibilityService`. Captures screen node hierarchies and screenshots, parses variables in scripts, and runs multi-page batches with comprehensive failure logging.
- **Target Audience / Persona**: Mobile QA engineers, growth testers, and automation researchers validating social media posting flows and UI accessibility without requiring physical touch or tethered ADB.
- **Key Value**: Deterministic UI test execution with zero network dependency, on-device step replay, automated capture diagnostics on failure, and proactive human-in-the-loop safety detection.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Service Setup & Home**:
   - Status indicators for `AccessibilityService` enabled state and Overlay capabilities.
   - Quick "Enable Service" button navigating straight to Android Accessibility Settings.
   - Quick status cards: Scripts count, Captures count, Safety triggers list.
2. **Inspector & Floating Overlay**:
   - Draggable pill overlay with quick Capture button and Stop button.
   - On tap Capture: reads node tree (package, class, text, desc, viewId, clickable, bounds), captures screenshot (`AccessibilityService.takeScreenshot`), writes metadata JSON + PNG into app cache/files, and shows a snackbar.
   - Captures screen: browsable list with screenshot thumbnail, timestamp, package name, node hierarchy tree explorer, and a "Share" button to export zip/json/png.
3. **Scripts Builder & Editor**:
   - List of scripts with preloaded "Facebook Story with Link" template.
   - Step builder supporting all 14 step types: `launchApp`, `tapText`, `tapDesc`, `tapId`, `tapXY`, `longPressText`, `typeText`, `waitText`, `waitDesc`, `scrollDown`, `scrollUp`, `back`, `home`, `pickNewestVideo`, `sleep`, `screenshot`.
   - Reorderable, editable parameters, variable placeholder insertion helpers (`{{PAGE_NAME}}`, `{{LINK}}`), and JSON export/import.
4. **Batch Run Screen**:
   - Script selector dropdown, multi-line Page Names input, Link input.
   - "Start in 5 seconds" action initiating a 5-second countdown allowing the tester to position the device.
   - Live overlay & screen execution state: active page, step index / total, step description, elapsed time, live log.
   - Immediate "Stop" action from overlay and in-app.
   - Post-run summary table: Page Name, Status (OK / FAILED), Failed Step Name, Duration, and a "Share Results" export.
5. **Safety Guard Settings**:
   - Editable list of trigger phrases (e.g. "Confirm it's you", "security check", "captcha", "Log in", "Enter the code").
   - Ability to add custom trigger phrases.

### Visual Identity & Theme

- **Aesthetic Direction**: Precision test engineering workbench. Clean, high-contrast dark-mode-first aesthetic with Material Design 3 tokens.
- **Color Palette**:
  - `Primary`: Electric Emerald (`#00E676` / `#00C853`) denoting active, automated precision.
  - `Error / Stop`: Vivid Coral Red (`#FF5252`) for emergency stop and failure indicators.
  - `Warning / Safety`: Amber Gold (`#FFD740`) for "Needs a person" safety pauses.
  - `Background & Surface`: Deep Obsidian (`#121316`) and Slate Charcoal (`#1E2025`) with elevated container cards.
- **Typography & Hierarchy**: Clean monospace accents for step commands and view IDs; bold geometric sans-serif for screen titles and status badges.
- **Interactive Feedback**: Haptic feedback on step execution, animated progress indicators during runs, pulse effect during 5s launch countdown, and draggable physics for the overlay pill.

---

## 3. Key Product Decisions & Trade-Offs

- **Accessibility Overlay (`TYPE_ACCESSIBILITY_OVERLAY`) vs System Alert Window**:
  - *Chosen Approach*: Leverage `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY` initialized directly within `StoryTestService`.
  - *Why*: In Android 8.0+, accessibility services with overlay capabilities do not require a separate manual `SYSTEM_ALERT_WINDOW` permission grant in system settings, providing a seamless user experience.
- **Room Database for Scripts & Captures**:
  - *Chosen Approach*: Store script definitions, step configurations, and safety keywords in Room with TypeConverters for step arguments; store capture metadata in Room with file pointers to disk images.
  - *Why*: Provides robust local persistence, reactive `Flow` observation in Compose, and easy schema migrations without network dependencies.
- **Robolectric & Pure Kotlin Unit Tests for StepRunner**:
  - *Chosen Approach*: Decouple variable expansion, retry loops, and step validation into a testable `StepRunner` / `VariableResolver` engine covered by Robolectric/JUnit test cases.
  - *Why*: Allows thorough verification of timeout/retry and variable interpolation logic without requiring live Android devices or emulator instrumentation.

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                          Jetpack Compose UI                            │
│  ┌──────────────┐  ┌───────────────┐  ┌─────────────┐  ┌────────────┐  │
│  │ Home & State │  │ Scripts / Edit│  │ Run & Batch │  │  Captures  │  │
│  └──────┬───────┘  └───────┬───────┘  └──────┬──────┘  └─────┬──────┘  │
│         │                  │                 │               │         │
│  ┌──────┴──────────────────┴─────────────────┴───────────────┴──────┐  │
│  │                    StoryTestViewModel                            │  │
│  └──────────────────────────────┬───────────────────────────────────┘  │
└─────────────────────────────────┼──────────────────────────────────────┘
                                  │
┌─────────────────────────────────┴──────────────────────────────────────┐
│                            Domain Layer                                │
│  ┌─────────────────────────┐               ┌────────────────────────┐  │
│  │       StepRunner        │               │   SafetyCheckDetector  │  │
│  │ - Variable Interpolator │               │ - Keywords Evaluator   │  │
│  │ - Retry & Timeout Loop  │               │ - Pauses & Alerts      │  │
│  └────────────┬────────────┘               └───────────┬────────────┘  │
│               │                                        │               │
│  ┌────────────┴────────────────────────────────────────┴────────────┐  │
│  │                      StoryTestService                            │  │
│  │  (AccessibilityService + WindowManager Floating Overlay)         │  │
│  │  - Gesture Dispatcher (tap, long press, scroll, XY)              │  │
│  │  - Node Hierarchy Inspector (find by text, id, desc)             │  │
│  │  - Text Input Injection (ACTION_SET_TEXT & clipboard)            │  │
│  │  - Screenshot Capture (takeScreenshot API)                       │  │
│  └────────────────────────────┬─────────────────────────────────────┘  │
└───────────────────────────────┼────────────────────────────────────────┘
                                │
┌───────────────────────────────┴────────────────────────────────────────┐
│                        Data Layer & Storage                            │
│  ┌───────────────────────┐                  ┌───────────────────────┐  │
│  │      Room Database    │                  │      Local Storage    │  │
│  │  - Scripts & Steps    │                  │  - PNG Screenshots    │  │
│  │  - Safety Rules       │                  │  - Node JSON Dumps    │  │
│  │  - Batch Run Reports  │                  │  - Export FileProvider│  │
│  └───────────────────────┘                  └───────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

### Data Models & Entities

- **`ScriptEntity`**: `id`, `name`, `description`, `stepsJson`, `createdAt`.
- **`ScriptStep`**: `type` (`LAUNCH_APP`, `TAP_TEXT`, `TAP_DESC`, `TAP_ID`, `TAP_XY`, `LONG_PRESS_TEXT`, `TYPE_TEXT`, `WAIT_TEXT`, `WAIT_DESC`, `SCROLL_DOWN`, `SCROLL_UP`, `BACK`, `HOME`, `PICK_NEWEST_VIDEO`, `SLEEP`, `SCREENSHOT`), `target`, `exactMatch`, `index`, `xPercent`, `yPercent`, `timeoutMs`, `value`.
- **`CaptureEntity`**: `id`, `timestamp`, `packageName`, `screenshotPath`, `nodeHierarchyJson`.
- **`RunResult`**: `pageName`, `status` (`OK`, `FAILED`, `PAUSED_NEEDS_PERSON`), `failedStep`, `durationSeconds`, `captureId`.

### Step Execution Strategy

1. **Variable Replacement**: `{{PAGE_NAME}}` and `{{LINK}}` replaced prior to step evaluation.
2. **Find & Retry**: Periodically inspect `rootInActiveWindow` or window roots every 300ms until condition is met or step timeout expires.
3. **Safety Verification**: Prior to each retry cycle, inspect active window text against `safetyKeywords`. If detected, suspend runner, post notification, and switch overlay to "Needs a person".
4. **Gesture & Action Execution**:
   - `tapXY`: `dispatchGesture` with `Path` at calculated screen dimensions.
   - `tapText` / `tapDesc` / `tapId`: Click node or perform gesture tap on node bounds center if node is not clickable directly.
   - `typeText`: `ACTION_SET_TEXT` bundle with fallback to clipboard paste or key event injection.
   - `pickNewestVideo`: Find gallery/media grid containers and select first child node.
