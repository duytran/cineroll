package app.cineroll.ui

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class ShootSmokeTest {

    @get:Rule(order = 0)
    val freshRoll = object : ExternalResource() {
        override fun before() {
            InstrumentationRegistry.getInstrumentation().targetContext
                .getSharedPreferences("cineroll_roll", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @get:Rule(order = 1)
    val camera: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    /** Leaves one developed Exposure in DCIM/Cineroll on the test device. */
    @Test
    fun shootingOneExposureMovesTheCounterFrom36To35() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("film-Everyday 400").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("film-Everyday 400").performClick()

        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("36/36").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("counter").assertTextEquals("36/36")

        compose.onNodeWithTag("shutter").performClick()

        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("35/36").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("counter").assertTextEquals("35/36")
    }
}
