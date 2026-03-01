package com.example.deepseekchat.domain.model

enum class ContextWindowMode {
    FULL_HISTORY,
    SUMMARY_PLUS_LAST_10,
    SLIDING_WINDOW_LAST_10;

    companion object {
        fun fromStored(value: String): ContextWindowMode {
            return entries.firstOrNull { it.name == value } ?: FULL_HISTORY
        }
    }
}
