package com.stgi.kotebook

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.TransitionDrawable
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.ConstraintSet.*
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import kotlinx.android.synthetic.main.fragment_writing.*
import kotlinx.android.synthetic.main.fragment_writing.view.*
import kotlinx.android.synthetic.main.layout_retracted.*
import android.animation.ValueAnimator
import android.content.Context
import android.view.*
import androidx.core.view.forEach


const val RESULT_OK = "RESULT_OK"
const val EXTRA_EDITED = "EDITED"

private const val ARG_X = "X"
private const val ARG_Y = "Y"
private const val ARG_W = "W"
private const val ARG_H = "H"

private const val ANGLE = 90.0f
private const val BASE_ROW = 4

class WritingFragment : Fragment(), View.OnClickListener, View.OnTouchListener {

    var note: Note? = null

    private var isEditing = false
    private var isPaletteShown = false

    private var startingSet: ConstraintSet? = null

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
        val view = inflater.inflate(R.layout.fragment_writing, container, false) as ConstraintLayout

        view.visibility = View.INVISIBLE
        view.setOnTouchListener { _, _ -> hidePalette(); true }

        view.fakeView.setOnTouchListener { _, _ ->
            activity?.let {
                (it as MainActivity).pushStatus(STATUS_CONFIRM, ConfirmStrategy(it))
            }
            true
        }

        //view.bulletPointScrollView.setOnTouchListener { _, _ -> hidePalette(); bulletPointEditor.requestFocus(); false }

        view.bulletsButton.setOnClickListener { bulletPointEditor.addBulletPoint(); hidePalette() }
        view.paintButton.setOnClickListener { showOrHidePalette(shouldShow = !isPaletteShown) }

        val l = View.OnFocusChangeListener { v, b ->
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
            } //else imm.hideSoftInputFromWindow(v.windowToken, 0)
        }

        view.titleEt.setText(note!!.title)
        view.titleEt.onFocusChangeListener = l

        view.bulletPointEditor.onFocusChangeListener = l
        view.bulletPointEditor.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, _ ->
            hidePalette()
            if (bulletPointEditor == null) return@OnCheckedChangeListener
            val editedText = bulletPointEditor.getText()
            val data = note!!.toData()
            data.text = editedText
            activity?.let { ViewModelProviders.of(it) }?.get(NotesModel::class.java)?.update(data)
        })

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
                bulletPointEditor.setText(note!!.text!!)
                view.visibility = View.VISIBLE
                bulletPointScrollView.invalidate()
                applyFinalConstraints()
            })
        }

        return view
    }

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

            startMarginAnimator(bulletPointScrollView, R.dimen.bullet_point_editor_end_margin_fullscreen, R.dimen.bullet_point_editor_end_margin_init, duration)

            set.connect(bulletPointScrollView.id, START, fakeView.id, START)
            set.connect(bulletPointScrollView.id, TOP, titleFrame.id, BOTTOM)
            set.connect(bulletPointScrollView.id, BOTTOM, fakeView.id, BOTTOM)

            set.applyTo(getConstraintLayout())
        }
    }

    private fun applyFinalConstraints(duration : Long = 150L, endTask : Runnable? = null) {
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

        startMarginAnimator(bulletPointScrollView, R.dimen.bullet_point_editor_end_margin_init, R.dimen.bullet_point_editor_end_margin_fullscreen, duration)

        set.applyTo(getConstraintLayout())
    }

    private fun startMarginAnimator(v: View?, initialRes: Int, targetRes: Int, duration: Long) {
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
        set.connect(bulletsButton.id, BOTTOM, PARENT_ID, BOTTOM, resources.getDimension(R.dimen.secondary_fabs_height).toInt())
        set.connect(paintButton.id, START, PARENT_ID, START)
        set.clear(paintButton.id, END)
        set.connect(paintButton.id, START, PARENT_ID, START, resources.getDimension(R.dimen.default_margin).toInt())
        set.applyTo(getConstraintLayout())
    }

    private fun collapseButtons(duration: Long = 100L) {
        hidePalette()

        TransitionManager.beginDelayedTransition(getConstraintLayout(), ChangeBounds().setDuration(duration))
        val set = ConstraintSet()
        set.clone(getConstraintLayout())
        set.connect(bulletsButton.id, BOTTOM, resources.getDimension(R.dimen.default_margin).toInt())
        set.clear(paintButton.id, START)
        set.connect(paintButton.id, END, PARENT_ID, START, resources.getDimension(R.dimen.default_margin).toInt())
        set.applyTo(getConstraintLayout())
    }

    private fun hidePalette(view: View? = getView()) {
        showOrHidePalette(view, false)
    }

    private fun showPalette(view: View? = getView()) {
        showOrHidePalette(view, true)
    }

    private fun showOrHidePalette(view: View? = getView(), shouldShow: Boolean = false) {
        if (shouldShow == isPaletteShown) return

        if (shouldShow) {
            titleEt.clearFocus()
            bulletPointEditor.clearFocus()
            val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view?.windowToken, 0)
        }

        isPaletteShown = shouldShow

        val transition = ChangeBounds()
        transition.duration = 200L
        TransitionManager.beginDelayedTransition(getConstraintLayout(), transition)

        val set = ConstraintSet()
        set.clone(getConstraintLayout())

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

                    set.constrainWidth(paletteItem.id, w)
                    set.constrainHeight(paletteItem.id, h)
                    set.constrainCircle(paletteItem.id, paintButton.id, radius, angle)
                }
            }

        set.applyTo(getConstraintLayout())
    }

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    private fun ConstraintSet.connect(id : Int, side : Int, margin : Int = 0, anchorId : Int = PARENT_ID) {
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
        if (!bulletPointEditor.hasFocus() && !titleFrame.titleEt.hasFocus())
            bulletPointEditor.requestFocus()

        expandButtons()
        return false
    }

    private fun closeEditing(saveEdits : Boolean = true) {
        isEditing = false
        titleEt.clearFocus()
        bulletPointEditor.clearFocus()
        hidePalette()
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)

        val editedTitle = titleEt.text!!.toString().trimSpaces()
        val editedText = bulletPointEditor.getText()
        if (saveEdits && ((editedText != note?.text || editedTitle != note?.text)) &&
            (editedTitle.isNotEmpty() || editedText.isNotEmpty())) {
            val data = note!!.toData()
            data.title = editedTitle
            data.text = editedText
            activity?.let { ViewModelProviders.of(it) }?.get(NotesModel::class.java)?.update(data)
        }

        collapseButtons()
    }

    fun applyColors(view: View? = getView()) {
        if (view == null) return

        view.fakeView.background.setColorFilter(note!!.getColor(), PorterDuff.Mode.SRC_ATOP)

        var hintColor = note!!.getTextColor()
        hintColor = Color.argb(120, hintColor.red, hintColor.green, hintColor.blue)
        view.titleEt.setTextColor(note!!.getTextColor())
        view.titleEt.setHintTextColor(hintColor)

        view.bulletPointEditor.setTextColor(note!!.getTextColor())
    }

    private fun applyPalette(view: View) {
        val newColor = resources.getColor(
            view.tag as Int,
            this@WritingFragment.context?.theme
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

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         */
        @JvmStatic
        fun newInstance(note: Note, v : View) =
            WritingFragment().apply {
                this.retainInstance = true
                this.note = note
                arguments = Bundle().apply {
                    putFloat(ARG_X, v.x)
                    putFloat(ARG_Y, v.y)
                    putInt(ARG_W, v.width)
                    putInt(ARG_H, v.height)
                }
            }
    }

    inner class EditStrategy(context: MainActivity): MainActivity.OnClickStrategy(context) {
        override fun getPrimaryDrawable() = resources.getDrawable(R.drawable.edit, context.theme)
        override fun isDismissable() = false

        override fun onBackPressed(): Boolean {
            exit()
            context.pushStatus(STATUS_INIT)
            return false
        }

        override fun onPrimaryClick() {
            hidePalette()
            openEditing()
            context.pushStatus(STATUS_CONFIRM, ConfirmStrategy(context))
        }
    }

    inner class ConfirmStrategy(context: MainActivity): MainActivity.OnClickStrategy(context) {
        override fun getPrimaryDrawable() = resources.getDrawable(R.drawable.done, context.theme)

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

    private fun String.trimSpaces() = trimStart { c -> c.isWhitespace() }.dropLastWhile { c -> c.isWhitespace() }
}
