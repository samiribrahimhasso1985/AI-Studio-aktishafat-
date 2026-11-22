package com.iktishafat.teleprompter

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iktishafat.teleprompter.data.db.AppDatabase
import com.iktishafat.teleprompter.data.repository.ScriptRepositoryImpl
import com.iktishafat.teleprompter.domain.model.Script
import com.iktishafat.teleprompter.domain.model.ScriptLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScriptDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ScriptRepositoryImpl
    private var clock = 1000L

    @Before fun setUp() {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repo = ScriptRepositoryImpl(db.scriptDao()) { clock++ }
    }

    @After fun tearDown() = db.close()

    private fun script(title: String, body: String, lang: ScriptLanguage) =
        Script(title = title, body = body, language = lang, createdAt = 0, updatedAt = 0)

    @Test fun createUpdateSearchDelete() = runTest {
        val ar = repo.save(script("بابل", "في قلب بلاد الرافدين قامت مدينة غيّرت التاريخ", ScriptLanguage.ARABIC))
        val de = repo.save(script("Uruk", "Die älteste Stadt der Welt", ScriptLanguage.GERMAN))
        assertEquals(2, repo.observeScripts().first().size)

        // Arabic text survives storage unchanged (diacritics included)
        assertEquals("في قلب بلاد الرافدين قامت مدينة غيّرت التاريخ", repo.getScript(ar)!!.body)
        assertEquals(ScriptLanguage.ARABIC, repo.getScript(ar)!!.language)

        // Search in Arabic and German
        assertEquals(listOf(ar), repo.observeScripts("الرافدين").first().map { it.id })
        assertEquals(listOf(de), repo.observeScripts("älteste").first().map { it.id })

        // Update keeps id, bumps updatedAt, newest first
        val before = repo.getScript(de)!!
        repo.save(before.copy(body = "Neu"))
        val after = repo.getScript(de)!!
        assertEquals("Neu", after.body)
        assertTrue(after.updatedAt > before.updatedAt)
        assertEquals(de, repo.observeScripts().first().first().id)

        // Pinned first
        repo.save(repo.getScript(ar)!!.copy(pinned = true))
        assertEquals(ar, repo.observeScripts().first().first().id)

        // Duplicate and last reading position
        val copy = repo.duplicate(ar, "(نسخة)")!!
        assertEquals("بابل (نسخة)", repo.getScript(copy)!!.title)
        repo.setLastReadOffset(ar, 42)
        assertEquals(42, repo.getScript(ar)!!.lastReadOffset)

        repo.delete(ar)
        assertNull(repo.getScript(ar))
        assertEquals(2, repo.observeScripts().first().size)
    }
}
