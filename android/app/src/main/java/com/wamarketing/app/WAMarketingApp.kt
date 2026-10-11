package com.wamarketing.app

import android.app.Application
import com.wamarketing.app.data.SessionManager
import com.wamarketing.app.data.WAMarketingRepository

class WAMarketingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WAMarketingRepository.init(this)
        SessionManager.getInstance(this)
    }
}
