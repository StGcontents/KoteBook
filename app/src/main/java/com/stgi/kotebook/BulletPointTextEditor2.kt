package com.stgi.kotebook

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Typeface
import android.text.*
import android.text.Layout.JUSTIFICATION_MODE_INTER_WORD
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.view.menu.ActionMenuItemView
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.children
import androidx.core.view.forEach
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.lang.StringBuilder

class BulletPointTextEditor2(context: Context, attrs: AttributeSet) : RecyclerView(context, attrs),
    BulletPointView.AddOrRemoveAdapter {

    private val bulletPointSplit = Regex(BULLET_POINT_SPLIT)
    private val mainText: EditText by lazy {
        (LayoutInflater.from(context)
            .inflate(R.layout.edit_text_layout, this@BulletPointTextEditor2, false) as EditText)
            .also {
                this@BulletPointTextEditor2.addView(it)
                it.onFocusChangeListener = focusChangeListener
                val spannable = SpannableString(context.getString(R.string.insert_text))
                spannable.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, spannable.length, SpannableString.SPAN_INCLUSIVE_INCLUSIVE)
                it.hint = spannable
                setEditTextColor(it)
                it.justificationMode = JUSTIFICATION_MODE_INTER_WORD
            }
    }

    private val adapter: TextEditorAdapter


    init {
        adapter = TextEditorAdapter()
        adapter.setData(null)
        layoutManager = LinearLayoutManager(context, VERTICAL, false)
    }

    private var focusChangeListener: OnFocusChangeListener? = null
    private var checkedChangeListener: CompoundButton.OnCheckedChangeListener? = null
    private var textColor = Color.BLACK

    fun setText(text: String?) {
        val list = ArrayList<String>()
        list.addAll(text!!.split(bulletPointSplit))
        adapter.setData(list)
    }

    fun getText(): String {
        val builder = StringBuilder()
        forEach { v ->
            when (v) {
                is EditText -> {
                    builder.append(v.text.trimSpaces())
                }
                is BulletPointView -> {
                    val str = v.getText()
                    if (!TextUtils.isEmpty(str))
                        builder.append(str)
                }
            }
        }
        return builder.toString()
    }

    override fun addAfter(v: BulletPointView?) {
        addBulletPointAfter(v)
    }

    override fun remove(v: BulletPointView?, requestFocus: Boolean) {
        if (v != null) removeBulletPoint(v, requestFocus)
    }

    private fun addBulletPointAfter(v: BulletPointView?) {
        if (v == null) addBulletPoint()
        else addBulletPoint(position = indexOfChild(v) + 1)
    }

    fun addBulletPoint(text: String = "", isChecked: Boolean = false, position: Int = childCount, focus: Boolean = true) {//: View {
        adapter.add(position, (if (isChecked) BULLET_POINT_FULL else BULLET_POINT_EMPTY) + text)
    }

    private fun removeBulletPoint(v: BulletPointView, requestFocus: Boolean = true) {
        /*val position = indexOfChild(v) - 1
        v.addOrRemoveAdapter = null
        v.setOnFocusChangeListener(null)
        removeView(v)
        if (requestFocus) {
            getChildAt(position).requestFocus()
        }*/
        adapter.remove(indexOfChild(v))
    }

    fun setTextColor(color: Int) {
        textColor = color
        forEach { view ->
            when (view) {
                is EditText -> {
                    setEditTextColor(view)
                }
                is BulletPointView-> {
                    view.setTextColor(color)
                }
            }
        }
    }

    private fun setEditTextColor(et: EditText) {
        et.setTextColor(textColor)
        et.highlightColor = textColor
        et.setHintTextColor(Color.argb(120, textColor.red, textColor.green, textColor.blue))
    }

    override fun setOnFocusChangeListener(l: OnFocusChangeListener?) {
        focusChangeListener = l
        forEach { view ->
            run {
                when (view) {
                    is EditText -> view.onFocusChangeListener = focusChangeListener
                    is BulletPointView -> view.setOnFocusChangeListener(focusChangeListener)
                }
            }
        }
    }

    fun setOnCheckedChangeListener(l: CompoundButton.OnCheckedChangeListener?) {
        checkedChangeListener = l
        forEach { view ->
            run {
                when (view) {
                    is BulletPointView -> view.setOnCheckedChangeListener(checkedChangeListener)
                }
            }
        }
    }

    override fun hasFocus(): Boolean {
        return children.any { view -> view.hasFocus() }
    }

    override fun clearFocus() {
        super.clearFocus()
        focusedChild?.clearFocus()
    }

    override fun requestFocus(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        return if (childCount == 0) mainText.requestFocus() else getChildAt(childCount - 1).requestFocus()
    }

    fun String.trimSpaces() = trimStart { c -> c.isWhitespace() }.dropLastWhile { c -> c.isWhitespace() }.toString()
    private fun Editable.trimSpaces() = toString().trimSpaces()

    private inner class TextEditorAdapter: RecyclerView.Adapter<EditorViewHolder>() {
        private val TYPE_BULLET = 0
        private val TYPE_TEXT = 1

        private var data: ArrayList<String> = arrayListOf("")

        fun setData(newData: ArrayList<String>?) {
            data = if (newData == null || newData.isEmpty()) arrayListOf("")
                else newData
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EditorViewHolder {
            val v: View
            return if (viewType == TYPE_TEXT) {
                v = (LayoutInflater.from(parent.context)
                    .inflate(R.layout.edit_text_layout, parent, false) as EditText)
                TextViewHolder(v)
            } else {
                v = BulletPointView(context, null)
                BulletViewHolder(v)
            }
        }

        override fun onBindViewHolder(holder: EditorViewHolder, position: Int) {
            holder.bind(data[position])
        }

        override fun getItemCount() = data.size

        override fun getItemViewType(position: Int) =
            if (data[position].startsWith(BULLET_POINT_FULL) || data[position].startsWith(BULLET_POINT_EMPTY))
                TYPE_BULLET else TYPE_TEXT

        fun add(position: Int = itemCount, text: String) {
            data.add(position, text)
            notifyItemInserted(position)
        }

        fun remove(position: Int) {
            data.removeAt(position)
            notifyItemRemoved(position)
        }

        fun replace(position: Int, text: String) {
            data[position] = text
        }
    }

    private abstract inner class EditorViewHolder(itemView: View): ViewHolder(itemView) {
        abstract fun getView(): View
        abstract fun bind(text: String)
        fun String.trimSpaces() = trimStart { c -> c.isWhitespace() }.dropLastWhile { c -> c.isWhitespace() }.toString()
    }

    private inner class TextViewHolder(itemView: TextView): EditorViewHolder(itemView) {
        init {
            getView().onFocusChangeListener = focusChangeListener
            val spannable = SpannableString(context.getString(R.string.insert_text))
            spannable.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, spannable.length, SpannableString.SPAN_INCLUSIVE_INCLUSIVE)
            getView().hint = spannable
            setEditTextColor(getView())
            getView().justificationMode = JUSTIFICATION_MODE_INTER_WORD
        }
        override fun getView() = itemView as EditText

        override fun bind(text: String) {
            getView().setText(text.trimSpaces())
            getView().addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(p0: Editable?) {
                    adapter.replace(adapterPosition, getView().text.toString())
                }

                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }
            })
        }
    }

    private inner class BulletViewHolder(itemView: BulletPointView): EditorViewHolder(itemView) {
        init {
            getView().addOrRemoveAdapter = this@BulletPointTextEditor2
            getView().setOnFocusChangeListener(focusChangeListener)
            getView().setOnCheckedChangeListener(checkedChangeListener)
        }
        override fun getView() = itemView as BulletPointView
        override fun bind(text: String) {
            val edited = text.trimSpaces()
            getView().setIsChecked(text.startsWith(BULLET_POINT_FULL))
            getView().setText(edited.trim { c -> c == BULLET_POINT_EMPTY || c == BULLET_POINT_FULL })
            getView().setTextWatcher(object : TextWatcher {
                override fun afterTextChanged(p0: Editable?) {
                    getView().getText()?.let { adapter.replace(adapterPosition, it) }
                }

                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }
            })
        }
    }
}