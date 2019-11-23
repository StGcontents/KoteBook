package com.stgi.rodentia

import android.annotation.SuppressLint
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
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.RelativeLayout
import androidx.core.view.marginEnd
import kotlinx.android.synthetic.main.layout_fab_audio.view.*
import java.lang.Float.max
import java.util.*
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

const val DEFAULT = -1

const val LEFT = -1
const val RIGHT = 1

const val VIBRATION = 0
const val NONE = 1

const val MINIMUM = 1000

open class SwipeButton(context: Context, attributeSet: AttributeSet?):
    RelativeLayout(context, attributeSet), View.OnTouchListener {

    protected val fabLayout: ViewGroup by lazy {
        LayoutInflater.from(context).inflate(R.layout.layout_fab_audio, this, false) as ViewGroup
    }
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    private val defaultSrc: Int
    private val swipedSrc: Int

    protected val indefinite: Boolean
    protected var maxDuration: Int

    private val threshold: Int
        get() {
            return when {
                field <= 0 || field > width -> when (direction) {
                    RIGHT -> width - resources.getDimensionPixelSize(R.dimen.fab_size) - paddingEnd
                    else -> paddingStart - fabLayout.leftGuides.width - fabLayout.leftGuides.marginEnd
                }
                else -> when (direction) {
                    RIGHT -> field
                    else -> width - paddingEnd - resources.getDimensionPixelSize(R.dimen.fab_size) - field
                }
            }
        }
    private val thresholdSensitivity: Int
        get() {
            return when {
                field <= 0 || field > threshold || field > width ->
                    resources.getDimensionPixelSize(R.dimen.fab_margin)
                else -> field
            }
        }
    private val signal: Int

    protected val direction: Int

    private val guidesTimer: CountDownTimer

    init {
        setOnTouchListener { _, _ -> false }

        addView(fabLayout)
        fabLayout.post {
            fabLayout.fab.setOnTouchListener(this)
        }

        val a = context.theme.obtainStyledAttributes(
            attributeSet,
            R.styleable.SwipeButton,
            0, 0
        )

        try {
            defaultSrc = a.getResourceId(R.styleable.SwipeButton_defaultSrc, -1)
            swipedSrc = a.getResourceId(R.styleable.SwipeButton_swipedSrc, -1)
            setDefaultSrc()

            indefinite = a.getBoolean(R.styleable.SwipeButton_indefinite, false)
            maxDuration = a.getInt(R.styleable.SwipeButton_maxDuration, MINIMUM)

            threshold = a.getDimensionPixelSize(R.styleable.SwipeButton_threshold, DEFAULT)
            thresholdSensitivity =
                a.getDimensionPixelSize(R.styleable.SwipeButton_thresholdSensitivity, DEFAULT)

            signal = a.getResourceId(R.styleable.SwipeButton_thresholdSignal, NONE)

            direction = a.getInt(R.styleable.SwipeButton_swipeDirection, RIGHT)
            when (direction) {
                LEFT -> {
                    val params = LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                    params.addRule(ALIGN_PARENT_END, TRUE)
                    fabLayout.layoutParams = params

                    fabLayout.rightGuides.visibility = View.GONE
                }
                RIGHT -> {
                    val params = LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                    params.addRule(ALIGN_PARENT_START, TRUE)
                    fabLayout.layoutParams = params

                    fabLayout.leftGuides.visibility = View.GONE
                }
            }
        } finally {
            a.recycle()
        }

        guidesTimer = object : CountDownTimer(5000L, 50L) {
            var increment = true

            override fun onFinish() {
                this.start()
            }

            override fun onTick(tick: Long) {
                val maxAlpha = when (direction) {
                    RIGHT -> 0.7f - (fabLayout.x * 0.7f / getSwipeThreshold()).pow(0.5f)
                    else -> 0.7f - ((width - fabLayout.x - fabLayout.width) * 0.7f / (width - getSwipeThreshold())).pow(0.5f)
                }
                val bit = 2 * maxAlpha * 0.025f
                val guides = when (direction) {
                    RIGHT -> fabLayout.rightGuides
                    else -> fabLayout.leftGuides
                }
                increment = when {
                    guides.alpha >= maxAlpha -> {
                        guides.alpha = maxAlpha - bit; false
                    }
                    guides.alpha <= 0f -> {
                        guides.alpha = bit; true
                    }
                    else -> {
                        guides.alpha += if (increment) bit else -bit; increment
                    }
                }
            }
        }
    }

    private var startX = 0f
    private var startingBound = 0f
    private var thresholdFlag = false

    private var isSwitchedOn: Boolean = false

    private var callback: OnSwipeListener? = null

    @SuppressLint("MissingPermission")
    override fun onTouch(v: View?, ev: MotionEvent?): Boolean {
        if (!isSwitchedOn) {
            when (ev?.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = ev.x
                    startingBound = fabLayout.x
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    move(ev.x - startX)
                    adjustAlpha()

                    val oldFlag = thresholdFlag

                    if (brokeThreshold()) {
                        thresholdFlag = true
                        turnGuidesOff()
                    } else if (retreated()) {
                        thresholdFlag = false
                        turnGuidesOn()
                    }

                    if (thresholdFlag != oldFlag) {
                        if (signal == VIBRATION) {
                            vibrator.vibrate(
                                VibrationEffect.createOneShot(
                                    100L,
                                    VibrationEffect.DEFAULT_AMPLITUDE
                                )
                            )
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!brokeThreshold(false) || !switchOn())
                        switchOff()

                    bounceBack()
                    return true
                }
            }
        }
        return true
    }

    private fun getSwipeThreshold(actual: Boolean = true): Float {
        return threshold - direction.sign * thresholdSensitivity * (if (actual) 0 else 1).toFloat()
    }

    private fun move(dx: Float) {
        fabLayout.x = when(direction) {
            RIGHT -> min(max(startingBound, fabLayout.x + dx), getSwipeThreshold())
            else -> max(min(startingBound, fabLayout.x + dx), getSwipeThreshold())
        }
    }

    private fun adjustAlpha() {
        val alpha = when (direction) {
            RIGHT -> min(fabLayout.x.toInt() * 255 / getSwipeThreshold().toInt(), 255)
            else -> min((width - fabLayout.x).toInt() * 255 / (width - getSwipeThreshold()).toInt(), 255)
        }

        fabLayout.circlet?.background?.setColorFilter(
            Color.argb(alpha, 0xb0, 0, 0x20),
            PorterDuff.Mode.DARKEN
        )
    }

    private fun brokeThreshold(countingFlag: Boolean = true): Boolean = (!countingFlag || !thresholdFlag) && when(direction) {
        RIGHT -> fabLayout.x >= getSwipeThreshold(false)
        else -> fabLayout.x < getSwipeThreshold(false)
    }

    private fun retreated(): Boolean = thresholdFlag &&
            direction.sign * fabLayout.x < direction.sign * getSwipeThreshold(false)

    private fun bounceBack() {
        fabLayout.animate()
            .translationX(0f)
            .setDuration(100L)

        startX = 0f
    }

    private fun turnGuidesOn() {
        guidesTimer.start()
    }

    private fun turnGuidesOff() {
        guidesTimer.cancel()
    }

    private var autoReleaseTimer: Timer? = null

    private fun scheduleAutoRelease() {
        cancelAutoRelease()
        if (!indefinite)
            autoReleaseTimer?.schedule(object : TimerTask() {
                override fun run() {
                    post { switchOff(true) }
                }
            }, maxDuration.toLong())
    }

    private fun cancelAutoRelease() {
        autoReleaseTimer?.purge()
        autoReleaseTimer?.cancel()
        autoReleaseTimer = Timer()
    }

    protected open fun switchOn(): Boolean {
        var result = false
        if (!isSwitchedOn && (callback == null || callback!!.onSwiped())) {
            isSwitchedOn = true
            scheduleAutoRelease()
            turnGuidesOff()
            setSwipedSrc()
            result = true
        }
        return result
    }

    open fun switchOff(isAutoRelease: Boolean = false): Boolean {
        var result = false
        if (isSwitchedOn) {
            isSwitchedOn = false
            cancelAutoRelease()
            turnGuidesOn()
            if (signal == VIBRATION) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        100L,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            }
            if (isAutoRelease)
                callback?.onAutoRelease()
            else callback?.onRelease()
            result = true
        }
        fabLayout.circlet?.background?.clearColorFilter()
        setDefaultSrc()
        return result
    }

    private fun setDefaultSrc() {
        fabLayout.fab.setImageDrawable(
            if (defaultSrc != -1) resources.getDrawable(defaultSrc, context.theme)
            else null
        )
    }

    private fun setSwipedSrc() {
        fabLayout.fab.setImageDrawable(
            if (swipedSrc != -1) resources.getDrawable(swipedSrc, context.theme)
            else null
        )
    }

    override fun setOnClickListener(listener: OnClickListener) {
        fabLayout.fab.setOnClickListener(listener)
    }

    fun setOnSwipeListener(listener: OnSwipeListener?) {
        callback = listener
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        turnGuidesOn()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        turnGuidesOff()
    }

    interface OnSwipeListener {
        fun onSwiped(): Boolean
        fun onRelease()
        fun onAutoRelease()
    }
}