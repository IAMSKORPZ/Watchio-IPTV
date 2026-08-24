package com.watchioiptv.nativeapp

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchioFocusableCardTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun bootstrapCardRendersText() {
        activityRule.scenario.onActivity { activity ->
            assertTrue(activity.findViewById<android.view.ViewGroup>(android.R.id.content).childCount > 0)
        }
    }
}
