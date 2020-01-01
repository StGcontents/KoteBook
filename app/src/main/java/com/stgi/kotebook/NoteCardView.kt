package com.stgi.kotebook

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.ConstraintSet.*
import androidx.core.view.forEach
import com.stgi.rodentia.*
import kotlinx.android.synthetic.main.card_base.view.*

class NoteCardView(context: Context, attributeSet: AttributeSet?): ConstraintLayout(context, attributeSet) {

    private var ellipsized: Boolean = false

    init {
        LayoutInflater.from(context).inflate(R.layout.card_base, this, true)
        background = resources.getDrawable(R.drawable.card_background, context.theme)

        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (height >= getPixels(R.dimen.card_max_height)) {
                if (!ellipsized) {
                    ellipsized = true
                    val set = ConstraintSet()
                    set.clone(this)
                    set.setGuidelineBegin(R.id.ellipsisGuideline, getPixels(R.dimen.card_max_height))
                    set.applyTo(this)

                    gradientSolid.visibility = View.VISIBLE
                    gradient.visibility = View.VISIBLE
                    tvEllipsis.visibility = View.VISIBLE
                }
            } else if (ellipsized) {
                ellipsized = false
                val set = ConstraintSet()
                set.clone(this)
                set.setGuidelineBegin(R.id.ellipsisGuideline,0)
                set.applyTo(this)

                gradientSolid.visibility = View.GONE
                gradient.visibility = View.GONE
                tvEllipsis.visibility = View.GONE
            }
        }
    }

    override fun setBackgroundColor(color: Int) {
        background.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        gradientSolid.background.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        gradient.background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(color, Color.TRANSPARENT))
    }

    fun setTextColor(color: Int) {
        tvTitle.setTextColor(color)
        tvNote.setTextColor(color)
        tvEllipsis.setTextColor(color)
        bpZero.setTextColor(color)
        bpOne.setTextColor(color)
        bpTwo.setTextColor(color)
        bpThree.setTextColor(color)
        bpFour.setTextColor(color)
    }


    inner class ShapeShifter(private val layout: NoteCardView) {
        var title: String? = null
            set(value) {
                field = value?.trimSpaces()
                layout.tvTitle.text = field
            }

        var text: String = ""
            set(value) {
                field = value.trimSpaces()
                layout.tvNote.text = field.trimBullets()
            }

        fun apply() {
            val set = ConstraintSet()
            set.clone(layout)

            set.clear(R.id.tvNote, START)
            set.clear(R.id.tvNote, END)
            set.clear(R.id.tvNote, TOP)
            when (text.indexOfFirstBullet()) {
                in Int.MIN_VALUE until 0 -> {
                    set.connect(R.id.tvNote, START, PARENT_ID, START, getPixels(R.dimen.base_card_margin))
                    set.connect(R.id.tvNote, END, R.id.options, START, getPixels(R.dimen.base_card_margin))
                    if (TextUtils.isEmpty(title))
                        set.connect(R.id.tvNote, TOP, PARENT_ID, TOP, getPixels(R.dimen.base_card_margin))
                    else
                        set.connect(R.id.tvNote, TOP, R.id.textGuideline, TOP)
                    layout.forEach { v ->
                        if (v is BulletPointView) {
                            v.visibility = View.GONE
                        }
                    }
                }
                0 -> addBullets(set, true)
                else -> {
                    set.connect(R.id.tvNote, START, PARENT_ID, START, getPixels(R.dimen.base_card_margin))
                    set.connect(R.id.tvNote, END, R.id.options, START, getPixels(R.dimen.base_card_margin))
                    if (TextUtils.isEmpty(title))
                        set.connect(R.id.tvNote, TOP, PARENT_ID, TOP, getPixels(R.dimen.base_card_margin))
                    else
                        set.connect(R.id.tvNote, TOP, R.id.textGuideline, TOP)

                    addBullets(set)
                }
            }

            set.clear(R.id.tvTitle, START)
            set.clear(R.id.tvTitle, END)
            set.clear(R.id.tvTitle, TOP)
            if (!TextUtils.isEmpty(title)) {
                        set.connect(R.id.tvTitle, START, PARENT_ID, START, getPixels(R.dimen.base_card_margin))
                set.connect(R.id.tvTitle, TOP, PARENT_ID, TOP, getPixels(R.dimen.base_card_margin))
                set.connect(R.id.tvTitle, END, R.id.options, START, getPixels(R.dimen.base_card_margin))
            }
            set.applyTo(layout)
        }

        private fun addBullets(set: ConstraintSet, stickToCeiling: Boolean = false) {
            val bullets = text.split(Regex(BULLET_POINT_SPLIT)).filter { s ->
                s.startsWith(BULLET_POINT_EMPTY) || s.startsWith(BULLET_POINT_FULL)
            }

            if (stickToCeiling) {
                if (TextUtils.isEmpty(title))
                    set.connect(R.id.bpZero, TOP, PARENT_ID, TOP, getPixels(R.dimen.base_card_margin))
                else
                    set.connect(R.id.bpZero, TOP, R.id.tvTitle, BOTTOM, getPixels(R.dimen.base_card_margin))
            } else {
                set.connect(R.id.bpZero, TOP, R.id.tvNote, BOTTOM, getPixels(R.dimen.base_card_margin))
            }

            var i = 0
            while (i < bullets.size && i < if (stickToCeiling) 5 else 3) {
                initBullet(findViewWithTag(i.toString()), bullets[i])
                i++
            }
        }

        private fun initBullet(v: BulletPointView, it: String) {
            v.visibility = View.VISIBLE
            v.setIsChecked(it[0] == BULLET_POINT_FULL)
            v.setText(it.substring(1).trimSpaces())
            v.setMaxLines(2)
            v.setIsChecked(it[0] == BULLET_POINT_FULL)
        }


        private fun String.trimBullets(): String {
            val str = trimSpaces()
            val index = str.indexOfFirstBullet()
            return when (index) {
                0 -> ""
                in 1..Int.MAX_VALUE -> str.substring(0, index)
                else -> str
            }.trimSpaces()
        }

        private fun String.indexOfFirstBullet(): Int = trimSpaces().indexOfFirst { c -> c == BULLET_POINT_FULL || c == BULLET_POINT_EMPTY }
    }

    fun shapeShift() = ShapeShifter(this)
}