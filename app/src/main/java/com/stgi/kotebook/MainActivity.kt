package com.stgi.kotebook

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.os.Bundle
import android.os.Environment
import android.os.PersistableBundle
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.transition.ChangeBounds
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.stgi.rodentia.SwipeButton
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.layout_retracted.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
import kotlin.random.Random

private const val DURATION = 150L

const val LAST_STATUS = "currentStatus"

const val STATUS_INIT = 1
private const val STATUS_ANNOTATE = 2
private const val STATUS_RECORDING = 4
private const val STATUS_SAVE_REC = 8
const val STATUS_EDIT = 16
const val STATUS_CONFIRM = 32
const val STATUS_CLOCK = 64

const val REC_HIDDEN_FLAGS = STATUS_ANNOTATE + STATUS_SAVE_REC + STATUS_EDIT + STATUS_CONFIRM + STATUS_CLOCK

private const val PERMISSIONS_REQUEST = 13

val palette = intArrayOf(
    R.color.noteColor31,
    R.color.noteColor28,
    R.color.noteColor34,
    R.color.noteColor30,

    R.color.noteColor35,
    R.color.noteColor25,
    R.color.noteColor26,
    R.color.noteColor17,
    R.color.noteColor21,

    R.color.noteColor33,
    R.color.noteColor12,
    R.color.noteColor10,
    R.color.noteColor9,
    R.color.noteColor11,
    R.color.noteColor13,

    R.color.noteColor16,
    R.color.noteColor15,
    R.color.noteColor14,
    R.color.noteColor19,
    R.color.noteColor18,
    R.color.noteColor22,
    R.color.noteColor24,

    R.color.noteColor0,
    R.color.noteColor1,
    R.color.noteColor2,
    R.color.noteColor3,
    R.color.noteColor5,
    R.color.noteColor36,
    R.color.noteColor8,
    R.color.noteColor7
    )

const val DIRECTORY = "/KoteBook/"

class MainActivity : AppCompatActivity(), Transition.TransitionListener, SwipeButton.OnSwipeListener,
    NotesAdapter.OnNotesChangedListener {

    private val adapter : NotesAdapter = NotesAdapter()

    private var currentStatus: Int? = null
    private var isAnimating : Boolean = false
    private var isRetracted : Boolean = true

    val dismissInputListener = View.OnTouchListener { _, _ ->
        if (!isAnimating && currentStrategy!!.isDismissible())
            currentStrategy?.onBackPressed()
        return@OnTouchListener false
    }

    val model: NotesModel by lazy {
        ViewModelProviders.of(this)[NotesModel::class.java]
    }

    var tmpFile: File? = null

    val directory = File(Environment.getExternalStorageDirectory().absolutePath + DIRECTORY).also {
        if (!it.exists())
            it.mkdirs()
    }

    private fun filterNotes() = GlobalScope.launch {
        val iterator = model.getAll().iterator()
        while (iterator.hasNext()) {
            val data = iterator.next()
            if (data.isRecording && !File(buildFilepath(data.text)).exists())
                model.remove(data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        filterNotes()

        rootLayout.setOnTouchListener(dismissInputListener)
        writingFrame.setOnTouchListener { _, _ -> false }
        bgView.setOnTouchListener { _, _ -> true }

        inputText.setTextColor(Color.WHITE)

        audioEt.setTextColor(Color.WHITE)
        audioEt.hint = getString(R.string.insert_title)
        audioEt.highlightColor = Color.argb(120, 0, 0, 0)
        audioEt.setHintTextColor(Color.argb(120, Color.WHITE.red, Color.WHITE.green, Color.WHITE.blue))
        audioEt.setOnFocusChangeListener { v, b ->
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            if (b) {
                imm.showSoftInput(v, 0)
            } else {
                imm.hideSoftInputFromWindow(v.windowToken, 0)
            }
        }

        adapter.onNotesChangedListener = this
        notesRecycler.adapter = adapter
        notesRecycler.layoutManager = getStaggeredLayout(resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
        notesRecycler.setOnTouchListener(dismissInputListener)
        notesRecycler.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                dismissInputListener.onTouch(rv, e)
                val child = rv.findChildViewUnder(e.x, e.y)
                hideAllOptionsBesides(child)
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(inputText.windowToken, 0)
                return false
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) { }
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) { }
        })

        audioFab.setOnSwipeListener(this)
    }

    override fun onResume() {
        super.onResume()
        model.notes.observe(this,
            Observer<List<Note.NoteData>> { t ->
                adapter.setData(t?.map { Note(it) }
                    ?.sortedWith(compareBy({ !it.pinned }, { it.id })))
            })

        getPreferences(Context.MODE_PRIVATE).getInt(LAST_STATUS, STATUS_INIT).apply {
            constraintLayout.show()
            if (this and REC_HIDDEN_FLAGS == this) {
                audioFab.hide()
            } else {
                audioFab.show()
            }
            pushStatus(this)
        }
    }

    override fun onPause() {
        super.onPause()
        model.notes.removeObservers(this)
        currentStrategy?.persistStatus()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        notesRecycler.layoutManager = when (newConfig.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> getStaggeredLayout(true)
            else -> getStaggeredLayout(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        getPreferences(Context.MODE_PRIVATE).edit().apply {
            remove(LAST_STATUS)
            apply()
        }
    }

    override fun onSaveInstanceState(outState: Bundle?, outPersistentState: PersistableBundle?) {
        currentStatus?.let { outPersistentState?.putInt(LAST_STATUS, it) }
        super.onSaveInstanceState(outState, outPersistentState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        savedInstanceState?.let { pushStatus(it.getInt(LAST_STATUS, STATUS_INIT)) }
    }

    private fun getStaggeredLayout(portrait: Boolean) =
        StaggeredGridLayoutManager(if (portrait) 2 else 3, LinearLayoutManager.VERTICAL)

    private fun consumeInput() {
        when (currentStatus) {
            STATUS_ANNOTATE -> {
                val str = inputText.getText().trimSpaces()
                inputText.setText("")
                if (TextUtils.isEmpty(str))
                    return
                model.add(Note.NoteData(text = str, color = RandomColor().gen()))
            }
            STATUS_SAVE_REC -> audioEt.text = null
        }
    }

    private fun applyConstraints(set : ConstraintSet, endTask: Runnable? = null) {
        val transition = ChangeBounds()
        transition.addListener(this)
        transition.duration = DURATION
        TransitionManager.beginDelayedTransition(constraintLayout, transition)
        set.applyTo(constraintLayout)
    }

    private fun expandEditText() {
        if (isRetracted) {
            isRetracted = false
            val set = ConstraintSet()
            set.clone(this, R.layout.layout_expanded)
            applyConstraints(set)
        }
    }

    private fun retract() {
        if (!isRetracted) {
            isRetracted = true
            val set = ConstraintSet()
            set.clone(this, R.layout.layout_retracted)
            applyConstraints(set)
        }
    }

    fun View.hide() {
        post {
            animate().cancel()
            animate().apply {
                translationY(height.toFloat())
                start()
            }
        }
    }

    fun View.show() {
        animate().cancel()
        animate().translationY(0f).start()
    }

    private fun hideAllOptionsBesides(v: View?) {
        notesRecycler.children.filter { it != v }.forEach {
            val holder = notesRecycler.getChildViewHolder(it) as NotesAdapter.NotesViewHolder
            holder.hideOptions()
            if (holder is NotesAdapter.AudioViewHolder) holder.playerView.stop()
        }
    }

    fun showEditFragment(note : Note, view : View) {
        val fragment = EditFragment.newInstance(note, view)
        supportFragmentManager.beginTransaction()
            .addToBackStack(null)
            .replace(R.id.writingFrame, fragment, EditFragment.javaClass.simpleName)
            .commit()
        pushStatus(STATUS_EDIT, fragment.EditStrategy(this))
        constraintLayout.show()
    }

    override fun onBackPressed() {
        if (currentStrategy!!.onBackPressed()) {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }


    override fun onNoteAdded(data: Note.NoteData) {

    }

    override fun onNoteUpdated(data: Note.NoteData) {
        model.update(data)
    }

    override fun onNoteRemoved(data: Note.NoteData) {
        model.remove(data)

        Snackbar
            .make(rootLayout, "You delete a note.", Snackbar.LENGTH_LONG)
            .setAction("UNDO") { model.add(data) }
            .setActionTextColor(getColor(R.color.colorAccent))
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    super.onDismissed(transientBottomBar, event)
                    when (event) {
                        BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_CONSECUTIVE,
                        BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_MANUAL,
                        BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_SWIPE,
                        BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_TIMEOUT -> {
                            if (data.isRecording) {
                                File(data.text).also {
                                    if (it.exists())
                                        it.delete()
                                }
                            }
                        }
                        else -> {
                            Log.d("SNACKBAR_DISMISS", "What is it? " + event)
                        }
                    }
                }
            })
            .show()
    }


    /** RECORDING **/
    override fun onSwiped(): Boolean {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            /*if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                Toast.makeText(this, "Take audio notes", Toast.LENGTH_LONG).show()
            } else */
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSIONS_REQUEST)
            return false
        } else {
            pushStatus(STATUS_RECORDING)
            return true
        }
    }

    override fun onAutoRelease() {
        tmpFile = audioFab.popRecording()
        if (currentStrategy is RecStrategy)
            currentStrategy?.onPrimaryClick()
    }

    override fun onRelease() {
        tmpFile = audioFab.popRecording()
    }

    fun persistRecording() = GlobalScope.launch {
        if (tmpFile != null) {
            var fileName = audioEt.text.toString().trimSpaces()
            if (fileName.isEmpty()) fileName = tmpFile!!.nameWithoutExtension
            fileName = fileName.plus(".").plus(tmpFile?.extension)
            val newFile = File(buildFilepath(fileName))
            tmpFile?.copyTo(newFile, overwrite = true)
            tmpFile?.delete()
            tmpFile = null

            val data = Note.NoteData(title = newFile.nameWithoutExtension,
                text = newFile.name, pinned = false,
                color = RandomColor().gen(), isRecording = true)
            model.add(data)
        }
    }

    var currentStrategy: OnClickStrategy? = null

    fun getStrategy(newStatus: Int): OnClickStrategy {
        return when (newStatus) {
            STATUS_ANNOTATE -> AnnotateStrategy(this)
            STATUS_RECORDING -> RecStrategy(this)
            STATUS_SAVE_REC -> SaveRecStrategy(this)
            else -> {
                val fragment: Fragment? =
                    supportFragmentManager.findFragmentByTag(EditFragment.javaClass.simpleName)
                when {
                    fragment != null -> {
                        fragment as EditFragment
                        when (newStatus) {
                            STATUS_CONFIRM -> fragment.ConfirmStrategy(this)
                            STATUS_CLOCK -> fragment.ClockStrategy(this)
                            STATUS_EDIT -> fragment.EditStrategy(this)
                            else -> InitStrategy(this)
                        }
                    }
                    else -> InitStrategy(this)
                }
            }
        }
    }

    fun pushStatus(newStatus: Int, strategy: OnClickStrategy = getStrategy(newStatus)) {
        if (currentStatus != newStatus) {
            currentStatus = newStatus
            currentStrategy = strategy
            currentStrategy?.initialize()
        }
    }

    abstract class OnClickStrategy(val context: MainActivity): View.OnClickListener {
        abstract fun getPrimaryDrawable(): Drawable
        open fun getSecondaryDrawable(): Drawable? = null

        open fun persistStatus() {
            context.getPreferences(Context.MODE_PRIVATE).edit().apply {
                putInt(LAST_STATUS, getStatus())
                apply()
            }
        }

        open fun initialize() {
            context.primaryButton.setImageDrawable(getPrimaryDrawable())
            context.primaryButton.setOnClickListener(this)

            context.secondaryButton.setImageDrawable(getSecondaryDrawable())
            context.secondaryButton.setOnClickListener(this)
        }

        open fun isDismissible() = true

        open fun onBackPressed(): Boolean {
            context.pushStatus(STATUS_INIT)
            return false
        }

        override fun onClick(v: View?) {
            when (v?.id) {
                context.primaryButton.id -> onPrimaryClick()
                context.secondaryButton.id -> onSecondaryClick()
            }
        }

        abstract fun onPrimaryClick()
        open fun onSecondaryClick() { }

        abstract fun getStatus(): Int
    }

    inner class InitStrategy(context: MainActivity): OnClickStrategy(context) {
        override fun getPrimaryDrawable() = resources.getDrawable(R.drawable.add, theme)
        override fun initialize() {
            super.initialize()
            retract()
            audioFab.show()
            constraintLayout.show()
        }
        override fun isDismissible() = false
        override fun onBackPressed(): Boolean = true
        override fun onPrimaryClick() {
            if (!isAnimating) {
                expandEditText()
                pushStatus(STATUS_ANNOTATE)
            }
        }

        override fun getStatus(): Int = STATUS_INIT
    }

    inner class AnnotateStrategy(context: MainActivity): OnClickStrategy(context) {
        override fun getPrimaryDrawable() = resources.getDrawable(R.drawable.done, theme)
        override fun getSecondaryDrawable(): Drawable? = context.getDrawable(R.drawable.bullets)

        override fun initialize() {
            super.initialize()
            constraintLayout.show()
            audioFab.hide()
            expandEditText()
        }

        override fun onPrimaryClick() {
            if (!isAnimating) {
                consumeInput()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(inputText.windowToken, 0)
                pushStatus(STATUS_INIT)
            }
        }

        override fun onSecondaryClick() {
            super.onSecondaryClick()
            inputText.addBulletPoint()
        }

        override fun getStatus(): Int = STATUS_ANNOTATE
    }

    inner class RecStrategy(context: MainActivity): OnClickStrategy(context) {
        override fun getPrimaryDrawable() = resources.getDrawable(R.drawable.stop, theme)
        override fun isDismissible() = false

        override fun initialize() {
            super.initialize()
            constraintLayout.show()
        }

        override fun onBackPressed(): Boolean {
            audioFab.switchOff()
            tmpFile?.delete()
            tmpFile = null
            return super.onBackPressed()
        }

        override fun onPrimaryClick() {
            audioFab.switchOff()
            expandEditText()
            pushStatus(STATUS_SAVE_REC)
        }

        override fun getStatus(): Int = STATUS_RECORDING
    }

    inner class SaveRecStrategy(context: MainActivity): OnClickStrategy(context) {
        override fun getPrimaryDrawable() = resources.getDrawable(R.drawable.done, theme)
        override fun getSecondaryDrawable(): Drawable? = context.getDrawable(R.drawable.cancel)

        override fun initialize() {
            super.initialize()
            constraintLayout.show()
            audioFab.hide()
            expandEditText()
        }

        override fun onBackPressed(): Boolean {
            tmpFile?.delete()
            tmpFile = null
            return super.onBackPressed()
        }

        override fun onPrimaryClick() {
            persistRecording()
            pushStatus(STATUS_INIT)
        }

        override fun onSecondaryClick() {
            context.onBackPressed()
        }

        override fun getStatus(): Int = STATUS_SAVE_REC
    }

    override fun onTransitionResume(transition: Transition) {
    }

    override fun onTransitionPause(transition: Transition) {
    }

    override fun onTransitionCancel(transition: Transition) {
    }

    override fun onTransitionStart(transition: Transition) {
        isAnimating = true
        val anim = bgView.background as TransitionDrawable
        when (currentStatus) {
            STATUS_ANNOTATE, STATUS_SAVE_REC -> {
                anim.startTransition((DURATION.toInt() * 1.5).toInt())
                secondaryButton.visibility = View.INVISIBLE
            }
            STATUS_INIT -> {
                anim.reverseTransition((DURATION.toInt() * 1.5).toInt())
                inputText.clearFocus()
            }
        }
    }

    override fun onTransitionEnd(transition: Transition) {
        when (currentStatus) {
            STATUS_INIT -> {
                secondaryButton.visibility = View.GONE
                inputText.visibility = View.GONE
                audioEt.visibility = View.GONE
            }
            STATUS_ANNOTATE -> {
                secondaryButton.visibility = View.VISIBLE
                audioEt.visibility = View.GONE
                inputText.visibility = View.VISIBLE
                inputText.requestFocus()
            }
            STATUS_SAVE_REC -> {
                secondaryButton.visibility = View.VISIBLE
                inputText.visibility = View.GONE
                audioEt.visibility = View.VISIBLE
                audioEt.setText(tmpFile?.nameWithoutExtension)
                audioEt.setSelection(0, audioEt.length())
                audioEt.requestFocus()
            }
            STATUS_CLOCK -> {
                secondaryButton.visibility = View.VISIBLE
                inputText.visibility = View.GONE
                audioEt.visibility = View.GONE
            }
        }
        isAnimating = false
    }


    fun buildFilepath(name: String?) = directory.absolutePath + "/" + name

    private fun String.trimSpaces() = trimStart { c -> c.isWhitespace() }.dropLastWhile { c -> c.isWhitespace() }

    inner class RandomColor {
        fun gen() : Int {
            val gen = Random(Date().time)
            return resources.getColor(palette[gen.nextInt(palette.size)], theme)
        }
    }
}
