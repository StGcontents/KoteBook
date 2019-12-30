package com.stgi.kotebook

import android.os.Environment
import java.io.File

const val DIRECTORY = "/KoteBook/"

val directory = File(Environment.getExternalStorageDirectory().absolutePath + DIRECTORY).also {
    if (!it.exists())
        it.mkdirs()
}

fun buildFilepath(name: String?) = directory.absolutePath + "/" + name