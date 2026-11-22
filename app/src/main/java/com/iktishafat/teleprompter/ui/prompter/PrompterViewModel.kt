package com.iktishafat.teleprompter.ui.prompter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iktishafat.teleprompter.AppContainer
import com.iktishafat.teleprompter.domain.model.PrompterSettings
import com.iktishafat.teleprompter.domain.model.Script
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PrompterViewModel(private val container: AppContainer, private val scriptId: Long) : ViewModel() {
    val script: StateFlow<Script?> = container.scripts.observeScript(scriptId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val settings: StateFlow<PrompterSettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun update(transform: (PrompterSettings) -> PrompterSettings) {
        viewModelScope.launch { container.settings.update(transform) }
    }

    /** Uses the app scope so the position is stored even while the screen is closing. */
    fun saveLastRead(offset: Int) {
        container.appScope.launch { container.scripts.setLastReadOffset(scriptId, offset) }
    }
}
