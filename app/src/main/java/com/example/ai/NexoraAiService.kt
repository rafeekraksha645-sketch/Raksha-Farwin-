package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.ChatMessageEntity
import com.example.model.NexoraLanguage
import com.example.model.NexoraMode
import com.example.model.NexoraModelTier
import com.example.model.NexoraResponseStyle
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.APINotConfiguredException
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ContentBlockedException
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.PermissionMissingException
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ServiceConnectionHandshakeFailedException
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.UnknownException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.ClosedChannelException
import java.nio.channels.UnresolvedAddressException
import java.util.Collections
import java.util.IdentityHashMap
import javax.net.ssl.SSLException
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

private const val TAG = "NexoraAiService"

enum class AiErrorCategory(
    val isSameModelRetryable: Boolean,
    val allowsModelFallback: Boolean
) {
    RATE_LIMIT_429(isSameModelRetryable = true, allowsModelFallback = true),
    QUOTA_EXHAUSTED(isSameModelRetryable = false, allowsModelFallback = true),
    MODEL_OVERLOADED_503(isSameModelRetryable = true, allowsModelFallback = true),
    MODEL_UNAVAILABLE(isSameModelRetryable = false, allowsModelFallback = true),
    TIMEOUT(isSameModelRetryable = true, allowsModelFallback = true),
    NETWORK(isSameModelRetryable = true, allowsModelFallback = false),
    APP_CHECK_403(isSameModelRetryable = false, allowsModelFallback = false),
    SERVICE_MISCONFIGURED(isSameModelRetryable = false, allowsModelFallback = false),
    SAFETY_BLOCKED(isSameModelRetryable = false, allowsModelFallback = false),
    EMPTY_RESPONSE(isSameModelRetryable = true, allowsModelFallback = true),
    UNKNOWN(isSameModelRetryable = true, allowsModelFallback = true)
}

class AiServiceException(
    val category: AiErrorCategory,
    val userMessage: String,
    cause: Throwable? = null
) : RuntimeException(userMessage, cause)

sealed interface AiStreamEvent {
    data class Status(
        val message: String,
        val modelTier: NexoraModelTier,
        val attempt: Int,
        val isFallback: Boolean
    ) : AiStreamEvent

    data class Chunk(
        val accumulatedText: String,
        val modelTier: NexoraModelTier,
        val isFallback: Boolean
    ) : AiStreamEvent
}

data class AiRetryPolicy(
    val maxPrimaryAttempts: Int = 3,
    val maxFallbackAttempts: Int = 2,
    val maxNetworkOrTimeoutAttempts: Int = 2,
    val baseBackoffMs: Long = 1_200L,
    val maxBackoffMs: Long = 6_000L,
    val minRequestIntervalMs: Long = 1_000L,
    val requestTimeoutMs: Long = 60_000L,
    val jitterProvider: () -> Long = { Random.nextLong(0L, 450L) }
)

interface AiStreamTransport {
    fun streamChat(
        modelTier: NexoraModelTier,
        history: List<Content>,
        systemInstruction: Content,
        temperature: Float,
        userPrompt: String,
        attachedBitmap: Bitmap?
    ): Flow<String>

    suspend fun generateUnary(
        modelTier: NexoraModelTier,
        history: List<Content>,
        systemInstruction: Content,
        temperature: Float,
        userPrompt: String,
        attachedBitmap: Bitmap?
    ): String {
        val sb = StringBuilder()
        streamChat(
            modelTier = modelTier,
            history = history,
            systemInstruction = systemInstruction,
            temperature = temperature,
            userPrompt = userPrompt,
            attachedBitmap = attachedBitmap
        ).collect { sb.append(it) }
        return sb.toString()
    }
}

class FirebaseAiStreamTransport : AiStreamTransport {
    private val firebaseAi by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
    }

    override fun streamChat(
        modelTier: NexoraModelTier,
        history: List<Content>,
        systemInstruction: Content,
        temperature: Float,
        userPrompt: String,
        attachedBitmap: Bitmap?
    ): Flow<String> = flow {
        val model = createModel(modelTier, systemInstruction, temperature)
        val chat = model.startChat(history = history)
        val requestContent = buildUserContent(userPrompt, attachedBitmap)
        chat.sendMessageStream(requestContent).collect { chunk ->
            val chunkText = chunk.text.orEmpty()
            if (chunkText.isNotEmpty()) {
                emit(chunkText)
            }
        }
    }

    override suspend fun generateUnary(
        modelTier: NexoraModelTier,
        history: List<Content>,
        systemInstruction: Content,
        temperature: Float,
        userPrompt: String,
        attachedBitmap: Bitmap?
    ): String {
        val model = createModel(modelTier, systemInstruction, temperature)
        val chat = model.startChat(history = history)
        val requestContent = buildUserContent(userPrompt, attachedBitmap)
        return chat.sendMessage(requestContent).text.orEmpty()
    }

    private fun createModel(
        modelTier: NexoraModelTier,
        systemInstruction: Content,
        temperature: Float
    ): GenerativeModel = firebaseAi.generativeModel(
        modelName = modelTier.modelId,
        generationConfig = generationConfig {
            this.temperature = temperature
        },
        systemInstruction = systemInstruction
    )

    private fun buildUserContent(
        userPrompt: String,
        attachedBitmap: Bitmap?
    ): Content = content("user") {
        if (attachedBitmap != null) {
            image(scaleBitmapUnderLimit(attachedBitmap))
        }
        text(userPrompt)
    }

    private fun scaleBitmapUnderLimit(bitmap: Bitmap): Bitmap {
        val maxDim = 1280
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
        val targetW = (width * ratio).toInt().coerceAtLeast(1)
        val targetH = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}

class NexoraAiService(
    private val transport: AiStreamTransport = FirebaseAiStreamTransport(),
    private val policy: AiRetryPolicy = AiRetryPolicy(),
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val logger: (Boolean, String, Throwable?) -> Unit = { isError, msg, err ->
        try {
            if (isError) Log.e(TAG, msg, err) else Log.w(TAG, msg, err)
        } catch (_: RuntimeException) {
            // Ignore android.util.Log stub exceptions in pure JVM unit tests
        }
    }
) {
    private val rateLimitMutex = Mutex()
    private var lastRequestStartMs: Long = 0L

    fun streamChatResponse(
        historyMessages: List<ChatMessageEntity>,
        userPrompt: String,
        attachedBitmap: Bitmap?,
        mode: NexoraMode,
        language: NexoraLanguage,
        responseStyle: NexoraResponseStyle,
        simpleExplanations: Boolean,
        modelTier: NexoraModelTier
    ): Flow<AiStreamEvent> = flow {
        val systemInstruction = buildSystemInstruction(
            mode = mode,
            language = language,
            responseStyle = responseStyle,
            simpleExplanations = simpleExplanations
        )
        val history = buildValidChatHistory(historyMessages)
        val temperature = when (mode) {
            NexoraMode.PROGRAMMING, NexoraMode.DEBUGGING, NexoraMode.CYBERSECURITY -> 0.35f
            NexoraMode.RESEARCH -> 0.4f
            else -> 0.6f
        }

        val modelSequence = buildModelFallbackOrder(modelTier)
        var lastException: AiServiceException? = null

        for ((tierIndex, currentTier) in modelSequence.withIndex()) {
            val isFallback = tierIndex > 0
            val maxAttemptsForTier = if (isFallback) policy.maxFallbackAttempts else policy.maxPrimaryAttempts
            var preferUnaryOnTier = false

            for (attempt in 1..maxAttemptsForTier) {
                val statusText = when {
                    isFallback && attempt == 1 ->
                        "${modelTier.label} is unavailable — switching to ${currentTier.label}..."
                    preferUnaryOnTier ->
                        "Retrying standard response with ${currentTier.label} (attempt $attempt/$maxAttemptsForTier)..."
                    attempt == 1 ->
                        "Connecting to ${currentTier.label}..."
                    else ->
                        "Retrying with ${currentTier.label} (attempt $attempt/$maxAttemptsForTier)..."
                }
                emit(
                    AiStreamEvent.Status(
                        message = statusText,
                        modelTier = currentTier,
                        attempt = attempt,
                        isFallback = isFallback
                    )
                )

                val accumulated = StringBuilder()
                var receivedAnyToken = false

                try {
                    waitForRateLimitSlot()

                    if (preferUnaryOnTier) {
                        val unaryText = withTimeout(policy.requestTimeoutMs) {
                            transport.generateUnary(
                                modelTier = currentTier,
                                history = history,
                                systemInstruction = systemInstruction,
                                temperature = temperature,
                                userPrompt = userPrompt,
                                attachedBitmap = attachedBitmap
                            )
                        }
                        if (unaryText.isNotBlank()) {
                            emit(
                                AiStreamEvent.Chunk(
                                    accumulatedText = unaryText,
                                    modelTier = currentTier,
                                    isFallback = isFallback
                                )
                            )
                            return@flow
                        } else {
                            throw AiServiceException(
                                category = AiErrorCategory.EMPTY_RESPONSE,
                                userMessage = "Gemini returned an empty response. Tap Retry to try again."
                            )
                        }
                    } else {
                        withTimeout(policy.requestTimeoutMs) {
                            transport.streamChat(
                                modelTier = currentTier,
                                history = history,
                                systemInstruction = systemInstruction,
                                temperature = temperature,
                                userPrompt = userPrompt,
                                attachedBitmap = attachedBitmap
                            ).collect { chunkText ->
                                if (chunkText.isNotEmpty()) {
                                    receivedAnyToken = true
                                    accumulated.append(chunkText)
                                    emit(
                                        AiStreamEvent.Chunk(
                                            accumulatedText = accumulated.toString(),
                                            modelTier = currentTier,
                                            isFallback = isFallback
                                        )
                                    )
                                }
                            }
                        }

                        if (accumulated.isNotBlank()) {
                            return@flow
                        } else {
                            throw AiServiceException(
                                category = AiErrorCategory.EMPTY_RESPONSE,
                                userMessage = "Gemini returned an empty response. Tap Retry to try again."
                            )
                        }
                    }
                } catch (e: Throwable) {
                    if (e is CancellationException && e !is TimeoutCancellationException) {
                        throw e
                    }

                    var classified = classifyError(e)
                    lastException = classified
                    logger(
                        !classified.category.isSameModelRetryable,
                        "Gemini ${if (preferUnaryOnTier) "unary" else "stream"} call failed on ${currentTier.modelId} (attempt $attempt/$maxAttemptsForTier, category=${classified.category}) | chain=${formatCausalChain(e)}",
                        e
                    )

                    // Do not restart & duplicate partial tokens if streaming already started
                    if (receivedAnyToken) {
                        throw classified
                    }

                    // If SSE streaming failed with UnknownException / JSON decoding / channel / empty error,
                    // switch this tier permanently to unary generateContent() and try unary immediately.
                    if (!preferUnaryOnTier && shouldTryUnaryFallback(e, classified)) {
                        preferUnaryOnTier = true
                        emit(
                            AiStreamEvent.Status(
                                message = "Streaming interrupted — trying standard response (${currentTier.label})...",
                                modelTier = currentTier,
                                attempt = attempt,
                                isFallback = isFallback
                            )
                        )
                        try {
                            waitForRateLimitSlot()
                            val unaryText = withTimeout(policy.requestTimeoutMs) {
                                transport.generateUnary(
                                    modelTier = currentTier,
                                    history = history,
                                    systemInstruction = systemInstruction,
                                    temperature = temperature,
                                    userPrompt = userPrompt,
                                    attachedBitmap = attachedBitmap
                                )
                            }
                            if (unaryText.isNotBlank()) {
                                emit(
                                    AiStreamEvent.Chunk(
                                        accumulatedText = unaryText,
                                        modelTier = currentTier,
                                        isFallback = isFallback
                                    )
                                )
                                return@flow
                            } else {
                                throw AiServiceException(
                                    category = AiErrorCategory.EMPTY_RESPONSE,
                                    userMessage = "Gemini returned an empty response. Tap Retry to try again."
                                )
                            }
                        } catch (unaryErr: Throwable) {
                            if (unaryErr is CancellationException && unaryErr !is TimeoutCancellationException) {
                                throw unaryErr
                            }
                            classified = classifyError(unaryErr)
                            lastException = classified
                            logger(
                                true,
                                "Gemini unary fallback failed on ${currentTier.modelId} (attempt $attempt/$maxAttemptsForTier, category=${classified.category}) | chain=${formatCausalChain(unaryErr)}",
                                unaryErr
                            )
                        }
                    }

                    val effectiveTierCap = when (classified.category) {
                        AiErrorCategory.NETWORK,
                        AiErrorCategory.TIMEOUT,
                        AiErrorCategory.EMPTY_RESPONSE,
                        AiErrorCategory.UNKNOWN -> minOf(maxAttemptsForTier, policy.maxNetworkOrTimeoutAttempts)
                        else -> maxAttemptsForTier
                    }

                    val shouldRetrySameModel =
                        classified.category.isSameModelRetryable && attempt < effectiveTierCap

                    if (shouldRetrySameModel) {
                        val backoffMs = computeBackoffDelayMs(attempt)
                        val waitSeconds = ((backoffMs + 500L) / 1000L).coerceAtLeast(1L)
                        val waitReason = when (classified.category) {
                            AiErrorCategory.MODEL_OVERLOADED_503 -> "High demand on ${currentTier.label}"
                            AiErrorCategory.RATE_LIMIT_429 -> "Rate limit cooldown"
                            AiErrorCategory.TIMEOUT -> "Connection timed out"
                            AiErrorCategory.NETWORK -> "Network glitch"
                            AiErrorCategory.EMPTY_RESPONSE -> "Empty response"
                            AiErrorCategory.UNKNOWN -> "Temporary transport issue"
                            else -> "Temporary error"
                        }
                        emit(
                            AiStreamEvent.Status(
                                message = "$waitReason — retrying in ${waitSeconds}s...",
                                modelTier = currentTier,
                                attempt = attempt,
                                isFallback = isFallback
                            )
                        )
                        delay(backoffMs)
                        continue
                    }

                    val hasNextFallback = tierIndex < modelSequence.lastIndex
                    if (hasNextFallback && classified.category.allowsModelFallback) {
                        break // Advance to compatible fallback model
                    }

                    throw classified
                }
            }
        }

        throw lastException ?: AiServiceException(
            category = AiErrorCategory.UNKNOWN,
            userMessage = "Unable to complete AI request. Please wait a moment and tap Retry."
        )
    }

    private suspend fun waitForRateLimitSlot() {
        val minInterval = policy.minRequestIntervalMs
        if (minInterval <= 0L) return
        rateLimitMutex.withLock {
            val now = clockMs()
            val elapsed = now - lastRequestStartMs
            if (lastRequestStartMs > 0L && elapsed in 0 until minInterval) {
                delay(minInterval - elapsed)
            }
            lastRequestStartMs = clockMs()
        }
    }

    private fun computeBackoffDelayMs(failedAttempt: Int): Long {
        val shift = (failedAttempt - 1).coerceIn(0, 6)
        val exponential = (policy.baseBackoffMs * (1L shl shift)).coerceAtMost(policy.maxBackoffMs)
        val jitter = policy.jitterProvider().coerceAtLeast(0L)
        return (exponential + jitter).coerceAtMost(policy.maxBackoffMs + 500L)
    }

    companion object {
        private const val GENERIC_SDK_UNKNOWN_MSG = "something unexpected happened."

        fun buildModelFallbackOrder(primary: NexoraModelTier): List<NexoraModelTier> =
            when (primary) {
                NexoraModelTier.FLASH -> listOf(NexoraModelTier.FLASH, NexoraModelTier.FLASH_LITE)
                NexoraModelTier.FLASH_LITE -> listOf(NexoraModelTier.FLASH_LITE, NexoraModelTier.FLASH)
            }

        fun buildValidChatHistory(messages: List<ChatMessageEntity>): List<Content> {
            val clean = messages
                .filter { !it.hasError && it.content.isNotBlank() }
                .sortedBy { it.sequenceIndex }

            val normalized = mutableListOf<Content>()
            var expectUser = true
            for (msg in clean) {
                val role = if (msg.isUser) "user" else "model"
                if (expectUser && role == "user") {
                    normalized.add(content("user") { text(msg.content) })
                    expectUser = false
                } else if (!expectUser && role == "model") {
                    normalized.add(content("model") { text(msg.content) })
                    expectUser = true
                }
            }
            if (normalized.size % 2 != 0) {
                normalized.removeAt(normalized.lastIndex)
            }
            return normalized
        }

        private fun collectCausalChain(error: Throwable): List<Throwable> {
            val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
            val result = mutableListOf<Throwable>()
            val queue = ArrayDeque<Throwable>()
            queue.add(error)
            while (queue.isNotEmpty() && result.size < 16) {
                val current = queue.removeFirst()
                if (!visited.add(current)) continue
                result.add(current)
                current.cause?.let { queue.add(it) }
                current.suppressed.forEach { queue.add(it) }
            }
            return result
        }

        fun formatCausalChain(error: Throwable): String =
            collectCausalChain(error).joinToString(" -> ") { t ->
                val msg = t.message?.trim()?.takeIf { it.isNotEmpty() } ?: "<no message>"
                "${t::class.java.name}: $msg"
            }

        private fun isGenericWrapperMessage(message: String?): Boolean {
            val trimmed = message?.trim()?.lowercase() ?: return true
            return trimmed.isEmpty() || trimmed == GENERIC_SDK_UNKNOWN_MSG
        }

        private fun shouldTryUnaryFallback(error: Throwable, classified: AiServiceException): Boolean {
            if (classified.category == AiErrorCategory.UNKNOWN ||
                classified.category == AiErrorCategory.EMPTY_RESPONSE
            ) {
                return true
            }
            val chain = collectCausalChain(error)
            val hasStreamChannelOrDecodeCause = chain.any { t ->
                val cls = t::class.java.name.lowercase()
                t is ClosedChannelException ||
                    t is EOFException ||
                    cls.contains("closedbytechannel") ||
                    cls.contains("closedreceivechannel") ||
                    cls.contains("streamreset") ||
                    cls.contains("serializationexception") ||
                    cls.contains("jsondecodingexception")
            }
            if (hasStreamChannelOrDecodeCause && classified.category == AiErrorCategory.NETWORK) {
                val hasOfflineDnsCause = chain.any { t ->
                    val cls = t::class.java.name.lowercase()
                    t is UnknownHostException || t is UnresolvedAddressException || cls.contains("unresolvedaddress")
                }
                return !hasOfflineDnsCause
            }
            return false
        }

        fun classifyError(error: Throwable): AiServiceException {
            if (error is AiServiceException) return error

            val causalChain = collectCausalChain(error)
            val combinedText = causalChain
                .mapNotNull { it.message?.takeIf { msg -> msg.isNotBlank() } }
                .joinToString(" | ")
            val lower = combinedText.lowercase()
            val classNamesLower = causalChain.joinToString(" | ") { it::class.java.name.lowercase() }

            // 1. Timeout checks (including Ktor ConnectTimeoutException / SocketTimeoutException wrapped in UnknownException)
            if (causalChain.any {
                    it is TimeoutCancellationException ||
                        it is RequestTimeoutException ||
                        it is SocketTimeoutException ||
                        it::class.java.name.lowercase().contains("timeout")
                } ||
                lower.contains("timed out") ||
                lower.contains("timeout") ||
                lower.contains("deadline exceeded")
            ) {
                return AiServiceException(
                    category = AiErrorCategory.TIMEOUT,
                    userMessage = "Request timed out while waiting for Gemini. Check your connection stability and tap Retry.",
                    cause = error
                )
            }

            // 2. App Check / HTTP 403 / Auth attestation
            if (causalChain.any {
                    it is InvalidAPIKeyException ||
                        it is PermissionMissingException ||
                        it is ServiceConnectionHandshakeFailedException ||
                        it::class.java.name.lowercase().contains("appcheck") ||
                        it::class.java.name.lowercase().contains("integrity")
                } ||
                lower.contains("403") ||
                lower.contains("appcheck") ||
                lower.contains("app check") ||
                lower.contains("attestation") ||
                lower.contains("debug token") ||
                lower.contains("permission_denied") ||
                lower.contains("api key not valid") ||
                lower.contains("unauthenticated")
            ) {
                return AiServiceException(
                    category = AiErrorCategory.APP_CHECK_403,
                    userMessage = "AI service authorization / Firebase App Check verification failed (HTTP 403). Ensure App Check is active for this build and try again.",
                    cause = error
                )
            }

            // 3. Service disabled / misconfigured
            if (causalChain.any { it is ServiceDisabledException || it is APINotConfiguredException } ||
                lower.contains("api has not been used") ||
                lower.contains("service is disabled") ||
                lower.contains("genai config not found") ||
                lower.contains("not configured")
            ) {
                return AiServiceException(
                    category = AiErrorCategory.SERVICE_MISCONFIGURED,
                    userMessage = "Firebase AI Logic is not configured or enabled for this project.",
                    cause = error
                )
            }

            // 4. Safety / content policy blocks
            if (causalChain.any {
                    it is PromptBlockedException ||
                        it is ContentBlockedException ||
                        it is ResponseStoppedException
                }
            ) {
                return AiServiceException(
                    category = AiErrorCategory.SAFETY_BLOCKED,
                    userMessage = "The request or response was blocked by Gemini safety filters. Rephrase your question and tap Retry.",
                    cause = error
                )
            }

            // 5. Explicit model unavailable / 404 on endpoint -> allows fallback model
            if ((lower.contains("404") || lower.contains("model is not found") || lower.contains("not found for api version")) &&
                !lower.contains("429") && !lower.contains("quota")
            ) {
                return AiServiceException(
                    category = AiErrorCategory.MODEL_UNAVAILABLE,
                    userMessage = "The selected Gemini model is temporarily unavailable on this endpoint. Tap Retry to try again.",
                    cause = error
                )
            }

            // 6. Distinguish HTTP 503 high demand / server overload from 429 quota/rate limits
            val isExplicitOverload503 =
                lower.contains("503") ||
                    lower.contains("high demand") ||
                    lower.contains("currently experiencing high demand") ||
                    lower.contains("unavailable") ||
                    lower.contains("overloaded") ||
                    lower.contains("service_unavailable") ||
                    lower.contains("model is overloaded") ||
                    lower.contains("502") ||
                    lower.contains("504") ||
                    lower.contains("500") ||
                    causalChain.any { it is ServerException }

            if (isExplicitOverload503 && !lower.contains("429") && !lower.contains("quota")) {
                return AiServiceException(
                    category = AiErrorCategory.MODEL_OVERLOADED_503,
                    userMessage = "Gemini is experiencing high demand right now (HTTP 503), and the fallback model was also busy. Please wait a few seconds and tap Retry.",
                    cause = error
                )
            }

            // 7. Distinguish permanent / daily quota exhaustion vs transient HTTP 429 rate limit
            val hasQuotaOr429Signal =
                causalChain.any { it is QuotaExceededException } ||
                    lower.contains("429") ||
                    lower.contains("resource_exhausted") ||
                    lower.contains("rate limit") ||
                    lower.contains("too many requests") ||
                    lower.contains("quota")

            if (hasQuotaOr429Signal) {
                val isHardQuotaExhausted =
                    (lower.contains("daily") ||
                        lower.contains("per day") ||
                        lower.contains("limit: 0") ||
                        lower.contains("free_tier") ||
                        lower.contains("billing") ||
                        lower.contains("quota exhausted") ||
                        lower.contains("exceeded your current quota") ||
                        lower.contains("insufficient quota")) &&
                        !lower.contains("per minute") &&
                        !lower.contains("rpm")

                return if (isHardQuotaExhausted) {
                    AiServiceException(
                        category = AiErrorCategory.QUOTA_EXHAUSTED,
                        userMessage = "API quota is exhausted for this Firebase project or model tier. Verify Gemini/Firebase AI Logic quota & billing limits, or try again later.",
                        cause = error
                    )
                } else {
                    AiServiceException(
                        category = AiErrorCategory.RATE_LIMIT_429,
                        userMessage = "Rate limit reached (HTTP 429). Too many requests were sent in a short time — please wait 15–30 seconds and tap Retry.",
                        cause = error
                    )
                }
            }

            // 8. Network connectivity & Ktor/NIO channel disconnect issues
            if (causalChain.any {
                    it is UnknownHostException ||
                        it is UnresolvedAddressException ||
                        it is ConnectException ||
                        it is SocketException ||
                        it is SSLException ||
                        it is ClosedChannelException ||
                        it is EOFException ||
                        it is IOException
                } ||
                classNamesLower.contains("unresolvedaddress") ||
                classNamesLower.contains("unknownhost") ||
                classNamesLower.contains("connectexception") ||
                classNamesLower.contains("closedchannel") ||
                classNamesLower.contains("closedbytechannel") ||
                classNamesLower.contains("closedreceivechannel") ||
                classNamesLower.contains("streamreset") ||
                classNamesLower.contains("eofexception") ||
                classNamesLower.contains("firebasenetwork") ||
                classNamesLower.contains("sslhandshake") ||
                classNamesLower.contains("certpath") ||
                lower.contains("unable to resolve host") ||
                lower.contains("failed to connect") ||
                lower.contains("no address associated with hostname") ||
                lower.contains("connection reset") ||
                lower.contains("network is unreachable") ||
                lower.contains("software caused connection abort") ||
                lower.contains("broken pipe") ||
                lower.contains("connection closed") ||
                lower.contains("unexpected end of stream") ||
                lower.contains("end of input") ||
                lower.contains("connection refused") ||
                lower.contains("network error")
            ) {
                return AiServiceException(
                    category = AiErrorCategory.NETWORK,
                    userMessage = "Network connection unavailable or interrupted. Check your internet access and tap Retry.",
                    cause = error
                )
            }

            // 9. Incomplete / malformed JSON stream payload (e.g. SSE line-parser encountering '{' + EOF)
            if (classNamesLower.contains("jsondecodingexception") ||
                classNamesLower.contains("serializationexception")
            ) {
                val rootType = causalChain.asReversed()
                    .firstOrNull { it !is UnknownException }
                    ?.javaClass?.simpleName?.ifBlank { "SerializationException" }
                    ?: "JsonDecodingException"
                return AiServiceException(
                    category = AiErrorCategory.UNKNOWN,
                    userMessage = "Gemini response stream was interrupted or returned incomplete data ($rootType). Tap Retry to try again.",
                    cause = error
                )
            }

            // 10. Generic fallback: skip meaningless "Something unexpected happened." wrapper messages
            val meaningfulCause = causalChain.asReversed().firstOrNull { !isGenericWrapperMessage(it.message) }
            val deepestCause = causalChain.asReversed().firstOrNull { it !is UnknownException } ?: causalChain.last()
            val detailText = meaningfulCause?.message?.trim()
            val rootType = deepestCause::class.java.simpleName.ifBlank { "Error" }

            val userMsg = if (!detailText.isNullOrBlank()) {
                "Unexpected AI service error ($rootType): $detailText. Tap Retry to try again."
            } else {
                "Unexpected AI transport error ($rootType). Check your connection and tap Retry."
            }

            return AiServiceException(
                category = AiErrorCategory.UNKNOWN,
                userMessage = userMsg,
                cause = error
            )
        }
    }

    private fun buildSystemInstruction(
        mode: NexoraMode,
        language: NexoraLanguage,
        responseStyle: NexoraResponseStyle,
        simpleExplanations: Boolean
    ): Content {
        val simplicityRule = if (simpleExplanations) {
            "Use beginner-friendly, plain language with clear everyday analogies and avoid unnecessary jargon."
        } else {
            "Match technical precision appropriately while keeping explanations clear and well-structured."
        }

        val fullInstruction = """
            You are NEXORA AI, an automated global AI-powered Information Technology & Computer Science Intelligence Platform.

            CORE ACCURACY & TRUST PRINCIPLES (MANDATORY):
            1. Never fabricate facts, statistics, benchmarks, APIs, CVEs, or external URLs/citations.
            2. If you do not know something or cannot verify real-time/post-cutoff facts, clearly say so and explain what you can verify.
            3. Explicitly distinguish verified technical principles from assumptions or estimates.
            4. Never claim code was compiled, executed, or tested unless you actually observed execution.
            5. Use clean Markdown formatting (headings, bullet lists, and fenced code blocks with language tags like ```python, ```kotlin, ```sql, ```bash, ```typescript) when writing code or technical walkthroughs.

            SPECIALIZED MODE (${mode.title}):
            ${mode.systemBehavior}

            MULTILINGUAL DIRECTIVE (${language.displayName}):
            ${language.aiInstruction}

            RESPONSE STYLE (${responseStyle.label}):
            ${responseStyle.instruction}
            $simplicityRule
        """.trimIndent()

        return content { text(fullInstruction) }
    }
}
