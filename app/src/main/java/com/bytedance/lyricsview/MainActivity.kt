package com.bytedance.lyricsview

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import com.bytedance.lyricsview.databinding.ActivityMainBinding
import com.bytedance.lyricsview.databinding.ScrollLocateViewBinding

class MainActivity : AppCompatActivity() {
    var progress = 0L
    val updateProgressTask = LoopTask(interval = 50) {
        progress += 100
        vb.krcView.setProgress(progress)
        vb.seekBar.progress = progress.toInt()
    }
    lateinit var vb: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityMainBinding.inflate(layoutInflater)
        setContentView(vb.root)
        val krcData = KrcParser.readFromAsset(this, "花海.krc")
        vb.krcView.setKrcData(krcData)
        val scrollLocateView=ScrollLocateViewBinding.inflate(layoutInflater)
        vb.krcView.setScrollLocateView(scrollLocateView.root)
        progress = krcData[0].startTimeMs - 100
        vb.seekBar.max = krcData!!.get(krcData.size - 1).endTimeMs().toInt() + 10000
        vb.seekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {

            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                progress = seekBar!!.progress.toLong()
                vb.krcView.setProgress(progress)
            }

        })
    }

    fun onClickPlayPause(view: View) {
        val btn = view as TextView
        when (btn.text) {
            "播放" -> {
                updateProgressTask.start()
                btn.text = "暂停"
            }
            "暂停" -> {
                updateProgressTask.paused()
                btn.text = "播放"
            }
        }
    }
}