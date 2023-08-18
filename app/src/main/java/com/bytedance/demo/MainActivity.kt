package com.bytedance.demo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import com.bytedance.krcview.KrcLineInfo
import com.bytedance.krcview.KrcView
import com.bytedance.lyricsview.R
import com.bytedance.lyricsview.databinding.ActivityMainBinding
import com.bytedance.lyricsview.databinding.LocatedViewBinding

class MainActivity : AppCompatActivity(), KrcView.onDraggingListener {
    companion object {
        private const val TAG = "MainActivity"
    }

    var progress = 0L
    var skipProgress = 0L
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
        // 解析krc 歌词文件并得到 krc歌词数据。
        val krcData = KrcParser.readFromAsset(this, "花海.krc")
        // 设置krc歌词数据
        vb.krcView.setKrcData(krcData)
        progress = krcData[0].startTimeMs - 100
        vb.seekBar.max = krcData!!.get(krcData.size - 1).endTimeMs().toInt()
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
        LocatedViewBinding.inflate(layoutInflater, vb.krcView, false).run {
            // 点击跳转指定进度。
            btnSeekTo.setOnClickListener {
                progress = skipProgress
                vb.krcView.setProgress(skipProgress)
                vb.seekBar.progress = skipProgress.toInt()
            }
            // 设置 located view
            vb.krcView.locatedView = this.root
        }
        // 设置拖拽歌词监听器
        vb.krcView.setOnDraggingListener(this)
    }

    override fun onStartDragging(krcView: KrcView, info: KrcLineInfo, position: Int) {
        skipProgress = info.startTimeMs
        val ms = info.startTimeMs.formatMMss()
        Log.i(TAG, "===> onStartDragging: $ms")
        krcView.locatedView?.run {
            findViewById<TextView>(R.id.tv_pro).text = ms
        }
    }

    override fun onDragging(krcView: KrcView, positionChanged: Boolean, info: KrcLineInfo, position: Int) {
        krcView.locatedView?.run {
            if (positionChanged) {
                skipProgress = info.startTimeMs
                val ms = info.startTimeMs.formatMMss()
                Log.i(TAG, "===> onDragging: positionChanged true ms:$ms")
                findViewById<TextView>(R.id.tv_pro).text = ms
            }
        }

    }


    override fun onStopDragging(krcView: KrcView, info: KrcLineInfo, position: Int) {
        Log.i(TAG, "===> onStopDragging")

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

    override fun onDestroy() {
        super.onDestroy()
        updateProgressTask.quit()
    }
}