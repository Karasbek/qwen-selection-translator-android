package com.karas.qwentranslator

import com.karas.qwentranslator.data.ApiKeyStore
import com.karas.qwentranslator.data.AppSettings
import com.karas.qwentranslator.data.SettingsStore

class FakeSettings(var value: AppSettings = AppSettings()) : SettingsStore {
    override suspend fun snapshot() = value
    override suspend fun save(settings: AppSettings) { value = settings }
}
class FakeSecrets(var value: String? = "test-placeholder") : ApiKeyStore {
    override fun getApiKey() = value
    override fun setApiKey(value: String) { this.value = value }
}
