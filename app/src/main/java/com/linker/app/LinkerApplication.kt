package com.linker.app

import android.app.Application
import com.linker.app.di.AppContainer
import com.linker.app.di.DefaultAppContainer

class LinkerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
