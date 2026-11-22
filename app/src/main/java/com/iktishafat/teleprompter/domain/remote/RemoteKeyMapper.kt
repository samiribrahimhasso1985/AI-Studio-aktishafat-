package com.iktishafat.teleprompter.domain.remote

enum class RemoteCommand { PLAY_PAUSE, SPEED_UP, SPEED_DOWN, FORWARD, BACKWARD, RESTART, TOGGLE_MIRROR, EXIT }

/**
 * Maps key codes from Bluetooth keyboards, presentation clickers, page-turner pedals
 * and media/camera remotes to prompter commands. Values mirror android.view.KeyEvent
 * constants so this class stays free of Android dependencies and unit-testable.
 */
class RemoteKeyMapper(var volumeKeysEnabled: Boolean = true) {

    object Keys {
        const val HOME_KEY = 3
        const val DPAD_UP = 19
        const val DPAD_DOWN = 20
        const val DPAD_LEFT = 21
        const val DPAD_RIGHT = 22
        const val DPAD_CENTER = 23
        const val VOLUME_UP = 24
        const val VOLUME_DOWN = 25
        const val CAMERA = 27
        const val M = 41
        const val R = 46
        const val SPACE = 62
        const val ENTER = 66
        const val MINUS = 69
        const val EQUALS = 70
        const val PLUS = 81
        const val HEADSETHOOK = 79
        const val MEDIA_PLAY_PAUSE = 85
        const val MEDIA_STOP = 86
        const val MEDIA_NEXT = 87
        const val MEDIA_PREVIOUS = 88
        const val MEDIA_REWIND = 89
        const val MEDIA_FAST_FORWARD = 90
        const val PAGE_UP = 92
        const val PAGE_DOWN = 93
        const val ESCAPE = 111
        const val MOVE_HOME = 122
        const val MEDIA_PLAY = 126
        const val MEDIA_PAUSE = 127
        const val NUMPAD_SUBTRACT = 156
        const val NUMPAD_ADD = 157
        const val NUMPAD_ENTER = 160
    }

    fun map(keyCode: Int): RemoteCommand? = when (keyCode) {
        Keys.SPACE, Keys.ENTER, Keys.NUMPAD_ENTER, Keys.DPAD_CENTER, Keys.MEDIA_PLAY_PAUSE,
        Keys.MEDIA_PLAY, Keys.MEDIA_PAUSE, Keys.HEADSETHOOK, Keys.CAMERA -> RemoteCommand.PLAY_PAUSE

        Keys.DPAD_RIGHT, Keys.PLUS, Keys.EQUALS, Keys.NUMPAD_ADD, Keys.MEDIA_FAST_FORWARD -> RemoteCommand.SPEED_UP
        Keys.DPAD_LEFT, Keys.MINUS, Keys.NUMPAD_SUBTRACT, Keys.MEDIA_REWIND -> RemoteCommand.SPEED_DOWN

        // Clickers and pedals send PAGE_DOWN/PAGE_UP (or next/previous).
        Keys.DPAD_DOWN, Keys.PAGE_DOWN, Keys.MEDIA_NEXT -> RemoteCommand.FORWARD
        Keys.DPAD_UP, Keys.PAGE_UP, Keys.MEDIA_PREVIOUS -> RemoteCommand.BACKWARD

        Keys.R, Keys.MOVE_HOME, Keys.MEDIA_STOP -> RemoteCommand.RESTART
        Keys.M -> RemoteCommand.TOGGLE_MIRROR
        Keys.ESCAPE -> RemoteCommand.EXIT

        // Cheap Bluetooth "selfie" remotes send volume keys.
        Keys.VOLUME_UP -> if (volumeKeysEnabled) RemoteCommand.PLAY_PAUSE else null
        Keys.VOLUME_DOWN -> if (volumeKeysEnabled) RemoteCommand.FORWARD else null
        else -> null
    }
}
