package org.zamcan.zamrescue

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun overviewExplainsCurrentFeatureLimitations() {
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.overview_title)).assertExists()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.not_available_title)).assertExists()
    }

    @Test
    fun deviceTabShowsValuesReadFromRuntimeBuildProperties() {
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.tab_device)).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.device_title)).assertExists()
        composeRule.onNodeWithText(android.os.Build.MODEL.ifBlank { "Unknown" }).assertExists()
        composeRule.onNodeWithText(android.os.Build.VERSION.SDK_INT.toString()).assertExists()
    }

    @Test
    fun securityTabDisclosesThatProtocolHasNoAndroidTransportIntegration() {
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.tab_security)).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.protocol_status_title)).assertExists()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.no_bypass_title)).assertExists()
    }
}
