package com.shyan.dreamin.viewmodel

data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)
