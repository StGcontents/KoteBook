package com.stgi.rodentia

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.CountDownTimer
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign


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

        val player = LayoutInflater.from(context).inflate(com.stgi.rodentia.R.layout.cassette_player, this, false)
        playButton = player.playButton
        cassetteLeft = player.cassetteLeft
        cassetteRight = player.cassetteRight
        addView(player)

        barker = LayoutInflater.from(context).inflate(com.stgi.rodentia.R.layout.cassette_barker, this, false) as ConstraintLayout
        progress = barker.progress
        buffer = barker.buffer
        addView(barker)

        playButton.setOnClickListener { if (playButton.isSelected) pause() else start() }

        val tl: OnTouchListener = object : OnTouchListener {
            private var lastAngle: Float = 0f
            override fun onTouch(v: View?, ev: MotionEvent?): Boolean {
                when (ev?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastAngle = ev.x + v!!.left
                    }
                    MotionEvent.ACTION_MOVE -> {
                        pause()
                        /*val pos = IntArray(2)
                        v?.getLocationInWindow(pos)
                        //pos[0] += cassetteLeft.width / 2
                        //pos[1] += cassetteLeft.height / 2*/
                        val gamma: Float

                        /*if (abs(pos[0] - ev.x) < 1)
                            gamma = if (ev.y > pos[1]) 180f else 0f
                        else {
                            val a = ev.y - pos[1]
                            val b = (ev.y.pow(2) + (ev.x - pos[0]).pow(2)).pow(0.5f)
                            val c = ((pos[1] - ev.y).pow(2) + (ev.x - pos[0]).pow(2)).pow(0.5f)
                            gamma = acos((a.pow(2) + b.pow(2) - c.pow(2)).div(2 * a * b))
                        }*/
                        gamma = ev.x + v!!.left - lastAngle
                        Log.d("ANGLETAG", "" + ev.rawX + " | " + lastAngle + " | " + (gamma.sign * (gamma % 360)))

                        cassetteLeft.rotation += gamma.div(4)
                        cassetteRight.rotation += gamma.div(4)
                        seekStep(gamma > 0)

                        //lastAngle = gamma
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        lastAngle = 0f
                        start()
                    }
                }
                return true
            }
        }
        //cassetteLeft.setOnTouchListener(tl)
        //cassetteRight.setOnTouchListener(tl)
        isEnabled = false

        val a = context.theme.obtainStyledAttributes(
            attrs,
            com.stgi.rodentia.R.styleable.CassettePlayerView,
            0, 0
        )

        try {
            val color = a.getColor(com.stgi.rodentia.R.styleable.CassettePlayerView_cassetteColor, Color.WHITE)
            setCassetteColor(color)
        } finally {
            a.recycle()
        }

        resetProgress()
    }

    var uri: Uri? = null

    fun setContentUri(uri: Uri) {
        mediaPlayer?.release()
        status = CREATED
        playButton.isEnabled = false

        this.uri = uri
        if (this.uri != null) {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(context, uri)
                setOnCompletionListener { stop() }
                setOnPreparedListener {
                    status = INIT
                    isEnabled = true
                }
                prepareAsync()
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
            status = STARTED
            mediaPlayer?.setOnCompletionListener {
                stop()
            }
            playButton.isSelected = true
            mediaPlayer?.start()
            initTimer()
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
        }
    }

    override fun setEnabled(enabled: Boolean) {
        playButton.isEnabled = enabled
        cassetteLeft.isEnabled = enabled
        cassetteRight.isEnabled = enabled
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mediaPlayer?.release()
    }

    private var lastPercent = 0f

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