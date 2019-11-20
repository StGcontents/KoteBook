package com.stgi.kotebook

import android.Manifest
import android.animation.Animator
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.text.TextUtils
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.children
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.transition.ChangeBounds
import androidx.transition.Transition
import androidx.transition.TransitionManager
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.layout_retracted.*
import kotlinx.android.synthetic.main.note_card.view.noteTitleTv
import kotlinx.android.synthetic.main.note_card.view.noteTv
import kotlinx.android.synthetic.main.note_card.view.options
import kotlinx.android.synthetic.main.note_card.view.pinButton
import kotlinx.android.synthetic.main.note_card.view.removeButton
import kotlinx.android.synthetic.main.note_card_audio.view.*
import kotlinx.android.synthetic.main.note_card_bulleted.view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.random.Random

private const val DURATION = 150L

const val STATUS_INIT = 0
private const val STATUS_ANNOTATE = 1
private const val STATUS_VERTICAL = 2
private const val STATUS_RECORDING = 3
private const val STATUS_SAVE_REC = 4
const val STATUS_EDIT = 5
const val STATUS_CONFIRM = 6
const val STATUS_ALARM = 7

private const val NOTE_TYPE_AUDIO = R.layout.note_card_audio
private const val NOTE_TYPE_BASE = R.layout.note_card
private const val NOTE_TYPE_TITLE = R.layout.note_card_with_title
private const val NOTE_TYPE_BULLETS = R.layout.note_card_bulleted
private const val NOTE_TYPE_BULLETED_TITLE = R.layout.note_card_bulleted_with_title

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

class MainActivity : AppCompatActivity(), Transition.TransitionListener, ItemTouchHelperAdapter {

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

    var recorder: MediaRecorder? = null
    var tmpFile: File? = null
    private var isRecording: Boolean = false
    var timer: CountDownTimer? = null


    val directory = File(Environment.getExternalStorageDirectory().absolutePath + DIRECTORY).also {
        if (!it.exists())
            it.mkdirs()
    }

    fun filterNotes() = GlobalScope.launch {
        val iterator = model.getAll().iterator()
        while (iterator.hasNext()) {
            val data = iterator.next()
            if (data.isRecording && !File(getFilepath(data.text)).exists())
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

        pushStatus(STATUS_INIT)

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

        notesRecycler.adapter = adapter
        notesRecycler.layoutManager = StaggeredGridLayoutManager(getSpans(), LinearLayoutManager.VERTICAL)
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

        audioFab.setSwipeAdapter(this)
    }

    override fun onResume() {
        super.onResume()
        model.notes.observe(this,
            Observer<List<Note.NoteData>> { t ->
                adapter.setData(t?.map { Note(it) }
                    ?.sortedWith(compareBy({ !it.pinned }, { it.id })))
            })
    }

    override fun onPause() {
        super.onPause()
        model.notes.removeObservers(this)
    }

    private fun getSpans() = if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 2 else 3

    private fun consumeInput() {
        when (currentStatus) {
            STATUS_ANNOTATE -> {
                val str = inputText.getText().trimSpaces() //inputText.text.trimSpaces()
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
        animate().cancel()
        animate().translationY(height.toFloat()).start()
    }

    fun View.show() {
        animate().cancel()
        animate().translationY(0f).start()
    }

    private fun hideAllOptionsBesides(v: View?) {
        notesRecycler.children.filter { it != v }.forEach {
            val holder = notesRecycler.getChildViewHolder(it) as NotesViewHolder
            holder.hideOptions()
            if (holder is AudioViewHolder) holder.playerView.stop()
        }
    }

    fun showEditFragment(note : Note, view : View) {
        supportFragmentManager.beginTransaction()
            .addToBackStack(null)
            .replace(R.id.writingFrame, EditFragment.newInstance(note, view))
            .commit()
        constraintLayout.show()
        audioFab.hide()
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




    /** RECORDING **/

    override fun onItemMove(fromPosition: Int, toPosition: Int) = false
    override fun onItemMoveEnded(fromPosition: Int, toPosition: Int) { }

    override fun onItemSwiped(position: Int, direction: Int) {
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
        } else {
            pushStatus(STATUS_RECORDING)
            initRecording()
        }
    }

    private fun initRecording() {
        if (!isRecording) {
            isRecording = true
            audioFab.setIsRecording(true)

            recorder = MediaRecorder().also {
                it.setAudioSource(MediaRecorder.AudioSource.MIC)
                it.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                it.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                tmpFile = File.createTempFile("rec", ".aac", cacheDir)
                it.setOutputFile(tmpFile)
                it.prepare()
            }
            recorder!!.start()

            timer = object : CountDownTimer(10000L, 50L) {
                override fun onFinish() {
                    if (currentStrategy is RecStrategy)
                        currentStrategy?.onPrimaryClick()
                }

                override fun onTick(tick: Long) {
                    audioFab.updateAmplitude(recorder?.maxAmplitude)
                }
            }
            timer?.start()
        }
    }

    private fun stopRecording() {
        if (isRecording) {
            isRecording = false
            audioFab.setIsRecording(false)
            timer?.cancel()

            recorder?.stop()
            recorder?.release()
            recorder = null
        }
    }

    fun persistRecording() = GlobalScope.launch {
        if (tmpFile != null) {
            var fileName = audioEt.text.toString().trimSpaces()
            if (fileName.isEmpty()) fileName = tmpFile!!.nameWithoutExtension
            fileName = fileName.plus(".").plus(tmpFile?.extension)
            val newFile = File(getFilepath(fileName))
            tmpFile?.copyTo(newFile, overwrite = true)
            tmpFile?.delete()

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
            else -> InitStrategy(this)
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
    }

    inner class InitStrategy(context: MainActivity): OnClickStrategy(context) {
        override fun getPrimaryDrawable() = resources.getDrawable(R.drawable.add, theme)
        override fun initialize() {
            super.initialize()
            retract()
            audioFab.show()
        }
        override fun isDismissible() = false
        override fun onBackPressed(): Boolean = true
        override fun onPrimaryClick() {
            if (!isAnimating) {
                expandEditText()
                pushStatus(STATUS_ANNOTATE)
            }
        }
    }

    inner class AnnotateStrategy(context: MainActivity): OnClickStrategy(context) {
        override fun getPrimaryDrawable() = resources.getDrawable(R.drawable.done, theme)
        override fun getSecondaryDrawable(): Drawable? = context.getDrawable(R.drawable.bullets)

        override fun initialize() {
            super.initialize()
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
    }

    inner class RecStrategy(context: MainActivity): OnClickStrategy(context) {
        override fun getPrimaryDrawable() = resources.getDrawable(R.drawable.stop, theme)
        override fun isDismissible() = false
        override fun onBackPressed(): Boolean {
            timer?.cancel()
            tmpFile?.delete()
            stopRecording()
            return super.onBackPressed()
        }

        override fun onPrimaryClick() {
            timer?.cancel()
            stopRecording()
            expandEditText()
            pushStatus(STATUS_SAVE_REC)
        }
    }

    inner class SaveRecStrategy(context: MainActivity): OnClickStrategy(context) {
        override fun getPrimaryDrawable() = resources.getDrawable(R.drawable.done, theme)
        override fun getSecondaryDrawable(): Drawable? = context.getDrawable(R.drawable.cancel)

        override fun initialize() {
            super.initialize()
            audioFab.hide()
            expandEditText()
        }

        override fun onBackPressed(): Boolean {
            tmpFile?.delete()
            return super.onBackPressed()
        }

        override fun onPrimaryClick() {
            persistRecording()
            pushStatus(STATUS_INIT)
        }

        override fun onSecondaryClick() {
            context.onBackPressed()
        }
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
            STATUS_ALARM -> {
                secondaryButton.visibility = View.VISIBLE
                inputText.visibility = View.GONE
                audioEt.visibility = View.GONE
            }
        }
        isAnimating = false
    }


    inner class NotesAdapter : RecyclerView.Adapter<NotesViewHolder>(), View.OnClickListener {
        private val items = mutableListOf<Note>()
        private var recycler : RecyclerView? = null

        init {
            items.sortWith(compareBy({it.pinned}, {it.id}))
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotesViewHolder {
            val view: View = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
            return if (viewType == NOTE_TYPE_AUDIO) AudioViewHolder(view, this)
            else TextViewHolder(view, this)
        }

        override fun getItemCount() = items.size

        override fun getItemViewType(position: Int): Int {
            val note = items[position]
            return when {
                note.isRecording -> NOTE_TYPE_AUDIO
                note.title.isNotEmpty() && note.text?.any { c -> c == BULLET_POINT_FULL || c == BULLET_POINT_EMPTY }!! ->
                        NOTE_TYPE_BULLETED_TITLE
                note.title.isNotEmpty() -> NOTE_TYPE_TITLE
                note.text?.any { c -> c == BULLET_POINT_FULL || c == BULLET_POINT_EMPTY }!! -> NOTE_TYPE_BULLETS
                else -> NOTE_TYPE_BASE
            }
        }

        override fun onBindViewHolder(holder: NotesViewHolder, position: Int) {
            holder.bind(items[position])
            holder.hideOptions()
        }

        override fun onClick(v: View?) {
            if (v is ImageButton) {
                val position = recycler?.getChildAdapterPosition(v.parent.parent as View) ?: -1
                when (v.tag) {
                    "rmv" -> removeNote(position)
                    "pin" -> {
                        pinNote(position, !v.isSelected)

                        v.isSelected = !v.isSelected
                        v.visibility = if (v.isSelected) View.VISIBLE else View.GONE
                        v.alpha = if (v.isSelected) 0.5f else 0f
                        (recycler?.findContainingViewHolder(v.parent as ViewGroup) as NotesViewHolder).hideOptions()
                    }
                }
            }
        }

        private fun removeNote(position : Int) {
            if (position < 0 || position > items.size) return
            val data = items[position].toData()
            model.remove(data)
            if (data.isRecording) {
                File(data.text).also {
                    if (it.exists())
                        it.delete()
                }
            }
        }

        private fun pinNote(position: Int, pinned: Boolean) {
            if (position < 0 || position > items.size) return
            val data = items[position].toData()
            data.pinned = pinned
            model.update(data)
        }

        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
            recycler = recyclerView
        }

        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            super.onDetachedFromRecyclerView(recyclerView)
            recycler = null
        }

        fun setData(data: List<Note>?) {
            val oldItems = ArrayList<Note>().also { it.addAll(items) }
            items.clear()
            items.addAll(data!!)
            val diffCallback = NotesDiffUtilsCallback(oldItems, items)
            val diffResult = DiffUtil.calculateDiff(diffCallback)
            diffResult.dispatchUpdatesTo(this)
        }

        inner class NotesDiffUtilsCallback(private val oldNotes: List<Note>, private val newNotes: List<Note>): DiffUtil.Callback() {

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                oldNotes[oldItemPosition] == newNotes[newItemPosition]

            override fun getOldListSize() = oldNotes.size

            override fun getNewListSize() = newNotes.size

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldNote = oldNotes[oldItemPosition]
                val newNote = newNotes[newItemPosition]
                val result = oldNote.id == newNote.id && oldNote.text == newNote.text &&
                        oldNote.title == newNote.title && oldNote.pinned == newNote.pinned &&
                        oldNote.getColor() == newNote.getColor() && oldNote.isRecording == newNote.isRecording
                return result
            }
        }
    }

    abstract inner class NotesViewHolder(itemView : View) : RecyclerView.ViewHolder(itemView), View.OnTouchListener {

        protected var note : Note? = null

        private val rmvButton : ImageButton = itemView.removeButton
        private val pinButton : ImageButton = itemView.pinButton
        private val options : ViewGroup = itemView.options

        private val hideRunnable = Runnable { hideOptions() }
        private var areOptionsVisible = true

        private val gestures = object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent?): Boolean {
                if (areOptionsVisible)
                    hideOptions()
                else showOptions()
                return super.onSingleTapUp(e)
            }

            override fun onDoubleTap(e: MotionEvent?): Boolean {
                if (this@NotesViewHolder is AudioViewHolder) this@NotesViewHolder.playerView.stop()
                (itemView.context as MainActivity).showEditFragment(note!!, itemView)
                return super.onDoubleTap(e)
            }

            override fun onLongPress(e: MotionEvent?) {
                showOptions()
                super.onLongPress(e)
            }
        }

        private val detector = GestureDetector(itemView.context, gestures)

        constructor(itemView: View, cl : View.OnClickListener) : this(itemView) {
            this.apply {
                itemView.setOnTouchListener(this)
                val wrapperListener = getWrapper(cl)
                rmvButton.setOnClickListener(wrapperListener)
                pinButton.setOnClickListener(wrapperListener)
            }
        }

        open fun getWrapper(cl: View.OnClickListener) = object : AbstractListenerDecorator(cl) {
            override fun decorate() {
                scheduleHideOptions()
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        final override fun onTouch(v: View?, ev: MotionEvent?): Boolean {
            if (v is ImageButton) {
                return false
            }
            if (v is ImageView || (v is CheckBox && v.isClickable)) {
                return true
            }
            detector.onTouchEvent(ev)

            return true
        }

        private fun showOption(button: ImageButton) {
            button.animate().cancel()
            button.animate()
                .setDuration(100)
                .alpha(0.5f)
                .setListener(object: Animator.AnimatorListener {
                    override fun onAnimationRepeat(p0: Animator?) { }
                    override fun onAnimationCancel(p0: Animator?) { }
                    override fun onAnimationStart(p0: Animator?) {
                        button.visibility = View.VISIBLE
                    }
                    override fun onAnimationEnd(p0: Animator?) {
                        button.alpha = 0.5f
                        areOptionsVisible = true
                    }
                })
                .start()
        }

        private fun hideOption(button: ImageButton) {
            button.animate().cancel()
            button.animate()
                .setDuration(100)
                .alpha(0f)
                .setListener(object: Animator.AnimatorListener {
                    override fun onAnimationRepeat(p0: Animator?) { }
                    override fun onAnimationCancel(p0: Animator?) { }
                    override fun onAnimationStart(p0: Animator?) { }
                    override fun onAnimationEnd(p0: Animator?) {
                        button.visibility = View.GONE
                        button.alpha = 0f
                        areOptionsVisible = false
                    }
                })
                .start()
        }

        fun showOptions() {
            if (!areOptionsVisible) {
                areOptionsVisible = true
                cancelHideOptions()

                showOption(rmvButton)
                showOption(pinButton)

                scheduleHideOptions()
            }
        }

        private fun cancelHideOptions() {
            options.removeCallbacks(hideRunnable)
        }

        protected fun scheduleHideOptions() {
            cancelHideOptions()
            options.postDelayed(hideRunnable, 5000L)
        }

        fun hideOptions() {
            if (areOptionsVisible) {
                areOptionsVisible = false
                cancelHideOptions()
                hideOption(rmvButton)
                if (!pinButton.isSelected) hideOption(pinButton)
            }
        }

        open fun bind(note: Note) {
            this.note = note

            itemView as CardView
            itemView.setCardBackgroundColor(note.getColor())

            rmvButton.setColorFilter(note.getTextColor(), PorterDuff.Mode.SRC_ATOP)
            pinButton.setColorFilter(note.getTextColor(), PorterDuff.Mode.SRC_ATOP)
            pinButton.isSelected = note.pinned
            pinButton.visibility = if (note.pinned) View.VISIBLE else View.GONE
            pinButton.alpha = if (note.pinned) 0.5f else 0f
        }
    }

    inner class AudioViewHolder(itemView : View, cl : View.OnClickListener) : NotesViewHolder(itemView, cl) {
        private val titleTv : TextView = itemView.audioTitleTv
        internal val playerView: CassettePlayerView = itemView.playerView

        init {
            playerView.parent.requestDisallowInterceptTouchEvent(true)
        }

        override fun getWrapper(cl: View.OnClickListener) = object : AbstractListenerDecorator(cl) {
            override fun decorate() {
                scheduleHideOptions()
                //playerView.stop()
            }
        }

        override fun bind(note: Note) {
            super.bind(note)

            titleTv.text = note.title
            titleTv.setTextColor(note.getTextColor())

            val file = File(getFilepath(note.text))
            if (file.exists()) {
                playerView.setContentUri(Uri.fromFile(file))
            }

            playerView.setColorFilter(note.getTextColor())
        }
    }

    inner class TextViewHolder(itemView : View, cl : View.OnClickListener) : NotesViewHolder(itemView, cl) {
        private val titleTv : TextView = itemView.noteTitleTv
        private val textTv : TextView = itemView.noteTv

        override fun bind(note : Note) {
            super.bind(note)

            if (note.title.isNotEmpty()) {
                titleTv.visibility = View.VISIBLE
                titleTv.text = note.title
            } else {
                titleTv.visibility = View.GONE
                titleTv.text = ""
            }
            titleTv.setTextColor(note.getTextColor())
            textTv.setTextColor(note.getTextColor())

            val trimmedStr = note.text!!.trimSpaces()
            val indexOfFirstBullet = trimmedStr.indexOfFirst { c -> c == BULLET_POINT_FULL || c == BULLET_POINT_EMPTY }
            textTv.visibility = if (indexOfFirstBullet == 0) View.GONE else View.VISIBLE
            if (itemView.bulletContainer != null) itemView.bulletContainer.removeAllBullets()

            if (indexOfFirstBullet < 0) {
                textTv.text = trimmedStr
            } else {
                textTv.text = trimmedStr.subSequence(0, indexOfFirstBullet).toString().trimSpaces()
                val bullets = note.text?.split(Regex(BULLET_POINT_SPLIT))!!.filter { s ->
                    s.startsWith(BULLET_POINT_EMPTY) || s.startsWith(BULLET_POINT_FULL)
                }
                addBulletPoints(bullets)
            }
        }

        private fun addBulletPoints(bullets: List<String>) {
            val availableSpace = resources.getDimension(R.dimen.card_max_height).toInt()
            val bulletCount = bullets.size
            val allowedCount =
                availableSpace / resources.getDimension(R.dimen.bullet_point_height).toInt()

            var counter: Int = allowedCount
            bullets.forEach {
                if (counter <= 0) return@forEach

                val v = BulletPointView(itemView.context, null, false)
                v.setIsChecked(it[0] == BULLET_POINT_FULL)
                v.setText(it.substring(1).trimSpaces())
                v.setMaxLines(2)
                v.setTextColor(note!!.getTextColor())
                v.setIsChecked(it[0] == BULLET_POINT_FULL)
                v.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
                    var charCounter = 0
                    var bulletCounter = 0
                    var charPosition = 0
                    val position = bullets.indexOf(it)
                    note!!.text!!.forEach { c ->
                        if (c == BULLET_POINT_EMPTY || c == BULLET_POINT_FULL) {
                            if (bulletCounter == position) {
                                charPosition = charCounter
                            }
                            bulletCounter++
                        }
                        charCounter++
                    }

                    val newBullet = if (isChecked) BULLET_POINT_FULL else BULLET_POINT_EMPTY
                    val builder = StringBuilder(note!!.text!!)
                    builder.setCharAt(charPosition, newBullet)
                    val editedText = builder.toString()
                    val data = note!!.toData()
                    data.text = editedText
                    model.update(data)
                })
                itemView.bulletContainer.addView(v)

                counter--
            }

            if (allowedCount < bulletCount) {
                itemView.ellipsisTv?.setTextColor(textTv.currentTextColor)
                itemView.ellipsisTv?.visibility = View.VISIBLE
            } else {
                itemView.ellipsisTv?.visibility = View.GONE
            }
        }

        private fun ViewGroup.removeAllBullets() {
            while (childCount > 1) {
                val child = getChildAt(childCount - 1)
                if (child is BulletPointView || (child is TextView && child.text == "..."))
                    removeViewAt(childCount - 1)
            }
            if (getChildAt(0) is BulletPointView)
                removeViewAt(0)
        }
    }



    fun getFilepath(name: String?) = directory.absolutePath + "/" + name

    fun String.trimSpaces() = trimStart { c -> c.isWhitespace() }.dropLastWhile { c -> c.isWhitespace() }

    inner class RandomColor {
        fun gen() : Int {
            val gen = Random(Date().time)
            return resources.getColor(palette[gen.nextInt(palette.size)], theme)
        }
    }
}
