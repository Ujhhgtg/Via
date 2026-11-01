package dev.ujhhgtg.via.settings.compose

import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SettingsData
import dev.ujhhgtg.via.data.SettingsDataRepository
import dev.ujhhgtg.via.sites.SitePolicySettingsFragment
import dev.ujhhgtg.via.sites.SiteSettingsFragment
import dev.ujhhgtg.via.sites.SiteUserAgentSettingsFragment

/** jb.y4.h3/m3: the all-sites policy overview, now Material; each row opens its policy page. */
class KsuSiteConfSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.site_conf

    private val preferences by lazy { BrowserPreferences(requireContext()) }

    override fun groups(): List<KsuGroup> {
        val flags = preferences.webFlags
        fun allowed(value: Boolean) = getString(if (value) R.string.allowed else R.string.blocked)
        fun on(value: Boolean) = getString(if (value) R.string.on else R.string.off)
        fun permission(value: Boolean) = getString(if (value) R.string.ask_first else R.string.blocked)
        fun mode(block: Int, ask: Int) = when {
            flags and ask != 0 -> getString(R.string.ask_first)
            flags and block != 0 -> getString(R.string.blocked)
            else -> getString(R.string.allowed)
        }
        val content = listOf(
            KsuNavRow(1, R.drawable.ic_ksu_public, getString(R.string.all_sites)),
            KsuNavRow(14, R.drawable.ic_ksu_font_download, getString(R.string.size), value = "${preferences.textSize}%"),
            KsuNavRow(3, R.drawable.ic_ksu_smartphone, getString(R.string.agent), value = agentLabel()),
            KsuNavRow(17, R.drawable.ic_ksu_open_in_new, getString(R.string.action_pcview), value = on(flags and 2048 != 0)),
            KsuNavRow(2, R.drawable.ic_ksu_image, getString(R.string.images),
                value = if (flags and 32 != 0) getString(R.string.images_allowed_via_wifi_description_short) else allowed(flags and 16 != 0)),
            KsuNavRow(8, R.drawable.ic_ksu_code, getString(R.string.javascript), value = allowed(flags and 8 != 0)),
            KsuNavRow(9, R.drawable.ic_ksu_cookie, getString(R.string.cookies),
                value = if (flags and 8192 != 0 && flags and 16384 == 0) getString(R.string.cookies_allowed_except_third_party_description_short)
                else allowed(flags and 8192 != 0)),
            KsuNavRow(10, R.drawable.ic_ksu_open_in_new, getString(R.string.popups),
                value = getString(if (flags and 32768 != 0) R.string.ask_first else R.string.allowed)),
        )
        val basics = listOf(
            KsuNavRow(12, R.drawable.ic_ksu_block, getString(R.string.block_ads), value = on(flags and 1 != 0)),
        )
        val permissions = listOf(
            KsuNavRow(4, R.drawable.ic_ksu_content_copy, getString(R.string.clipboard), value = mode(131072, 262144)),
            KsuNavRow(11, R.drawable.ic_ksu_apps, getString(R.string.open_app), value = mode(33554432, 67108864)),
            KsuNavRow(5, R.drawable.ic_ksu_location, getString(R.string.location1), value = permission(flags and 2 != 0)),
            KsuNavRow(6, R.drawable.ic_ksu_videocam, getString(R.string.camera), value = permission(flags and 8388608 != 0)),
            KsuNavRow(7, R.drawable.ic_ksu_mic, getString(R.string.microphone), value = permission(flags and 16777216 != 0)),
            KsuNavRow(13, R.drawable.ic_ksu_link_off, getString(R.string.page_redirection),
                value = if (flags and 134217728 == 0) allowed(true) else getString(R.string.page_redirection_ask_first_description_short)),
            KsuNavRow(15, R.drawable.ic_ksu_gesture, getString(R.string.vibrate), value = allowed(flags and 1073741824 != 0)),
        )
        val advanced = if (!dev.ujhhgtg.via.engine.Engines.backend.capabilities.quickBackSegments) emptyList() else listOf(
            KsuNavRow(16, R.drawable.ic_ksu_gesture, getString(R.string.quick_back), value = on(flags and 512 != 0)),
        )
        return listOf(
            KsuGroup(getString(R.string.category_content), content),
            KsuGroup(getString(R.string.category_basics), basics),
            KsuGroup(getString(R.string.category_permissions), permissions),
            KsuGroup(getString(R.string.settings_advanced), advanced),
        )
    }

    override fun onRow(row: KsuRow) {
        val host = requireActivity() as Shell
        when (row.key) {
            1 -> host.navigate(SiteSettingsFragment.newInstance())
            3 -> host.navigate(SiteUserAgentSettingsFragment())
            14 -> host.navigate(KsuWebTextSizeSettingsFragment())
            else -> host.navigate(SitePolicySettingsFragment.create(row.key as Int))
        }
    }

    private fun agentLabel(): String? {
        val choice = preferences.userAgentChoice
        val builtins = mapOf(0 to R.string.default_set, -1 to R.string.agent_android_phone,
            -2 to R.string.agent_android_tablet, -3 to R.string.agent_windows_chrome,
            -4 to R.string.agent_windows_ie11, -5 to R.string.agent_osx,
            -6 to R.string.agent_iphone, -7 to R.string.agent_ipad, -8 to R.string.agent_symbian)
        builtins[choice]?.let { return getString(it) }
        return BrowserDatabase(requireContext()).use { database ->
            SettingsDataRepository(database).list(SettingsData.USER_AGENT).firstOrNull { it.id == choice }?.title
        }
    }
}
