package com.shadowing.app.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadRequestVersionsTest {

    @Test
    fun newVideoInvalidatesEarlierSubtitleLoad() {
        val versions = LoadRequestVersions()
        versions.startVideoSelection()
        val oldSubtitle = versions.startSubtitleLoad(isJapanese = false)

        versions.startVideoSelection()

        assertFalse(versions.isCurrent(oldSubtitle))
    }

    @Test
    fun latestSubtitleLoadWinsWithinSameVideo() {
        val versions = LoadRequestVersions()
        versions.startVideoSelection()
        val first = versions.startSubtitleLoad(isJapanese = true)
        val second = versions.startSubtitleLoad(isJapanese = true)

        assertFalse(versions.isCurrent(first))
        assertTrue(versions.isCurrent(second))
    }

    @Test
    fun otherLanguageDoesNotInvalidateCurrentLoad() {
        val versions = LoadRequestVersions()
        val videoVersion = versions.startVideoSelection()
        val english = versions.startSubtitleLoad(isJapanese = false)

        versions.startSubtitleLoad(isJapanese = true)

        assertTrue(versions.isCurrentVideo(videoVersion))
        assertTrue(versions.isCurrent(english))
    }

    @Test
    fun userSelectionInvalidatesPendingRestoreDecision() {
        val versions = LoadRequestVersions()
        val restoreSelectionVersion = versions.currentUserSelectionVersion()

        versions.markUserSelection()

        assertFalse(versions.isCurrentUserSelection(restoreSelectionVersion))
    }
}
