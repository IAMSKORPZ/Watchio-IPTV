package com.iamskorpz.watchioiptv

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iamskorpz.watchioiptv.uitest.StartupFixtureState
import com.iamskorpz.watchioiptv.uitest.StartupNotificationFixture
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UitestStartupFixtureControllerTest {
    @Test
    fun configure() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val targetContext = instrumentation.targetContext
        check(targetContext.packageName == "com.iamskorpz.watchioiptv.uitest") {
            "Fixture controller target package is ${targetContext.packageName}."
        }
        check(instrumentation.context.packageName == "com.iamskorpz.watchioiptv.uitest.test") {
            "Fixture controller test package is ${instrumentation.context.packageName}."
        }
        val fixture = StartupNotificationFixture(targetContext)
        when (arguments.getString("operation") ?: "reset") {
            "select", "select-fresh" -> {
                val state = StartupFixtureState.valueOf(requireNotNull(arguments.getString("state")))
                if (arguments.getString("operation") == "select-fresh") fixture.selectFresh(state) else fixture.select(state)
                val application = targetContext.applicationContext as WatchioNativeApplication
                runBlocking { application.container.announcementRepository.refresh().getOrThrow() }
                if (state == StartupFixtureState.MIXED_NOTIFICATIONS) {
                    runBlocking { application.container.announcementRepository.markRead(fixture.inboxAnnouncementIds().first()) }
                }
            }
            "next-announcement" -> fixture.nextAnnouncementId()
            "reset" -> fixture.reset()
            else -> error("Unsupported fixture operation")
        }
    }
}
