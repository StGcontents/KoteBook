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
import kotlin.math.max
import kotlin.math.min

private const val MIRRORED = 1
private const val VBOTTOM = 2
private const val CENTERED = 4
private const val VTOP = 8
private const val IMPLICIT_CENTERED = VTOP or VBOTTOM

private const val MIN_SAMPLES = 5
private const val DEFAULT_SAMPLES = 9
private const val MAX_SAMPLES = 20

class VisualizerView(context: Context, attributeSet: AttributeSet): ConstraintLayout(context, attributeSet),
    MediaRecorder.OnInfoListener {

    private val startingSet: ConstraintSet
    private val samples: Int
    private val visualizerStyle: Int
    private val dotSize: Int
    private val dotSpacing: Int
    private val dotColor: Int

    init {
        val a = context.theme.obtainStyledAttributes(
            attributeSet,
            R.styleable.VisualizerView,
            0, 0
        )

        try {
            samples = min(max(a.getInt(R.styleable.VisualizerView_samples, DEFAULT_SAMPLES), MIN_SAMPLES), MAX_SAMPLES)
            visualizerStyle = a.getInteger(R.styleable.VisualizerView_visualizerStyle, CENTERED)
            dotSize = a.getDimensionPixelSize(R.styleable.VisualizerView_dotSize, resources.getDimensionPixelSize(R.dimen.visualizer_item_size))
            dotSpacing = a.getDimensionPixelSize(R.styleable.VisualizerView_dotSpacing, resources.getDimensionPixelSize(R.dimen.visualizer_item_size))
            dotColor = a.getColor(R.styleable.VisualizerView_dotColor, Color.WHITE)
        } finally {
            a.recycle()
        }

        startingSet = ConstraintSet()
        startingSet.clone(this)

        addVisualizerDot(position = samples / 2)

        startingSet.applyTo(this)
    }

    private fun addVisualizerDot(branchLeft: Boolean? = null, chainedView: VisualizerDot? = null, position: Int, amount: Int = samples) {
        val view = VisualizerDot(context)
        view.id = View.generateViewId()
        view.setBackgroundColor(dotColor)

        startingSet.constrainWidth(view.id, dotSize)
        startingSet.constrainMinHeight(view.id, dotSize)
        startingSet.constrainPercentHeight(view.id, 0f)
        when {
            visualizerStyle and CENTERED == CENTERED ||
                    visualizerStyle and IMPLICIT_CENTERED == IMPLICIT_CENTERED ||
                    visualizerStyle <= MIRRORED -> {
                startingSet.connect(view.id, TOP, PARENT_ID, TOP)
                startingSet.connect(view.id, BOTTOM, PARENT_ID, BOTTOM)
            }
            visualizerStyle and VTOP == VTOP -> startingSet.connect(view.id, TOP, PARENT_ID, TOP)
            visualizerStyle and VBOTTOM == VBOTTOM -> startingSet.connect(view.id, BOTTOM, PARENT_ID, BOTTOM)
        }

        if (chainedView == null) {
            addVisualizerDot(true, view, position - 1)

            addView(view, position)
            startingSet.centerHorizontally(view.id, PARENT_ID)

            addVisualizerDot(false, view, position + 1)
        } else {
            when {
                position < 0 || position >= amount -> return
                branchLeft == true -> {
                    if (position > 0)
                        addVisualizerDot(true, view, position - 1)
                    addView(view, position)
                    startingSet.connect(view.id, END, chainedView.id, START, dotSpacing)
                }
                else -> {
                    addView(view, position)
                    startingSet.connect(view.id, START, chainedView.id, END, dotSpacing)
                    if (position < amount - 1)
                        addVisualizerDot(false, view, position + 1)
                }
            }
        }
    }

    private var currentRecorder: MediaRecorder? = null
    private var currentPlayer: MediaPlayer? = null

    private val timer = object : CountDownTimer(10000L, 50L) {
        override fun onFinish() {
            start()
        }

        override fun onTick(tick: Long) {
            val sample: Int? = when {
                currentRecorder != null -> currentRecorder!!.maxAmplitude
                currentPlayer != null -> {
                    val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    manager.getStreamVolume(AudioManager.STREAM_MUSIC)
                }
                else -> null
            }
            if (sample != null)
                updateAmplitude(min(sample / 32000f, 1f))
        }
    }

    fun updateAmplitude(amplitude: Float, set: ConstraintSet? = null, position: Int = 0, propagateRight: Boolean = true) {
        if (set == null) {
            val newSet = ConstraintSet()
            newSet.clone(this)
            val view: VisualizerDot
            val oldAmplitude: Float
            when (visualizerStyle) {
                visualizerStyle or MIRRORED -> {
                    val half = childCount / 2
                    view = getChildAt(half) as VisualizerDot
                    oldAmplitude = (view.layoutParams as LayoutParams)
                        .matchConstraintPercentHeight
                    newSet.constrainPercentHeight(view.id, amplitude)
                    updateAmplitude(oldAmplitude, newSet, half + 1)
                    updateAmplitude(oldAmplitude, newSet, half - 1, false)
                }
                else -> {
                    view = getChildAt(0) as VisualizerDot
                    oldAmplitude = (view.layoutParams as LayoutParams)
                        .matchConstraintPercentHeight
                    newSet.constrainPercentHeight(view.id, amplitude)
                    updateAmplitude(oldAmplitude, newSet, 1)
                }
            }
            //update according to strategy
            //propagate according to strategy
            newSet.applyTo(this)
        } else {
            val view = getChildAt(position) as VisualizerDot
            val oldAmplitude = (view.layoutParams as LayoutParams)
                .matchConstraintPercentHeight
            set.constrainPercentHeight(view.id, amplitude)
            when (propagateRight) {
                true -> {
                    if (position == samples - 1) return
                    updateAmplitude(oldAmplitude, set, position + 1)
                }
                else -> {
                    if (position == 0) return
                    updateAmplitude(oldAmplitude, set, position - 1, false)
                }
            }
        }
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

    inner class VisualizerDot(context: Context): View(context) {
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