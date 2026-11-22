package com.iktishafat.teleprompter

import android.app.Application
import android.content.Context
import com.iktishafat.teleprompter.data.ai.AssistantRouter
import com.iktishafat.teleprompter.data.db.AppDatabase
import com.iktishafat.teleprompter.data.importer.DocumentImporter
import com.iktishafat.teleprompter.data.repository.ScriptRepositoryImpl
import com.iktishafat.teleprompter.data.repository.SettingsRepositoryImpl
import com.iktishafat.teleprompter.domain.repository.ScriptRepository
import com.iktishafat.teleprompter.domain.repository.SettingsRepository
import com.iktishafat.teleprompter.platform.remote.RemoteControlBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container: small app, no DI framework needed. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val scripts: ScriptRepository by lazy { ScriptRepositoryImpl(database.scriptDao()) }
    val settings: SettingsRepository by lazy { SettingsRepositoryImpl(appContext) }
    val importer: DocumentImporter by lazy { DocumentImporter(appContext) }
    val assistants: AssistantRouter by lazy { AssistantRouter() }
    val remote: RemoteControlBus by lazy { RemoteControlBus() }
    /** Outlives screens; used to finish autosaves when an editor closes. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

class TeleprompterApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as TeleprompterApp).container
