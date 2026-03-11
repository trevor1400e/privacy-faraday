package com.privacy.faraday.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.privacy.faraday.data.db.ConversationPreview
import com.privacy.faraday.network.ChatManager
import kotlinx.coroutines.flow.Flow

class ConversationListViewModel(application: Application) : AndroidViewModel(application) {
    val conversations: Flow<List<ConversationPreview>> =
        ChatManager.getDatabase().messageDao().getConversationPreviews()

    val initialized = ChatManager.initialized
}
