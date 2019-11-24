package com.stgi.kotebook

import android.animation.Animator
import android.annotation.SuppressLint
import android.graphics.PorterDuff
import android.net.Uri
import android.view.*
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.stgi.rodentia.CassettePlayerView
import kotlinx.android.synthetic.main.note_card.view.*
import kotlinx.android.synthetic.main.note_card_audio.view.*
import kotlinx.android.synthetic.main.note_card_bulleted.view.*
import java.io.File

private const val NOTE_TYPE_AUDIO = R.layout.note_card_audio
private const val NOTE_TYPE_BASE = R.layout.note_card
private const val NOTE_TYPE_TITLE = R.layout.note_card_with_title
private const val NOTE_TYPE_BULLETS = R.layout.note_card_bulleted
private const val NOTE_TYPE_BULLETED_TITLE = R.layout.note_card_bulleted_with_title

class NotesAdapter : RecyclerView.Adapter<NotesAdapter.NotesViewHolder>(), View.OnClickListener {
    private val items = mutableListOf<Note>()
    private var recycler : RecyclerView? = null
    var onNotesChangedListener: OnNotesChangedListener? = null

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
        onNotesChangedListener?.onNoteRemoved(items[position].toData())
    }

    private fun pinNote(position: Int, pinned: Boolean) {
        if (position < 0 || position > items.size) return
        val data = items[position].toData()
        data.pinned = pinned
        onNotesChangedListener?.onNoteUpdated(data)
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


    interface OnNotesChangedListener {
        fun onNoteAdded(data: Note.NoteData)
        fun onNoteUpdated(data: Note.NoteData)
        fun onNoteRemoved(data: Note.NoteData)
    }


    abstract inner class NotesViewHolder(itemView : View) : RecyclerView.ViewHolder(itemView), View.OnTouchListener {

        protected var note : Note? = null

        private val rmvButton : ImageButton = itemView.findViewById(R.id.removeButton)
        private val pinButton : ImageButton = itemView.findViewById(R.id.pinButton)
        private val options : ViewGroup = itemView.findViewById(R.id.options)

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

            val file = File((itemView.context as MainActivity).buildFilepath(note.text))
            if (file.exists()) {
                playerView.setContentUri(Uri.fromFile(file))
            }

            playerView.setCassetteColor(note.getTextColor())
        }
    }

    inner class TextViewHolder(itemView : View, cl : View.OnClickListener) : NotesViewHolder(itemView, cl) {
        private val titleTv : TextView = itemView.findViewById(R.id.noteTitleTv)
        private val textTv : TextView = itemView.findViewById(R.id.noteTv)

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
            val availableSpace = itemView.context.resources.getDimension(R.dimen.card_max_height).toInt()
            val bulletCount = bullets.size
            val allowedCount =
                availableSpace / itemView.context.resources.getDimension(R.dimen.bullet_point_height).toInt()

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

                    onNotesChangedListener?.onNoteUpdated(data)
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


    fun String.trimSpaces() = trimStart { c -> c.isWhitespace() }.dropLastWhile { c -> c.isWhitespace() }
}