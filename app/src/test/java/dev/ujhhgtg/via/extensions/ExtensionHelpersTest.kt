package dev.ujhhgtg.via.extensions

import dev.ujhhgtg.via.ui.BrowserMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ExtensionHelpersTest {
    @Test fun packagesAreRecognizedByMimeOrXpiPathOnly() {
        assertTrue(ExtensionFiles.isPackage("https://addons.mozilla.org/x/latest.xpi", "application/x-xpinstall", null))
        assertTrue(ExtensionFiles.isPackage("https://example.com/download?id=1", "application/x-xpinstall", null))
        assertTrue(ExtensionFiles.isPackage("https://example.com/addon.xpi?v=2", "application/octet-stream", null))
        assertTrue(ExtensionFiles.isPackage("https://example.com/get", null, "attachment; filename=\"tool.xpi\""))
        assertFalse(ExtensionFiles.isPackage("https://example.com/archive.zip", "application/zip", null))
        assertFalse(ExtensionFiles.isPackage("https://example.com/page.xpi", "text/html", null))
        assertFalse(ExtensionFiles.isPackage("file:///sdcard/addon.xpi", null, null))
    }

    @Test fun matchPatternsBecomeSitesOrAllSites() {
        assertNull(ExtensionPermissions.host("<all_urls>"))
        assertNull(ExtensionPermissions.host("*://*/*"))
        assertNull(ExtensionPermissions.host("https://*/*"))
        assertEquals("example.com", ExtensionPermissions.host("*://*.example.com/*"))
        assertEquals("mail.example.org", ExtensionPermissions.host("https://mail.example.org:8443/inbox/*"))
    }

    @Test fun updatesFollowTheIntervalAndNeverMeansOff() {
        val day = 86_400_000L
        val now = 10 * day
        assertTrue(ExtensionUpdater.isDue(day, 0, now))
        assertFalse(ExtensionUpdater.isDue(day, now - day / 2, now))
        assertTrue(ExtensionUpdater.isDue(day, now - day - 1, now))
        assertFalse(ExtensionUpdater.isDue(0, 0, now))
    }

    @Test fun menuEntryExistsOnEveryEngine() {
        assertTrue(BrowserMenu.EXTENSIONS in BrowserMenu.entries)
        assertTrue(BrowserMenu.EXTENSIONS in BrowserMenu.defaults)
    }

    /** Every extension string, including Firefox's permission wording, ships in English and both Chinese locales. */
    @Test fun extensionStringsExistInAllThreeLocales() {
        fun keys(folder: String): Set<String> {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$folder/strings.xml"))
            val nodes = document.getElementsByTagName("string")
            return (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue }.toSet()
        }
        val english = keys("values").filter { it.startsWith("extension") || it.contains("_extension") || it == "uninstall" || it == "uninstall_item_message" }
        assertTrue(english.size > 60)
        listOf("values-zh-rCN", "values-zh-rTW").forEach { folder ->
            val missing = english - keys(folder)
            assertTrue("$folder is missing $missing", missing.isEmpty())
        }
        val referenced = (ExtensionPermissions.permissions.values + ExtensionPermissions.dataCollection.values).toSet()
        assertEquals(ExtensionPermissions.permissions.size + ExtensionPermissions.dataCollection.size, referenced.size)
    }
}
