package com.auto.rr

import android.app.Application
import com.auto.rr.data.ApiKeyStore
import com.auto.rr.data.sync.SyncScheduler
import com.auto.rr.di.AppLifecycle

class AutoRrApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiKeyStore.init(this)
        SyncScheduler.schedulePeriodic(this)
        AppLifecycle.start(this)
    }
}
