package com.bytedance.demo

import android.os.Handler
import android.os.Looper
import android.util.Log

/**

 * Author：岑胜德 on 2022/12/30 18:31

 * 说明：

 */
class LoopTask(
    private val interval: Long = 900,
    isUIThread: Boolean = true,
    private val block: () -> Unit
) : Runnable {
    companion object {
        private const val TAG = "UpdateProgressTask"
        private const val THREAD_STATE_IDLE = 0
        private const val THREAD_STATE_PAUSED = 1
        private const val THREAD_STATE_WORKING = 2
    }

    private val lock by lazy {
        Object()
    }
    private var workThread: Thread? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    @Volatile
    private var threadState = 0

    init {
        if (!isUIThread) {
            workThread = Thread(this, "UpdateProgressTask-thread")
        }
    }

    fun paused() {
        if (workThread != null) {
            if (threadState == THREAD_STATE_WORKING) {
                threadState = THREAD_STATE_PAUSED
            }
        } else {
            mainHandler.removeCallbacks(this)
        }
    }

    fun start() {
        if (workThread != null) {
            when (threadState) {
                THREAD_STATE_IDLE -> {
                    threadState = THREAD_STATE_WORKING
                    workThread?.start()
                    waitSafely()
                }
                THREAD_STATE_PAUSED -> {
                    threadState = THREAD_STATE_WORKING
                    notifySafely()
                }
                else -> {
                    return
                }
            }
        } else {
            mainHandler.post(this)
        }
    }


    fun quit() {
        if (workThread != null) {
            threadState = THREAD_STATE_IDLE
            notifySafely()
            workThread?.join()
            workThread = null
        } else {
            mainHandler.removeCallbacks(this)
        }
    }

    override fun run() {
        if (workThread != null) {
            notifySafely()
            while (true) {
                if (threadState == THREAD_STATE_PAUSED) {
                    Log.i(TAG, "run: ===> paused")
                    waitSafely()
                    Log.i(TAG, "run: ===> resumed")
                }
                if (threadState == THREAD_STATE_IDLE) {
                    break
                }
                block()
                if (interval > 0) {
                    try {
                        Thread.sleep(interval)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } else {
            // 循环更新进度条
            block()
            mainHandler.postDelayed(this, interval)
        }
    }

    private fun notifySafely() {
        synchronized(lock) {
            try {
                lock.notify()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun waitSafely() {
        synchronized(lock) {
            try {
                lock.wait()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

}