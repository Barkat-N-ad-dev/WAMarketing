package com.wamarketing.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.wamarketing.app.data.SessionManager
import com.wamarketing.app.data.WAMarketingRepository
import com.wamarketing.app.ui.main.MainScreen

@Composable
fun MainNavigation() {
    val context = LocalContext.current.applicationContext
    val repository = remember { WAMarketingRepository.getInstance(context) }
    val sessionManager = remember { SessionManager.getInstance(context) }
    MainScreen(
        repository = repository,
        sessionManager = sessionManager,
        modifier = Modifier.fillMaxSize()
    )
}
