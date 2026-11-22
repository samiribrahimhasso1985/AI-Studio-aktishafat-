package com.iktishafat.teleprompter.domain.repository

import com.iktishafat.teleprompter.domain.model.PrompterSettings
import com.iktishafat.teleprompter.domain.model.Script
import kotlinx.coroutines.flow.Flow

interface ScriptRepository {
    fun observeScripts(query: String = ""): Flow<List<Script>>
    fun observeScript(id: Long): Flow<Script?>
    suspend fun getScript(id: Long): Script?
    /** Inserts when [Script.id] is 0, updates otherwise. Returns the id. */
    suspend fun save(script: Script): Long
    suspend fun delete(id: Long)
    suspend fun setLastReadOffset(id: Long, offset: Int)
    suspend fun duplicate(id: Long, copySuffix: String): Long?
}

interface SettingsRepository {
    val settings: Flow<PrompterSettings>
    suspend fun update(transform: (PrompterSettings) -> PrompterSettings)
}
