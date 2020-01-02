package com.stgi.kotebook

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.ConstraintSet.*
import androidx.core.view.isVisible
import com.stgi.rodentia.*
import kotlinx.android.synthetic.main.card_base.view.*

class NoteCardView(context: Context, attributeSet: AttributeSet?): ConstraintLayout(context, attributeSet) {

    private val startingSet: ConstraintSet
    private val bulletsIds = intArrayOf(-1, -1, -1, -1, -1)

    var listener: OnCheckedChangeListener? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.card_base, this, true)
        background = resources.getDrawable(R.drawable.card_background, context.theme)

        startingSet = ConstraintSet()
        startingSet.clone(this)

        var lastId = -1
        val index = indexOfChild(tvNote) + 1
        for (i: Int in 0..4) {
            val view = BulletPointView(context, null, false)
            view.id = View.generateViewId()
            bulletsIds[i] = view.id
            view.tag = i
            view.visibility = View.GONE
            view.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
                listener?.onCheckedChanged(isChecked, i)
            })

            startingSet.connect(view.id, START, PARENT_ID, START, getPixels(R.dimen.base_card_margin))
            startingSet.connect(view.id, END, PARENT_ID, END, getPixels(R.dimen.base_card_margin))
            if (lastId != -1)
                startingSet.connect(view.id, TOP, lastId, BOTTOM)
            startingSet.constrainMinHeight(view.id, 0)
            startingSet.constrainMaxHeight(bulletsIds[i], getPixels(R.dimen.bullet_point_height))
            addView(view, index + i)

            lastId = view.id
        }
        startingSet.applyTo(this)
    }

    private fun adjustEllipsis() {
        if (height >= getPixels(R.dimen.card_max_height)) {
            val set = ConstraintSet()
            set.clone(this)
            set.setGuidelineBegin(R.id.ellipsisGuideline, getPixels(R.dimen.card_max_height))
            if (findViewWithTag<BulletPointView>(0).isVisible)
                set.clear(R.id.tvNote, BOTTOM)
            else {
                for (i in bulletsIds.lastIndex downTo 0) {
                    val view = findViewById<BulletPointView>(bulletsIds[i])
                    if (!TextUtils.isEmpty(view.getText())) {
                        set.clear(view.id, BOTTOM)
                        break
                    }
                }
            }
            set.applyTo(this)

            gradient.visibility = View.VISIBLE
            tvEllipsis.visibility = View.VISIBLE
        } else {
            val set = ConstraintSet()
            set.clone(this)
            set.setGuidelineBegin(R.id.ellipsisGuideline,0)
            set.applyTo(this)

            gradient.visibility = View.GONE
            tvEllipsis.visibility = View.GONE
        }
    }

    override fun setBackgroundColor(color: Int) {
        background.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)

        val optionsGradient = GradientDrawable(
            GradientDrawable.Orientation.RIGHT_LEFT,
            intArrayOf(color, color, color, color, Color.TRANSPARENT)).apply {
            cornerRadius = getDimen(R.dimen.base_card_margin)
        }
        options.background = optionsGradient

        val ellipsisGradient = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(color, color, Color.TRANSPARENT)).apply {
            cornerRadius = getDimen(R.dimen.base_card_margin)
        }
        gradient.background = ellipsisGradient
    }

    fun setTextColor(color: Int) {
        tvTitle.setTextColor(color)
        tvNote.setTextColor(color)
        tvEllipsis.setTextColor(color)
        for (id in bulletsIds) {
            findViewById<BulletPointView>(id).setTextColor(color)
        }
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
            set.clone(startingSet)

            for (id in bulletsIds) {
                findViewById<BulletPointView>(id).setText("")
            }

            set.clear(R.id.tvNote, TOP)
            set.clear(R.id.tvNote, BOTTOM)
            when (text.indexOfFirstBullet()) {
                -1 -> {
                    if (TextUtils.isEmpty(title))
                        set.connect(R.id.tvNote, TOP, PARENT_ID, TOP, getPixels(R.dimen.base_card_margin))
                    else set.connect(R.id.tvNote, TOP, R.id.textGuideline, TOP)
                    set.connect(R.id.tvNote, BOTTOM, PARENT_ID, BOTTOM, getPixels(R.dimen.base_card_margin))
                }
                0 -> addBullets(set, true)
                else -> {
                    if (TextUtils.isEmpty(title))
                        set.connect(R.id.tvNote, TOP, PARENT_ID, TOP, getPixels(R.dimen.base_card_margin))
                    else
                        set.connect(R.id.tvNote, TOP, R.id.textGuideline, TOP)

                    addBullets(set)
                }
            }

            set.clear(R.id.tvTitle, TOP)
            if (!TextUtils.isEmpty(title)) {
                set.connect(R.id.tvTitle, TOP, PARENT_ID, TOP, getPixels(R.dimen.base_card_margin))
            }
            set.applyTo(layout)
            post { adjustEllipsis() }
        }

        private fun addBullets(set: ConstraintSet, stickToCeiling: Boolean = false) {
            val bullets = text.split(Regex(BULLET_POINT_SPLIT)).filter { s ->
                s.startsWith(BULLET_POINT_EMPTY) || s.startsWith(BULLET_POINT_FULL)
            }

            if (stickToCeiling) {
                if (TextUtils.isEmpty(title))
                    set.connect(bulletsIds[0], TOP, PARENT_ID, TOP, getPixels(R.dimen.base_card_margin))
                else
                    set.connect(bulletsIds[0], TOP, R.id.tvTitle, BOTTOM, getPixels(R.dimen.base_card_margin))
            } else {
                set.connect(bulletsIds[0], TOP, R.id.tvNote, BOTTOM)
            }

            var i = 0
            while (i < bullets.size && i < if (stickToCeiling) 5 else 3) {
                set.constrainMinHeight(bulletsIds[i], getPixels(R.dimen.bullet_point_height))
                initBullet(findViewById(bulletsIds[i]), bullets[i])
                i++
            }
            set.connect(bulletsIds[i - 1], BOTTOM, PARENT_ID, BOTTOM, getPixels(R.dimen.base_card_margin))
        }

        private fun initBullet(v: BulletPointView, bulletText: String) {
            v.visibility = View.VISIBLE
            v.setIsChecked(bulletText[0] == BULLET_POINT_FULL)
            v.setText(bulletText.substring(1).trimSpaces())
            v.setMaxLines(1)
            v.setIsChecked(bulletText[0] == BULLET_POINT_FULL)
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

    interface OnCheckedChangeListener {
        fun onCheckedChanged(isChecked: Boolean, position: Int)
    }
}