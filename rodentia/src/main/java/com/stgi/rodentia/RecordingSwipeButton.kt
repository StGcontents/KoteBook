package com.stgi.rodentia

import android.content.Context
import android.media.MediaRecorder
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import kotlinx.android.synthetic.main.layout_fab_audio.view.*
import java.io.File
import java.lang.Integer.max

const val REC_MINIMUM = 15000

class RecordingSwipeButton(context: Context, attributeSet: AttributeSet?): SwipeButton(context, attributeSet),
    VisualizerView.OnAutoReleaseListener{
    private val visualizer: VisualizerView = fabLayout.visualizer

    init {
        visualizer.outlineProvider = ViewOutlineProvider.BACKGROUND
        visualizer.clipToOutline = true

        maxDuration = max(maxDuration, REC_MINIMUM)

        val a = context.theme.obtainStyledAttributes(
            attributeSet,
            R.styleable.SwipeButton,
            0, 0
        )

        try {
            //TODO
        } finally {
            a.recycle()
        }
    }

    private var recorder: MediaRecorder? = null
    private var tmpFile: File? = null

    override fun switchOn(): Boolean {
        if (super.switchOn()) {
            fabLayout.fab.visibility = View.GONE
            visualizer.visibility = View.VISIBLE

            visualizer.callback = this

            recorder = MediaRecorder().also {
                it.setAudioSource(MediaRecorder.AudioSource.MIC)
                it.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                it.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                if (!indefinite) it.setMaxDuration(maxDuration)
                tmpFile = File.createTempFile("rec", ".aac", context.cacheDir)
                it.setOutputFile(tmpFile)
                it.prepare()
            }
            visualizer.connectToRecorder(recorder)
            recorder!!.start()

            return true
        }
        return false
    }

    override fun switchOff(isAutoRelease: Boolean): Boolean {
        visualizer.disconnectToAny()
        visualizer.callback = null

        recorder?.stop()
        recorder?.release()
        recorder = null

        fabLayout.fab.visibility = View.VISIBLE
        visualizer.visibility = View.GONE

        return super.switchOff(isAutoRelease)
    }

    fun popRecording(): File? {
        val product = tmpFile
        tmpFile = null
        return product
    }

    override fun onAutoRelease() {
        post { switchOff(true) }
    }
}