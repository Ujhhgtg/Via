package dev.ujhhgtg.via.downloads

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ApkSplitSelectionTest {
    private val device = ApkSplitSelection.Device(listOf("arm64-v8a", "armeabi-v7a"), 440, listOf("zh-CN", "en-US"), 35)
    private fun part(name: String?, target: String? = null, dependencies: Set<String> = emptySet(),
        version: Long = 7, minSdk: Int = 29, abis: Set<String> = emptySet(), pkg: String = "com.example.app") =
        ApkPart(File(name ?: "base"), name ?: "base", ApkManifest(pkg, version, name, target, dependencies, minSdk, false), abis)

    @Test fun selectsDeviceAbiDensityLanguagesAndFeatureDependencies() {
        val base = part(null)
        val parts = listOf(base, part("config.arm64_v8a"), part("config.armeabi_v7a"), part("config.x86"),
            part("config.xhdpi"), part("config.xxhdpi"), part("config.xxxhdpi"), part("config.zh"), part("config.en"), part("config.fr"),
            part("maps"), part("maps.config.arm64_v8a", "maps"), part("maps.config.x86", "maps"), part("maps.config.xxhdpi", "maps"))
        val selected = ApkSplitSelection.recommended(parts, base, device)
        assertEquals(setOf(null, "config.arm64_v8a", "config.xxhdpi", "config.zh", "config.en", "maps", "maps.config.arm64_v8a", "maps.config.xxhdpi"), selected.map { it.manifest.splitName }.toSet())
        assertTrue(ApkSplitSelection.valid(selected.toList(), device.sdk))
    }

    @Test fun exactLanguageMatchesCanIncludeSeveralSystemLocales() {
        val base = part(null)
        val parts = listOf(base, part("config.en"), part("config.zh"), part("config.fr"))
        val selected = ApkSplitSelection.recommended(parts, base, device.copy(languages = listOf("zh", "en")))
        assertEquals(setOf(null, "config.en", "config.zh"), selected.map { it.manifest.splitName }.toSet())
    }

    @Test fun nativeFeatureMastersRemainSelected() {
        val base = part(null)
        val feature = part("delivery", abis = setOf("arm64-v8a"))
        assertNull(ApkSplitSelection.abi(feature))
        assertTrue(feature in ApkSplitSelection.recommended(listOf(base, feature), base, device))
    }

    @Test fun legacyLanguageCodesAndRegionalQualifiersMatchSystemLocales() {
        val base = part(null)
        val hebrew = part("config.iw")
        val taiwan = part("config.zh-rTW")
        val selected = ApkSplitSelection.recommended(listOf(base, hebrew, taiwan), base,
            device.copy(languages = listOf("he", "zh-TW")))
        assertEquals(setOf(base, hebrew, taiwan), selected)
    }

    @Test fun dependenciesDuplicatesMixedVersionsAndMixedPackagesAreRejected() {
        val base = part(null)
        val config = part("maps.config.en", "maps")
        assertFalse(ApkSplitSelection.valid(listOf(base, config), 35))
        assertTrue(ApkSplitSelection.valid(listOf(base, part("maps"), config), 35))
        assertFalse(ApkSplitSelection.valid(listOf(base, part("config.en"), part("config.en")), 35))
        assertFalse(ApkSplitSelection.valid(listOf(base, part("config.en", version = 8)), 35))
        assertFalse(ApkSplitSelection.valid(listOf(base, part("config.en", pkg = "another.app")), 35))
        assertFalse(ApkSplitSelection.valid(listOf(base, part("module", dependencies = setOf("required"))), 35))
    }

    @Test fun selectsOnlyOneCompatibleAlternativeForEachSplitName() {
        val base = part(null)
        val old = part("config.en", minSdk = 21)
        val current = part("config.en", minSdk = 30)
        val future = part("config.en", minSdk = 36)
        val selected = ApkSplitSelection.recommended(listOf(base, old, current, future), base, device.copy(languages = listOf("en")))
        assertEquals(setOf(base, current), selected)
    }

    @Test fun baseSelectionRespectsAbiSdkAndVersion() {
        val future = part(null, minSdk = 36)
        val wrongAbi = part(null, abis = setOf("x86"))
        val current = part(null, abis = setOf("arm64-v8a"), version = 8)
        val old = part(null, abis = setOf("arm64-v8a"), version = 7)
        assertEquals(current, ApkSplitSelection.bestBase(listOf(future, wrongAbi, current, old), device))
    }

    @Test fun binaryManifestReadsBothStringEncodingsAndTypedAttributes() {
        for (utf8 in listOf(true, false)) {
            val manifest = ApkManifest.read(BinaryManifestFixture.create(split = "maps.config.en", target = "maps", dependency = "maps",
                version = -1, versionMajor = 2, minSdk = 24, splitRequired = true, utf8 = utf8))
            assertEquals("com.example.app", manifest.packageName)
            assertEquals("maps.config.en", manifest.splitName)
            assertEquals("maps", manifest.configForSplit)
            assertEquals(setOf("maps"), manifest.dependencies)
            assertEquals(0x2ffffffffL, manifest.versionCode)
            assertEquals(24, manifest.minSdk)
            assertTrue(manifest.splitRequired)
        }
    }

    @Test fun corruptChunkBoundsAreRejectedWithoutLooping() {
        val bytes = BinaryManifestFixture.create()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(12, 0)
        try { ApkManifest.read(bytes); fail("Zero-length chunk accepted") } catch (_: IOException) { }
    }
}
