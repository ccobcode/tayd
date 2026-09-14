package com.cclilshy.tayc.ui

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cclilshy.tayc.R
import com.cclilshy.tayc.app.MainActivity
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationInteractionTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun hoverAndSelectionCoverTheLabelRow() {
        val label = compose.activity.getString(R.string.nav_services)
        val item = compose.onNode(
            hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected),
        )
        item.assertIsNotSelected()
        val before = item.captureToImage().toPixelMap()
        val x = before.width - 20
        val y = before.height * 3 / 4
        val background = before[x, y]

        item.performMouseInput { enter(center) }
        compose.waitForIdle()
        item.assertIsNotSelected()
        val hovered = item.captureToImage().toPixelMap()
        assertNotEquals("Hover must cover the text row, not only the icon", background, hovered[x, y])
        item.performMouseInput { exit() }

        item.performClick()
        item.assertIsSelected()
        val selected = item.captureToImage().toPixelMap()
        assertNotEquals("Selection must cover the text row", background, selected[x, y])
    }
}
