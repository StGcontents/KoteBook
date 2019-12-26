package com.stgi.kotebook

import android.content.Context
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.ConstraintSet.*
import java.util.*

class ClockButton(context: Context, attributeSet: AttributeSet?): ConstraintLayout(context, attributeSet) {
    private val clock: ClockView
    private val amPmButton: ImageButton

    init {
        background = resources.getDrawable(R.drawable.circle, context.theme)
        foreground = resources.getDrawable(R.drawable.circular_ripple, context.theme)

        clock = ClockView(context, null)
        clock.id = View.generateViewId()
        clock.layoutParams = LayoutParams(
            resources.getDimension(R.dimen.no_dimen).toInt(),
            resources.getDimension(R.dimen.no_dimen).toInt()
        )
        clock.isEnabled = false

        amPmButton = ImageButton(context)
        amPmButton.id = View.generateViewId()
        amPmButton.layoutParams = LayoutParams(
            resources.getDimension(R.dimen.no_dimen).toInt(),
            resources.getDimension(R.dimen.no_dimen).toInt()
        )
        amPmButton.setImageDrawable(resources.getDrawable(R.drawable.am_pm, context.theme))
        amPmButton.isSelected = true
        amPmButton.scaleType = ImageView.ScaleType.FIT_CENTER
        amPmButton.rotation = 45f
        amPmButton.background = resources.getDrawable(R.drawable.circle, context.theme)
        amPmButton.foreground = resources.getDrawable(R.drawable.circular_ripple, context.theme)
        amPmButton.setOnClickListener {
            amPmButton.isSelected = !amPmButton.isSelected
            if (!amPmButton.isSelected) amPmButton.rotation = 30f
        }
        post { amPmButton.visibility = View.GONE }

        val paddingView = View(context)
        paddingView.id = View.generateViewId()
        paddingView.visibility = View.INVISIBLE

        addView(clock)
        addView(amPmButton)
        addView(paddingView)

        val set = ConstraintSet()

        set.connect(clock.id, TOP, PARENT_ID, TOP)
        set.connect(clock.id, BOTTOM, PARENT_ID, BOTTOM)
        set.connect(clock.id, START, PARENT_ID, START)
        set.connect(clock.id, END, PARENT_ID, END)
        set.constrainPercentWidth(clock.id, 0.7f)
        set.constrainPercentHeight(clock.id, 0.7f)

        set.connect(amPmButton.id, BOTTOM, paddingView.id, TOP)
        set.connect(amPmButton.id, END, paddingView.id, START)
        set.constrainPercentWidth(amPmButton.id, 0.2f)
        set.constrainPercentHeight(amPmButton.id, 0.2f)

        set.connect(paddingView.id, BOTTOM, PARENT_ID, BOTTOM)
        set.connect(paddingView.id, END, PARENT_ID, END)
        set.constrainPercentWidth(paddingView.id, 0.025f)
        set.constrainPercentHeight(paddingView.id, 0.025f)

        set.applyTo(this)

        post{ setHour(2) }
        post{ setMinute(0) }
    }

    fun asClock() {
        isEnabled = false
        clock.isEnabled = true

        showAmPmButton()
    }

    fun asButton() {
        isEnabled = true
        clock.isEnabled = false

        hideAmPmButton()
    }

    fun showAmPmButton(immediate: Boolean = false) {
        if (immediate) {
            amPmButton.apply {
                alpha = 1f
                visibility = View.VISIBLE
            }
        } else {
            amPmButton.apply {
                visibility = View.VISIBLE
                animate().apply {
                    cancel()
                    alpha(1f)
                    duration = 150L
                    start()
                }
            }
        }
    }

    fun hideAmPmButton(immediate: Boolean = false) {
        if (immediate) {
            amPmButton.apply {
                alpha = 0f
                visibility = View.GONE
            }
        } else {
            amPmButton.apply {
                animate().apply {
                    cancel()
                    alpha(0f)
                    duration = 150L
                    withEndAction { amPmButton.visibility = View.GONE }
                    start()
                }
            }
        }
    }

    fun getHour() = clock.getHour() + (if (isAm()) 0 else 12)
    fun getMinute() = clock.getMinute()

    fun setHour(hour: Int) {
        if (0 <= hour && hour < ClockView.Tick.HOURS_24.ticks) {
            if (hour >= ClockView.Tick.HOURS.ticks) {
                clock.setHour(hour - ClockView.Tick.HOURS.ticks)
                amPmButton.isSelected = false
            } else {
                clock.setHour(hour)
                amPmButton.isSelected = true
            }
        }
    }
    fun setMinute(minute: Int) {
        clock.setMinute(minute)
    }

    fun isAm() = amPmButton.isSelected

    fun getTime(): Long = getHour() * 360000L + getMinute() * 60000L
    fun getTimeMessage(): String = clock.getTimeMessage() + " " + (if (isAm()) "AM" else "PM")
}