package com.iamskorpz.watchioiptv

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iamskorpz.watchioiptv.uitest.StartupFixtureState
import com.iamskorpz.watchioiptv.uitest.StartupNotificationFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UitestStartupFixturePersistenceTest {
    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun durableWritesBelongToTargetAndSurviveFixtureRecreation() {
        assertEquals("com.iamskorpz.watchioiptv.uitest", targetContext.packageName)
        val fixture = StartupNotificationFixture(targetContext)
        fixture.reset()
        fixture.select(StartupFixtureState.OPTIONAL_UPDATE)

        assertEquals(
            StartupFixtureState.OPTIONAL_UPDATE,
            StartupNotificationFixture(targetContext).state(),
        )
        assertTrue(
            targetContext.getSharedPreferences(StartupNotificationFixture.PREFERENCES, Context.MODE_PRIVATE)
                .contains(StartupNotificationFixture.KEY_STATE),
        )
    }

    @Test
    fun nextAnnouncementAndResetAreDurableAndFixtureScoped() {
        val unrelated = targetContext.getSharedPreferences("uitest_unrelated_sentinel", Context.MODE_PRIVATE)
        assertTrue(unrelated.edit().putBoolean("preserved", true).commit())
        val fixture = StartupNotificationFixture(targetContext)
        fixture.reset()
        val firstId = fixture.announcementId()
        val revision = fixture.nextAnnouncementId()
        val fixturePreferences = targetContext.getSharedPreferences(
            StartupNotificationFixture.PREFERENCES,
            Context.MODE_PRIVATE,
        )
        assertEquals(revision, fixturePreferences.getInt(StartupNotificationFixture.KEY_ANNOUNCEMENT_REVISION, -1))
        assertTrue(firstId != StartupNotificationFixture(targetContext).announcementId())

        fixture.select(StartupFixtureState.ANNOUNCEMENT)
        fixture.reset()
        assertEquals(StartupFixtureState.NONE, StartupNotificationFixture(targetContext).state())
        assertFalse(fixturePreferences.contains(StartupNotificationFixture.KEY_STATE))
        assertTrue(fixturePreferences.contains(StartupNotificationFixture.KEY_ANNOUNCEMENT_GENERATION))
        assertTrue(unrelated.getBoolean("preserved", false))
    }
}
