package app.cineroll.ui

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
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

    private fun waitForTag(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForText(text: String, timeoutMillis: Long = 15_000) {
        compose.waitUntil(timeoutMillis) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Leaves one developed Exposure in DCIM/Cineroll on the test device. */
    @Test
    fun pickAFilmShootRewindAndLoadAgain() {
        // A fresh install shows the picker.
        waitForTag("film-Mono 400")
        compose.onNodeWithTag("film-Mono 400").performClick()

        // The viewfinder shows the Film and a full Roll.
        waitForText("36/36")
        compose.onNodeWithTag("film-name").assertTextEquals("Mono 400")
        compose.onNodeWithTag("counter").assertTextEquals("36/36")

        // One Exposure.
        compose.onNodeWithTag("shutter").performClick()
        waitForText("35/36", timeoutMillis = 30_000)
        compose.onNodeWithTag("counter").assertTextEquals("35/36")

        // Rewind, confirm. The button is disabled until the Exposure has been developed.
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag("rewind") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("rewind").performClick()
        compose.onNodeWithTag("rewind-confirm").performClick()
        waitForText("This Roll is finished.")

        // Load new Roll goes back to the picker.
        compose.onNodeWithTag("load-new-roll").performClick()
        waitForTag("film-Mono 400")
    }
}
