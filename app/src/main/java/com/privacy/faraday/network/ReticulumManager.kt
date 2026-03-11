package com.privacy.faraday.network

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ReticulumManager {

    data class ReceivedMessage(
        val sourceHash: String,
        val content: String,
        val timestamp: Double,
        val hash: String
    )

    private val bridge: PyObject by lazy {
        Python.getInstance().getModule("reticulum_bridge")
    }

    /** Convert a PyObject dict to a Kotlin Map<String, Any?>. */
    private fun PyObject.toStringMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        for ((k, v) in asMap()) {
            map[k.toString()] = v?.toJava(Object::class.java)
        }
        return map
    }

    suspend fun initialize(context: Context): String = withContext(Dispatchers.IO) {
        val configDir = File(context.filesDir, "reticulum")
        configDir.mkdirs()

        // Copy default config if not present
        val configFile = File(configDir, "config")
        if (!configFile.exists()) {
            context.assets.open("reticulum_default_config").use { input ->
                configFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        val result = bridge.callAttr("init_reticulum", configDir.absolutePath).toStringMap()
        if (result["status"] == "error") {
            throw RuntimeException(result["error"]?.toString() ?: "Unknown error")
        }
        result["address"]?.toString() ?: ""
    }

    suspend fun getAddress(): String = withContext(Dispatchers.IO) {
        val result = bridge.callAttr("get_lxmf_address").toStringMap()
        if (result["status"] == "error") {
            throw RuntimeException(result["error"]?.toString() ?: "Unknown error")
        }
        result["address"]?.toString() ?: ""
    }

    suspend fun getIdentityHash(): String = withContext(Dispatchers.IO) {
        val result = bridge.callAttr("get_identity_hash").toStringMap()
        if (result["status"] == "error") {
            throw RuntimeException(result["error"]?.toString() ?: "Unknown error")
        }
        result["identity_hash"]?.toString() ?: ""
    }

    suspend fun sendMessage(destHash: String, content: ByteArray): Map<String, String> = withContext(Dispatchers.IO) {
        val result = bridge.callAttr("send_message", destHash, String(content, Charsets.UTF_8)).toStringMap()
        result.mapValues { it.value?.toString() ?: "" }
    }

    suspend fun announce() = withContext(Dispatchers.IO) {
        val result = bridge.callAttr("announce").toStringMap()
        if (result["status"] == "error") {
            throw RuntimeException(result["error"]?.toString() ?: "Unknown error")
        }
    }

    suspend fun getStatus(): Map<String, Any?> = withContext(Dispatchers.IO) {
        bridge.callAttr("get_status").toStringMap()
    }

    suspend fun pollMessages(): List<ReceivedMessage> = withContext(Dispatchers.IO) {
        val pyResult = bridge.callAttr("get_pending_messages")
        val resultMap = pyResult.toStringMap()

        // "messages" is a Python list of dicts — access via PyObject
        val pyMessages = pyResult.callAttr("get", "messages") ?: return@withContext emptyList()
        val messageList = pyMessages.asList()

        messageList.map { pyMsg ->
            val msg = pyMsg.toStringMap()
            ReceivedMessage(
                sourceHash = msg["source_hash"]?.toString() ?: "",
                content = msg["content"]?.toString() ?: "",
                timestamp = (msg["timestamp"] as? Number)?.toDouble() ?: 0.0,
                hash = msg["hash"]?.toString() ?: ""
            )
        }
    }

    suspend fun shutdown() = withContext(Dispatchers.IO) {
        bridge.callAttr("shutdown")
    }
}
