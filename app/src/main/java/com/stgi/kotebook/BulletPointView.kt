package com.stgi.kotebook

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.text.*
import android.text.style.StrikethroughSpan
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
import android.icu.lang.UCharacter.GraphemeClusterBreak.T



class BulletPointView(context: Context, attrs: AttributeSet?, private val isInteractive: Boolean = true) : LinearLayout(context, attrs) {
    val cb: CheckBox
    private val et: EditText?
    private val tv: TextView?

    private fun applyIsInteractive(allowCheckBox: Boolean = true) {
        isClickable = isInteractive || allowCheckBox
        isEnabled = isInteractive

        et?.isFocusable = isInteractive
        et?.isFocusableInTouchMode = isInteractive
    }

    private var onCheckedChangeListener: OnCheckedChangedListener? = null

    var addOrRemoveAdapter: AddOrRemoveAdapter? = null

    override fun setOnFocusChangeListener(l: OnFocusChangeListener?) {
        et?.onFocusChangeListener = BulletFocusListener(l)
    }

    fun setOnCheckedChangeListener(l: CompoundButton.OnCheckedChangeListener?) {
        onCheckedChangeListener = OnCheckedChangedListener(l)
        cb.setOnCheckedChangeListener(onCheckedChangeListener)
    }

    override fun setClickable(clickable: Boolean) {
        cb.isClickable = clickable
        cb.isLongClickable = clickable
    }

    override fun setEnabled(enabled: Boolean) {
        cb.isEnabled = cb.isClickable
        et?.isEnabled = enabled
    }

    fun setMaxLines(lines: Int) {
        if (lines == 1) {
            et?.setSingleLine(true)
            tv?.setSingleLine()
        } else {
            et?.maxLines = lines
            tv?.maxLines = lines
        }
    }

    fun setTextColor(color: Int) {
        cb.buttonDrawable?.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        et?.setTextColor(color)
        et?.highlightColor = color
        tv?.setTextColor(color)
        tv?.highlightColor = color
    }

    fun setText(text: String) {
        setText(et, text)
        setText(tv, text)
    }

    private fun setText(v: TextView?, text: String) {
        if (v is EditText) {
            v.setText(text)
            if (cb.isChecked) v.setStrikeThrough(end = text.length)
            else v.removeStrikeThrough()
        }
        else if (v != null){
            v.text = text
            if (cb.isChecked) {
                v.paintFlags = v.paintFlags or STRIKE_THRU_TEXT_FLAG
            }
        }
    }

    fun getText(): String? {
        val str = (if (et != null) et.text else tv?.text).toString().trimSpaces()
        if (str.isEmpty()) return null
        return StringBuilder().append("\n")
            .append(if (cb.isChecked) BULLET_POINT_FULL else BULLET_POINT_EMPTY)
            .append(str)
            .toString()
    }

    fun setIsChecked(isChecked: Boolean) {
        cb.isChecked = isChecked
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (gainFocus) et?.requestFocus()
    }

    init {
        setPadding(context.resources.getDimension(R.dimen.bullet_point_start_padding).toInt(), 0, 0, 0)
        isFocusable = false

        cb = LayoutInflater.from(context).inflate(R.layout.bullet_point_checkbox, this, false) as CheckBox
        if (isInteractive) {
            et = LayoutInflater.from(context).inflate(
                R.layout.bullet_point_et,
                this,
                false
            ) as EditText
            tv = null
        } else {
            et = null
            tv = LayoutInflater.from(context).inflate(
                R.layout.bullet_point_tv,
                this,
                false
            ) as TextView
        }

        applyIsInteractive()

        addView(cb)
        addView(if (isInteractive) et else tv)

        setOnCheckedChangeListener(null)

        val editor = BulletPointEditor()
        et?.setOnEditorActionListener(editor)
        et?.setOnKeyListener(editor)
        setTextWatcher(null)
    }

    private var animator: ValueAnimator? = null

    private fun EditText.setStrikeThrough(start: Int = 0, end: Int) {
        text.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
    }

    private fun EditText.removeStrikeThrough() {
        text.getSpans(0, text.length, StrikethroughSpan::class.java).forEach {
            text.removeSpan(it)
        }
    }

    private fun EditText.startStrikeThroughAnimation(duration: Long = 200L): ValueAnimator {
        return animateStrikeThrough(duration = duration)
    }

    private fun EditText.reverseStrikeThroughAnimation(duration: Long = 200L): ValueAnimator {
        return animateStrikeThrough(true, duration)
    }

    private fun EditText.animateStrikeThrough(reverse: Boolean = false, duration: Long): ValueAnimator {
        if (animator != null) {
            animator!!.cancel()
        }
        animator = ValueAnimator.ofInt(if (reverse) text.length else 0, if (reverse) 0 else text.length)

        animator!!.duration = duration
        animator!!.addUpdateListener {
            setStrikeThrough(0, it.animatedValue as Int)
        }
        animator!!.addListener(object: Animator.AnimatorListener {
            override fun onAnimationRepeat(p0: Animator?) { }
            override fun onAnimationCancel(p0: Animator?) { }
            override fun onAnimationStart(p0: Animator?) { }
            override fun onAnimationEnd(p0: Animator?) {
                if (reverse) {
                    removeStrikeThrough()
                }
                animator = null
            }
        })
        animator!!.start()
        return animator!!
    }

    inner class OnCheckedChangedListener(private val l: CompoundButton.OnCheckedChangeListener?): CompoundButton.OnCheckedChangeListener {
        override fun onCheckedChanged(v: CompoundButton?, isChecked: Boolean) {
            l?.onCheckedChanged(v, isChecked)
            if (et != null) {
                if (isChecked) et.startStrikeThroughAnimation()
                else et.reverseStrikeThroughAnimation()
            } else if (tv != null) {
                if (isChecked) tv.paintFlags = tv.paintFlags or STRIKE_THRU_TEXT_FLAG
                else tv.paintFlags = tv.paintFlags xor STRIKE_THRU_TEXT_FLAG
            }
        }
    }

    inner class BulletPointEditor: TextView.OnEditorActionListener, OnKeyListener {
        override fun onKey(v: View?, keyCode: Int, ev: KeyEvent?): Boolean {
            if (ev?.action == KeyEvent.ACTION_UP || ev?.repeatCount != 0) return keyCode != KeyEvent.KEYCODE_BACK
            v as EditText
            when (ev.keyCode) {
                KeyEvent.KEYCODE_ENTER -> {
                    if (v.text.isNotEmpty())
                        addOrRemoveAdapter?.addAfter(this@BulletPointView)
                    //removeBulletPoint(this@BulletPointView)
                    //addBulletPoint(position = indexOfChild(v.parent as View) + 1)
                    return true
                }
                KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> {
                    if (v.text.isEmpty()) {
                        addOrRemoveAdapter?.remove(this@BulletPointView)
                        //removeBulletPoint(this@BulletPointView)
                        return true
                    }
                }
            }
            return false
        }

        override fun onEditorAction(v: TextView?, actionId: Int, ev: KeyEvent?): Boolean {
            v as EditText
            when (actionId) {
                EditorInfo.IME_ACTION_DONE, EditorInfo.IME_ACTION_NEXT -> {
                    if (v.text.isNotEmpty())
                        addOrRemoveAdapter?.addAfter(this@BulletPointView)
                    //addBulletPoint(position = indexOfChild(v.parent as View) + 1)
                    return true
                }
            }
            when (ev?.keyCode) {
                KeyEvent.KEYCODE_ENTER -> {
                    if (v.text.isNotEmpty())
                        addOrRemoveAdapter?.addAfter(this@BulletPointView)
                    //addBulletPoint(position = indexOfChild(v.parent as View) + 1)
                    return true
                }
                KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> {
                    if (v.text.isEmpty()) {
                        addOrRemoveAdapter?.remove(this@BulletPointView)
                        //removeBulletPoint(this@BulletPointView)
                        return true
                    }
                }
            }
            return false
        }
    }

    inner class BulletFocusListener(private val l: OnFocusChangeListener?):
        OnFocusChangeListener {
        override fun onFocusChange(v: View?, hasFocus: Boolean) {
            l?.onFocusChange(v, hasFocus)
            v as EditText
            if (!hasFocus && v.text.trimSpaces().isEmpty()) {
                addOrRemoveAdapter?.remove(this@BulletPointView, false)
                //removeBulletPoint(this@BulletPointView, false)
            }
        }
    }

    interface AddOrRemoveAdapter {
        fun addAfter(v: BulletPointView?)
        fun remove(v: BulletPointView?, requestFocus: Boolean = true)
    }

    private fun String.trimSpaces() = trimStart { c -> c.isWhitespace() }.dropLastWhile { c -> c.isWhitespace() }.toString()
    fun Editable.trimSpaces() = toString().trimSpaces()

    fun setTextWatcher(watcher: TextWatcher?) {
        et?.removeTextChangedListener(watcherWrapper)
        watcherWrapper = TextWatcherWrapper(watcher)
        et?.addTextChangedListener(watcherWrapper)
    }

    var watcherWrapper: TextWatcherWrapper? = null

    inner class TextWatcherWrapper(val watcher: TextWatcher?): TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            watcher?.afterTextChanged(editable)

            val str = editable?.toString() ?: ""
            if (str.endsWith("\n")) {
                et?.setText(editable?.trimSpaces())
                if (str != "\n") addOrRemoveAdapter?.addAfter(this@BulletPointView)
                return
            }

            if (editable!!.isNotEmpty() && editable.isNotEmpty()) {
                if (cb.isChecked) {
                    val spans = editable.getSpans(0, editable.length, StrikethroughSpan::class.java)
                    if (spans.isEmpty() ||
                        editable.getSpanStart(spans[0]) != 0 ||
                        editable.getSpanEnd(spans[0]) != editable.length) {
                        editable.setSpan(
                            StrikethroughSpan(), 0, editable.length,
                            Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                    }
                }
            }
        }
        override fun beforeTextChanged(str: CharSequence?, start: Int, count: Int, after: Int) {
            watcher?.beforeTextChanged(str, start, count, after)
        }
        override fun onTextChanged(str: CharSequence?, start: Int, before: Int, count: Int) {
            watcher?.onTextChanged(str, start, before, count)
        }
    }
}