package com.iktishafat.teleprompter.platform.remote

import com.iktishafat.teleprompter.domain.remote.RemoteCommand
import com.iktishafat.teleprompter.domain.remote.RemoteKeyMapper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Routes hardware key presses (Bluetooth keyboards, clickers, pedals, media remotes)
 * from the Activity to whichever prompter screen is currently collecting.
 * Keys are only consumed while a prompter is listening, so text fields keep working.
 */
class RemoteControlBus {
    val mapper = RemoteKeyMapper()
    private val _commands = MutableSharedFlow<RemoteCommand>(extraBufferCapacity = 32)
    val commands: SharedFlow<RemoteCommand> = _commands

    val isListening: Boolean get() = _commands.subscriptionCount.value > 0

    /** Returns true if the key was mapped and delivered (the Activity should consume it). */
    fun onKey(keyCode: Int): Boolean {
        if (!isListening) return false
        val command = mapper.map(keyCode) ?: return false
        return _commands.tryEmit(command)
    }

    /** True when this key code would be handled; used to also swallow the matching key-up. */
    fun handles(keyCode: Int): Boolean = isListening && mapper.map(keyCode) != null
}
