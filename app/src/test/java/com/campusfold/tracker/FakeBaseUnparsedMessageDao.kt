package com.campusfold.tracker

import com.campusfold.tracker.data.dao.UnparsedMessageDao
import com.campusfold.tracker.data.entity.UnparsedMessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

open class FakeBaseUnparsedMessageDao : UnparsedMessageDao {
    val messages = mutableMapOf<Long, UnparsedMessageEntity>()
    var idCounter = 1L

    override fun getAllUnparsedMessages(): Flow<List<UnparsedMessageEntity>> =
        flowOf(messages.values.sortedByDescending { it.receivedAt })

    override suspend fun getById(id: Long): UnparsedMessageEntity? = messages[id]
    override suspend fun getByBodyHash(hash: String): UnparsedMessageEntity? =
        messages.values.find { it.bodyHash == hash }

    override suspend fun insertMessage(message: UnparsedMessageEntity): Long {
        val id = if (message.id > 0) message.id else idCounter++
        messages[id] = message.copy(id = id)
        return id
    }

    override suspend fun insertMessages(msgs: List<UnparsedMessageEntity>): List<Long> =
        msgs.map { insertMessage(it) }

    override suspend fun purgeOlderThan(cutoffTimestamp: Long): Int {
        val toRemove = messages.values.filter { it.receivedAt < cutoffTimestamp }
        toRemove.forEach { messages.remove(it.id) }
        return toRemove.size
    }

    override suspend fun deleteMessage(message: UnparsedMessageEntity) {
        messages.remove(message.id)
    }

    override suspend fun deleteById(id: Long) {
        messages.remove(id)
    }

    override suspend fun clearAll() {
        messages.clear()
    }

    // Convenience test helpers
    open suspend fun getAllUnparsedMessagesSync(): List<UnparsedMessageEntity> =
        messages.values.sortedByDescending { it.receivedAt }

    open suspend fun getUnparsedMessageById(id: Long): UnparsedMessageEntity? = getById(id)
    open suspend fun findByBodyHash(bodyHash: String): UnparsedMessageEntity? = getByBodyHash(bodyHash)
    open suspend fun insertUnparsedMessage(message: UnparsedMessageEntity): Long = insertMessage(message)
    open suspend fun deleteUnparsedMessage(message: UnparsedMessageEntity) = deleteMessage(message)
    open suspend fun deleteUnparsedMessageById(id: Long) = deleteById(id)
    open suspend fun getUnparsedCount(): Int = messages.size
}
