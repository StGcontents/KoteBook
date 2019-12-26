package com.stgi.kotebook

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.IBinder
import androidx.core.app.NotificationCompat

const val ID = "id"
const val TITLE = "title"
const val TEXT = "text"
const val IS_RECORDING = "isRecording"
const val COLOR = "color"

class NotificationService: Service() {

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("KoteBook", "KoteBook", NotificationManager.IMPORTANCE_DEFAULT))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            it.extras?.apply {
                val id = getInt(ID, 13)
                val isRecording = getBoolean(IS_RECORDING, false)

                val notification = NotificationCompat.Builder(this@NotificationService, "KoteBook").apply {
                    setContentTitle(getString(TITLE, "Note"))
                    if (!isRecording)
                        setContentText(getString(TEXT, null))

                    setAutoCancel(true)
                    setOngoing(false)
                    setContentIntent(PendingIntent.getActivity(
                        this@NotificationService,
                        13,
                        Intent(this@NotificationService, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT))

                    setSmallIcon(if (isRecording) R.drawable.play else R.drawable.bullets)
                    setColorized(true)
                    color = getInt(COLOR, Color.TRANSPARENT)

                    setGroup("KoteBookGroup")
                    setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                    setGroupSummary(true)
                }.build()

                startForeground(id, notification)
            }
        }
        return START_NOT_STICKY
    }
}