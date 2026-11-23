package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.ChatMessageEntity
import com.example.model.ConversationEntity
import com.example.model.NexoraLanguage
import com.example.model.NexoraMode
import com.example.model.NexoraModelTier
import com.example.model.NexoraPreferencesData
import com.example.model.NexoraResponseStyle
import com.example.model.NexoraThemeMode
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireSignedInUser(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(uid == expectedUserId) { "Authenticated user mismatch; refusing cross-user operation." }
        return uid
    }

    fun observeConversations(userId: String): Flow<List<ConversationEntity>> = flow {
        requireSignedInUser(userId)
        val path = "users/$userId/conversations"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("conversations")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ConversationEntity::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMessages(userId: String, conversationId: String): Flow<List<ChatMessageEntity>> = flow {
        requireSignedInUser(userId)
        require(conversationId.isNotBlank()) { "Conversation ID must not be blank" }
        val path = "users/$userId/conversations/$conversationId/messages"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("conversations")
                .document(conversationId)
                .collection("messages")
                .orderBy("sequenceIndex", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessageEntity::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createConversation(
        userId: String,
        conversationId: String,
        title: String,
        mode: NexoraMode,
        language: NexoraLanguage,
        lastMessagePreview: String = "",
        messageCount: Long = 0L
    ): Result<Unit> {
        val path = "users/$userId/conversations/$conversationId"
        return try {
            requireSignedInUser(userId)
            val safeTitle = title.trim().ifBlank { "New IT Session" }.take(120)
            val safePreview = lastMessagePreview.trim().take(240)
            val docRef = db.collection("users")
                .document(userId)
                .collection("conversations")
                .document(conversationId)
            val payload = mapOf(
                "userId" to userId,
                "title" to safeTitle,
                "mode" to mode.name,
                "language" to language.name,
                "lastMessagePreview" to safePreview,
                "messageCount" to messageCount.coerceIn(0L, 10000L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun renameConversation(
        userId: String,
        conversationId: String,
        newTitle: String
    ): Result<Unit> {
        val path = "users/$userId/conversations/$conversationId"
        return try {
            requireSignedInUser(userId)
            val safeTitle = newTitle.trim().ifBlank { "IT Session" }.take(120)
            db.collection("users")
                .document(userId)
                .collection("conversations")
                .document(conversationId)
                .update(
                    mapOf(
                        "title" to safeTitle,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateConversationActivity(
        userId: String,
        conversationId: String,
        title: String,
        mode: NexoraMode,
        language: NexoraLanguage,
        lastMessagePreview: String,
        messageCount: Long
    ): Result<Unit> {
        val path = "users/$userId/conversations/$conversationId"
        return try {
            requireSignedInUser(userId)
            db.collection("users")
                .document(userId)
                .collection("conversations")
                .document(conversationId)
                .update(
                    mapOf(
                        "title" to title.trim().ifBlank { "IT Session" }.take(120),
                        "mode" to mode.name,
                        "language" to language.name,
                        "lastMessagePreview" to lastMessagePreview.trim().take(240),
                        "messageCount" to messageCount.coerceIn(0L, 10000L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun createMessage(
        userId: String,
        conversationId: String,
        messageId: String,
        role: String,
        content: String,
        mode: NexoraMode,
        hasError: Boolean,
        sequenceIndex: Long,
        attachedImageHint: String? = null
    ): Result<Unit> {
        val path = "users/$userId/conversations/$conversationId/messages/$messageId"
        return try {
            requireSignedInUser(userId)
            val safeContent = content.ifBlank { "..." }.take(32000)
            val payload = mutableMapOf<String, Any>(
                "userId" to userId,
                "conversationId" to conversationId,
                "role" to role,
                "content" to safeContent,
                "mode" to mode.name,
                "hasError" to hasError,
                "sequenceIndex" to sequenceIndex.coerceIn(0L, 100000L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!attachedImageHint.isNullOrBlank()) {
                payload["attachedImageHint"] = attachedImageHint.take(120)
            }
            db.collection("users")
                .document(userId)
                .collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document(messageId)
                .set(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMessage(
        userId: String,
        conversationId: String,
        messageId: String,
        content: String,
        mode: NexoraMode,
        hasError: Boolean
    ): Result<Unit> {
        val path = "users/$userId/conversations/$conversationId/messages/$messageId"
        return try {
            requireSignedInUser(userId)
            val safeContent = content.ifBlank { "..." }.take(32000)
            db.collection("users")
                .document(userId)
                .collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document(messageId)
                .update(
                    mapOf(
                        "content" to safeContent,
                        "mode" to mode.name,
                        "hasError" to hasError,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(
        userId: String,
        conversationId: String,
        messageId: String
    ): Result<Unit> {
        val path = "users/$userId/conversations/$conversationId/messages/$messageId"
        return try {
            requireSignedInUser(userId)
            db.collection("users")
                .document(userId)
                .collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document(messageId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun clearConversationMessages(
        userId: String,
        conversationId: String
    ): Result<Unit> {
        val path = "users/$userId/conversations/$conversationId/messages"
        return try {
            requireSignedInUser(userId)
            val messagesRef = db.collection("users")
                .document(userId)
                .collection("conversations")
                .document(conversationId)
                .collection("messages")
            val docs = messagesRef.get().await().documents
            docs.chunked(400).forEach { batchDocs ->
                val batch = db.batch()
                batchDocs.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(
        userId: String,
        conversationId: String
    ): Result<Unit> {
        val path = "users/$userId/conversations/$conversationId"
        return try {
            requireSignedInUser(userId)
            clearConversationMessages(userId, conversationId).getOrThrow()
            db.collection("users")
                .document(userId)
                .collection("conversations")
                .document(conversationId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAllUserData(userId: String): Result<Unit> {
        val path = "users/$userId/conversations"
        return try {
            requireSignedInUser(userId)
            val convDocs = db.collection("users")
                .document(userId)
                .collection("conversations")
                .get()
                .await()
                .documents
            for (conv in convDocs) {
                deleteConversation(userId, conv.id).getOrThrow()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun loadCloudPreferences(userId: String): Result<NexoraPreferencesData?> {
        val path = "users/$userId/preferences/settings"
        return try {
            requireSignedInUser(userId)
            val snap = db.document(path).get().await()
            if (!snap.exists()) {
                Result.success(null)
            } else {
                val data = NexoraPreferencesData(
                    language = NexoraLanguage.fromName(snap.getString("language")),
                    themeMode = NexoraThemeMode.fromName(snap.getString("themeMode")),
                    responseStyle = NexoraResponseStyle.fromName(snap.getString("responseStyle")),
                    simpleExplanations = snap.getBoolean("simpleExplanations") ?: false,
                    defaultMode = NexoraMode.fromName(snap.getString("defaultMode")),
                    modelTier = NexoraModelTier.fromName(snap.getString("modelTier"))
                )
                Result.success(data)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun saveCloudPreferences(
        userId: String,
        prefs: NexoraPreferencesData
    ): Result<Unit> {
        val path = "users/$userId/preferences/settings"
        return try {
            requireSignedInUser(userId)
            val docRef = db.document(path)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "language" to prefs.language.name,
                        "themeMode" to prefs.themeMode.name,
                        "responseStyle" to prefs.responseStyle.name,
                        "simpleExplanations" to prefs.simpleExplanations,
                        "defaultMode" to prefs.defaultMode.name,
                        "modelTier" to prefs.modelTier.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to userId,
                        "language" to prefs.language.name,
                        "themeMode" to prefs.themeMode.name,
                        "responseStyle" to prefs.responseStyle.name,
                        "simpleExplanations" to prefs.simpleExplanations,
                        "defaultMode" to prefs.defaultMode.name,
                        "modelTier" to prefs.modelTier.name,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }
}
