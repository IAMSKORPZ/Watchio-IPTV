package com.iamskorpz.watchioiptv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iamskorpz.watchioiptv.ui.closeLiveFullscreen
import com.iamskorpz.watchioiptv.ui.LivePlaybackCloseGuard
import com.iamskorpz.watchioiptv.ui.LivePlaybackOrigin
import com.iamskorpz.watchioiptv.ui.LivePlaybackReturnBackGuard
import com.iamskorpz.watchioiptv.ui.livePlaybackOrigin
import com.iamskorpz.watchioiptv.ui.navigateHomeAsRoot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LivePlaybackNavigationComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sportsPlaySystemBackReturnsDirectlyToPreservedSportsState() {
        setNavigationContent(startDestination = "sports")

        composeRule.onNodeWithTag("sports-watch").performClick()
        composeRule.onNodeWithTag("sports-play").performClick()
        composeRule.onNodeWithTag("fullscreen-player").assertIsDisplayed()
        pressBack()

        composeRule.onNodeWithTag("sports-screen-date-2026-09-06").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("live-tv-screen").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun sportsPlayPlayerBackReturnsDirectlyToSports() {
        setNavigationContent(startDestination = "sports")

        composeRule.onNodeWithTag("sports-watch").performClick()
        composeRule.onNodeWithTag("sports-play").performClick()
        composeRule.onNodeWithTag("player-back").performClick()

        composeRule.onNodeWithTag("sports-screen-date-2026-09-06").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("live-tv-screen").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun livePlayerBackReturnsToLive() {
        setNavigationContent(startDestination = "live")

        composeRule.onNodeWithTag("live-fullscreen").performClick()
        composeRule.onNodeWithTag("player-back").performClick()

        composeRule.onNodeWithTag("live-tv-screen").assertIsDisplayed()
    }

    @Test
    fun livePlayerSystemBackReturnsToLive() {
        setNavigationContent(startDestination = "live")

        composeRule.onNodeWithTag("live-fullscreen").performClick()
        pressBack()

        composeRule.onNodeWithTag("live-tv-screen").assertIsDisplayed()
    }

    @Test
    fun repeatedSportsCloseIsConsumedOnceAndCannotPopPastSports() {
        setNavigationContent(startDestination = "sports")

        composeRule.onNodeWithTag("sports-watch").performClick()
        composeRule.onNodeWithTag("sports-play").performClick()
        composeRule.onNodeWithTag("player-double-back").performClick()

        composeRule.onNodeWithTag("sports-screen-date-2026-09-06").assertIsDisplayed()
    }

    @Test
    fun rapidBackAfterSportsPlayerCloseCannotPopRestoredSports() {
        setNavigationContent(startDestination = "sports")

        composeRule.onNodeWithTag("sports-watch").performClick()
        composeRule.onNodeWithTag("sports-play").performClick()
        composeRule.onNodeWithTag("player-back").performClick()
        pressBack()

        composeRule.onNodeWithTag("sports-screen-date-2026-09-06").assertIsDisplayed()
    }

    @Test
    fun sportsBackWorksAfterReturnProtectionExpires() {
        setNavigationContent(startDestination = "sports")

        composeRule.onNodeWithTag("sports-watch").performClick()
        composeRule.onNodeWithTag("sports-play").performClick()
        composeRule.onNodeWithTag("player-back").performClick()
        Thread.sleep(300L)
        pressBack()

        composeRule.onNodeWithTag("sports-screen-date-backed").assertIsDisplayed()
    }

    @Test
    fun restoredSportsPlayerWithoutOriginEntryFallsBackInsideWatchio() {
        setNavigationContent(startDestination = "live/fullscreen?origin=sports")

        composeRule.onNodeWithTag("player-back").performClick()

        composeRule.onNodeWithTag("sports-screen-date-2026-09-06").assertIsDisplayed()
    }

    @Test
    fun restoredTvGuidePlayerWithoutOriginEntryFallsBackToGuide() {
        setNavigationContent(startDestination = "live/fullscreen?origin=tv-guide")

        composeRule.onNodeWithTag("player-back").performClick()

        composeRule.onNodeWithTag("tv-guide-screen").assertIsDisplayed()
    }

    @Test
    fun completedProviderFlowMakesHomeTheBackStackRoot() {
        composeRule.setContent { HomeRootHarness() }

        composeRule.onNodeWithTag("open-provider-form").performClick()
        composeRule.onNodeWithTag("complete-provider-form").performClick()

        composeRule.onNodeWithTag("home-without-previous-entry").assertIsDisplayed()
    }

    @Composable
    private fun HomeRootHarness() {
        val navController = rememberNavController()
        val currentEntry by navController.currentBackStackEntryAsState()
        NavHost(navController = navController, startDestination = "providers") {
            composable("providers") {
                Button(
                    onClick = { navController.navigate("providers/add") },
                    modifier = Modifier.testTag("open-provider-form"),
                ) { Text("Add provider") }
            }
            composable("providers/add") {
                Button(
                    onClick = { navigateHomeAsRoot(navController) },
                    modifier = Modifier.testTag("complete-provider-form"),
                ) { Text("Connect") }
            }
            composable("home") {
                Text(
                    text = "Home",
                    modifier = Modifier.testTag(
                        if (currentEntry != null && navController.previousBackStackEntry == null) {
                            "home-without-previous-entry"
                        } else {
                            "home-with-previous-entry"
                        }
                    ),
                )
            }
        }
    }

    @Composable
    private fun NavigationHarness(startDestination: String) {
        val navController = rememberNavController()
        val returnBackGuard = remember { LivePlaybackReturnBackGuard() }
        var sportsDate by remember { mutableStateOf("2026-09-06") }
        NavHost(navController = navController, startDestination = startDestination) {
            composable("sports") {
                BackHandler {
                    if (!returnBackGuard.consumeIfProtected(LivePlaybackOrigin.Sports)) {
                        sportsDate = "backed"
                    }
                }
                Button(
                    onClick = { sportsDate = "2026-09-06" },
                    modifier = Modifier.testTag("sports-screen-date-$sportsDate"),
                ) { Text("Sports") }
                Button(
                    onClick = { navController.navigate("watch") },
                    modifier = Modifier.testTag("sports-watch"),
                ) { Text("WATCH") }
            }
            composable("watch") {
                Button(
                    onClick = {
                        navController.navigate("live")
                        navController.navigate("live/fullscreen?origin=sports")
                    },
                    modifier = Modifier.testTag("sports-play"),
                ) { Text("PLAY") }
            }
            composable("live") {
                Column(Modifier.testTag("live-tv-screen")) {
                    Button(
                        onClick = {
                            navController.navigate("live/fullscreen?origin=live")
                        },
                        modifier = Modifier.testTag("live-fullscreen"),
                    ) { Text("Live TV") }
                }
            }
            composable(
                route = "tv-guide?categoryId={categoryId}",
                arguments = listOf(navArgument("categoryId") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) {
                Text("TV Guide", modifier = Modifier.testTag("tv-guide-screen"))
            }
            composable(
                route = "live/fullscreen?origin={origin}",
                arguments = listOf(navArgument("origin") { type = NavType.StringType; defaultValue = "live" }),
            ) { backStackEntry ->
                val origin = livePlaybackOrigin(backStackEntry.arguments?.getString("origin"))
                val closeGuard = remember(backStackEntry.id) { LivePlaybackCloseGuard() }
                val close = {
                    closeGuard.close {
                        returnBackGuard.arm(origin)
                        closeLiveFullscreen(navController, origin)
                    }
                }
                BackHandler(onBack = close)
                Button(onClick = close, modifier = Modifier.testTag("fullscreen-player")) {
                    Text("Player")
                }
                Button(onClick = close, modifier = Modifier.testTag("player-back")) {
                    Text("Back")
                }
                Button(
                    onClick = {
                        close()
                        close()
                    },
                    modifier = Modifier.testTag("player-double-back"),
                ) { Text("Double Back") }
            }
        }
    }

    private fun setNavigationContent(startDestination: String) {
        composeRule.setContent { NavigationHarness(startDestination) }
    }
}
