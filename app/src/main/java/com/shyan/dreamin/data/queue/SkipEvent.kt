package com.shyan.dreamin.data.queue

enum class SkipDirection { NEXT, PREVIOUS }

data class SkipEvent(val direction: SkipDirection, val changedTrack: Boolean)
