package com.stgi.kotebook

import android.animation.Animator
import android.annotation.SuppressLint
import android.graphics.PorterDuff
import android.view.*
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.stgi.rodentia.BULLET_POINT_EMPTY
import com.stgi.rodentia.BULLET_POINT_FULL
import com.stgi.rodentia.CassettePlayerView
import kotlinx.android.synthetic.main.note_card_audio.view.*
import java.io.File

private const val NOTE_TYPE_AUDIO = R.layout.note_card_audio
private const val NOTE_TYPE_BASE = R.layout.note_card

class NotesAdapter : RecyclerView.Adapter<NotesAdapter.NotesViewHolder>(), View.OnClickListener {
    private val items = mutableListOf<Note>()
    private var recycler : RecyclerView? = null
    var onNotesChangedListener: OnNotesChangedListener? = null

    init {
        items.sortWith(compareBy({it.isTutorial}, {it.pinned}, {it.id}))
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
                    oldNote.getColor() == newNote.getColor() && oldNote.isRecording == newNote.isRecording &&
                    oldNote.timestamp == newNote.timestamp
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

        private val rmvButton : ImageButton = itemView.findViewById(R.id.btnRmv)
        private val pinButton : ImageButton = itemView.findViewById(R.id.btnPin)
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
            if (v is CheckBox && v.isClickable) {
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

            if (itemView is CardView)
                itemView.setCardBackgroundColor(note.getColor())
            else
                itemView.setBackgroundColor(note.getColor())

            rmvButton.setColorFilter(note.getTextColor(), PorterDuff.Mode.SRC_ATOP)
            pinButton.setColorFilter(note.getTextColor(), PorterDuff.Mode.SRC_ATOP)
            pinButton.isSelected = note.pinned
            pinButton.visibility = if (note.pinned) View.VISIBLE else View.GONE
            pinButton.alpha = if (note.pinned) 0.5f else 0f
        }
    }

    inner class AudioViewHolder(itemView : View, cl : View.OnClickListener) : NotesViewHolder(itemView, cl) {
        private val titleTv: TextView = itemView.audioTitleTv
        internal val playerView: CassettePlayerView = itemView.playerView

        init {
            playerView.parent.requestDisallowInterceptTouchEvent(true)
        }

        override fun getWrapper(cl: View.OnClickListener) = object : AbstractListenerDecorator(cl) {
            override fun decorate() {
                scheduleHideOptions()
            }
        }

        override fun bind(note: Note) {
            super.bind(note)

            titleTv.text = note.title
            titleTv.setTextColor(note.getTextColor())

            val file =
                if (note.isTutorial) getAudioFromAssets(itemView.context, R.string.tutorial_audio)
                else File(getFilepath(note.text))
            if (file.exists() || note.isTutorial) {
                playerView.setContentFile(file)
            }

            playerView.setCassetteColor(note.getTextColor())
        }
    }

    inner class TextViewHolder(itemView : View, cl : View.OnClickListener) : NotesViewHolder(itemView, cl), NoteCardView.OnCheckedChangeListener {
        override fun bind(note : Note) {
            super.bind(note)

            itemView as NoteCardView
            itemView.listener = this
            itemView.setTextColor(note.getTextColor())
            itemView.shapeShift(note.isTutorial).apply {
                title = note.title
                text = note.text!!
            }.apply()
        }

        override fun onCheckedChanged(isChecked: Boolean, position: Int) {
            var charCounter = 0
            var bulletCounter = 0
            var charPosition = 0
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
        }
    }
}