package com.stgi.kotebook

import android.view.View

abstract class AbstractListenerDecorator(private val l : View.OnClickListener) : View.OnClickListener {

    override fun onClick(p0: View?) {
        decorate()
        l.onClick(p0)
    }

    abstract fun decorate()
}