package com.stgi.kotebook

import android.Manifest
import android.app.AlarmManager
import android.app.AlarmManager.RTC_WAKEUP
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.PersistableBundle
import android.text.TextUtils
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.stgi.rodentia.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.layout_retracted.*
import kotlinx.android.synthetic.main.layout_retracted.view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

const val AGOUTI = 1
const val BEAVER = 2
const val CAVIA = 3
const val DORMOUSE = 4
const val ESQUIREL = 5
const val FLYING_SQUIRREL = 6

const val LAST_INSTALLED_VERSION = "last_installed_version"

const val LAST_STATUS = "currentStatus"

private const val PERMISSIONS_REQUEST = 13

class MainActivity : AppCompatActivity(), SwipeButton.OnSwipeListener,
    NotesAdapter.OnNotesChangedListener, FabStationView.FabStationController {

    private val adapter: NotesAdapter = NotesAdapter()

    private var currentStatus: Int? = null
    var currentStrategy: FabStationView.OnClickStrategy? = null

    val dismissInputListener = View.OnTouchListener { _, _ ->
        if (currentStrategy!!.isDismissible())
            currentStrategy?.onBackPressed()
        return@OnTouchListener false
    }

    private val model: NotesModel by lazy {
        ViewModelProviders.of(this)[NotesModel::class.java]
    }

    private var tmpFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootLayout.setOnTouchListener(dismissInputListener)
        writingFrame.setOnTouchListener { _, _ -> false }
        bgView.setOnTouchListener { _, _ -> true }

        adapter.onNotesChangedListener = this
        notesRecycler.adapter = adapter
        notesRecycler.layoutManager =
            getStaggeredLayout(resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
        notesRecycler.setOnTouchListener(dismissInputListener)
        notesRecycler.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (e.action == KeyEvent.ACTION_DOWN) {
                    dismissInputListener.onTouch(rv, e)
                    val child = rv.findChildViewUnder(e.x, e.y)
                    hideAllOptionsBesides(child)
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(inputText.windowToken, 0)
                }
                return false
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
        })
    }

    override fun onResume() {
        super.onResume()
        model.notes.observe(this,
            Observer<List<Note.NoteData>> { t ->
                adapter.setData(t?.map { Note(it) }
                    ?.sortedWith(compareBy({ !it.pinned }, { it.id })))
            })

        getPreferences(Context.MODE_PRIVATE).getInt(
            LAST_STATUS,
            STATUS_INIT
        ).apply {
            pushStatus(this)
        }
    }

    override fun onPause() {
        super.onPause()
        model.notes.removeObservers(this)
        currentStrategy?.let {
            getPreferences(Context.MODE_PRIVATE).edit().apply {
                putInt(LAST_STATUS, it.getStatus())
                apply()
            }
        }
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
        savedInstanceState?.let {
            pushStatus(
                it.getInt(
                    LAST_STATUS,
                    STATUS_INIT
                )
            )
        }
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
                model.add(
                    Note.NoteData(
                        text = str,
                        color = NoteGenerator(this).generateRandomColor()
                    )
                )
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

    fun showEditFragment(note: Note, view: View) {
        fabStation.setClockTo(note.timestamp)
        pushStatus(STATUS_INTERIM)
        val fragment = EditFragment.newInstance(note, view)
        supportFragmentManager.beginTransaction()
            .addToBackStack(null)
            .replace(R.id.writingFrame, fragment, EditFragment::class.simpleName)
            .commit()
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
            .make(rootLayout, getString(R.string.snackbar_message), Snackbar.LENGTH_LONG)
            .setAction(R.string.snackbar_undo) { model.add(data) }
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
                        else -> logd(
                            "Snackbar",
                            "Which is it? $event"
                        )
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
            != PackageManager.PERMISSION_GRANTED
        ) {
            /*if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                Toast.makeText(this, "Take audio notes", Toast.LENGTH_LONG).show()
            } else */
            requestPermissions(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), PERMISSIONS_REQUEST
            )
            return false
        } else {
            pushStatus(STATUS_RECORDING)
            return true
        }
    }

    override fun onAutoRelease() {
        tmpFile = fabStation.popRecording()
        if (currentStrategy is RecStrategy)
            currentStrategy?.onPrimaryClick()
    }

    override fun onRelease() {
        tmpFile = fabStation.popRecording()
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

            val data = Note.NoteData(
                title = newFile.nameWithoutExtension,
                text = newFile.name, pinned = false,
                color = NoteGenerator(this@MainActivity).generateRandomColor(), isRecording = true
            )
            model.add(data)
        }
    }

    override fun scheduleAlarm(data: Any) {
        if (data !is Note.NoteData) return

        model.update(data)
        val manager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(
            this,
            NotificationService::class.java
        )
            .putExtra(ID, data.uid)
            .putExtra(TITLE, data.title)
            .putExtra(TEXT, data.text)
            .putExtra(IS_RECORDING, data.isRecording)
            .putExtra(COLOR, data.color)

        manager.setExact(
            RTC_WAKEUP,
            if (BuildConfig.DEBUG) Date().time + 5000L else data.timestamp!!,
            PendingIntent.getService(
                this,
                13,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
    }

    private fun getStrategy(newStatus: Int): FabStationView.OnClickStrategy {
        return when (newStatus) {
            STATUS_INTERIM -> FabStationView.InterimStrategy(currentStrategy!!)
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

    override fun pushStatus(newStatus: Int) {
        pushStatus(newStatus, getStrategy(newStatus))
    }

    private fun pushStatus(newStatus: Int, strategy: FabStationView.OnClickStrategy) {
        if (currentStatus != newStatus) {
            currentStatus = newStatus
            currentStrategy = strategy
            fabStation.injectStrategy(strategy)
        }
    }

    inner class InitStrategy(context: MainActivity) : FabStationView.OnClickStrategy(context) {
        override fun getPrimaryDrawable(): Drawable? = resources.getDrawable(R.drawable.add, theme)
        override fun initialize(view: FabStationView) {
            super.initialize(view)
            fabStation.show()
        }

        override fun shapeStation(builder: FabStationView.SetBuilder) {
            super.shapeStation(builder)
            builder.showPrimary()
                .retractPrimary()
                .hideTextInput()
                .hideAudioInput()
                .hideSecondary()
                .hideTertiary()
                .showSwipe()
                .hideFan()
                .hideDatePicker()
                .apply()
        }

        override fun isDismissible() = false
        override fun onBackPressed(): Boolean = true
        override fun onPrimaryClick() {
            pushStatus(STATUS_ANNOTATE)
        }

        override fun getStatus(): Int = STATUS_INIT


        override fun onSwiped(): Boolean = this@MainActivity.onSwiped()
    }

    inner class AnnotateStrategy(context: MainActivity) : FabStationView.OnClickStrategy(context) {
        override fun getPrimaryDrawable(): Drawable? = getDrawable(R.drawable.done)
        override fun getSecondaryDrawable(): Drawable? = getDrawable(R.drawable.bullets)

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
            fabStation.inputText.clearFocus()
            pushStatus(STATUS_INIT)
        }

        override fun onSecondaryClick() {
            super.onSecondaryClick()
            inputText.addBulletPoint()
        }

        override fun getStatus(): Int = STATUS_ANNOTATE
    }

    inner class RecStrategy(context: MainActivity) : FabStationView.OnClickStrategy(context) {
        override fun getPrimaryDrawable(): Drawable? = getDrawable(R.drawable.stop)
        override fun isDismissible() = false

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
            fabStation.switchOff()
            tmpFile?.delete()
            tmpFile = null
            return super.onBackPressed()
        }

        override fun onPrimaryClick() {
            fabStation.switchOff()
            pushStatus(STATUS_SAVE_REC)
        }

        override fun getStatus(): Int = STATUS_RECORDING

        override fun onRelease() {
            this@MainActivity.onRelease()
        }

        override fun onAutoRelease() {
            this@MainActivity.onAutoRelease()
        }
    }

    inner class SaveRecStrategy(context: MainActivity) : FabStationView.OnClickStrategy(context) {
        override fun getPrimaryDrawable(): Drawable? = getDrawable(R.drawable.done)
        override fun getSecondaryDrawable(): Drawable? = getDrawable(R.drawable.cancel)

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
            onBackPressed()
        }

        override fun getStatus(): Int = STATUS_SAVE_REC
    }



    private fun String.trimSpaces() =
        trimStart { c -> c.isWhitespace() }.dropLastWhile { c -> c.isWhitespace() }
}
