package com.stgi.rodentia

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.CountDownTimer
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import kotlinx.android.synthetic.main.cassette_barker.view.*
import kotlinx.android.synthetic.main.cassette_player.view.*
import java.io.File
import java.io.FileInputStream
import kotlin.math.max
import kotlin.math.min


private const val ERROR = -1
private const val CREATED = 0
private const val INIT = 1
private const val STARTED = 2
private const val PAUSED = 3

class CassettePlayerView(context: Context, attrs: AttributeSet): LinearLayout(context, attrs) {
    private val playButton: ImageButton
    private val cassetteLeft: ImageView
    private val cassetteRight: ImageView
    private val barker: ConstraintLayout
    private val progress: View
    private val buffer: View

    private var mediaPlayer: MediaPlayer? = null

    private var rotateTimer: CountDownTimer? = null

    private var status = CREATED

    init {
        orientation = VERTICAL
        isFocusable = false
        isClickable = false

        val player = LayoutInflater.from(context).inflate(R.layout.cassette_player, this, false)
        playButton = player.playButton
        cassetteLeft = player.cassetteLeft
        cassetteRight = player.cassetteRight
        addView(player)

        barker = LayoutInflater.from(context).inflate(R.layout.cassette_barker, this, false) as ConstraintLayout
        progress = barker.progress
        buffer = barker.buffer
        addView(barker)

        playButton.setOnClickListener { if (playButton.isSelected) pause() else start() }

        setOnKeyListener { _, keyCode, ev ->
            if (ev.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ALL_APPS || keyCode == KeyEvent.KEYCODE_HOME) {
                    stop()
                }
            }
            false
        }

        addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewDetachedFromWindow(p0: View?) {
                stop()
            }

            override fun onViewAttachedToWindow(p0: View?) { }
        })

        isEnabled = false

        val a = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.CassettePlayerView,
            0, 0
        )

        try {
            val color = a.getColor(R.styleable.CassettePlayerView_cassetteColor, Color.WHITE)
            setCassetteColor(color)
        } finally {
            a.recycle()
        }

        resetProgress()
    }

    var uri: Uri? = null
    var file: File? = null

    fun setContentUri(uri: Uri) {
        this.uri = uri
        if (this.uri != null) {
            setContentInternal()
            mediaPlayer?.let {
                it.setDataSource(context, uri)
                it.prepareAsync()
            }
        }
    }

    fun setContentFile(file: File) {
        this.file = file
        if (this.file != null) {
            setContentInternal()
            mediaPlayer?.let {
                it.setDataSource(FileInputStream(this@CassettePlayerView.file).fd)
                it.prepareAsync()
            }
        }
    }

    private fun setContentInternal() {
        mediaPlayer?.release()
        status = CREATED
        playButton.isEnabled = false

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setOnErrorListener { _, _, p2 ->
                status = ERROR
                mediaPlayer?.release()
                false
            }
            setOnCompletionListener { stop() }
            setOnPreparedListener {
                status = INIT
                isEnabled = true
            }
        }
    }

    private fun start() {
        if (mediaPlayer == null) {
            playButton.isSelected = false
            Toast.makeText(context, "Unable to reproduce audio", Toast.LENGTH_LONG)
                .show()
            stop()
        } else if (status == INIT || status == PAUSED) {
            mediaPlayer?.let {
                status = STARTED
                it.setOnCompletionListener {
                    stop()
                }
                playButton.isSelected = true
                it.start()
                initTimer()
            }
        }
    }

    private fun seekStep(forward: Boolean) {
        val newPosition: Int = if (forward) {
            max(mediaPlayer!!.duration, mediaPlayer!!.currentPosition + 500)
        } else {
            min(0, mediaPlayer!!.currentPosition - 500)
        }
        mediaPlayer?.seekTo(newPosition)
        updateProgress()
    }

    private fun pause() {
        if (status == STARTED) {
            playButton.isSelected = false
            status = PAUSED
            mediaPlayer?.pause()
            resetTimer()
        }
    }

    fun stop() {
        pause()
        if (status == PAUSED) {
            resetProgress()
            mediaPlayer?.setOnCompletionListener(null)
            mediaPlayer?.stop()
            if (uri != null)
                setContentUri(uri!!)
            else if (file != null)
                setContentFile(file!!)
        }
    }

    override fun setEnabled(enabled: Boolean) {
        playButton.isEnabled = enabled
        cassetteLeft.isEnabled = enabled
        cassetteRight.isEnabled = enabled
    }

    override fun onDetachedFromWindow() {
        //mediaPlayer?.release()
        super.onDetachedFromWindow()
    }

    private fun updateProgress(percent: Float = mediaPlayer!!.currentPosition / mediaPlayer!!.duration.toFloat(), force: Boolean = false) {
        val set = ConstraintSet()
        set.clone(barker)
        set.constrainPercentWidth(progress.id, percent)
        set.constrainPercentWidth(buffer.id, 1f - percent)
        set.applyTo(barker)
    }

    private fun resetProgress() = updateProgress(0f, true)

    fun initTimer() {
        rotateTimer = object: CountDownTimer(10000L, 50L) {
            override fun onFinish() {
                start()
            }

            override fun onTick(tick: Long) {
                cassetteLeft.rotation += 2
                cassetteRight.rotation += 2

                updateProgress()
            }
        }.also { it.start() }
    }

    fun resetTimer() {
        rotateTimer?.cancel()
        rotateTimer = null
    }

    fun setCassetteColor(color: Int) {
        playButton.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        cassetteLeft.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        cassetteRight.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)

        progress.setBackgroundColor(color)
        buffer.setBackgroundColor(Color.argb(80, color.red, color.green, color.blue))
    }
}