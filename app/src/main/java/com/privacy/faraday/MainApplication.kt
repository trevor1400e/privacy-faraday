package com.privacy.faraday

import android.app.Application
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.privacy.faraday.network.ChatManager

class MainApplication : Application() {

    companion object {
        private const val TAG = "MainApplication"
    }

    override fun onCreate() {
        super.onCreate()
        initializePython()
        ChatManager.initialize(this)
    }

    private fun initializePython() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
            Log.d(TAG, "Chaquopy Python interpreter started")
        }

        try {
            val python = Python.getInstance()
            val bridge = python.getModule("reticulum_bridge")
            val result = bridge.callAttr("ping").toString()
            Log.d(TAG, "Python bridge ping: $result")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Python bridge", e)
        }
    }
}
