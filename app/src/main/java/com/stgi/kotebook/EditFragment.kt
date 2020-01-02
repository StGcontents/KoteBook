package com.stgi.kotebook

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.net.Uri
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.stgi.rodentia.*
import kotlinx.android.synthetic.main.fragment_edit.*
import kotlinx.android.synthetic.main.fragment_edit.view.*
import kotlinx.android.synthetic.main.layout_cassette.view.*
import kotlinx.android.synthetic.main.layout_text_editor.*
import kotlinx.android.synthetic.main.layout_text_editor.view.*
import java.io.File
import java.text.DateFormat


const val RESULT_OK = "RESULT_OK"
const val EXTRA_EDITED = "EDITED"

private const val ARG_X = "X"
private const val ARG_Y = "Y"
private const val ARG_W = "W"
private const val ARG_H = "H"
private const val ARG_PLAYER_H = "PLAYER_H"

private const val ANGLE = 90.0f
private const val BASE_ROW = 4

abstract class EditFragment : Fragment(), FabStationView.OnPaletteItemTouchedListener {

    var note: Note? = null

    private var isEditing = false

    private var startingSet: ConstraintSet? = null
    private var currentSet: ConstraintSet? = null

    protected val textFocusListener = View.OnFocusChangeListener { v, b ->
        v as EditText
        val imm =
            activity?.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
        if (b) {
            activity?.let {
                (it as MainActivity).pushStatus(STATUS_CONFIRM)
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

        view.fakeView.setOnTouchListener { _, _ ->
            if (!isEditing)
                (activity?.let {
                    (it as MainActivity).pushStatus(STATUS_CONFIRM)
                })
            true
        }

        view.titleEt.setText(note!!.title)
        view.titleEt.onFocusChangeListener = textFocusListener

        onCreateInternal(view)

        applyColors(view)

        if (currentSet == null) {
            view.visibility = View.INVISIBLE

            startingSet = ConstraintSet()
            startingSet?.clone(view)

            view.post {
                applyStartingConstraints(endTask = Runnable {
                    titleEt.setText(note!!.title)
                    onStartingConstraintsSet(view)
                    view.visibility = View.VISIBLE
                    applyFinalConstraints(endTask = Runnable {
                        onFinalConstraintsSet(view)
                        (activity as MainActivity).pushStatus(STATUS_EDIT)
                    })
                })
            }
        } else {
            view.post {
                (view.fakeView.background as TransitionDrawable).apply {
                    resetTransition()
                    startTransition(0)
                }

                currentSet?.applyTo(view)

                onStartingConstraintsSet(view)
                onFinalConstraintsSet(view)

                view.titleEt.setText(note!!.title)
            }
        }

        return view
    }

    abstract fun onCreateInternal(view: View)

    abstract fun onStartingConstraintsSet(view: View)
    abstract fun onFinalConstraintsSet(view: View)

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

            applyStartingConstraintsInternal(set, duration)

            set.applyTo(getConstraintLayout())
            currentSet = set
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
        set.connect(titleFrame.id, START, PARENT_ID, START, getPixels(R.dimen.writing_title_margin))
        set.connect(titleFrame.id, TOP, PARENT_ID, TOP, getPixels(R.dimen.writing_title_margin))

        applyFinalConstraintsInternal(set, duration)

        set.applyTo(getConstraintLayout())

        currentSet = set
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
        if (!isEditing) {
            isEditing = true
            openEditingInternal()
        }
        return false
    }

    abstract fun openEditingInternal()

    private fun closeEditing(saveEdits : Boolean = true) {
        if (isEditing) {
            isEditing = false
            titleEt.clearFocus()
            val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view?.windowToken, 0)

            closeEditingInternal(saveEdits)
        }
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

    private fun applyPalette(colorId: Int) {
        val newColor = resources.getColor(
            colorId,
            this@EditFragment.context?.theme
        )
        if (note!!.getColor() != newColor) {
            note!!.setColor(newColor)
            applyColors()
        }
    }


    override fun onColorSelected(colorId: Int) {
        applyPalette(colorId)
    }

    override fun onColorConfirmed() {
        activity?.let { ViewModelProviders.of(it) }?.get(NotesModel::class.java)
            ?.update(note!!.toData())
    }

    /**
     * Editing text notes
     */
    class WritingFragment: EditFragment() {

        override fun onCreateInternal(view: View) {
            view as ConstraintLayout

            val bulletPointScrollView = LayoutInflater.from(context).inflate(R.layout.layout_text_editor, view, false)
            view.addView(bulletPointScrollView, view.indexOfChild(view.titleFrame) + 1)

            bulletPointScrollView.setOnTouchListener { _, _ ->
                activity?.let {
                    (it as MainActivity).pushStatus(STATUS_CONFIRM)
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
                if (bulletPointEditor == null) return@OnCheckedChangeListener
                val editedText = bulletPointEditor.getText()
                val data = note!!.toData()
                data.text = editedText
                activity?.let { ViewModelProviders.of(it) }?.get(NotesModel::class.java)?.update(data)
            })
        }

        override fun onFinalConstraintsSet(view: View) { }

        override fun applyStartingConstraintsInternal(set: ConstraintSet, duration: Long) {
            set.connect(bulletPointScrollView.id, START, fakeView.id, START, getPixels(R.dimen.bullet_point_editor_end_margin_init))
            set.connect(bulletPointScrollView.id, END, fakeView.id, END, getPixels(R.dimen.bullet_point_editor_end_margin_init))
            set.connect(bulletPointScrollView.id, TOP, titleFrame.id, BOTTOM, getPixels(R.dimen.bullet_point_editor_end_margin_init))
            set.connect(bulletPointScrollView.id, BOTTOM, fakeView.id, BOTTOM, getPixels(R.dimen.bullet_point_editor_end_margin_init))
        }

        override fun applyFinalConstraintsInternal(set: ConstraintSet, duration: Long) {
            set.connect(bulletPointScrollView.id, START, fakeView.id, START, getPixels(R.dimen.bullet_point_editor_end_margin_fullscreen))
            set.connect(bulletPointScrollView.id, END, fakeView.id, END, getPixels(R.dimen.bullet_point_editor_end_margin_fullscreen))
            set.connect(bulletPointScrollView.id, TOP, titleFrame.id, BOTTOM, getPixels(R.dimen.bullet_point_editor_end_margin_fullscreen))
            set.connect(bulletPointScrollView.id, BOTTOM, fakeView.id, BOTTOM, getPixels(R.dimen.bullet_point_editor_end_margin_fullscreen))
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

    inner class EditStrategy(enforcer: FabStationView.FabStationController): FabStationView.OnClickStrategy(enforcer) {
        override fun getPrimaryDrawable(): Drawable? = resources.getDrawable(R.drawable.edit, activity?.theme)
        override fun isDismissible() = false

        override fun initialize(view: FabStationView) {
            super.initialize(view)
            view.paletteCallback = null
        }

        override fun shapeStation(builder: FabStationView.SetBuilder) {
            super.shapeStation(builder)
            builder.apply {
                showPrimary()
                hideSwipe()
                hideSecondary()
                hideTertiary()
                hideClock()
                collapseFan()
                hideFan()
            }.apply()
        }

        override fun onBackPressed(): Boolean {
            if (this@EditFragment is AudioFragment)
                this@EditFragment.view!!.playerView.stop()
            exit()
            enforcer.pushStatus(STATUS_INIT)
            return false
        }

        override fun onPrimaryClick() {
            enforcer.pushStatus(STATUS_CONFIRM)
        }

        override fun getStatus(): Int = STATUS_EDIT
    }

    inner class ConfirmStrategy(enforcer: FabStationView.FabStationController): FabStationView.OnClickStrategy(enforcer) {
        override fun getPrimaryDrawable(): Drawable? = resources.getDrawable(R.drawable.done, activity?.theme)
        override fun getTertiaryDrawable(): Drawable? = resources.getDrawable(R.drawable.bullets, activity?.theme)

        override fun initialize(view: FabStationView) {
            super.initialize(view)
            view.paletteCallback = this@EditFragment
            openEditing()
        }

        override fun shapeStation(builder: FabStationView.SetBuilder) {
            super.shapeStation(builder)
            builder.apply {
                showPrimary()
                hideSwipe()
                hideSecondary()
                if (this@EditFragment is WritingFragment) showTertiary()
                else hideTertiary()
                showClock()
                hideDatePicker()
                showFan()
            }.apply()
        }

        override fun onBackPressed(): Boolean {
            when {
                isEditing -> {
                    closeEditing()
                    enforcer.pushStatus(STATUS_EDIT)
                }
            }
            return false
        }

        override fun onPrimaryClick() {
            closeEditing()
            enforcer.pushStatus(STATUS_EDIT)
        }

        override fun onTertiaryClick() {
            super.onTertiaryClick()
            bulletPointEditor.addBulletPoint()
        }

        override fun onClockClicked() {
            enforcer.pushStatus(STATUS_CLOCK)
        }

        override fun getStatus(): Int = STATUS_CONFIRM
    }

    inner class ClockStrategy(enforcer: FabStationView.FabStationController): FabStationView.OnClickStrategy(enforcer) {
        override fun getPrimaryDrawable(): Drawable? = resources.getDrawable(R.drawable.done, activity?.theme)
        override fun getSecondaryDrawable(): Drawable? = resources.getDrawable(R.drawable.cancel, activity?.theme)
        override fun getTertiaryDrawable(): Drawable? = resources.getDrawable(R.drawable.bullets, activity?.theme)

        private var startingHour: Int = 0
        private var startingMinute: Int = 0

        override fun getStatus(): Int = STATUS_CLOCK

        override fun initialize(view: FabStationView) {
            super.initialize(view)
            startingHour = fabStation.getHour()
            startingMinute = fabStation.getMinute()
        }

        override fun shapeStation(builder: FabStationView.SetBuilder) {
            super.shapeStation(builder)
            builder.apply {
                showPrimary()
                hideSwipe()
                showSecondary()
                if (this@EditFragment is WritingFragment) showTertiary()
                else hideTertiary()
                expandClock()
                minimizeDatePicker()
            }.apply()
        }

        override fun onBackPressed(): Boolean {
            fabStation.setHour(startingHour)
            fabStation.setMinute(startingMinute)

            enforcer.pushStatus(STATUS_CONFIRM)
            return false
        }

        override fun onPrimaryClick() {
            retrieveDate()
            enforcer.pushStatus(STATUS_CONFIRM)
        }

        override fun onSecondaryClick() {
            activity?.onBackPressed()
        }

        override fun onClockClicked() {
            super.onClockClicked()
            FabStationView.SetBuilder(fabStation)
                .minimizeDatePicker()
                .expandClock()
                .apply()
        }

        override fun onDatePickerClicked() {
            super.onDatePickerClicked()
            FabStationView.SetBuilder(fabStation)
                .minimizeClock()
                .expandDatePicker()
                .apply()
        }

        private fun retrieveDate() {
            val calendar = fabStation.getCalendar()
            note!!.timestamp = calendar.timeInMillis
            enforcer.scheduleAlarm(note!!.toData())

            Toast.makeText(activity, getString(R.string.toast_alarm_set)+ DateFormat.getInstance().format(calendar.time), Toast.LENGTH_SHORT).show()
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
