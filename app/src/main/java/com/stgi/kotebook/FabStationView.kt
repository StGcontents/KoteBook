package com.stgi.kotebook

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.ConstraintSet.*
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.transition.ChangeBounds
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.stgi.rodentia.RecordingSwipeButton
import com.stgi.rodentia.SwipeButton
import kotlinx.android.synthetic.main.date_picker_mask.view.*
import kotlinx.android.synthetic.main.fragment_edit.view.*
import kotlinx.android.synthetic.main.layout_fab_station.view.*
import kotlin.math.max

const val STATUS_INIT = 1
const val STATUS_ANNOTATE = 2
const val STATUS_RECORDING = 4
const val STATUS_SAVE_REC = 8
const val STATUS_EDIT = 16
const val STATUS_CONFIRM = 32
const val STATUS_CLOCK = 64

private const val ANGLE = 90.0f
private const val BASE_ROW = 4

class FabStationView(context: Context, attributeSet: AttributeSet): ConstraintLayout(context, attributeSet),
    View.OnTouchListener {

    var strategy: OnClickStrategy? = null

    var isCircle: Boolean = true

    var paletteCallback: OnPaletteItemTouchedListener? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.layout_fab_station, this, true)

        setOnTouchListener { _, ev -> if (ev!!.action == KeyEvent.ACTION_UP) strategy?.collapseFanIfNeeded(); false }
        paletteMask.setOnTouchListener { _, _ -> true }
        mask.setOnTouchListener{ _, ev -> if (ev!!.action == KeyEvent.ACTION_UP)  strategy?.onBackPressed(); true }

        val set = ConstraintSet().apply { clone(this@FabStationView) }
        palette
            .forEach { color ->
                val paletteItem = LayoutInflater.from(context)
                    .inflate(R.layout.palette_item_view, this@FabStationView, false).apply {
                        id = View.generateViewId()
                        tag = color
                        background = context.getDrawable(R.drawable.circle)
                        background.setColorFilter(
                            context.getColor(color),
                            PorterDuff.Mode.SRC_ATOP
                        )
                        elevation = resources.getDimension(R.dimen.small_fab_elevation)
                        val size = resources.getDimensionPixelSize(R.dimen.small_fab_size)
                        layoutParams = LayoutParams(size, size)
                    }

                paletteItem.setOnTouchListener(this)

                set.constrainWidth(paletteItem.id, getNoDimen())
                set.constrainHeight(paletteItem.id, getNoDimen())
                set.constrainCircle(paletteItem.id, fanFab.id, 0, 0f)
                addView(paletteItem, indexOfChild(fanFab) - 1)
            }
        set.applyTo(this)
    }

    fun toSquare() {
        if (isCircle) {
            isCircle = false
            (bgView.background as TransitionDrawable).apply {
                startTransition(150)
            }
        }
    }

    fun toCircle() {
        if (!isCircle) {
            isCircle = true
            (bgView.background as TransitionDrawable).apply {
                reverseTransition(150)
            }
        }
    }

    override fun onTouch(view: View?, ev: MotionEvent?): Boolean {
        if (ev != null && view != null && paletteCallback != null) {
            if (ev.action == MotionEvent.ACTION_MOVE ||
                ev.action == MotionEvent.ACTION_DOWN) {
                if (0 < ev.x && ev.x < view.width &&
                    0 < ev.y && ev.y < view.height) {
                    paletteCallback?.onColorSelected(view.tag as Int)
                } else {
                    val absoluteX = view.x + ev.x
                    val absoluteY = view.y + ev.y
                    forEach {
                        if (it != view && it.tag is Int && it.tag as Int in palette) {
                            if (it.x < absoluteX && absoluteX < (it.x + it.width) &&
                                it.y < absoluteY && absoluteY < (it.y + it.height)) {
                                paletteCallback?.onColorSelected(it.tag as Int)
                                return@forEach
                            }
                        }
                    }
                }
            } else if (ev.action == KeyEvent.ACTION_UP) {
                paletteCallback?.onColorConfirmed()
                strategy?.collapseFanIfNeeded()
            }
        }
        return true
    }

    fun injectStrategy(strategy: OnClickStrategy) {
        this.strategy = strategy
        this.strategy?.initialize(this)
    }

    abstract class OnClickStrategy(val activity: MainActivity): OnClickListener, SwipeButton.OnSwipeListener {
        protected lateinit var fabStation: FabStationView

        abstract fun getPrimaryDrawable(): Drawable
        open fun getSecondaryDrawable(): Drawable? = null
        open fun getTertiaryDrawable(): Drawable? = null

        open fun persistStatus() {
            activity.getPreferences(Context.MODE_PRIVATE).edit().apply {
                putInt(LAST_STATUS, getStatus())
                apply()
            }
        }

        open fun initialize(view: FabStationView) {
            fabStation = view

            fabStation.primaryFab.setImageDrawable(getPrimaryDrawable())
            fabStation.primaryFab.setOnClickListener(this)

            fabStation.secondaryFab.setImageDrawable(getSecondaryDrawable())
            fabStation.secondaryFab.setOnClickListener(this)

            fabStation.tertiaryFab.setImageDrawable(getTertiaryDrawable())
            fabStation.tertiaryFab.setOnClickListener(this)

            (fabStation.swipeFab as RecordingSwipeButton).setOnSwipeListener(this)

            fabStation.clockFab.setOnClickListener(this)
            fabStation.datePickerMask.setOnClickListener(this)

            fabStation.fanFab.setOnClickListener(this)

            shapeStation(SetBuilder(fabStation))
        }

        open fun shapeStation(builder: SetBuilder) { }

        open fun isDismissible() = true

        open fun onBackPressed(): Boolean {
            activity.pushStatus(STATUS_INIT)
            return false
        }

        override fun onClick(v: View?) {
            when (v?.id) {
                fabStation.primaryFab.id -> onPrimaryClick()
                fabStation.secondaryFab.id -> onSecondaryClick()
                fabStation.tertiaryFab.id -> onTertiaryClick()
                fabStation.clockFab.id -> onClockClicked()
                fabStation.datePickerMask.id -> onDatePickerClicked()
                fabStation.fanFab.id -> onFanClicked()
            }
        }

        abstract fun onPrimaryClick()
        open fun onSecondaryClick() { }
        open fun onTertiaryClick() { }

        open fun onClockClicked() { }
        open fun onDatePickerClicked() { }

        open fun onFanClicked() {
            fabStation.fanFab.isSelected =
                if (fabStation.fanFab.isSelected) {
                    SetBuilder(fabStation).collapseFan().apply()
                    false
                } else {
                    SetBuilder(fabStation).expandFan().apply()
                    true
                }
        }

        fun collapseFanIfNeeded() {
            if (fabStation.fanFab.isSelected) onFanClicked()
        }

        abstract fun getStatus(): Int

        override fun onSwiped(): Boolean = false
        override fun onRelease() { }
        override fun onAutoRelease() { }
    }

    class SetBuilder(private val station: FabStationView) {
        private val set: ConstraintSet = ConstraintSet()
        private var chainedStart: ChainedRunnable? = null
        private var chainedEnd: ChainedRunnable? = null

        init {
            set.clone(station)
        }

        /**
         * PRIMARY FAB
         */
        fun showPrimary(): SetBuilder {
            val id = station.primaryFab.id
            //set.clear(id)
            set.connect(id, BOTTOM, margin = station.getPixels(R.dimen.fab_margin))
            set.connect(id, END, margin = station.getPixels(R.dimen.fab_margin))
            return this
        }

        fun hidePrimary(): SetBuilder {
            val id = station.primaryFab.id
            //set.clear(id)
            set.connect(id, TOP, PARENT_ID, BOTTOM)
            set.connect(id, END)
            return this
        }

        fun expandPrimary(): SetBuilder {
            chainedStart = object: ChainedRunnable(chainedStart) {
                override fun runInternal() {
                    station.toSquare()
                }
            }
            val id = station.bgView.id
            set.connect(id, START)
            set.connect(id, END)
            set.connect(id, BOTTOM)
            set.connect(id, TOP, station.first_floor_guideline.id, BOTTOM)
            return this
        }

        fun retractPrimary(): SetBuilder {
            chainedStart = object: ChainedRunnable(chainedStart) {
                override fun runInternal() {
                    station.toCircle()
                }
            }
            val id = station.bgView.id
            val primaryId = station.primaryFab.id
            set.connect(id, START, primaryId, START)
            set.connect(id, END, primaryId, END)
            set.connect(id, BOTTOM, primaryId, BOTTOM)
            set.connect(id, TOP, primaryId, TOP)
            return hideInputs()
        }


        /**
         * SECONDARY FAB
         */
        fun showSecondary(): SetBuilder {
            val id = station.secondaryFab.id
            set.constrainPercentWidth(id, 1f)
            set.constrainPercentHeight(id, 1f)
            return this
        }

        fun hideSecondary(): SetBuilder {
            val id = station.secondaryFab.id
            set.constrainPercentWidth(id, 0f)
            set.constrainPercentHeight(id, 0f)
            return hideInputs()
        }


        /**
         * TERTIARY FAB
         */
        fun showTertiary(): SetBuilder {
            val id = station.tertiaryFab.id
            set.clear(id)
            set.connect(id, BOTTOM, station.primaryFab.id, TOP, station.getPixels(R.dimen.fab_margin))
            set.connect(id, END, station.primaryFab.id, END)
            set.constrainWidth(id, station.getPixels(R.dimen.fab_size))
            set.constrainHeight(id, station.getPixels(R.dimen.fab_size))
            return this
        }

        fun hideTertiary(): SetBuilder {
            val id = station.tertiaryFab.id
            set.clear(id)
            set.connect(id, BOTTOM, station.primaryFab.id, BOTTOM)
            set.connect(id, END, station.primaryFab.id, END)
            set.constrainWidth(id, station.getPixels(R.dimen.fab_size))
            set.constrainHeight(id, station.getPixels(R.dimen.fab_size))
            return this
        }


        /**
         * SWIPE FAB
         */
        fun showSwipe(): SetBuilder {
            val id = station.swipeFab.id
            set.clear(id, TOP)
            set.connect(id, BOTTOM, margin = station.getPixels(R.dimen.fab_margin))
            return this
        }

        fun hideSwipe(): SetBuilder {
            val id = station.swipeFab.id
            set.clear(id, BOTTOM)
            set.connect(id, TOP, PARENT_ID, BOTTOM)
            return this
        }


        /**
         * CLOCK FAB
         */
        fun showClock(): SetBuilder {
            chainedStart = object : ChainedRunnable(chainedStart) {
                override fun runInternal() {
                    station.clockFab.asButton()
                    if (station.mask.isVisible) {
                        station.mask.animate().apply {
                            cancel()
                            duration = 100L
                            alpha(0f)
                            withEndAction { station.mask.visibility = GONE }
                            start()
                        }
                    }
                }
            }

            val id = station.clockFab.id
            set.clear(id)
            set.connect(id, BOTTOM, station.tertiaryFab.id, TOP, station.getPixels(R.dimen.fab_margin))
            set.connect(id, END, station.primaryFab.id, END)
            set.constrainWidth(id, station.getPixels(R.dimen.fab_size))
            set.constrainHeight(id, station.getPixels(R.dimen.fab_size))
            return this
        }

        fun hideClock(): SetBuilder {
            val id = station.clockFab.id
            set.clear(id)
            set.connect(id, BOTTOM, station.primaryFab.id, BOTTOM)
            set.connect(id, END, station.primaryFab.id, END)
            set.constrainWidth(id, station.getPixels(R.dimen.fab_size))
            set.constrainHeight(id, station.getPixels(R.dimen.fab_size))
            return this
        }

        fun expandClock(): SetBuilder {
            chainedStart = object : ChainedRunnable(chainedStart) {
                override fun runInternal() {
                    if (!station.mask.isVisible) {
                        station.mask.alpha = 0f
                        station.mask.visibility = View.VISIBLE
                        station.mask.animate().apply {
                            cancel()
                            duration = 100L
                            alpha(1f)
                            start()
                        }
                    }
                }
            }
            chainedEnd = object : ChainedRunnable(chainedEnd) {
                override fun runInternal() {
                    station.clockFab.asClock()
                }
            }

            station.tertiaryFab.elevation = 0f

            val clockId = station.clockFab.id
            set.clear(clockId)
            set.connect(clockId, TOP)
            set.connect(clockId, BOTTOM)
            set.connect(clockId, START)
            set.connect(clockId, END)
            set.constrainWidth(clockId, station.getPixels(R.dimen.bigger_play_button_size))
            set.constrainHeight(clockId, station.getPixels(R.dimen.bigger_play_button_size))

            return this
        }

        fun minimizeClock(): SetBuilder {
            chainedEnd = object : ChainedRunnable(chainedEnd) {
                override fun runInternal() {
                    station.clockFab.asButton()
                }
            }

            val clockId = station.clockFab.id
            set.clear(clockId, START)
            set.clear(clockId, END)
            set.connect(clockId, START, station.right_side_guideline.id, END)
            set.constrainWidth(clockId, station.getPixels(R.dimen.fab_size))
            set.constrainHeight(clockId, station.getPixels(R.dimen.fab_size))

            return this
        }

        fun hideDatePicker(minimize: Boolean = false): SetBuilder {
            chainedStart = object : ChainedRunnable(chainedStart) {
                override fun runInternal() {
                    station.datePickerMask.alpha = 1f
                    station.datePickerMask.visibility = View.VISIBLE
                    station.datePicker.visibility = View.INVISIBLE
                }
            }
            chainedEnd = object : ChainedRunnable(chainedEnd) {
                override fun runInternal() {
                    station.datePicker.visibility = View.GONE
                }
            }

            val calendarId = station.datePickerMask.id
            set.clear(calendarId, START)
            set.clear(calendarId, END)
            set.connect(calendarId, END, if (minimize) station.left_side_guideline.id else PARENT_ID, START)
            set.constrainWidth(calendarId, station.getPixels(R.dimen.date_picker_mask_size))
            set.constrainHeight(calendarId, station.getPixels(R.dimen.date_picker_mask_size))

            return this
        }

        fun expandDatePicker(): SetBuilder {
            chainedStart = object : ChainedRunnable(chainedStart) {
                override fun runInternal() {
                    station.datePickerMask.visibility = View.VISIBLE
                    station.datePickerMask.alpha = 1f
                    station.datePicker.visibility = View.INVISIBLE
                }
            }
            chainedEnd = object : ChainedRunnable(chainedEnd) {
                override fun runInternal() {
                    station.datePicker.visibility = View.VISIBLE
                    station.datePickerMask.animate().apply {
                        cancel()
                        alpha(0f)
                        duration = 100L
                        withEndAction { station.datePickerMask.visibility = View.INVISIBLE }
                        start()
                    }
                }
            }

            val calendarMaskId = station.datePickerMask.id
            val calendarId = station.datePicker.id
            set.clear(calendarMaskId)
            set.connect(calendarMaskId, START, calendarId, START)
            set.connect(calendarMaskId, END, calendarId, END)
            set.connect(calendarMaskId, TOP, calendarId, TOP)
            set.connect(calendarMaskId, BOTTOM, calendarId, BOTTOM)
            set.constrainWidth(calendarMaskId, station.datePicker.width)
            set.constrainHeight(calendarMaskId, station.datePicker.height)

            return this
        }

        fun minimizeDatePicker(): SetBuilder = hideDatePicker(true)

        /**
         * FAN FAB
         */
        fun showFan(): SetBuilder {
            val id = station.fanFab.id
            set.clear(id, END)
            set.connect(id, START, margin = station.getPixels(R.dimen.fab_margin))
            return this
        }

        fun hideFan(): SetBuilder {
            val id = station.fanFab.id
            set.clear(id, START)
            set.connect(id, END, PARENT_ID, START)
            return this
        }

        fun expandFan(): SetBuilder {
            chainedStart = object: ChainedRunnable(chainedStart) {
                override fun runInternal() {
                    station.post { station.paletteMask.visibility = View.VISIBLE }
                }
            }

            var maskSize = 0

            palette
                .forEach { color ->
                    station.findViewWithTag<View>(color)?.apply {
                        val index = palette.indexOf(color)
                        var rowCounter = 0
                        var offset = 0
                        while (index >= offset) offset += BASE_ROW + rowCounter++
                        offset -= BASE_ROW + rowCounter-- //adjustment
                        val angle = ANGLE * (index - offset - 1) / (BASE_ROW + rowCounter - 1).toFloat()

                        val size = getPixels(R.dimen.small_fab_size)
                        val radius = (resources.getDimension(R.dimen.fab_size) / 2f +
                                (size * 1.5f * (rowCounter + 1))).toInt()

                        set.constrainWidth(id, size)
                        set.constrainHeight(id, size)
                        set.constrainCircle(id, station.fanFab.id, radius, angle)

                        maskSize = max(maskSize, radius)
                    }
                }

            set.constrainWidth(station.paletteMask.id, maskSize + station.fanFab.width)
            set.constrainHeight(station.paletteMask.id, maskSize + station.fanFab.height)
            return this
        }

        fun collapseFan(): SetBuilder {
            chainedStart = object: ChainedRunnable(chainedStart) {
                override fun runInternal() {
                    station.post { station.paletteMask.visibility = View.GONE }
                }
            }
            palette
                .forEach { color ->
                    station.findViewWithTag<View>(color)?.apply {
                        set.constrainWidth(id, station.getNoDimen())
                        set.constrainHeight(id, station.getNoDimen())
                        set.constrainCircle(id, station.fanFab.id, 0, 0f)
                    }
                }

            set.constrainWidth(station.paletteMask.id, 0)
            set.constrainHeight(station.paletteMask.id, 0)
            return this
        }


        /**
         * INPUTS
         */
        fun hideInputs(): SetBuilder {
            chainedStart = object: ChainedRunnable(chainedStart) {
                override fun runInternal() {
                    station.post {
                        station.editorFrame.visibility = View.GONE
                        station.audioEt.visibility = View.GONE
                    }
                }
            }
            return this
        }

        fun showTextInput(): SetBuilder {
            chainedEnd = object: ChainedRunnable(chainedEnd) {
                override fun runInternal() {
                    station.post { station.editorFrame.visibility = View.VISIBLE }
                }
            }
            return hideAudioInput()
        }

        fun hideTextInput(): SetBuilder {
            chainedStart = object: ChainedRunnable(chainedStart) {
                override fun runInternal() {
                    station.post { station.editorFrame.visibility = View.GONE }
                }
            }
            return this
        }

        fun showAudioInput(): SetBuilder {
            chainedEnd = object: ChainedRunnable(chainedEnd) {
                override fun runInternal() {
                    station.post { station.audioEt.visibility = View.VISIBLE }
                }
            }
            return hideTextInput()
        }

        fun hideAudioInput(): SetBuilder {
            chainedStart = object: ChainedRunnable(chainedStart) {
                override fun runInternal() {
                    station.post { station.audioEt.visibility = View.GONE }
                }
            }
            return this
        }

        fun apply(duration: Long = 150L) {
            station.post {
                TransitionManager.beginDelayedTransition(station, ChangeBounds().apply {
                    this.duration = duration
                    addListener(StartEndListener(chainedStart, chainedEnd))
                })
                set.applyTo(station)
            }
        }

        @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
        fun ConstraintSet.connect(id : Int, side : Int, margin : Int = 0, anchorId : Int = PARENT_ID) {
            connect(id, side, anchorId, side, margin)
        }
    }

    abstract class ChainedRunnable(private val next: ChainedRunnable? = null): Runnable {
        override fun run() {
            runInternal()
            next?.run()
        }

        abstract fun runInternal()
    }

    class StartEndListener(private val startTask: Runnable? = null, private val endTask: Runnable? = null): Transition.TransitionListener {
        override fun onTransitionResume(transition: Transition) {
        }

        override fun onTransitionPause(transition: Transition) {
        }

        override fun onTransitionCancel(transition: Transition) {
        }

        override fun onTransitionStart(transition: Transition) {
            startTask?.run()
        }

        override fun onTransitionEnd(transition: Transition) {
            endTask?.run()
        }
    }

    interface OnPaletteItemTouchedListener {
        fun onColorSelected(colorId: Int)
        fun onColorConfirmed()
    }
}