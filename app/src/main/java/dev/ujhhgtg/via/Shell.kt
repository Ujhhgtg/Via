package dev.ujhhgtg.via

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import dev.ujhhgtg.via.common.SoftInputAssistObserver
import dev.ujhhgtg.via.common.WindowInsetsHelper
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.engine.Engines
import dev.ujhhgtg.via.home.HomeCustomizationFragment
import dev.ujhhgtg.via.settings.SettingsFragment
import dev.ujhhgtg.via.tools.PdfViewerFragment
import dev.ujhhgtg.via.ui.ViaActivity
import dev.ujhhgtg.via.ui.WelcomeFragment
import java.util.WeakHashMap

/** Original mark.via.Shell and g6.i: the activity owns the fragment container. */
open class Shell : ViaActivity() {
    private val lastNavigation = WeakHashMap<Fragment, Long>()
    private lateinit var softInputAssist: SoftInputAssistObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        // Original CustomTab uses the platform-fitted window; only the main
        // browser Shell opts into the edge-to-edge canvas.
        if (!intent.getBooleanExtra("CUSTOM_TAB", false)) WindowInsetsHelper.enableEdgeToEdge(this)
        super.onCreate(savedInstanceState)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

            if (Engines.backend.id == "gecko") {
                @Suppress("DEPRECATION")
                val targetDisplay = windowManager.defaultDisplay
                val currentMode = targetDisplay.mode

                preferredDisplayModeId = 0
                preferredRefreshRate = targetDisplay.supportedModes
                    .filter {
                        it.physicalWidth == currentMode.physicalWidth &&
                                it.physicalHeight == currentMode.physicalHeight
                    }
                    .maxOfOrNull { it.refreshRate }
                    ?: currentMode.refreshRate
            }
        }
        setContentView(FrameLayout(this).apply {
            id = R.id.fragment_container
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        })
        if (savedInstanceState == null) {
            // mark.via.CustomTab.V launches the browser directly and never
            // inserts the normal first-run agreement fragment.
            if (!intent.getBooleanExtra("CUSTOM_TAB", false) && BrowserPreferences(this).agreementLevel < 1) {
                supportFragmentManager.beginTransaction().setReorderingAllowed(true)
                    .replace(R.id.fragment_container, WelcomeFragment(), WelcomeFragment::class.java.name).commit()
            } else showBrowser()
        }
        softInputAssist = SoftInputAssistObserver(this).apply {
            onKeyboardHiddenChanged = { hidden -> browserFragment()?.onKeyboardHiddenChanged(hidden) }
        }
        lifecycle.addObserver(softInputAssist)
        window.decorView.post { WindowInsetsHelper.readRoundedCorners(androidx.core.view.ViewCompat.getRootWindowInsets(window.decorView)) }
    }

    fun showBrowser() {
        // Shell.g0 / CustomTab.V: without a loaded WebView provider the browser is replaced by ra.h.
        if (dev.ujhhgtg.via.ui.WebViewMissingFragment.webViewMissing()) {
            supportFragmentManager.beginTransaction().setReorderingAllowed(true)
                .replace(R.id.fragment_container, dev.ujhhgtg.via.ui.WebViewMissingFragment.newInstance(
                    dev.ujhhgtg.via.ui.WebViewMissingFragment.CODE_MISSING_WEBVIEW)).commit()
            return
        }
        supportFragmentManager.beginTransaction().setReorderingAllowed(true)
            .replace(R.id.fragment_container, BrowserFragment(), BrowserFragment::class.java.name).commit()
    }

    fun navigate(fragment: Fragment) {
        val manager = supportFragmentManager
        val current = manager.fragments.lastOrNull { it.isVisible } ?: return
        val now = SystemClock.elapsedRealtime()
        val previous = lastNavigation[current]
        if (previous != null && now - previous <= 300) return
        lastNavigation[current] = now
        manager.beginTransaction().setReorderingAllowed(true)
            .setCustomAnimations(R.anim.fragment_enter, R.anim.fragment_exit, R.anim.fragment_pop_enter, R.anim.fragment_pop_exit)
            .add(R.id.fragment_container, fragment, fragment.javaClass.name)
            .hide(current).addToBackStack(null).commit()
    }

    /**
     * With the KernelSU-style page enabled the settings root and the ported sub-pages are Compose;
     * every other action keeps its classic fragment.
     */
    fun openSettings(action: String? = null) {
        val ported = dev.ujhhgtg.via.data.BrowserPreferences(this).ksuSettingsUi
        val fragment = when {
            !ported -> SettingsFragment.newInstance(action)
            action == null || action == "settings" -> dev.ujhhgtg.via.settings.compose.KsuSettingsFragment()
            action == "settings_general" -> dev.ujhhgtg.via.settings.compose.KsuGeneralSettingsFragment()
            action == "settings_privacy" -> dev.ujhhgtg.via.settings.compose.KsuPrivacySettingsFragment()
            action == "settings_advanced" -> dev.ujhhgtg.via.settings.compose.KsuAdvancedSettingsFragment()
            action == "settings_about" -> dev.ujhhgtg.via.settings.compose.KsuAboutSettingsFragment()
            action == "settings_script" -> dev.ujhhgtg.via.settings.compose.KsuScriptSettingsFragment()
            action == "settings_extensions" -> dev.ujhhgtg.via.settings.compose.KsuExtensionSettingsFragment()
            action == "search_settings" -> dev.ujhhgtg.via.settings.compose.KsuSearchSettingsFragment()
            action == "agent" -> dev.ujhhgtg.via.settings.compose.KsuUserAgentSettingsFragment()
            action == "action_night" -> dev.ujhhgtg.via.settings.compose.KsuNightModeSettingsFragment()
            action == "block_ads" -> dev.ujhhgtg.via.settings.compose.KsuBlockAdsSettingsFragment()
            action == "settings_operation" -> dev.ujhhgtg.via.settings.compose.KsuGesturesSettingsFragment()
            action == "font" -> dev.ujhhgtg.via.settings.compose.KsuFontSettingsFragment()
            action == "password_manager" -> dev.ujhhgtg.via.settings.compose.KsuPasswordSettingsFragment()
            action == "customize_context_menu" -> dev.ujhhgtg.via.settings.compose.KsuContextMenuSettingsFragment()
            action == "reader_mode" -> dev.ujhhgtg.via.settings.compose.KsuReaderSettingsFragment()
            action == "toolbars_settings" -> dev.ujhhgtg.via.settings.compose.KsuToolbarSettingsFragment()
            else -> SettingsFragment.newInstance(action)
        }
        navigate(fragment)
    }

    fun openPage(action: String) {
        when (action.substringBefore(':')) {
            "homepage_customization" -> navigate(dev.ujhhgtg.via.home.HomeCustomizationFragment())
            "password_manager" -> navigate(dev.ujhhgtg.via.passwords.PasswordManagerFragment())
            "site_conf" -> navigate(dev.ujhhgtg.via.sites.GlobalSiteSettingsFragment())
            "user_syns" -> navigate(dev.ujhhgtg.via.sync.SyncSettingsFragment())
            else -> openSettings(action)
        }
    }

    fun openRecordFromRecords(url: String, background: Boolean) = browserFragment()?.openRecordFromRecords(url, background)
    fun openRecordFromRecords(url: String, mode: Int) = browserFragment()?.openRecordFromRecords(url, mode)
    fun openClosedTabFromRecords(id: String, mode: Int) = browserFragment()?.openClosedTabFromRecords(id, mode)
    fun copyRecordFromRecords(url: String) = browserFragment()?.copyRecordFromRecords(url)

    /** bb.v -> pc: recently closed tabs remains inside the records flow. */
    fun openTabsFromRecords() = browserFragment()?.openClosedTabsFromRecords()
    fun browserReloadPreferences() = browserFragment()?.reloadBrowserPreferences()

    /** Shell.onConfigurationChanged dispatches o8.b: browser rebinding, settings view rebuilding. */
    override fun onNightThemeChanged(night: Boolean) {
        // Publish the live browser background before a settings page takes its new snapshot.
        browserFragment()?.onNightThemeChanged(night)
        super.onNightThemeChanged(night)
        val manager = supportFragmentManager
        if (manager.isStateSaved) return
        manager.fragments.filter { it.view != null && !it.isDetached }.forEach { fragment ->
            when (fragment) {
                is HomeCustomizationFragment -> fragment.refreshForTheme()
                is PdfViewerFragment -> {
                    manager.beginTransaction().detach(fragment).commit()
                    manager.beginTransaction().attach(fragment).commit()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        browserFragment()?.onNewIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        browserFragment()?.onWindowFocusChanged(hasFocus)
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val browser = browserFragment()
        return event.action == KeyEvent.ACTION_DOWN && browser?.isVisible == true && browser.onKeyDown(
            event) || super.dispatchKeyEvent(event)
    }

    private fun browserFragment() = supportFragmentManager.findFragmentByTag(BrowserFragment::class.java.name) as? BrowserFragment
}
