package com.boykta.assistant.service

object BoyktaEvents {
    const val ACTION_RELOAD_MODELS = "com.boykta.assistant.ACTION_RELOAD_MODELS"
    const val ACTION_SIMULATE_WAKE = "com.boykta.assistant.ACTION_SIMULATE_WAKE"
    const val ACTION_STATE_UPDATE = "com.boykta.assistant.ACTION_STATE_UPDATE"

    const val EXTRA_TYPE = "type"
    const val EXTRA_TEXT = "text"
    const val EXTRA_REPLY = "reply"
    const val EXTRA_IS_LISTENING = "is_listening"
    const val EXTRA_AWAKE_WINDOW = "awake_window"

    const val TYPE_STATUS = "STATUS"
    const val TYPE_PARTIAL = "PARTIAL"
    const val TYPE_WAKE = "WAKE"
    const val TYPE_COMMAND = "COMMAND"
    const val TYPE_ERROR = "ERROR"
}
