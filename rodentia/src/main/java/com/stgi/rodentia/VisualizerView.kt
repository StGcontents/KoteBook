package com.stgi.rodentia

import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.CountDownTimer
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.ConstraintSet.*
import androidx.core.view.setMargins
import java.lang.Float.min

class VisualizerView(context: Context, attributeSet: AttributeSet): ConstraintLayout(context, attributeSet),
    MediaRecorder.OnInfoListener {

    val startingSet: ConstraintSet

    init {
        val size = resources.getDimension(R.dimen.visualizer_item_size).toInt()

        startingSet = ConstraintSet()
        startingSet.clone(this)
        for (i in 0..7) {
            val view = VisualizerItemView(context)
            view.id = View.generateViewId()
            addView(view, i)
            when (i) {
                0 -> startingSet.connect(view.id, START, PARENT_ID, START, size)
                else -> startingSet.connect(view.id, START, getChildAt(i - 1).id, END, size)
            }

            startingSet.constrainWidth(view.id, size)
            startingSet.constrainMinHeight(view.id, size)
            startingSet.constrainPercentHeight(view.id, 0f)
            startingSet.connect(view.id, TOP, PARENT_ID, TOP)
            startingSet.connect(view.id, BOTTOM, PARENT_ID, BOTTOM)
        }
        startingSet.applyTo(this)
    }

    private var currentRecorder: MediaRecorder? = null
    private var currentPlayer: MediaPlayer? = null

    private val timer = object : CountDownTimer(10000L, 50L) {
        override fun onFinish() {
            start()
        }

        override fun onTick(tick: Long) {
            if (currentRecorder != null)
                updateAmplitude(currentRecorder!!.maxAmplitude)
            else if (currentPlayer != null) {
                val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                updateAmplitude(manager.getStreamVolume(AudioManager.STREAM_MUSIC))
            }
        }
    }

    fun updateAmplitude(amplitude: Int) {
        val set = ConstraintSet()
        set.clone(this)
        for (i in childCount - 1 downTo 0) {
            val view = getChildAt(i)
            when (i) {
                0 -> {
                    val percent = min(amplitude / 32000f, 1f)
                    set.constrainPercentHeight(view.id, percent)
                }
                else -> {
                    val percent = (getChildAt(i - 1).layoutParams as LayoutParams)
                        .matchConstraintPercentHeight
                    set.constrainPercentHeight(view.id, percent)
                }
            }

        }
        set.applyTo(this)
    }

    fun connectToRecorder(source: MediaRecorder?) {
        disconnectToAny()
        currentRecorder = source
        currentRecorder?.let {
            it.setOnInfoListener(this)
            timer.start()
        }
    }

    fun connectToPlayer(source: MediaPlayer?) {
        disconnectToAny()
        currentPlayer = source
        currentPlayer?.let {
            it.setOnCompletionListener {
                disconnectToAny()
                callback?.onAutoRelease()
            }
            timer.start()
        }
    }

    fun disconnectToAny() {
        timer.cancel()
        if (currentPlayer != null) disconnectToPlayer()
        if (currentRecorder != null) disconnectToRecorder()
    }

    private fun disconnectToRecorder() {
        currentRecorder?.setOnInfoListener(null)
        currentRecorder = null
        reset()
    }

    private fun disconnectToPlayer() {
        currentPlayer?.setOnCompletionListener(null)
        currentPlayer = null
        reset()
    }

    private fun reset() {
        startingSet.applyTo(this)
    }

    inner class VisualizerItemView(context: Context): View(context) {
        init {
            val size = resources.getDimension(R.dimen.visualizer_item_size).toInt()
            val params = LayoutParams(size, 0)
            params.setMargins(size)
            layoutParams = params

            setBackgroundColor(Color.WHITE)
        }
    }

    override fun onInfo(recorder: MediaRecorder?, what: Int, extra: Int) {
        when (what) {
            MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED,
                MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED -> {
                disconnectToAny()
                callback?.onAutoRelease()
            }
        }
    }

    var callback: OnAutoReleaseListener? = null

    interface OnAutoReleaseListener {
        fun onAutoRelease()
    }
}