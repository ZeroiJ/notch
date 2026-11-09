# Reverse Engineering Report: The Notch (`com.dattatech.thenotch`)

Target Device: Nothing Phone (2a) Plus / `A009P` (`SuperContraIND`)
Analysis Date: 2026-11-09
Package: `com.dattatech.thenotch` (v0.1.31, targetSdk 36, minSdk 32)

---

## 1. System Overlay Architecture

"The Notch" utilizes a dual-tier window management strategy to achieve continuous visibility across all Android system surfaces:

### Tier 1: Accessibility Overlay (`IslandAccessibilityService`)
* **Window Type:** `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY` (`2032`)
* **Permission:** `android.permission.BIND_ACCESSIBILITY_SERVICE`
* **Z-Order:** Maximum possible on Android; renders above:
  * Lock screen / Keyguard
  * Power menu dialogs
  * Quick Settings and Notification shade pull-down
* **Global Capabilities:**
  * Direct invocation of `performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)`
  * Window content retrieval (`canRetrieveWindowContent = true`)
  * Global gesture dispatch (`canPerformGestures = true`)

### Tier 2: Application Overlay (`IslandOverlayService`)
* **Window Type:** `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` (`2038`)
* **Permission:** `android.permission.SYSTEM_ALERT_WINDOW`
* **Window Flags:**
  * `FLAG_NOT_FOCUSABLE`
  * `FLAG_LAYOUT_IN_SCREEN`
  * `FLAG_LAYOUT_NO_LIMITS`
  * `FLAG_NOT_TOUCH_MODAL`
* **Cutout Mode:** `layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS` (API 30+) / `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` (API 28-29)

---

## 2. Spring Physics Specification (Decompiled Constants)

Extracted from runtime physics table (`Lmq1;` / `SpringPair`):

| Animation Trigger | Target State | Damping Ratio ($\zeta$) | Stiffness ($k$) | Visual Effect |
| :--- | :--- | :--- | :--- | :--- |
| **Active Gesture Drag** | Tracking touch | `0.70f` | `1200.0f` | Immediate, zero-latency adherence to touch position |
| **Peek / Alert Pop** | `Peek` bubble | `0.45f` - `0.55f` | `760.0f` - `900.0f` | Playful iOS-style overshoot & bounce |
| **Card Expansion** | `Expanded` card | `0.62f` - `0.66f` | `600.0f` - `700.0f` | Snappy, fluid capsule-to-card morph |
| **Lens Tuck / Idle** | `Compact` pill | `0.68f` - `0.70f` | `260.0f` - `300.0f` | Gentle settling around the physical cutout |

### Spring Configuration Contract

```kotlin
data class SpringParams(
    val dampingRatio: Float,
    val stiffness: Float
) {
    init {
        require(stiffness > 0f) { "Spring stiffness constant must be positive." }
        require(dampingRatio >= 0f) { "Damping ratio must be non-negative." }
    }
}

data class SpringPair(
    val standard: SpringParams,   // damping: 0.65f, stiffness: 650f
    val expressive: SpringParams, // damping: 0.50f, stiffness: 850f
    val reduced: SpringParams     // damping: 1.00f, stiffness: 400f (accessibility)
)
```

---

## 3. State & Intent Machine Architecture

### A. Panel States (`PanelState`)
1. **`Hidden`**: Zero opacity, completely tucked into camera aperture.
2. **`Compact`**: Resting pill wrapping the lens with leading/trailing visualizers.
3. **`Expanded`**: Interactive modal card displaying full content.
4. **`Controls`**: Quick control toggles (flashlight, volume, system settings).
5. **`Activities`**: Multi-app dock when multiple background activities exist.

### B. Island Intents (`IslandIntent`)
* **Touch & Lifecycle:**
  * `Touched`: Raw initial touch detected in hit region.
  * `Expand`: Expansion triggered by downward drag or tap.
  * `Collapse`: Return to compact resting pill.
  * `TimeoutCollapse`: Auto-collapse timer fired.
  * `Dismiss`: Horizontal flick/swipe to hide current activity.
  * `SurfaceTransitionSettled`: Physics animation reached rest position.
* **Interactive Controls:**
  * `BeginReply` / `UpdateReply` / `SendReply` / `CancelReply`: Inline messaging quick-reply.
  * `Seek`: Media progress bar scrubbing.
  * `ToggleFlashlight`: Direct camera torch control.
  * `OpenNotificationShade`: Calls system notification panel.
  * `OpenAppSearch` / `LaunchApp`: App shortcut launcher.
* **Multi-Tasking:**
  * `OpenActivities`, `SelectActivity`, `PageActivity`, `DismissActivities`.

---

## 4. Hardware Cutout & Gesture Hit-Testing Insights

1. **`pillGestureRoom` (Touch Slop Cushion):**
   * Physical punch holes are small (~10-14mm).
   * Tapping directly on the camera lens causes smudges and high miss rates.
   * `pillGestureRoom` expands the invisible touch boundary around the pill by 12–16dp on all sides.
2. **`FindCameraActivity` (Calibration Mechanism):**
   * Provides user-adjustable offsets (`cutoutCenterXDp`, `cutoutTopInsetDp`, `cutoutWidthDp`, `cutoutClearanceDp`).
   * Compensates for fragmented OEM ROMs that report inaccurate `DisplayCutout.boundingRects`.
3. **OLED Black Contrast:**
   * Uses `#000000` base with a 15% opacity white rim (`0x26FFFFFF`) to ensure visual separation when apps under the island have dark status bars.
