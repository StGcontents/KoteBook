package com.stgi.kotebook

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat.getSystemService
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.fab_audio.view.*
import java.lang.Float.max
import java.util.*
import kotlin.math.min
import kotlin.math.pow

class RecordingFloatingActionButton(context: Context, attributeSet: AttributeSet?):
    RelativeLayout(context, attributeSet), View.OnTouchListener {

    private val fab: View = LayoutInflater.from(context).inflate(R.layout.fab_audio, this, false)

    var timer: CountDownTimer? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startTimer()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopTimer()
    }

    fun startTimer() {
        if (timer == null) {
            timer = object : CountDownTimer(5000L, 50L) {
                var increment = true

                override fun onFinish() {
                    this.start()
                }

                override fun onTick(p0: Long) {
                    val maxAlpha = 0.7f - (fab.x * 0.7f / getSwipeThreshold()).pow(0.5f)
                    val bit = 2 * maxAlpha * 0.025f
                    increment = when {
                        fab.swipeView.alpha >= maxAlpha -> {
                            fab.swipeView.alpha = maxAlpha - bit; false
                        }
                        fab.swipeView.alpha <= 0f -> {
                            fab.swipeView.alpha = bit; true
                        }
                        else -> {
                            fab.swipeView.alpha += if (increment) bit else -bit; increment
                        }
                    }
                }
            }.also { it.start() }
        }
    }

    fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    init {
        setOnTouchListener { _, _ -> false }

        addView(fab)
        fab.fab.setOnTouchListener(this)
    }

    var startX = 0f
    var leftBound = 0f
    var vibrationFlag = false

    private var callback: ItemTouchHelperAdapter? = null

    override fun onTouch(v: View?, ev: MotionEvent?): Boolean {
        if (!isRecording) {
            when (ev?.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = ev.x
                    leftBound = fab.x
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    fab.x = min(max(leftBound, fab.x + (ev.x - startX)), getSwipeThreshold())
                    val alpha = min(fab.x.toInt() * 255 / getSwipeThreshold().toInt(), 255)
                    fab.circlet?.background?.setColorFilter(
                        Color.argb(alpha, 255, 0, 0), PorterDuff.Mode.DARKEN
                    )
                    if (fab.x >= getSwipeThreshold(false) && !vibrationFlag) {
                        val vibrator =
                            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
                        vibrator?.vibrate(
                            VibrationEffect.createOneShot(
                                100L,
                                VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )

                        stopTimer()
                        fab.swipeView.alpha = 0f
                    } else if (fab.x < getSwipeThreshold(false))
                        startTimer()

                    vibrationFlag = fab.x >= getSwipeThreshold(false)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (fab.x >= getSwipeThreshold(false))
                        callback?.onItemSwiped(0, 0)
                    fab.animate()
                        .translationX(0f)
                        .setDuration(100L)
                    startX = 0f
                    fab.circlet?.background?.clearColorFilter()
                    return true
                }
            }
            //return false
        }
        return true
    }

    fun getSwipeThreshold(actual: Boolean = true): Float {
        return resources.displayMetrics.widthPixels - (resources.getDimension(R.dimen.fab_size) + resources.getDimension(R.dimen.fab_margin) * (if (actual) 2 else 4))
    }

    private var isRecording: Boolean = false

    fun setIsRecording(isRecording: Boolean) {
        this.isRecording = isRecording
        if (isRecording) {
            fab.fab.visibility = View.GONE
            fab.swipeView.visibility = View.GONE
            stopTimer()
            fab.visualizer.visibility = View.VISIBLE
        } else {
            fab.fab.visibility = View.VISIBLE
            fab.swipeView.visibility = View.VISIBLE
            startTimer()
            fab.visualizer.visibility = View.GONE
            fab.visualizer.reset()
        }
    }

    fun updateAmplitude(amplitude: Int?) {
        if (isRecording && amplitude != null) {
            fab.visualizer.updateAmplitude(amplitude)
        }
    }

    fun setSwipeAdapter(adapter: ItemTouchHelperAdapter) {
        callback = adapter
    }
}
    /*RecyclerView(context, attributeSet) {

    init {
        layoutManager = LinearLayoutManager(context, VERTICAL, false)
        adapter = AudioAdapter()
    }

    private var isRecording: Boolean = false

    fun setIsRecording(isRecording: Boolean) {
        this.isRecording = isRecording
        if (isRecording) {
            getChildAt(0).fab.visibility = View.GONE
            getChildAt(0).visualizer.visibility = View.VISIBLE
        } else {
            getChildAt(0).fab.visibility = View.VISIBLE
            getChildAt(0).visualizer.visibility = View.GONE
            getChildAt(0).visualizer.reset()
        }
    }

    fun setSwipeAdapter(adapter: ItemTouchHelperAdapter) {
        val helper = ItemTouchHelper(AudioCallback(AdapterDecorator(adapter), dragEnabled = false, swipeEnabled = true))
        helper.attachToRecyclerView(this)
    }

    fun updateAmplitude(amplitude: Int?) {
        if (isRecording && amplitude != null) {
            getChildAt(0).visualizer.updateAmplitude(amplitude)
        }
    }

    inner class AudioAdapter: Adapter<AudioViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fab_audio, parent, false)
            return AudioViewHolder(view)
        }

        override fun getItemCount() = 1

        override fun onBindViewHolder(holder: AudioViewHolder, position: Int) {

        }
    }

    inner class AudioViewHolder(itemView: View): ViewHolder(itemView)

    inner class AdapterDecorator(private val ithAdapter: ItemTouchHelperAdapter): ItemTouchHelperAdapter {
        override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
            return ithAdapter.onItemMove(fromPosition, toPosition)
        }

        override fun onItemMoveEnded(fromPosition: Int, toPosition: Int) {
            ithAdapter.onItemMoveEnded(fromPosition, toPosition)
        }

        override fun onItemSwiped(position: Int, direction: Int) {
            ithAdapter.onItemSwiped(position, direction)
        }

        override fun isItemViewSwipeEnabled(): Boolean = !isRecording
    }
}*/