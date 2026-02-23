package com.example.deepseekchat

import android.app.Application
import com.example.deepseekchat.core.di.databaseModule
import com.example.deepseekchat.core.di.networkModule
import com.example.deepseekchat.core.di.repositoryModule
import com.example.deepseekchat.core.di.useCaseModule
import com.example.deepseekchat.core.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DeepSeekChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@DeepSeekChatApplication)
            modules(
                databaseModule,
                networkModule,
                repositoryModule,
                useCaseModule,
                viewModelModule
            )
        }
    }
}
