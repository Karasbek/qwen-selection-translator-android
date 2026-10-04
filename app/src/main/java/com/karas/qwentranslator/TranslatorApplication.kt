package com.karas.qwentranslator

import android.app.Application
import com.karas.qwentranslator.di.AppContainer

class TranslatorApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
