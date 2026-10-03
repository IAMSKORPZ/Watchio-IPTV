package com.iamskorpz.watchioiptv

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iamskorpz.watchioiptv.data.announcements.DataStoreAnnouncementLocalStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnouncementArchiveDataStoreTest {
    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var store: DataStoreAnnouncementLocalStore

    @Before
    fun createStore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val directory = context.getDir("watchio_announcement_store_tests", Context.MODE_PRIVATE)
        file = File(directory, "announcement_${UUID.randomUUID()}.preferences_pb")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = DataStoreAnnouncementLocalStore(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    @After
    fun cleanup() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun archiveAndRestorePersistWithoutChangingReadOrDismissedIds() = runBlocking {
        store.markSeen("read")
        store.dismiss("dismissed")
        store.archive("unread")

        assertEquals(setOf("read"), store.seenIds.first())
        assertEquals(setOf("dismissed"), store.dismissedIds.first())
        assertEquals(setOf("unread"), store.archivedIds.first())

        store.restore("unread")

        assertEquals(emptySet<String>(), store.archivedIds.first())
        assertEquals(setOf("read"), store.seenIds.first())
        assertEquals(setOf("dismissed"), store.dismissedIds.first())
    }
}
