package com.iktishafat.teleprompter.domain

import com.iktishafat.teleprompter.domain.engine.PrompterState
import com.iktishafat.teleprompter.domain.engine.ScrollEngine
import com.iktishafat.teleprompter.domain.model.ScriptLanguage
import com.iktishafat.teleprompter.domain.remote.RemoteCommand
import com.iktishafat.teleprompter.domain.remote.RemoteKeyMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollEngineTest {

    @Test
    fun countdownThenScrollsAtConfiguredSpeed() {
        val e = ScrollEngine(speedPxPerSecond = 100f, countdownSeconds = 3)
        e.setContentLength(10_000f)
        e.start()
        assertEquals(PrompterState.COUNTDOWN, e.state)
        assertEquals(3, e.countdownDisplay)
        e.tick(1000); assertEquals(2, e.countdownDisplay)
        e.tick(2000)
        assertEquals(PrompterState.PLAYING, e.state)
        assertEquals(0f, e.position, 0.001f)
        repeat(60) { e.tick(1000f / 60f) } // ~1 s of frames
        assertEquals(100f, e.position, 2f)
    }

    @Test
    fun pauseResumeRestart() {
        val e = ScrollEngine(200f, countdownSeconds = 0)
        e.setContentLength(5000f)
        e.start(); e.tick(500)
        assertEquals(100f, e.position, 0.01f)
        e.togglePlayPause()
        assertEquals(PrompterState.PAUSED, e.state)
        e.tick(1000)
        assertEquals(100f, e.position, 0.01f)
        e.togglePlayPause(); e.tick(500)
        assertEquals(200f, e.position, 0.01f)
        e.restart()
        assertEquals(0f, e.position, 0.01f)
        assertEquals(PrompterState.PLAYING, e.state)
    }

    @Test
    fun stopsAtEndAndClampsSpeed() {
        val e = ScrollEngine(1000f, 0, minSpeed = 10f, maxSpeed = 500f)
        assertEquals(500f, e.speedPxPerSecond, 0.01f)
        e.setContentLength(300f)
        e.start(); e.tick(5000)
        assertEquals(300f, e.position, 0.01f)
        assertEquals(PrompterState.FINISHED, e.state)
        e.changeSpeed(0.001f)
        assertEquals(10f, e.speedPxPerSecond, 0.01f)
        e.start() // starting after finish rewinds
        assertEquals(0f, e.position, 0.01f)
    }

    @Test
    fun nudgeAndSeekAreClamped() {
        val e = ScrollEngine(100f, 0)
        e.setContentLength(1000f)
        e.nudge(-50f); assertEquals(0f, e.position, 0.01f)
        e.seekTo(5000f); assertEquals(1000f, e.position, 0.01f)
        e.nudge(-200f); assertEquals(800f, e.position, 0.01f)
    }

    @Test
    fun followTargetEasesTowardTargetOnlyWhilePlaying() {
        val e = ScrollEngine(100f, 0)
        e.setContentLength(10_000f)
        assertFalse(e.followTarget(1000f, 16f))
        e.start()
        repeat(200) { e.followTarget(1000f, 16f) } // ~3 s
        assertEquals(1000f, e.position, 1f)
        e.pause()
        assertFalse(e.followTarget(2000f, 16f))
    }
}

class RemoteKeyMapperTest {
    private val m = RemoteKeyMapper()

    @Test
    fun mapsKeyboardsClickersAndMediaRemotes() {
        assertEquals(RemoteCommand.PLAY_PAUSE, m.map(RemoteKeyMapper.Keys.SPACE))
        assertEquals(RemoteCommand.PLAY_PAUSE, m.map(RemoteKeyMapper.Keys.MEDIA_PLAY_PAUSE))
        assertEquals(RemoteCommand.FORWARD, m.map(RemoteKeyMapper.Keys.PAGE_DOWN))
        assertEquals(RemoteCommand.BACKWARD, m.map(RemoteKeyMapper.Keys.PAGE_UP))
        assertEquals(RemoteCommand.SPEED_UP, m.map(RemoteKeyMapper.Keys.DPAD_RIGHT))
        assertEquals(RemoteCommand.SPEED_DOWN, m.map(RemoteKeyMapper.Keys.MINUS))
        assertEquals(RemoteCommand.RESTART, m.map(RemoteKeyMapper.Keys.R))
        assertNull(m.map(9999))
    }

    @Test
    fun volumeKeysCanBeDisabled() {
        assertEquals(RemoteCommand.PLAY_PAUSE, m.map(RemoteKeyMapper.Keys.VOLUME_UP))
        val off = RemoteKeyMapper(volumeKeysEnabled = false)
        assertNull(off.map(RemoteKeyMapper.Keys.VOLUME_UP))
        assertNull(off.map(RemoteKeyMapper.Keys.VOLUME_DOWN))
    }
}

class LanguageDetectionTest {
    @Test
    fun detectsArabicGermanEnglish() {
        assertEquals(ScriptLanguage.ARABIC, ScriptLanguage.detect("في قلب بلاد الرافدين قامت مدينة غيّرت التاريخ"))
        assertEquals(ScriptLanguage.GERMAN, ScriptLanguage.detect("Das ist eine Geschichte über die Stadt und ihre Menschen."))
        assertEquals(ScriptLanguage.ENGLISH, ScriptLanguage.detect("This is the story of the city and its people."))
        assertTrue(ScriptLanguage.ARABIC.isRtl)
    }
}
