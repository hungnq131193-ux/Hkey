package com.hkey.app.settings.ui.sections

import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.5.2: changelog Giới thiệu dựng từ markdown — tiêu đề + gạch đầu dòng nối dòng. */
class ChangelogBlocksTest {
    @Test
    fun headersBulletsAndContinuations() {
        val md = "# Changelog\n\n## 1.5.2 (versionCode 28)\n\n- Một\n  hai\n- Ba\n"
        assertEquals(
            listOf(true to "1.5.2 (versionCode 28)", false to "Một hai", false to "Ba"),
            changelogBlocks(md)
        )
    }
}
