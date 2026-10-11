package dev.ujhhgtg.via.downloads

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class DownloadPackageArchiveTest {
    @Test fun contentClassificationDrivesPackageUiWithoutChangingTheTransferFlags() {
        val raw = DownloadRecord(name = "download.data", url = null, mimeType = "application/zip", flags = 7, state = DownloadState.COMPLETE)
        val bundle = DownloadPackageArchive.classified(raw, DownloadPackageArchive.Kind.BUNDLE)
        assertTrue(bundle.packageInspected)
        assertTrue(bundle.isAndroidPackage)
        assertTrue(bundle.isPackageBundle)
        assertEquals(7, bundle.flags and 7)
        assertEquals(3, DownloadPresentation.category(bundle))
        val apk = DownloadPackageArchive.classified(raw, DownloadPackageArchive.Kind.APK)
        assertTrue(apk.isAndroidPackage)
        assertFalse(apk.isPackageBundle)
        val disguised = DownloadPackageArchive.classified(raw.copy(name = "file.apk", mimeType = "application/vnd.android.package-archive"), DownloadPackageArchive.Kind.OTHER)
        assertFalse(disguised.isAndroidPackage)
        assertEquals(8, DownloadPresentation.category(disguised))
    }

    @Test fun detectsApkAndBundleFromContentsWithArbitraryNames() = runBlocking {
        val apk = zip(mapOf("AndroidManifest.xml" to BinaryManifestFixture.create()))
        withArchive(apk) { assertEquals(DownloadPackageArchive.Kind.APK, DownloadPackageArchive.kind(it)) }
        val archive = zip(mapOf("readme.txt" to "hello".toByteArray(), "nested/package.data" to apk))
        withArchive(archive) { assertEquals(DownloadPackageArchive.Kind.BUNDLE, DownloadPackageArchive.kind(it)) }
    }

    @Test fun ordinaryZipAndNestedNonApkZipAreNotInstallablePackages() = runBlocking {
        val document = zip(mapOf("document.xml" to "document".toByteArray()))
        withArchive(zip(mapOf("file.apk" to document))) { assertEquals(DownloadPackageArchive.Kind.OTHER, DownloadPackageArchive.kind(it)) }
    }

    @Test fun malformedNestedZipDoesNotHideALaterValidApk() = runBlocking {
        val apk = zip(mapOf("AndroidManifest.xml" to BinaryManifestFixture.create()))
        withArchive(zip(mapOf("broken.data" to byteArrayOf(0x50, 0x4b, 3, 4, 0), "valid.data" to apk))) {
            assertEquals(DownloadPackageArchive.Kind.BUNDLE, DownloadPackageArchive.kind(it))
        }
    }

    @Test fun readsSplitMetadataAndExtractsIntoOwnedPaths() = runBlocking {
        val apk = zip(mapOf("AndroidManifest.xml" to BinaryManifestFixture.create()))
        val split = zip(mapOf("AndroidManifest.xml" to BinaryManifestFixture.create(split = "config.arm64_v8a"), "lib/arm64-v8a/a.so" to byteArrayOf(1)))
        val obb = "game data".toByteArray()
        val archive = zip(mapOf("../../base.data" to apk, "folder/config.bin" to split,
            "Android/obb/com.example.app/main.7.com.example.app.obb" to obb))
        val directory = Files.createTempDirectory("via-package-test-").toFile()
        try {
            withArchive(archive) { file ->
                val prepared = DownloadPackageArchive.prepare(file, directory)
                assertEquals(2, prepared.parts.size)
                assertTrue(prepared.parts.all { it.file.canonicalFile.parentFile == directory.canonicalFile })
                assertEquals("config.arm64_v8a", prepared.parts[1].manifest.splitName)
                assertEquals(setOf("arm64-v8a"), prepared.parts[1].abis)
                assertArrayEquals(obb, prepared.obbFiles("com.example.app").single().readBytes())
                assertTrue(prepared.obbFiles("other.package").isEmpty())
                prepared.close()
                assertFalse(directory.exists())
            }
        } finally { directory.deleteRecursively() }
    }

    @Test fun archiveWithoutBaseIsRejectedAndItsCacheRemoved() = runBlocking {
        val split = zip(mapOf("AndroidManifest.xml" to BinaryManifestFixture.create(split = "config.en")))
        val directory = Files.createTempDirectory("via-package-test-").toFile()
        withArchive(zip(mapOf("split.data" to split))) {
            try { DownloadPackageArchive.prepare(it, directory); fail("Missing base accepted") }
            catch (_: IOException) { assertFalse(directory.exists()) }
        }
    }

    @Test fun obbPathsCannotEscapeThePackageFolder() {
        assertEquals("com.example.app/main.7.com.example.app.obb", DownloadPackageArchive.obbPath("main.7.com.example.app.obb"))
        assertEquals("com.example.app/patch.7.com.example.app.obb", DownloadPackageArchive.obbPath("Android/obb/com.example.app/patch.7.com.example.app.obb"))
        assertNull(DownloadPackageArchive.obbPath("../com.example.app/main.7.com.example.app.obb"))
        assertNull(DownloadPackageArchive.obbPath("Android/obb/other.app/main.7.com.example.app.obb"))
    }

    private fun zip(files: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip -> files.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
    }.toByteArray()

    private suspend fun withArchive(bytes: ByteArray, block: suspend (ZipFile) -> Unit) {
        val file = File.createTempFile("via-package-test-", ".data")
        try { file.writeBytes(bytes); ZipFile(file).use { block(it) } } finally { file.delete() }
    }
}

/** Small, independently encoded binary-XML fixtures, with both string-pool encodings and typed values. */
internal object BinaryManifestFixture {
    fun create(packageName: String = "com.example.app", split: String? = null, target: String? = null,
        dependency: String? = null, minSdk: Int = 29, version: Int = 7, versionMajor: Int = 0,
        splitRequired: Boolean = false, utf8: Boolean = true): ByteArray {
        val root = linkedMapOf<String, Any>("package" to packageName, "versionCode" to version, "versionCodeMajor" to versionMajor)
        split?.let { root["split"] = it }; target?.let { root["configForSplit"] = it }
        val tags = mutableListOf("manifest" to root, "uses-sdk" to linkedMapOf<String, Any>("minSdkVersion" to minSdk),
            "application" to linkedMapOf<String, Any>("isSplitRequired" to splitRequired))
        dependency?.let { tags += "uses-split" to linkedMapOf<String, Any>("name" to it) }
        val strings = tags.flatMap { (name, attrs) -> listOf(name) + attrs.keys + attrs.values.filterIsInstance<String>() }.distinct()
        val text = ByteArrayOutputStream()
        val offsets = strings.map { value ->
            val offset = text.size()
            fun length(value: Int) { if (value >= 128) text.write((value shr 8) or 0x80); text.write(value and 0xff) }
            if (utf8) { val bytes = value.toByteArray(); length(value.length); length(bytes.size); text.write(bytes); text.write(0) }
            else { text.write(value.length and 0xff); text.write(value.length shr 8); text.write(value.toByteArray(Charsets.UTF_16LE)); text.write(0); text.write(0) }
            offset
        }
        while (text.size() % 4 != 0) text.write(0)
        val pool = ByteBuffer.allocate(28 + strings.size * 4 + text.size()).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(1); putShort(28); putInt(capacity()); putInt(strings.size); putInt(0); putInt(if (utf8) 0x100 else 0)
            putInt(28 + strings.size * 4); putInt(0); offsets.forEach(::putInt); put(text.toByteArray())
        }.array()
        val chunks = tags.map { (name, attrs) -> ByteBuffer.allocate(36 + attrs.size * 20).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0x102); putShort(16); putInt(capacity()); putInt(1); putInt(-1)
            putInt(-1); putInt(strings.indexOf(name)); putShort(20); putShort(20); putShort(attrs.size.toShort()); putShort(0); putShort(0); putShort(0)
            attrs.forEach { (key, value) ->
                putInt(-1); putInt(strings.indexOf(key)); putInt(-1); putShort(8); put(0)
                when (value) {
                    is String -> { put(3); putInt(strings.indexOf(value)) }
                    is Boolean -> { put(0x12); putInt(if (value) 1 else 0) }
                    is Int -> { put(0x10); putInt(value) }
                }
            }
        }.array() }
        val size = 8 + pool.size + chunks.sumOf { it.size }
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(3); putShort(8); putInt(size); put(pool); chunks.forEach(::put)
        }.array()
    }
}
