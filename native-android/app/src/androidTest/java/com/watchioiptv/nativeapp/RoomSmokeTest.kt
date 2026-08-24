package com.watchioiptv.nativeapp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.watchioiptv.nativeapp.core.database.AppMetadataEntity
import com.watchioiptv.nativeapp.core.database.WatchioDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomSmokeTest {
    private lateinit var database: WatchioDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WatchioDatabase::class.java).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun metadataDaoWritesAndReads() = runBlocking {
        database.appMetadataDao().upsert(AppMetadataEntity("phase", "one"))

        assertEquals("one", database.appMetadataDao().valueForKey("phase"))
    }
}
