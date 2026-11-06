package dev.ujhhgtg.via.settings.compose

import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.common.GeneratedDocumentState
import dev.ujhhgtg.via.data.BrowserPreferences

/** hb.y4: the three privacy controls, now as Material rows with their own icons. */
class KsuPrivacySettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.settings_privacy
    private val preferences by lazy { BrowserPreferences(requireContext()) }

    override fun groups(): List<KsuGroup> {
        GeneratedDocumentState.initialize(preferences)
        return listOf(
            KsuGroup(null, listOf(
                KsuToggleRow(1, R.drawable.ic_ksu_visibility_off, getString(R.string.do_not_track),
                    checked = preferences.doNotTrack),
                KsuToggleRow(6, R.drawable.ic_ksu_videocam_off, getString(R.string.disable_webrtc),
                    checked = preferences.disableWebRtc),
                KsuToggleRow(4, R.drawable.ic_ksu_share, getString(R.string.do_not_sell_or_share),
                    getString(R.string.do_not_sell_or_share_description), checked = preferences.doNotSellOrShare),
            )),
        )
    }

    override fun onToggle(row: KsuToggleRow, checked: Boolean) {
        when (row.key) {
            1 -> preferences.doNotTrack = checked
            6 -> preferences.disableWebRtc = checked
            4 -> preferences.doNotSellOrShare = checked
        }
        // hb.y4.i3: header switches additionally request e8.i.L to rebind resident WebViews.
        if (row.key == 1 || row.key == 4) GeneratedDocumentState.mark(GeneratedDocumentState.PAGE_SETTINGS)
        refresh()
    }
}
