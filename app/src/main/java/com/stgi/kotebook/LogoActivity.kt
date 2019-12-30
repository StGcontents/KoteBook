package com.stgi.kotebook

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProviders
import kotlinx.android.synthetic.main.activity_logo.*
import kotlinx.coroutines.*
import java.io.File
import java.lang.Runnable
import java.util.concurrent.CyclicBarrier

class LogoActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logo)
    }

    private val model: NotesModel by lazy {
        ViewModelProviders.of(this)[NotesModel::class.java]
    }

    private val logoRunnable = Runnable { ivLogo.fadeIn() }
    private val progressRunnable = Runnable { progress.fadeIn(500L) }
    private val minLoadingTimeRunnable = Runnable { GlobalScope.async { barrier.await() } }
    private val launchRunnable = Runnable {
        handler.removeCallbacks(logoRunnable)
        handler.removeCallbacks(progressRunnable)

        startActivity(Intent(this@LogoActivity, MainActivity::class.java))
        finish()
    }

    private val handler = Handler(Looper.getMainLooper())

    private val barrier = CyclicBarrier(3, Runnable {
        handler.post(launchRunnable)
    })

    private var filterAsync: Deferred<Unit>? = null
    private var notesAsync: Deferred<Int>? = null
    private var minTimeAsync: Deferred<Int>? = null

    override fun onResume() {
        super.onResume()
        handler.post(logoRunnable)
        handler.postDelayed(progressRunnable, 2000L)

        filterAsync = filterNotesAsync()
        notesAsync = createInitialNotesAsync()
        minTimeAsync = loadingTimeAsync()

        GlobalScope.async {
            filterAsync?.await()
            notesAsync?.await()
            minTimeAsync?.await()

            filterAsync = null
            notesAsync = null
            minTimeAsync = null

            handler.post(launchRunnable)
        }
    }

    override fun onStop() {
        super.onStop()

        filterAsync?.cancel()
        notesAsync?.cancel()
        minTimeAsync?.cancel()
    }

    private fun filterNotesAsync() = GlobalScope.async {
        val iterator = model.getAll().iterator()
        while (iterator.hasNext()) {
            val data = iterator.next()
            if (data.isRecording && !File(buildFilepath(data.text)).exists())
                model.remove(data)
        }
    }

    private fun createInitialNotesAsync() = GlobalScope.async {
        getPreferences(Context.MODE_PRIVATE).apply {
            val lastInstalledVersion = getInt(LAST_INSTALLED_VERSION, 0)
            if (lastInstalledVersion < BuildConfig.VERSION_CODE) {
                edit().putInt(LAST_INSTALLED_VERSION, BuildConfig.VERSION_CODE).apply()
                model.add(*NoteGenerator(this@LogoActivity).generateNotes(lastInstalledVersion))
            }
        }
        0
    }

    private fun loadingTimeAsync() = GlobalScope.async {
        delay(1500L)
        0
    }

    private fun View.fadeIn(duration: Long = 250L) {
        animate().apply {
            cancel()
            alpha(1f)
            this.duration = duration
            start()
        }
    }

    private fun CyclicBarrier.safeAwait() {
        Thread(Runnable { await() }).start()
    }
}