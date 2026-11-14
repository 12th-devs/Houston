package com.astroboii47.commander

import org.junit.Assert.*
import org.junit.Test

class NativeRankingTest {
    @Test fun musicControlsShareOneIdentityPerKind() {
        val toggle = NativeResult("Play music", "Music", NativeKind.MusicToggle)
        val next = NativeResult("Next track", "Music", NativeKind.MusicNext)
        assertEquals("MusicToggle", toggle.rankIdentity())
        assertEquals("MusicNext", next.rankIdentity())
        assertEquals("MusicToggle:MusicToggle", nativeResultId(toggle))
    }

    @Test fun titleFallsBackWhenNoPayload() {
        val web = NativeResult("Search the web for “cats”", kind = NativeKind.Web)
        assertEquals("Search the web for “cats”", web.rankIdentity())
        val clip = NativeResult("hello", "Clipboard · tap to copy", NativeKind.Clipboard, clipboard = "hello")
        assertEquals("hello", clip.rankIdentity())
    }

    @Test fun sourceMappingMatchesSurface() {
        assertEquals(UnifiedSource.APP, NativeResult("Spotify", "App", NativeKind.App).toRankCandidate().source)
        assertEquals(UnifiedSource.FILE, NativeResult("a.pdf", "dl", NativeKind.File).toRankCandidate().source)
        assertEquals(UnifiedSource.SETTING, NativeResult("Wi-Fi", "net", NativeKind.Setting).toRankCandidate().source)
        assertEquals(UnifiedSource.COMMAND, NativeResult("1+1", "Calculation", NativeKind.Calculator).toRankCandidate().source)
        assertEquals(UnifiedSource.COMMAND, NativeResult("Run command: ?foo", kind = NativeKind.Command).toRankCandidate().source)
        assertEquals(UnifiedSource.HISTORY, NativeResult("x", "Clipboard · tap to copy", NativeKind.Clipboard, clipboard = "x").toRankCandidate().source)
        assertEquals(UnifiedSource.HISTORY, NativeResult("y", "Search again", NativeKind.SearchHistory).toRankCandidate().source)
        assertEquals(UnifiedSource.WEB, NativeResult("Search the web for “x”", kind = NativeKind.Web).toRankCandidate().source)
        assertEquals(UnifiedSource.CONTROL, NativeResult("Buds", "Paired", NativeKind.BluetoothDevice).toRankCandidate().source)
        assertEquals(UnifiedSource.ALIAS, NativeResult("Screenshots", "Browse", NativeKind.ScreenshotMenu).toRankCandidate().source)
    }

    @Test fun candidateIdIsStable() {
        val first = NativeResult("Spotify", "App", NativeKind.App).toRankCandidate()
        val second = NativeResult("Spotify", "App", NativeKind.App).toRankCandidate()
        assertEquals(first.id, second.id)
        assertTrue(first.id.startsWith("App:"))
    }

    @Test fun dedupeKeepsFirstOrder() {
        val a = NativeResult("Spotify", "App", NativeKind.App)
        val dup = NativeResult("Spotify", "App", NativeKind.App)
        val b = NativeResult("Search the web for “spotify”", kind = NativeKind.Web)
        val deduped = dedupeNativeResults(listOf(a, dup, b))
        assertEquals(2, deduped.size)
        assertSame(a, deduped[0])
        assertEquals(b, deduped[1])
    }

    @Test fun musicResultsAlwaysFour() {
        val items = musicResults(null)
        assertEquals(4, items.size)
        assertEquals(
            listOf(NativeKind.MusicPrevious, NativeKind.MusicToggle, NativeKind.MusicNext, NativeKind.MusicOpen),
            items.map { it.kind },
        )
        assertEquals("Play music", items[1].title)
    }

    @Test fun musicQueryPredicates() {
        assertTrue(matchesMusicQuery(NativeKind.MusicNext, "Next track", "next"))
        assertTrue(matchesMusicQuery(NativeKind.MusicPrevious, "Previous track", "previous"))
        assertTrue(matchesMusicQuery(NativeKind.MusicToggle, "Play music", "pause music"))
        assertTrue(matchesMusicQuery(NativeKind.MusicOpen, "Open player", "player"))
        assertFalse(matchesMusicQuery(NativeKind.MusicNext, "Next track", "bluetooth"))
        assertFalse(matchesMusicQuery(NativeKind.Web, "Search the web", "play"))
    }

    @Test fun filterMusicByQueryKeepsOnlyMatches() {
        val items = musicResults(null)
        val filtered = filterMusicByQuery(items, "next")
        assertEquals(1, filtered.size)
        assertEquals(NativeKind.MusicNext, filtered.first().kind)
        assertTrue(filterMusicByQuery(items, "no-such-control").isEmpty())
    }
}
