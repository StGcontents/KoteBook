package com.stgi.kotebook

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.TransitionDrawable
import android.net.Uri
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.ConstraintSet.*
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.stgi.rodentia.CassettePlayerView
import kotlinx.android.synthetic.main.fragment_edit.*
import kotlinx.android.synthetic.main.fragment_edit.view.*
import kotlinx.android.synthetic.main.layout_bullets_fab.view.*
import kotlinx.android.synthetic.main.layout_cassette.view.*
import kotlinx.android.synthetic.main.layout_retracted.*
import kotlinx.android.synthetic.main.layout_text_editor.*
import kotlinx.android.synthetic.main.layout_text_editor.view.*
import java.io.File
import java.text.DateFormat
import java.util.*
import kotlin.math.max


const val RESULT_OK = "RESULT_OK"
const val EXTRA_EDITED = "EDITED"

private const val ARG_X = "X"
private const val ARG_Y = "Y"
private const val ARG_W = "W"
private const val ARG_H = "H"
private const val ARG_PLAYER_H = "PLAYER_H"

private const val ANGLE = 90.0f
private const val BASE_ROW = 4

abstract class EditFragment : Fragment(), View.OnClickListener, View.OnTouchListener {

    var note: Note? = null

    private var isEditing = false
    private var isPaletteShown = false

    private var startingSet: ConstraintSet? = null

    protected val textFocusListener = View.OnFocusChangeListener { v, b ->
        hidePalette()
        v as EditText
        val imm =
            activity?.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
        if (b) {
            activity?.let {
                (it as MainActivity).pushStatus(STATUS_CONFIRM, ConfirmStrategy(it))
            }
            imm.showSoftInput(v, 0)
            v.setSelection(v.text.length)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            //note = it.getSerializable(ARG_NOTE) as Note
        }
    }

    private fun getConstraintLayout() = view as ConstraintLayout

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_edit, container, false) as ConstraintLayout

        view.visibility = View.INVISIBLE

        view.fakeView.setOnTouchListener { _, _ ->
            hidePalette()
            if (!isEditing)
                (activity?.let { (it as MainActivity).pushStatus(STATUS_CONFIRM, ConfirmStrategy(it)) })
            true
        }

        view.paintButton.setOnClickListener { showOrHidePalette(shouldShow = !isPaletteShown) }
        view.paletteMask.setOnTouchListener { _, _ -> true }

        view.alarmButton.setOnClickListener {
            (context as MainActivity).let {
                it.pushStatus(STATUS_CLOCK, ClockStrategy(it))
            }
        }
        view.post {
            Calendar.getInstance().apply {
                time = Date()
                view.datePicker.minDate = time.time
                view.datePicker.updateDate(
                    get(Calendar.YEAR),
                    get(Calendar.MONTH),
                    get(Calendar.DAY_OF_MONTH)
                )
            }
        }

        view.clockMask.setOnTouchListener { _, ev ->
            if (MotionEvent.ACTION_DOWN == ev?.action)
                activity?.onBackPressed()
            true
        }

        view.titleEt.setText(note!!.title)
        view.titleEt.onFocusChangeListener = textFocusListener

        onCreateInternal(view)

        startingSet = ConstraintSet()
        startingSet?.clone(view)

        applyColors(view)

        context?.let { cxt ->
            palette
                .forEach { color ->
                    val paletteItem = View(cxt).also {
                        it.id = View.generateViewId()
                        it.tag = color
                        it.background = cxt.getDrawable(R.drawable.circle)
                        it.background.setColorFilter(cxt.getColor(color), PorterDuff.Mode.SRC_ATOP)
                        it.elevation = resources.getDimension(R.dimen.small_fab_elevation)
                    }

                    paletteItem.setOnClickListener(this)
                    paletteItem.setOnTouchListener(this)
                    view.addView(paletteItem)

                    startingSet?.constrainHeight(paletteItem.id, 0)
                    startingSet?.constrainWidth(paletteItem.id,  0)
                    startingSet?.constrainCircle(paletteItem.id, view.paintButton.id, 0, 0f)
                }
        }

        view.post {
            applyStartingConstraints(endTask = Runnable {
                titleEt.setText(note!!.title)
                onStartingConstraintsSet(view)
                view.visibility = View.VISIBLE
                applyFinalConstraints(endTask = Runnable { onFinalConstraintsSet(view) })
            })
        }

        return view
    }

    abstract fun onCreateInternal(view: View)

    abstract fun onStartingConstraintsSet(view: View)
    abstract fun onFinalConstraintsSet(view: View)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (context as MainActivity).pushStatus(STATUS_EDIT, EditStrategy(context))
    }

    private fun applyStartingConstraints(duration: Long = 0L, endTask: Runnable? = null) {
        arguments?.let {
            val transition = ChangeBounds()
            transition.duration = duration
            val startTask = Runnable {
                (fakeView.background as TransitionDrawable).startTransition(duration.toInt())
            }
            transition.addListener(StartToFinishTaskListener(startTask, endTask))

            TransitionManager.beginDelayedTransition(getConstraintLayout(), transition)

            val set = ConstraintSet()
            set.clone(startingSet)

            val h = it.getInt(ARG_H)
            val w = it.getInt(ARG_W)
            val x = it.getFloat(ARG_X)
            val y = it.getFloat(ARG_Y)

            set.constrainHeight(fakeView.id, h)
            set.constrainWidth(fakeView.id, w)
            set.connect(fakeView.id, START, x.toInt())
            set.connect(fakeView.id, TOP, y.toInt())

            if (note!!.title.isNotEmpty()) set.constrainHeight(titleFrame.id, WRAP_CONTENT)

            set.connect(view!!.datePickerMask.id, END, PARENT_ID, START, resources.getDimension(R.dimen.fab_margin).toInt())

            applyStartingConstraintsInternal(set, duration)

            set.applyTo(getConstraintLayout())
        }
    }

    abstract fun applyStartingConstraintsInternal(set: ConstraintSet, duration: Long)

    protected fun applyFinalConstraints(duration : Long = 150L, endTask : Runnable? = null) {
        val transition = ChangeBounds()
        transition.duration = duration
        val startTask = Runnable {
            (fakeView.background as TransitionDrawable).startTransition(duration.toInt())
        }
        transition.addListener(StartToFinishTaskListener(startTask, endTask))

        TransitionManager.beginDelayedTransition(getConstraintLayout(), transition)

        val set = ConstraintSet()
        set.clone(getConstraintLayout())
        set.connect(fakeView.id, START)
        set.connect(fakeView.id, END)
        set.connect(fakeView.id, TOP)
        set.connect(fakeView.id, BOTTOM)
        set.constrainHeight(fakeView.id, 0)
        set.constrainWidth(fakeView.id, 0)

        set.constrainHeight(titleFrame.id, WRAP_CONTENT)

        applyFinalConstraintsInternal(set, duration)

        set.applyTo(getConstraintLayout())
    }

    abstract fun applyFinalConstraintsInternal(set: ConstraintSet, duration: Long)

    protected fun startMarginAnimator(v: View?, initialRes: Int, targetRes: Int, duration: Long) {
        v?.animate()?.cancel()
        val animator = ValueAnimator.ofInt(resources.getDimension(initialRes).toInt(), resources.getDimension(targetRes).toInt())
        animator.duration = duration
        animator.addUpdateListener { animation ->
            (v?.layoutParams as ConstraintLayout.LayoutParams).marginEnd = animation.animatedValue as Int
        }
        animator.start()
    }

    private fun expandButtons(duration: Long = 100L) {
        TransitionManager.beginDelayedTransition(getConstraintLayout(), ChangeBounds().setDuration(duration))
        val set = ConstraintSet()
        set.clone(getConstraintLayout())

        expandButtonsInternal(set)

        set.connect(paintButton.id, START, PARENT_ID, START)
        set.clear(paintButton.id, END)
        set.connect(paintButton.id, START, PARENT_ID, START, resources.getDimension(R.dimen.default_margin).toInt())
        set.applyTo(getConstraintLayout())
    }

    abstract fun expandButtonsInternal(set: ConstraintSet)

    private fun collapseButtons(duration: Long = 100L) {
        hidePalette()

        TransitionManager.beginDelayedTransition(getConstraintLayout(), ChangeBounds().setDuration(duration))
        val set = ConstraintSet()
        set.clone(getConstraintLayout())

        collapseButtonsInternal(set)

        set.clear(paintButton.id, START)
        set.connect(paintButton.id, END, PARENT_ID, START, resources.getDimension(R.dimen.default_margin).toInt())
        set.applyTo(getConstraintLayout())
    }

    abstract fun collapseButtonsInternal(set: ConstraintSet)

    fun expandClock() {
        view?.alarmButton?.asClock()
        view?.datePickerMask?.setOnClickListener {
            slideCalendarIn()
        }

        val set = ConstraintSet()
        set.clone(getConstraintLayout())

        set.constrainWidth(view!!.alarmButton.id, resources.getDimension(R.dimen.bigger_play_button_size).toInt())
        set.constrainHeight(view!!.alarmButton.id, resources.getDimension(R.dimen.bigger_play_button_size).toInt())
        set.connect(view!!.alarmButton.id, TOP)
        set.connect(view!!.alarmButton.id, BOTTOM)
        set.connect(view!!.alarmButton.id, START)
        set.connect(view!!.alarmButton.id, END)

        set.constrainWidth(view!!.datePickerMask.id, resources.getDimension(R.dimen.fab_size).toInt())
        set.constrainHeight(view!!.datePickerMask.id, resources.getDimension(R.dimen.fab_size).toInt())
        set.clear(view!!.datePickerMask.id, START)
        set.connect(view!!.datePickerMask.id, END, view!!.leftGuideline.id, END, 0)

        TransitionManager.beginDelayedTransition(getConstraintLayout(), ChangeBounds().apply {
            duration = 150L
            addListener(StartToFinishTaskListener(startTask = Runnable {
                view!!.alarmButton.showAmPmButton()
                view!!.clockMask.apply {
                    alpha = 0f
                    visibility = View.VISIBLE
                }.animate().apply {
                    cancel()
                    alpha(1f)
                    duration = 150L
                    start()
                }
            }))
        })
        set.applyTo(getConstraintLayout())
    }

    fun collapseClock() {
        view?.alarmButton?.asButton()

        val set = ConstraintSet()
        set.clone(getConstraintLayout())

        set.constrainWidth(view!!.alarmButton.id, resources.getDimension(R.dimen.fab_size).toInt())
        set.constrainHeight(view!!.alarmButton.id, resources.getDimension(R.dimen.fab_size).toInt())
        set.clear(view!!.alarmButton.id, TOP)
        set.clear(view!!.alarmButton.id, START)
        set.connect(view!!.alarmButton.id, END, PARENT_ID, END, resources.getDimension(R.dimen.fab_margin).toInt())
        if (this is WritingFragment)
            set.connect(view!!.alarmButton.id, BOTTOM, view!!.bulletsButton.id, TOP, resources.getDimension(R.dimen.fab_margin).toInt())
        else
            set.connect(view!!.alarmButton.id, BOTTOM, PARENT_ID, BOTTOM, resources.getDimension(R.dimen.secondary_fabs_height).toInt())

        set.constrainWidth(view!!.datePickerMask.id, resources.getDimension(R.dimen.fab_size).toInt())
        set.constrainHeight(view!!.datePickerMask.id, resources.getDimension(R.dimen.fab_size).toInt())
        set.clear(view!!.datePickerMask.id, START)
        set.connect(view!!.datePickerMask.id, END, PARENT_ID, START, resources.getDimension(R.dimen.fab_margin).toInt())
        set.connect(view!!.datePickerMask.id, TOP, PARENT_ID, TOP)
        set.connect(view!!.datePickerMask.id, BOTTOM, PARENT_ID, BOTTOM)

        TransitionManager.beginDelayedTransition(getConstraintLayout(), ChangeBounds().apply {
            duration = 150L
            addListener(StartToFinishTaskListener(startTask = Runnable {
                view?.datePicker?.visibility = View.GONE
                view?.datePickerMask?.visibility = View.VISIBLE
                view!!.alarmButton.hideAmPmButton()
                view!!.clockMask.animate().apply {
                    cancel()
                    alpha(0f)
                    duration = 150L
                    withEndAction { view!!.clockMask.visibility = View.GONE }
                    start()
                }
            }))
        })
        set.applyTo(getConstraintLayout())
    }

    fun slideCalendarIn() {
        view?.alarmButton?.asButton()
        view?.datePickerMask?.setOnClickListener(null)
        view?.datePicker?.visibility = View.INVISIBLE

        val set = ConstraintSet()
        set.clone(getConstraintLayout())

        set.constrainWidth(view!!.alarmButton.id, resources.getDimension(R.dimen.fab_size).toInt())
        set.constrainHeight(view!!.alarmButton.id, resources.getDimension(R.dimen.fab_size).toInt())
        set.clear(view!!.alarmButton.id, END)
        set.connect(view!!.alarmButton.id, START, view!!.rightGuideline.id, START)

        set.constrainWidth(view!!.datePickerMask.id, resources.getDimension(R.dimen.no_dimen).toInt())
        set.constrainHeight(view!!.datePickerMask.id, resources.getDimension(R.dimen.no_dimen).toInt())
        set.connect(view!!.datePickerMask.id, START, view!!.datePicker.id, START)
        set.connect(view!!.datePickerMask.id, END, view!!.datePicker.id, END, 0)
        set.connect(view!!.datePickerMask.id, TOP, view!!.datePicker.id, TOP)
        set.connect(view!!.datePickerMask.id, BOTTOM, view!!.datePicker.id, BOTTOM)

        TransitionManager.beginDelayedTransition(getConstraintLayout(), ChangeBounds().apply {
            duration = 150L
            addListener(StartToFinishTaskListener(endTask = Runnable {
                view?.datePicker?.visibility = View.VISIBLE
                view?.datePickerMask?.visibility = View.INVISIBLE
            }))
        })
        set.applyTo(getConstraintLayout())
    }

    fun slideCalendarOut() {
        view?.alarmButton?.asClock()
        view?.datePickerMask?.setOnClickListener {
            slideCalendarIn()
        }

        val set = ConstraintSet()
        set.clone(getConstraintLayout())

        set.constrainWidth(view!!.alarmButton.id, resources.getDimension(R.dimen.bigger_play_button_size).toInt())
        set.constrainHeight(view!!.alarmButton.id, resources.getDimension(R.dimen.bigger_play_button_size).toInt())
        set.connect(view!!.alarmButton.id, START, PARENT_ID, START)
        set.connect(view!!.alarmButton.id, END, PARENT_ID, END)

        set.constrainWidth(view!!.datePickerMask.id, resources.getDimension(R.dimen.fab_size).toInt())
        set.constrainHeight(view!!.datePickerMask.id, resources.getDimension(R.dimen.fab_size).toInt())
        set.clear(view!!.datePickerMask.id, START)
        set.connect(view!!.datePickerMask.id, TOP, PARENT_ID, TOP)
        set.connect(view!!.datePickerMask.id, BOTTOM, PARENT_ID, BOTTOM)
        set.connect(view!!.datePickerMask.id, END, view!!.leftGuideline.id, END, 0)

        TransitionManager.beginDelayedTransition(getConstraintLayout(), ChangeBounds().apply {
            duration = 150L
            addListener(StartToFinishTaskListener(startTask = Runnable {
                view?.datePicker?.visibility = View.INVISIBLE
                view?.datePickerMask?.visibility = View.VISIBLE
            }, endTask = Runnable { view!!.datePicker.visibility = View.GONE }))
        })
        set.applyTo(getConstraintLayout())
    }

    protected fun hidePalette(view: View? = getView()) {
        showOrHidePalette(view, false)
    }

    private fun showPalette(view: View? = getView()) {
        showOrHidePalette(view, true)
    }

    open fun showOrHidePalette(view: View? = getView(), shouldShow: Boolean = false) {
        if (shouldShow == isPaletteShown) return

        isPaletteShown = shouldShow

        val transition = ChangeBounds()
        transition.duration = 200L
        TransitionManager.beginDelayedTransition(getConstraintLayout(), transition)

        val set = ConstraintSet()
        set.clone(getConstraintLayout())

        var maxRadius = 0

        palette
            .forEach {
                val paletteItem = view?.findViewWithTag<View>(it)
                if (paletteItem != null) {
                    var angle = 0f
                    var radius = 0
                    var w = 0
                    var h = 0

                    if (shouldShow) {
                        val index = palette.indexOf(it)
                        var rowCounter = 0
                        var offset = 0
                        while (index >= offset) offset += BASE_ROW + rowCounter++
                        offset -= BASE_ROW + rowCounter-- //adjustment
                        angle = ANGLE * (index - offset - 1) / (BASE_ROW + rowCounter - 1).toFloat()

                        w = resources.getDimension(R.dimen.small_fab_size).toInt()
                        h = w
                        radius = (resources.getDimension(R.dimen.fab_size) / 2f +
                                (w * 1.5f * (rowCounter + 1))).toInt()
                    }

                    maxRadius = max(maxRadius, radius)

                    set.constrainWidth(paletteItem.id, w)
                    set.constrainHeight(paletteItem.id, h)
                    set.constrainCircle(paletteItem.id, paintButton.id, radius, angle)
                }
            }

        set.constrainWidth(paletteMask.id, maxRadius)
        set.constrainHeight(paletteMask.id, maxRadius)

        set.applyTo(getConstraintLayout())
    }

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    fun ConstraintSet.connect(id : Int, side : Int, margin : Int = 0, anchorId : Int = PARENT_ID) {
        connect(id, side, anchorId, side, margin)
    }

    fun exit() {
         applyStartingConstraints(150L, Runnable {
             activity?.let {
                 it.supportFragmentManager.popBackStackImmediate()
                 it.supportFragmentManager.beginTransaction().remove(this).commit()
             }
         })
    }

    private fun openEditing(): Boolean {
        isEditing = true
        expandButtons()
        openEditingInternal()
        return false
    }

    abstract fun openEditingInternal()

    private fun closeEditing(saveEdits : Boolean = true) {
        isEditing = false
        titleEt.clearFocus()
        hidePalette()
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)

        closeEditingInternal(saveEdits)

        collapseButtons()
    }

    abstract fun closeEditingInternal(saveEdits: Boolean)

    open fun applyColors(view: View? = getView()) {
        if (view == null) return

        view.fakeView.background.setColorFilter(note!!.getColor(), PorterDuff.Mode.SRC_ATOP)

        var hintColor = note!!.getTextColor()
        hintColor = Color.argb(120, hintColor.red, hintColor.green, hintColor.blue)
        view.titleEt.setTextColor(note!!.getTextColor())
        view.titleEt.setHintTextColor(hintColor)
    }

    private fun applyPalette(view: View) {
        val newColor = resources.getColor(
            view.tag as Int,
            this@EditFragment.context?.theme
        )
        if (note!!.getColor() != newColor) {
            note!!.setColor(newColor)
            applyColors()
        }
    }

    override fun onClick(v: View?) {
        if (v?.tag is Int && v.tag as Int in palette) {
            activity?.let { ViewModelProviders.of(it) }?.get(NotesModel::class.java)
                ?.update(note!!.toData())
            hidePalette()
        }
    }

    override fun onTouch(view: View?, ev: MotionEvent?): Boolean {
        if (ev != null && view != null) {

            if (ev.action == KeyEvent.ACTION_MULTIPLE ||
                ev.action == KeyEvent.ACTION_DOWN) {

                if (0 < ev.x && ev.x < view.width &&
                    0 < ev.y && ev.y < view.height) {
                    applyPalette(view)
                } else {
                    val absoluteX = view.x + ev.x
                    val absoluteY = view.y + ev.y
                    getConstraintLayout().forEach {
                        if (it != view && it.tag is Int && it.tag as Int in palette) {
                            if (it.x < absoluteX && absoluteX < (it.x + it.width) &&
                                it.y < absoluteY && absoluteY < (it.y + it.height)) {
                                applyPalette(it)
                                return@forEach
                            }
                        }
                    }
                }
            } else if (ev.action == KeyEvent.ACTION_UP)
                view.performClick()
        }
        return true
    }


    /**
     * Editing text notes
     */
    class WritingFragment: EditFragment() {

        override fun onCreateInternal(view: View) {
            view as ConstraintLayout

            val bulletPointScrollView = LayoutInflater.from(context).inflate(R.layout.layout_text_editor, view, false)
            view.addView(bulletPointScrollView, view.indexOfChild(view.titleFrame) + 1)

            val bulletsButton = LayoutInflater.from(context).inflate(R.layout.layout_bullets_fab, view, false)
            view.addView(bulletsButton, view.indexOfChild(view.paintButton) + 1)

            bulletsButton.setOnClickListener { view.bulletPointEditor.addBulletPoint(); hidePalette() }

            bulletPointScrollView.setOnTouchListener { _, _ ->
                hidePalette()
                activity?.let {
                    (it as MainActivity).pushStatus(STATUS_CONFIRM, ConfirmStrategy(it))
                    view.bulletPointEditor.requestFocus()
                }
                false
            }

            view.bulletPointEditor.onFocusChangeListener = textFocusListener
        }

        override fun onStartingConstraintsSet(view: View) {
            view.bulletPointEditor.setText(note!!.text!!)
            view.bulletPointScrollView.invalidate()
            view.bulletPointEditor.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, _ ->
                hidePalette()
                if (bulletPointEditor == null) return@OnCheckedChangeListener
                val editedText = bulletPointEditor.getText()
                val data = note!!.toData()
                data.text = editedText
                activity?.let { ViewModelProviders.of(it) }?.get(NotesModel::class.java)?.update(data)
            })
        }

        override fun onFinalConstraintsSet(view: View) { }

        override fun applyStartingConstraintsInternal(set: ConstraintSet, duration: Long) {
            startMarginAnimator(bulletPointScrollView, R.dimen.bullet_point_editor_end_margin_fullscreen, R.dimen.bullet_point_editor_end_margin_init, duration)

            set.connect(bulletPointScrollView.id, START, fakeView.id, START)
            set.connect(bulletPointScrollView.id, TOP, titleFrame.id, BOTTOM)
            set.connect(bulletPointScrollView.id, BOTTOM, fakeView.id, BOTTOM)
        }

        override fun applyFinalConstraintsInternal(set: ConstraintSet, duration: Long) {
            startMarginAnimator(bulletPointScrollView, R.dimen.bullet_point_editor_end_margin_init, R.dimen.bullet_point_editor_end_margin_fullscreen, duration)
        }


        override fun expandButtonsInternal(set: ConstraintSet) {
            set.connect(view!!.bulletsButton.id, BOTTOM, PARENT_ID, BOTTOM, resources.getDimension(R.dimen.secondary_fabs_height).toInt())
            set.connect(view!!.alarmButton.id, BOTTOM, view!!.bulletsButton.id, TOP, resources.getDimension(R.dimen.fab_margin).toInt())
        }

        override fun collapseButtonsInternal(set: ConstraintSet) {
            set.connect(view!!.bulletsButton.id, BOTTOM, resources.getDimension(R.dimen.default_margin).toInt())
            set.connect(view!!.alarmButton.id, BOTTOM, resources.getDimension(R.dimen.default_margin).toInt())
        }

        override fun showOrHidePalette(view: View?, shouldShow: Boolean) {
            if (shouldShow) {
                titleEt.clearFocus()
                bulletPointEditor.clearFocus()
                val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view?.windowToken, 0)
            }

            super.showOrHidePalette(view, shouldShow)
        }

        override fun openEditingInternal() { }

        override fun closeEditingInternal(saveEdits: Boolean) {
            bulletPointEditor.clearFocus()

            val editedTitle = titleEt.text!!.toString().trimSpaces()
            val editedText = bulletPointEditor.getText()
            if (saveEdits && ((editedText != note?.text || editedTitle != note?.text)) &&
                (editedTitle.isNotEmpty() || editedText.isNotEmpty())) {
                val data = note!!.toData()
                data.title = editedTitle
                data.text = editedText
                activity?.let { ViewModelProviders.of(it) }?.get(NotesModel::class.java)?.update(data)
            }
        }

        override fun applyColors(view: View?) {
            super.applyColors(view)
            view?.bulletPointEditor?.setTextColor(note!!.getTextColor())
        }
    }


    /**
     * Editing audio notes
     */
    class AudioFragment: EditFragment() {

        override fun onCreateInternal(view: View) {
            view as ConstraintLayout
            val playerView = LayoutInflater.from(context).inflate(R.layout.layout_cassette, view, false) as CassettePlayerView
            playerView.setCassetteColor(note!!.getTextColor())
            view.addView(playerView, view.indexOfChild(view.titleFrame) + 1)
        }
        override fun onStartingConstraintsSet(view: View) { }

        override fun onFinalConstraintsSet(view: View) {
            val file = File(getFilepath(note!!.text))
            if (file.exists()) {
                view.playerView.setContentUri(Uri.fromFile(file))
            }
        }

        override fun applyStartingConstraintsInternal(set: ConstraintSet, duration: Long) {
            set.constrainHeight(view!!.playerView.id,
                arguments!!.getInt(ARG_PLAYER_H, resources.getDimension(R.dimen.play_button_size).toInt()))
            set.clear(view!!.playerView.id, TOP)
        }

        override fun applyFinalConstraintsInternal(set: ConstraintSet, duration: Long) {
            set.constrainHeight(view!!.playerView.id, resources.getDimension(R.dimen.bigger_play_button_size).toInt())
            set.connect(view!!.playerView.id, TOP, fakeView.id, TOP)
        }

        override fun expandButtonsInternal(set: ConstraintSet) {
            set.connect(view!!.alarmButton.id, BOTTOM, PARENT_ID, BOTTOM, resources.getDimension(R.dimen.secondary_fabs_height).toInt())
        }

        override fun collapseButtonsInternal(set: ConstraintSet) {
            set.connect(view!!.alarmButton.id, BOTTOM, resources.getDimension(R.dimen.default_margin).toInt())
        }

        override fun openEditingInternal() {
            view!!.playerView.stop()
            view!!.playerView.isEnabled = false
        }

        override fun closeEditingInternal(saveEdits: Boolean) {
            val editedTitle = titleEt.text!!.toString().trimSpaces()
            if (saveEdits && editedTitle != note?.text && editedTitle.isNotEmpty()) {
                val data = note!!.toData()
                data.title = editedTitle
                activity?.let { ViewModelProviders.of(it) }?.get(NotesModel::class.java)?.update(data)
            }
            view!!.playerView.isEnabled = true
        }

        override fun applyColors(view: View?) {
            super.applyColors(view)
            view!!.playerView?.setCassetteColor(note!!.getTextColor())
        }

        fun getFilepath(name: String?) = (activity as MainActivity).directory.absolutePath + "/" + name
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         */
        @JvmStatic
        fun newInstance(note: Note, v : View) =
            (if (note.isRecording) AudioFragment() else WritingFragment()).apply {
                this.retainInstance = true
                this.note = Note(note.toData())
                arguments = Bundle().apply {
                    putFloat(ARG_X, v.x)
                    putFloat(ARG_Y, v.y)
                    putInt(ARG_W, v.width)
                    putInt(ARG_H, v.height)
                }
                if (this is AudioFragment) {
                    arguments?.putInt(ARG_PLAYER_H, v.playerView.height)
                }
            }
    }

    inner class EditStrategy(context: MainActivity): MainActivity.OnClickStrategy(context) {
        override fun getPrimaryDrawable() = resources.getDrawable(R.drawable.edit, context.theme)
        override fun isDismissible() = false

        override fun onBackPressed(): Boolean {
            if (this@EditFragment is AudioFragment)
                this@EditFragment.view!!.playerView.stop()
            exit()
            context.pushStatus(STATUS_INIT)
            return false
        }

        override fun onPrimaryClick() {
            context.pushStatus(STATUS_CONFIRM, ConfirmStrategy(context))
        }
    }

    inner class ConfirmStrategy(context: MainActivity): MainActivity.OnClickStrategy(context) {
        override fun getPrimaryDrawable() = resources.getDrawable(R.drawable.done, context.theme)

        override fun initialize() {
            super.initialize()
            openEditing()
        }

        override fun onBackPressed(): Boolean {
            when {
                isPaletteShown -> hidePalette()
                isEditing -> {
                    closeEditing()
                    context.pushStatus(STATUS_EDIT, EditStrategy(context))
                }
            }
            return false
        }

        override fun onPrimaryClick() {
            closeEditing()
            context.pushStatus(STATUS_EDIT, EditStrategy(context))
        }
    }

    inner class ClockStrategy(context: MainActivity): MainActivity.OnClickStrategy(context) {
        override fun getPrimaryDrawable() = resources.getDrawable(R.drawable.done, context.theme)
        override fun getSecondaryDrawable() = resources.getDrawable(R.drawable.cancel, context.theme)

        private val startingHour: Int = view!!.alarmButton.getHour()
        private val startingMinute: Int = view!!.alarmButton.getMinute()

        override fun initialize() {
            super.initialize()
            context.secondaryButton.visibility = View.VISIBLE
            view!!.alarmButton.setOnClickListener {
                slideCalendarOut()
            }
            expandClock()
        }

        override fun onBackPressed(): Boolean {
            view!!.alarmButton.setHour(startingHour)
            view!!.alarmButton.setMinute(startingMinute)

            context.secondaryButton.visibility = View.GONE
            collapseClock()
            context.pushStatus(STATUS_CONFIRM, ConfirmStrategy(context))

            onExit()
            return false
        }

        override fun onPrimaryClick() {
            retrieveDate()
            context.secondaryButton.visibility = View.GONE
            collapseClock()
            onExit()

            context.pushStatus(STATUS_CONFIRM, ConfirmStrategy(context))
        }

        override fun onSecondaryClick() {
            activity?.onBackPressed()
        }

        private fun onExit() {
            view!!.alarmButton.setOnClickListener {
                context.let {
                    it.pushStatus(STATUS_CLOCK, ClockStrategy(it))
                }
            }
        }

        private fun retrieveDate() {
            val hhmm: Long = view!!.alarmButton.getTime()
            val calendar = Calendar.getInstance()
            view!!.datePicker.apply {
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            }
            var dateTime: Long = calendar.timeInMillis + hhmm
            if (dateTime < Date().time)
                dateTime += 24L * 60L * 60L * 1000L
            Toast.makeText(context, "Alarm set to " + DateFormat.getInstance().format(Date(dateTime)), Toast.LENGTH_SHORT).show()
        }
    }

    inner class StartToFinishTaskListener(private val startTask : Runnable? = null, private val endTask: Runnable? = null) : android.transition.Transition.TransitionListener {
        override fun onTransitionEnd(p0: android.transition.Transition?) {
            endTask?.run()
        }

        override fun onTransitionResume(p0: android.transition.Transition?) {
        }

        override fun onTransitionPause(p0: android.transition.Transition?) {
        }

        override fun onTransitionCancel(p0: android.transition.Transition?) {
        }

        override fun onTransitionStart(p0: android.transition.Transition?) {
            startTask?.run()
        }
    }

    fun String.trimSpaces() = trimStart { c -> c.isWhitespace() }.dropLastWhile { c -> c.isWhitespace() }
}
