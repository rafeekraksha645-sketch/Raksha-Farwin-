package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.test.core.app.ApplicationProvider
import com.example.model.GlobalItCatalog
import com.example.model.NexoraLanguage
import com.example.model.NexoraMode
import com.example.model.NexoraModelTier
import com.example.model.NexoraPreferencesData
import com.example.ui.screens.ExploreItScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.NexoraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import androidx.compose.ui.test.onNodeWithText

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app metadata and firestore database id are configured for NEXORA AI`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        val dbId = context.getString(R.string.firestore_database_id)
        assertEquals("NEXORA AI", appName)
        assertTrue(dbId.isNotBlank())
    }

    @Test
    fun `all 7 AI modes, 4 languages, and IT catalog categories are valid`() {
        assertEquals(7, NexoraMode.entries.size)
        assertEquals(4, NexoraLanguage.entries.size)
        assertEquals(7, GlobalItCatalog.categories.size)
        assertTrue(NexoraModelTier.FLASH.modelId == "gemini-3.8-flash")
        assertEquals(NexoraMode.GENERAL_IT, NexoraMode.fromName("UNKNOWN"))
        assertEquals(NexoraLanguage.AUTO, NexoraLanguage.fromName(null))
    }

    @Test
    fun `home screen displays NEXORA AI hero headline and primary actions`() {
        composeRule.setContent {
            NexoraTheme {
                HomeScreen(
                    preferences = NexoraPreferencesData(),
                    isAuthenticated = false,
                    onAskPrompt = { _, _ -> },
                    onNavigateExplore = {},
                    onNavigateFeatures = {},
                    onNavigateExhibition = {},
                    onNavigateAbout = {},
                    onNavigatePrivacy = {},
                    onNavigateTerms = {},
                    onNavigateContact = {},
                    onOpenAccount = {}
                )
            }
        }

        composeRule.onNodeWithText("Discover More. Understand Better. Think Beyond.").assertIsDisplayed()
        composeRule.onNodeWithTag("hero_prompt_input").assertIsDisplayed()
        composeRule.onNodeWithTag("start_exploring_button").assertIsDisplayed()
        composeRule.onNodeWithTag("explore_capabilities_button").assertIsDisplayed()
    }

    @Test
    fun `explore IT screen renders search and IT domains`() {
        composeRule.setContent {
            NexoraTheme {
                ExploreItScreen(
                    initialCategoryId = null,
                    onLaunchTopicPrompt = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Explore Global IT").assertIsDisplayed()
        composeRule.onNodeWithTag("explore_search_input").assertIsDisplayed()
        composeRule.onAllNodesWithText("Computer Science").onFirst().assertIsDisplayed()
        composeRule.onNodeWithTag("topic_card_computer_architecture").assertIsDisplayed()
    }
}
