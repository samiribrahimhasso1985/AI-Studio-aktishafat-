package com.iktishafat.teleprompter.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.iktishafat.teleprompter.AppContainer
import com.iktishafat.teleprompter.R
import com.iktishafat.teleprompter.domain.model.Script
import com.iktishafat.teleprompter.domain.model.ScriptLanguage
import com.iktishafat.teleprompter.ui.editor.EditorScreen
import com.iktishafat.teleprompter.ui.library.LibraryScreen
import com.iktishafat.teleprompter.ui.prompter.PrompterScreen
import com.iktishafat.teleprompter.ui.settings.SettingsScreen
import com.iktishafat.teleprompter.ui.studio.StudioScreen
import kotlinx.coroutines.flow.StateFlow

object Routes {
    const val LIBRARY = "library"
    const val EDITOR = "editor/{id}"
    const val PROMPTER = "prompter/{id}"
    const val STUDIO = "studio/{id}"
    const val SETTINGS = "settings"
    fun editor(id: Long) = "editor/$id"
    fun prompter(id: Long) = "prompter/$id"
    fun studio(id: Long) = "studio/$id"
}

@Composable
fun AppNavHost(
    container: AppContainer,
    pendingImport: StateFlow<Uri?>,
    onImportConsumed: () -> Unit,
) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val importUri by pendingImport.collectAsState()

    LaunchedEffect(importUri) {
        val uri = importUri ?: return@LaunchedEffect
        onImportConsumed()
        runCatching {
            val doc = container.importer.import(uri)
            val now = System.currentTimeMillis()
            container.scripts.save(
                Script(title = doc.title, body = doc.text, language = ScriptLanguage.detect(doc.text), createdAt = now, updatedAt = now)
            )
        }.onSuccess { id -> nav.navigate(Routes.editor(id)) }
            .onFailure { e -> Toast.makeText(context, context.getString(R.string.import_failed, e.message ?: ""), Toast.LENGTH_LONG).show() }
    }

    val idArg = listOf(navArgument("id") { type = NavType.LongType })
    NavHost(navController = nav, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                container = container,
                onOpen = { nav.navigate(Routes.editor(it)) },
                onPrompt = { nav.navigate(Routes.prompter(it)) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.EDITOR, arguments = idArg) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            EditorScreen(
                container = container,
                scriptId = id,
                onBack = { nav.popBackStack() },
                onPrompter = { nav.navigate(Routes.prompter(id)) },
                onStudio = { nav.navigate(Routes.studio(id)) },
            )
        }
        composable(Routes.PROMPTER, arguments = idArg) { entry ->
            PrompterScreen(
                container = container,
                scriptId = entry.arguments?.getLong("id") ?: 0L,
                onExit = { nav.popBackStack() },
            )
        }
        composable(Routes.STUDIO, arguments = idArg) { entry ->
            StudioScreen(
                container = container,
                scriptId = entry.arguments?.getLong("id") ?: 0L,
                onExit = { nav.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(container = container, onBack = { nav.popBackStack() })
        }
    }
}
