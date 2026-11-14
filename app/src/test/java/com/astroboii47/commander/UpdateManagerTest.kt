package com.astroboii47.commander

import org.junit.Assert.*
import org.junit.Test

class UpdateManagerTest {
    @Test fun parsesTags() {
        assertEquals(Pair("0.2.0-alpha.1", 10L), UpdateManager.parseTag("v0.2.0-alpha.1+10"))
        assertNull(UpdateManager.parseTag("v0.2.0-alpha.1"))
        assertNull(UpdateManager.parseTag("0.2.0-alpha.1+10"))
        assertNull(UpdateManager.parseTag("latest"))
    }

    private fun releasesFixture() = """
        [
          {"tag_name": "v0.2.0-alpha.1+10", "draft": false, "prerelease": true, "body": "First",
           "assets": [{"name": "Houston-0.2.0-alpha.1.apk", "browser_download_url": "https://example.com/a.apk"}]},
          {"tag_name": "v0.2.0-alpha.2+11", "draft": false, "prerelease": true, "body": "Second",
           "assets": [{"name": "notes.txt", "browser_download_url": "https://example.com/n.txt"}]},
          {"tag_name": "v0.2.0-alpha.3+12", "draft": true, "prerelease": true, "body": "Draft",
           "assets": [{"name": "Houston.apk", "browser_download_url": "https://example.com/d.apk"}]},
          {"tag_name": "weird", "draft": false, "prerelease": true, "body": "x", "assets": []}
        ]
    """.trimIndent()

    @Test fun picksNewestInstallablePrerelease() {
        val root = parseJson(releasesFixture())
        // alpha.2 has no APK asset and the draft is skipped: alpha.1 wins over installed 9.
        val pick = UpdateManager.pickUpdate(root, 9L)
        assertNotNull(pick)
        assertEquals("0.2.0-alpha.1", pick!!.versionName)
        assertEquals(10L, pick.versionCode)
        assertEquals("https://example.com/a.apk", pick.apkUrl)
        assertEquals("First", pick.notes)
    }

    @Test fun returnsNullWhenUpToDate() {
        val root = parseJson(releasesFixture())
        assertNull(UpdateManager.pickUpdate(root, 10L))
        assertNull(UpdateManager.pickUpdate(root, 99L))
    }

    @Test fun ignoresMalformedPayloads() {
        assertNull(UpdateManager.pickUpdate(parseJson("{}"), 1L))
        assertNull(UpdateManager.pickUpdate(parseJson("[]"), 1L))
    }
}
