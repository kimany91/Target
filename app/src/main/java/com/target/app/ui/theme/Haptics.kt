package com.target.app.ui.theme

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/** Rich haptic vocabulary: light ticks for navigation, confirm/reject, and a celebratory pattern for deals. */
class Haptics(private val view: View, private val enabled: () -> Boolean) {
    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31) (view.context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") view.context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }.getOrNull()

    private fun perform(constant: Int) { if (enabled()) view.performHapticFeedback(constant) }

    fun tick() = perform(if (Build.VERSION.SDK_INT >= 27) HapticFeedbackConstants.KEYBOARD_PRESS else HapticFeedbackConstants.VIRTUAL_KEY)
    fun click() = perform(HapticFeedbackConstants.CONTEXT_CLICK)
    fun toggle(on: Boolean) = perform(if (Build.VERSION.SDK_INT >= 34) (if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF) else HapticFeedbackConstants.CONTEXT_CLICK)
    fun confirm() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
    fun reject() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    fun longPress() = perform(HapticFeedbackConstants.LONG_PRESS)
    fun segment() = perform(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK)

    /** Rising "found it" effect. */
    fun success() = compose(
        listOf(VibrationEffect.Composition.PRIMITIVE_TICK to 0.5f, VibrationEffect.Composition.PRIMITIVE_CLICK to 0.9f),
        fallback = longArrayOf(0, 18, 60, 28),
    )

    /** Strong double pulse reserved for deals. */
    fun deal() = compose(
        listOf(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE to 0.7f, VibrationEffect.Composition.PRIMITIVE_CLICK to 1f, VibrationEffect.Composition.PRIMITIVE_CLICK to 1f),
        fallback = longArrayOf(0, 30, 70, 30, 70, 60),
    )

    private fun compose(prims: List<Pair<Int, Float>>, fallback: LongArray) {
        if (!enabled()) return
        val v = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 30 && v.areAllPrimitivesSupported(*prims.map { it.first }.toIntArray())) {
                val c = VibrationEffect.startComposition()
                prims.forEachIndexed { i, (p, s) -> c.addPrimitive(p, s, if (i == 0) 0 else 40) }
                v.vibrate(c.compose())
            } else {
                v.vibrate(VibrationEffect.createWaveform(fallback, -1))
            }
        }
    }
}

val LocalHaptics = staticCompositionLocalOf<Haptics> { error("Haptics not provided") }

@Composable
fun rememberHaptics(enabled: () -> Boolean): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view, enabled) }
}
