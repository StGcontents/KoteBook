package com.stgi.kotebook

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Environment
import android.os.PersistableBundle
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
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
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.stgi.rodentia.SwipeButton
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.layout_fab_station.*
import kotlinx.android.synthetic.main.layout_retracted.audioEt
import kotlinx.android.synthetic.main.layout_retracted.bgView
import kotlinx.android.synthetic.main.layout_retracted.inputText
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
import kotlin.random.Random

private const val DURATION = 150L

const val LAST_STATUS = "currentStatus"

const val REC_HIDDEN_FLAGS = STATUS_ANNOTATE + STATUS_SAVE_REC + STATUS_EDIT + STATUS_CONFIRM + STATUS_CLOCK

private const val PERMISSIONS_REQUEST = 13

const val DIRECTORY = "/KoteBook/"

class MainActivity : AppCompatActivity(), SwipeButton.OnSwipeListener,
    NotesAdapter.OnNotesChangedListener {

    private val adapter : NotesAdapter = NotesAdapter()

    private var currentStatus: Int? = null
    var currentStrategy: FabStationView.OnClickStrategy? = null

    val dismissInputListener = View.OnTouchListener { _, _ ->
        if (currentStrategy!!.isDismissible())
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
    }

    override fun onResume() {
        super.onResume()
        model.notes.observe(this,
            Observer<List<Note.NoteData>> { t ->
                adapter.setData(t?.map { Note(it) }
                    ?.sortedWith(compareBy({ !it.pinned }, { it.id })))
            })

        getPreferences(Context.MODE_PRIVATE).getInt(LAST_STATUS, STATUS_INIT).apply {
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

    fun FabStationView.hide() {
        post {
            animate().cancel()
            animate().apply {
                translationY(height.toFloat())
                start()
            }
        }
    }

    fun FabStationView.show() {
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
            .replace(R.id.writingFrame, fragment, EditFragment::class.simpleName)
            .commit()
        pushStatus(STATUS_EDIT, fragment.EditStrategy(this))
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
        tmpFile = swipeFab.popRecording()
        if (currentStrategy is RecStrategy)
            currentStrategy?.onPrimaryClick()
    }

    override fun onRelease() {
        tmpFile = swipeFab.popRecording()
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

    fun getStrategy(newStatus: Int): FabStationView.OnClickStrategy {
        return when (newStatus) {
            STATUS_ANNOTATE -> AnnotateStrategy(this)
            STATUS_RECORDING -> RecStrategy(this)
            STATUS_SAVE_REC -> SaveRecStrategy(this)
            else -> {
                val fragment: Fragment? =
                    supportFragmentManager.findFragmentByTag(EditFragment::class.simpleName)
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

    fun pushStatus(newStatus: Int, strategy: FabStationView.OnClickStrategy = getStrategy(newStatus)) {
        if (currentStatus != newStatus) {
            currentStatus = newStatus
            currentStrategy = strategy
            fabStation.injectStrategy(strategy)
        }
    }

    inner class InitStrategy(context: MainActivity): FabStationView.OnClickStrategy(context) {
        override fun getPrimaryDrawable() = resources.getDrawable(R.drawable.add, theme)
        override fun initialize(view: FabStationView) {
            super.initialize(view)
            fabStation.show()
        }

        override fun shapeStation(builder: FabStationView.SetBuilder) {
            super.shapeStation(builder)
            builder.showPrimary()
                .retractPrimary()
                .hideSecondary()
                .hideTertiary()
                .showSwipe()
                .hideFan()
                .apply()
        }

        override fun isDismissible() = false
        override fun onBackPressed(): Boolean = true
        override fun onPrimaryClick() {
            pushStatus(STATUS_ANNOTATE)
        }

        override fun getStatus(): Int = STATUS_INIT


        override fun onSwiped(): Boolean = activity.onSwiped()
    }

    inner class AnnotateStrategy(context: MainActivity): FabStationView.OnClickStrategy(context) {
        override fun getPrimaryDrawable() = resources.getDrawable(R.drawable.done, theme)
        override fun getSecondaryDrawable(): Drawable? = activity.getDrawable(R.drawable.bullets)

        override fun initialize(view: FabStationView) {
            super.initialize(view)
        }

        override fun shapeStation(builder: FabStationView.SetBuilder) {
            super.shapeStation(builder)
            builder.expandPrimary()
                .showSecondary()
                .showTextInput()
                .hideTertiary()
                .hideSwipe()
                .hideFan()
                .apply()
        }

        override fun onPrimaryClick() {
            consumeInput()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(inputText.windowToken, 0)
            pushStatus(STATUS_INIT)
        }

        override fun onSecondaryClick() {
            super.onSecondaryClick()
            inputText.addBulletPoint()
        }

        override fun getStatus(): Int = STATUS_ANNOTATE
    }

    inner class RecStrategy(context: MainActivity): FabStationView.OnClickStrategy(context) {
        override fun getPrimaryDrawable() = resources.getDrawable(R.drawable.stop, theme)
        override fun isDismissible() = false

        override fun initialize(view: FabStationView) {
            super.initialize(view)
        }

        override fun shapeStation(builder: FabStationView.SetBuilder) {
            super.shapeStation(builder)
            builder.showPrimary()
                .retractPrimary()
                .hideSecondary()
                .hideTertiary()
                .showSwipe()
                .hideFan()
                .apply()
        }

        override fun onBackPressed(): Boolean {
            swipeFab.switchOff()
            tmpFile?.delete()
            tmpFile = null
            return super.onBackPressed()
        }

        override fun onPrimaryClick() {
            swipeFab.switchOff()
            pushStatus(STATUS_SAVE_REC)
        }

        override fun getStatus(): Int = STATUS_RECORDING

        override fun onRelease() { activity.onRelease() }
        override fun onAutoRelease() { activity.onAutoRelease() }
    }

    inner class SaveRecStrategy(context: MainActivity): FabStationView.OnClickStrategy(context) {
        override fun getPrimaryDrawable() = resources.getDrawable(R.drawable.done, theme)
        override fun getSecondaryDrawable(): Drawable? = activity.getDrawable(R.drawable.cancel)

        override fun initialize(view: FabStationView) {
            super.initialize(view)
            tmpFile?.let { audioEt.setText(it.nameWithoutExtension) }
        }

        override fun shapeStation(builder: FabStationView.SetBuilder) {
            super.shapeStation(builder)
            builder.expandPrimary()
                .showSecondary()
                .showAudioInput()
                .hideTertiary()
                .hideSwipe()
                .hideFan()
                .apply()
        }

        override fun onBackPressed(): Boolean {
            tmpFile?.delete()
            tmpFile = null
            audioEt.text = null
            return super.onBackPressed()
        }

        override fun onPrimaryClick() {
            persistRecording()
            audioEt.text = null
            pushStatus(STATUS_INIT)
        }

        override fun onSecondaryClick() {
            activity.onBackPressed()
        }

        override fun getStatus(): Int = STATUS_SAVE_REC
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
