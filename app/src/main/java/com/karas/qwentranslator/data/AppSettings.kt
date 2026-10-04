package com.karas.qwentranslator.data

data class AppSettings(
    val targetLanguage: String = "ru",
    val baseUrl: String = "https://api.groq.com/openai/v1",
    val model: String = "qwen/qwen3.8-27b",
)
