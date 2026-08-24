package com.watchioiptv.nativeapp

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.watchioiptv.nativeapp.core.database.ProviderEntity
import com.watchioiptv.nativeapp.core.database.WatchioDatabase
import com.watchioiptv.nativeapp.core.database.WatchioMigrations
import com.watchioiptv.nativeapp.core.security.ProviderCredentialStore
import com.watchioiptv.nativeapp.core.security.SecretStore
import com.watchioiptv.nativeapp.core.util.WatchioClock
import com.watchioiptv.nativeapp.data.epg.EpgRepository
import com.watchioiptv.nativeapp.domain.model.ProviderType
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpgRepositoryInstrumentedTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        WatchioDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private lateinit var database: WatchioDatabase
    private lateinit var server: MockWebServer
    private lateinit var repository: EpgRepository
    private lateinit var secrets: InMemorySecretStore

    @Before
    fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            WatchioDatabase::class.java,
        ).build()
        server = MockWebServer()
        server.start()
        secrets = InMemorySecretStore()
        repository = EpgRepository(
            database = database,
            okHttpClient = com.watchioiptv.nativeapp.core.network.NetworkModule().okHttpClient,
            credentialStore = ProviderCredentialStore(secrets),
            clock = object : WatchioClock { override fun nowEpochMs(): Long = 1_767_225_600_000L },
        )
        database.providerDao().upsert(ProviderEntity("p1", "Provider", ProviderType.M3uUrl.persisted, server.url("/guide.xml").toString(), 1, 1, null, true))
        database.providerDao().upsert(ProviderEntity("xt", "Xtream", ProviderType.Xtream.persisted, server.url("/").toString().trimEnd('/'), 1, 1, null, true))
        ProviderCredentialStore(secrets).saveXtreamCredentials("xt", com.watchioiptv.nativeapp.core.security.XtreamCredentials("fake-user", "fake-pass"))
    }

    @After
    fun tearDown() {
        server.shutdown()
        database.close()
    }

    @Test
    fun importsPlainGzipAndBrotliXmltvAndQueriesCurrentNext() = runBlocking {
        val url = server.url("/guide.xml").toString()
        repository.upsertCustomSource("p1", url)
        server.enqueue(xmlResponse(xml()))
        val first = repository.refresh("p1")
        assertEquals(2, first.channelCount)
        assertEquals(2, first.programmeCount)
        val nowNext = repository.currentNext("p1", "bbc.one", parserTime("20260101003000 +0000"))
        assertEquals("Morning & News", nowNext.current?.title)
        assertEquals(0.5f, nowNext.progress, 0.01f)
        assertEquals("Next", nowNext.next?.title)

        server.enqueue(xmlResponse(xml(), gzip = true))
        val gzip = repository.refresh("p1")
        assertEquals(2, gzip.programmeCount)

        server.enqueue(MockResponse().setHeader("Content-Encoding", "br").setBody(okio.Buffer().write(Base64.getDecoder().decode(BROTLI_XML_BASE64))))
        val brotli = repository.refresh("p1")
        assertEquals(1, brotli.channelCount)
        assertEquals(1, brotli.programmeCount)
    }

    @Test
    fun failedRefreshAnd304PreserveExistingGuide() = runBlocking {
        val url = server.url("/guide.xml").toString()
        repository.upsertCustomSource("p1", url)
        server.enqueue(xmlResponse(xml(), etag = "v1", lastModified = "Mon, 01 Jan 2026 00:00:00 GMT"))
        repository.refresh("p1")
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(runCatching { repository.refresh("p1") }.isFailure)
        assertEquals(2, database.epgDao().programmeCount("p1"))
        server.enqueue(MockResponse().setResponseCode(304))
        val notModified = repository.refresh("p1")
        assertEquals(2, notModified.programmeCount)
    }

    @Test
    fun repeatedRefreshReplacesGuideWithoutDuplicates() = runBlocking {
        repository.upsertCustomSource("p1", server.url("/guide.xml").toString())
        server.enqueue(xmlResponse(xml()))
        repository.refresh("p1")
        assertEquals(2, database.epgDao().programmeCount("p1"))

        server.enqueue(xmlResponse(xml()))
        val second = repository.refresh("p1")

        assertEquals(2, second.programmeCount)
        assertEquals(2, database.epgDao().programmeCount("p1"))
        assertEquals(2, database.epgDao().channelCount("p1"))
    }

    @Test
    fun xtreamSourceBuildsEphemeralXmltvUrlAndProviderIsolationWorks() = runBlocking {
        server.enqueue(xmlResponse(xml()))
        val result = repository.refresh("xt")
        assertEquals(2, result.programmeCount)
        val request = server.takeRequest()
        assertEquals("/xmltv.php?username=fake-user&password=fake-pass", request.path)
        assertEquals(0, database.epgDao().programmeCount("p1"))
    }

    @Test
    fun xtreamExplicitSourceFallsBackToStandardXmltv() = runBlocking {
        repository.upsertCustomSource("xt", server.url("/explicit-guide.xml").toString())
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(xmlResponse(xml()))

        val result = repository.refresh("xt")

        assertEquals(2, result.programmeCount)
        assertEquals("/explicit-guide.xml", server.takeRequest().path)
        assertEquals("/xmltv.php?username=fake-user&password=fake-pass", server.takeRequest().path)
    }

    @Test
    fun largeGuideImportsAndPrunes() = runBlocking {
        val url = server.url("/large.xml").toString()
        repository.upsertCustomSource("p1", url)
        server.enqueue(xmlResponse(largeXml(channelCount = 2_000, programmes = 10_000)))
        val result = repository.refresh("p1")
        assertEquals(2_000, result.channelCount)
        assertEquals(10_000, database.epgDao().programmeCount("p1"))
    }

    @Test
    fun migrationThreeToFourCreatesEpgSourceTables() {
        val db = migrationHelper.createDatabase("phase5-migration", 3)
        db.close()
        migrationHelper.runMigrationsAndValidate("phase5-migration", 4, true, WatchioMigrations.MIGRATION_3_4)
    }

    private fun xml(): String =
        """
        <tv>
          <channel id="bbc.one"><display-name>BBC One</display-name><icon src="http://example.invalid/bbc.png"/></channel>
          <channel id="bbc.two"><display-name>BBC Two</display-name></channel>
          <programme channel="bbc.one" start="20260101000000 +0000" stop="20260101010000 +0000"><title>Morning &amp; News</title><desc>Desc &lt;x&gt;</desc></programme>
          <programme channel="bbc.one" start="20260101010000 +0000" stop="20260101020000 +0000"><title>Next</title></programme>
          <programme channel="bbc.one" start="bad" stop="20260101030000 +0000"><title>Bad</title></programme>
        </tv>
        """.trimIndent()

    private fun largeXml(channelCount: Int, programmes: Int): String = buildString {
        appendLine("<tv>")
        repeat(channelCount) { appendLine("""<channel id="c$it"><display-name>Channel $it</display-name></channel>""") }
        repeat(programmes) {
            val channel = "c${it % channelCount}"
            appendLine("""<programme channel="$channel" start="20260101000000 +0000" stop="20260101010000 +0000"><title>Program $it</title></programme>""")
        }
        appendLine("</tv>")
    }

    private fun xmlResponse(body: String, gzip: Boolean = false, etag: String? = null, lastModified: String? = null): MockResponse {
        val response = MockResponse().setHeader("Content-Type", "application/xml")
        etag?.let { response.setHeader("ETag", it) }
        lastModified?.let { response.setHeader("Last-Modified", it) }
        return if (gzip) {
            response.setHeader("Content-Encoding", "gzip").setBody(okio.Buffer().write(gzip(body)))
        } else {
            response.setBody(body)
        }
    }

    private fun gzip(value: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(value.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    private fun parserTime(value: String): Long = com.watchioiptv.nativeapp.data.epg.XmlTvParser().parseXmlTvTime(value)!!

    private class InMemorySecretStore : SecretStore {
        private val values = mutableMapOf<String, String>()
        override suspend fun putSecret(key: String, value: String) { values[key] = value }
        override suspend fun getSecret(key: String): String? = values[key]
        override suspend fun removeSecret(key: String) { values.remove(key) }
    }

    companion object {
        const val BROTLI_XML_BASE64 = "GwgBoBwHdky8xVzgTbjiCYL/dk6rrNK/m+kNpt2ghEFSJk+WuwgzTPluD/32bRU8UNL2AlmYaDavT49Pz4pyYsZsJtwHex5jLY8oprlqY+n2X71KoWI/wZPnIc6xkGzK7IiCvDhmepHQK38pEQO3mzMph2QG3bsPlW60x4J+tfsOSFtl72qbt/wOTZQ5xVCxHg8="
    }
}
