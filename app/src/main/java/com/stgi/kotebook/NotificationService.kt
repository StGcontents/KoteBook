package com.stgi.kotebook

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
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
        manager.createNotificationChannel(NotificationChannel("KoteBook", "KoteBook", NotificationManager.IMPORTANCE_HIGH))
        manager.createNotificationChannelGroup(NotificationChannelGroup("KoteBookGroup", "KoteBook"))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            it.extras?.apply {
                val id = getInt(ID, 13)
                val isRecording = getBoolean(IS_RECORDING, false)

                val notification =
                    NotificationCompat.Builder(this@NotificationService, "KoteBook").apply {
                        setContentTitle(getString(TITLE, "Note"))
                        if (!isRecording)
                            setContentText(getString(TEXT, null))

                        setAutoCancel(true)
                        setOngoing(false)
                        setContentIntent(
                            PendingIntent.getActivity(
                                this@NotificationService,
                                13,
                                Intent(this@NotificationService, MainActivity::class.java),
                                PendingIntent.FLAG_UPDATE_CURRENT
                            )
                        )

                        setVibrate(longArrayOf(0L, 200L, 200L, 200L))
                        setSmallIcon(if (isRecording) R.drawable.play else R.drawable.bullets)

                        val mediaSession = MediaSessionCompat(applicationContext, "tag")
                        mediaSession.setFlags(0)
                        mediaSession.setPlaybackState(
                            PlaybackStateCompat.Builder()
                                .setState(PlaybackStateCompat.STATE_NONE, 0, 0f)
                                .build()
                        )


                        setStyle(
                            androidx.media.app.NotificationCompat.DecoratedMediaCustomViewStyle()
                                .setMediaSession(mediaSession.sessionToken)
                        )
                        color = getInt(COLOR, Color.TRANSPARENT)
                        setColorized(true)
                        mediaSession.release()

                        setGroup("KoteBookGroup")
                        setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
                        setGroupSummary(true)
                    }.build()

                val manager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(id, notification)

                Thread(
                    Runnable {
                        NotesDatabase.instance(this@NotificationService)
                            .notesDao()
                            .resetAlarm(id)
                    }).start()
            }
        }
        stopSelf()
        return START_NOT_STICKY
    }
}