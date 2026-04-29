package com.forge.bridge.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.forge.bridge.ForgeBridgeApp
import com.forge.bridge.R
import com.forge.bridge.data.remote.api.BridgeServer
import com.forge.bridge.data.remote.api.TokenRefreshScheduler
import com.forge.bridge.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps the Ktor server alive and starts the token
 * refresh scheduler.
 */
@AndroidEntryPoint
class BridgeService : LifecycleService() {

    @Inject lateinit var server: BridgeServer
    @Inject lateinit var refresher: TokenRefreshScheduler

    override fun onCreate() {
        super.onCreate()
        startForeground(ForgeBridgeApp.NOTIFICATION_ID, buildNotification())
        lifecycleScope.launch(Dispatchers.IO) { server.start() }
        refresher.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        refresher.stop()
        server.stop()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, ForgeBridgeApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
