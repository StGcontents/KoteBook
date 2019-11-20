package com.stgi.kotebook

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast
import kotlinx.android.synthetic.main.layout_clock.view.*
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.pow

class ClockView(context: Context, attributeSet: AttributeSet?): RelativeLayout(context, attributeSet), View.OnTouchListener {
    private val minutesPointer: View
    private val hourPointer: View

    init {
        val clock = LayoutInflater.from(context).inflate(R.layout.layout_clock, this, false)
        addView(clock)

        minutesPointer = clock.minutesPointer
        hourPointer = clock.hourPointer

        minutesPointer.setOnTouchListener(this)
        hourPointer.setOnTouchListener(this)
    }

    var movingView: View? = null

    override fun onTouch(v: View?, ev: MotionEvent?): Boolean {
        if (!isEnabled) return false

        if (ev != null) {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> movingView = v
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        if (!isEnabled) return false

        if (ev != null) {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (movingView == null) {
                        val coordinates = Coordinates(ev)
                        movingView = if (coordinates.isHourHalf) hourPointer else minutesPointer
                        movingView?.rotation = coordinates.getTickedGamma().toFloat()
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    movingView?.rotation = Coordinates(ev)
                        .getTickedGamma(if (movingView?.id == hourPointer.id) Tick.HOURS else Tick.MINUTES)
                        .toFloat()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    movingView = null
                    performClick()
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        //TODO ?
        return super.performClick()
    }

    fun getTimeMessage() = "Alarm set to ${getHour()}:${getMinute()}"

    fun getHour() = (hourPointer.rotation / Tick.HOURS.angle).toInt()
    fun getMinute() = (minutesPointer.rotation / Tick.MINUTES.angle).toInt()

    fun setHour(hour: Int) {
        if (0 <= hour && hour < Tick.HOURS.ticks) {
            hourPointer.rotation = (hour * Tick.HOURS.angle).toFloat()
        }
    }
    fun setMinute(minute: Int) {
        if (0 <= minute && minute < Tick.MINUTES.ticks) {
            minutesPointer.rotation = (minute * Tick.MINUTES.angle).toFloat()
        }
    }

    enum class Quadrant { FIRST, SECOND, THIRD, FOURTH }

    enum class Tick(val ticks: Int, val angle: Int) {
        MINUTES(60, 6),
        HOURS(12, 30),
        HOURS_24(24, 15)
    }

    private inner class Coordinates(ev: MotionEvent) {
        private val c1: Float
        private val c2: Float
        private val i: Float
        val isHourHalf: Boolean
        private val quadrant: Quadrant
        val gamma: Int
            get() {
                return when (quadrant) {
                    Quadrant.SECOND -> 180 - field
                    Quadrant.THIRD -> 180 + field
                    Quadrant.FOURTH -> 360 - field
                    else -> field
                } % 360
            }

        init {
            val origin = floatArrayOf(left + width / 2f, top + height / 2f)
            val touchPoint = floatArrayOf(left + ev.x, top + ev.y)
            c1 = abs(touchPoint[1] - origin[1])
            c2 = abs(touchPoint[0] - origin[0])
            i = (c1.pow(2) + c2.pow(2)).pow(0.5f)

            isHourHalf = i < height / 4f

            quadrant = when {
                touchPoint[0] > origin[0] && touchPoint[1] > origin[1] -> Quadrant.SECOND
                touchPoint[0] < origin[0] && touchPoint[1] > origin[1] -> Quadrant.THIRD
                touchPoint[0] < origin[0] && touchPoint[1] < origin[1] -> Quadrant.FOURTH
                else -> Quadrant.FIRST
            }

            gamma = (asin((c2 / i).toDouble()) * 180 / Math.PI).toInt()
        }

        fun getTickedGamma(tick: Tick = if (isHourHalf) Tick.HOURS else Tick.MINUTES): Int {
            return (gamma / tick.angle) * tick.angle
        }
    }
}