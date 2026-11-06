package dev.ujhhgtg.via.settings

import android.os.Build
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.SwipeBackLayout

/**
 * z8.n3.h + the original swipe-back/​predictive-back wiring, shared by the classic settings pages
 * and the Compose (KernelSU-style) root page so both behave identically when going back.
 */
internal object SettingsSwipeBack {
    /** Predictive back is enabled from API 35 on, except on HONOR/HUAWEI builds. */
    fun predictiveBackSupported(): Boolean = Build.VERSION.SDK_INT >= 35 &&
        listOf(Build.BRAND, Build.MANUFACTURER).none { it.equals("HONOR", true) || it.equals("HUAWEI", true) }

    fun install(
        fragment: Fragment,
        swipe: SwipeBackLayout,
        allowPredictiveBack: () -> Boolean,
        onBack: () -> Unit,
    ) {
        if (!predictiveBackSupported()) return
        fragment.requireActivity().onBackPressedDispatcher.addCallback(
            fragment.viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                private var predictive = false
                override fun handleOnBackStarted(backEvent: BackEventCompat) {
                    if (!predictive && !BrowserPreferences(fragment.requireContext()).disablePredictiveBack &&
                        allowPredictiveBack() && fragment.view != null
                    ) predictive = swipe.startPredictiveBack()
                }
                override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                    if (predictive) swipe.updatePredictiveBack(backEvent.progress)
                }
                override fun handleOnBackCancelled() {
                    if (predictive) { swipe.cancelPredictiveBack(); predictive = false }
                }
                override fun handleOnBackPressed() {
                    if (predictive) { predictive = false; swipe.finishPredictiveBack() } else onBack()
                }
            },
        )
    }
}
