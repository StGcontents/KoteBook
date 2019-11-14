package com.stgi.kotebook

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.ConstraintSet.*
import androidx.core.view.forEach
import androidx.core.view.setMargins
import kotlin.math.max

class VisualizerView(context: Context, attributeSet: AttributeSet): ConstraintLayout(context, attributeSet) {
    init {
        val size = resources.getDimension(R.dimen.visualizer_item_size).toInt()

        val set = ConstraintSet()
        set.clone(this)
        for (i in 0..7) {
            val view = VisualizerItemView(context)
            view.id = View.generateViewId()
            addView(view, i)
            when (i) {
                0 -> set.connect(view.id, START, PARENT_ID, START, size)
                else -> set.connect(view.id, START, getChildAt(i - 1).id, END, size)
            }

            set.constrainWidth(view.id, size)
            set.constrainHeight(view.id, size)
            set.connect(view.id, TOP, PARENT_ID, TOP)
            set.connect(view.id, BOTTOM, PARENT_ID, BOTTOM)
        }
        set.applyTo(this)
    }

    fun updateAmplitude(amplitude: Int) {
        val set = ConstraintSet()
        set.clone(this)
        for (i in childCount - 1 downTo 0) {
            val view = getChildAt(i)
            when (i) {
                0 -> set.constrainHeight(view.id, max(resources.getDimension(R.dimen.fab_size).toInt() * amplitude / 32767, resources.getDimension(R.dimen.visualizer_item_size).toInt()))
                else -> set.constrainHeight(view.id, getChildAt(i - 1).height)
            }

        }
        set.applyTo(this)
    }

    fun reset() {
        val set = ConstraintSet()
        set.clone(this)
        forEach { v -> set.constrainHeight(v.id, resources.getDimension(R.dimen.visualizer_item_size).toInt()) }
        set.applyTo(this)
    }

    inner class VisualizerItemView(context: Context): View(context) {
        init {
            val size = resources.getDimension(R.dimen.visualizer_item_size).toInt()
            val params = ConstraintLayout.LayoutParams(size, 0)
            params.setMargins(size)
            layoutParams = params

            setBackgroundColor(Color.WHITE)
        }
    }
}