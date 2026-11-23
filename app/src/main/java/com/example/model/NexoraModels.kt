package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class NexoraLanguage(
    val displayName: String,
    val nativeLabel: String,
    val shortBadge: String,
    val promptHint: String,
    val aiInstruction: String
) {
    AUTO(
        displayName = "Auto-Detect",
        nativeLabel = "Auto (EN / தமிழ் / සිංහල)",
        shortBadge = "AUTO",
        promptHint = "Ask any IT, coding, or cybersecurity question in English, தமிழ், or සිංහල...",
        aiInstruction = "Automatically detect the language of the user's message (English, Tamil / தமிழ், Sinhala / සිංහල, or any other language) and respond naturally in that exact same language. Keep programming code, CLI commands, and standard technical identifiers accurate while writing explanations in the detected language."
    ),
    ENGLISH(
        displayName = "English",
        nativeLabel = "English",
        shortBadge = "EN",
        promptHint = "Ask about algorithms, Python, cybersecurity, networking, cloud...",
        aiInstruction = "Respond in clear, international English."
    ),
    TAMIL(
        displayName = "Tamil",
        nativeLabel = "தமிழ் (Tamil)",
        shortBadge = "TA",
        promptHint = "தொழில்நுட்பம், நிரலாக்கம், அல்லது இணையப் பாதுகாப்பு பற்றி கேளுங்கள்...",
        aiInstruction = "Respond in clear, natural, easily understandable Tamil (தமிழ்). Keep code blocks, keywords, CLI commands, and standard technical terms in English/ASCII where standard, and explain concepts clearly in Tamil."
    ),
    SINHALA(
        displayName = "Sinhala",
        nativeLabel = "සිංහල (Sinhala)",
        shortBadge = "SI",
        promptHint = "තොරතුරු තාක්ෂණය, ක්‍රමලේඛනය හෝ සයිබර් ආරක්ෂාව ගැන අසන්න...",
        aiInstruction = "Respond in clear, natural, easily understandable Sinhala (සිංහල). Keep code blocks, keywords, CLI commands, and standard technical terms in English/ASCII where standard, and explain concepts clearly in Sinhala."
    );

    companion object {
        fun fromName(raw: String?): NexoraLanguage =
            entries.firstOrNull { it.name == raw } ?: AUTO
    }
}

enum class NexoraThemeMode(val label: String) {
    DARK("Deep Black (Dark)"),
    LIGHT("Daylight Crisp (Light)"),
    SYSTEM("Follow System");

    companion object {
        fun fromName(raw: String?): NexoraThemeMode =
            entries.firstOrNull { it.name == raw } ?: DARK
    }
}

enum class NexoraResponseStyle(val label: String, val instruction: String) {
    CONCISE("Concise", "Be direct, structured, and concise while staying accurate."),
    BALANCED("Balanced", "Provide a well-structured, thorough explanation with practical examples."),
    DETAILED("Deep Dive", "Provide comprehensive architectural depth, edge cases, and detailed walkthroughs.");

    companion object {
        fun fromName(raw: String?): NexoraResponseStyle =
            entries.firstOrNull { it.name == raw } ?: BALANCED
    }
}

enum class NexoraModelTier(val label: String, val modelId: String, val description: String) {
    FLASH(
        label = "Gemini 3.8 Flash",
        modelId = "gemini-3.8-flash",
        description = "Frontier-class reasoning & coding intelligence (Recommended)"
    ),
    FLASH_LITE(
        label = "Gemini 3.5 Flash-Lite",
        modelId = "gemini-3.5-flash-lite",
        description = "Ultra-low latency for rapid Q&A and lightweight definitions"
    );

    companion object {
        fun fromName(raw: String?): NexoraModelTier =
            entries.firstOrNull { it.name == raw } ?: FLASH
    }
}

enum class NexoraMode(
    val title: String,
    val shortTitle: String,
    val tagline: String,
    val samplePrompt: String,
    val systemBehavior: String
) {
    GENERAL_IT(
        title = "General IT",
        shortTitle = "General IT",
        tagline = "Architecture, systems, OS, networking & tech concepts",
        samplePrompt = "Explain how DNS resolution and HTTPS TLS handshakes work together when loading a website.",
        systemBehavior = "Operate as a global IT & Computer Science intelligence specialist. Classify the user's IT topic implicitly, answer accurately with clear structure, real-world analogies, and practical examples."
    ),
    LEARNING(
        title = "Learning Mode",
        shortTitle = "Learn",
        tagline = "Step-by-step guided lessons for students & beginners",
        samplePrompt = "Teach me binary, hexadecimal, and logic gates step by step from scratch.",
        systemBehavior = "Operate as an encouraging Computer Science tutor. Break complex ideas into numbered step-by-step lessons, use simple mental models before formal definitions, check potential misconceptions, and include a mini practice question at the end."
    ),
    PROGRAMMING(
        title = "Programming",
        shortTitle = "Code",
        tagline = "Code generation, refactoring, SQL & algorithms",
        samplePrompt = "Write a clean Python & Kotlin comparison implementing binary search with time complexity analysis.",
        systemBehavior = "Operate as a principal software engineer. Provide clean, idiomatic, production-quality code in fenced Markdown code blocks with language tags (e.g. ```python, ```kotlin, ```sql, ```typescript), explain how the code works, and state time/space complexity where relevant. Never claim code was executed unless verified."
    ),
    DEBUGGING(
        title = "Debugging",
        shortTitle = "Debug",
        tagline = "Root-cause error analysis & stack trace fixes",
        samplePrompt = "Why does a React useEffect hook trigger an infinite re-render loop and how do I fix it cleanly?",
        systemBehavior = "Operate as a senior debugging & troubleshooting specialist. Identify the exact root cause, explain why the error happens, provide the corrected code/configuration, and list preventive checks."
    ),
    CYBERSECURITY(
        title = "Cybersecurity",
        shortTitle = "Security",
        tagline = "Ethical security education, cryptography & defense",
        samplePrompt = "Explain SQL injection and XSS from a defensive engineering perspective with secure parameterized fixes.",
        systemBehavior = "Operate as a defensive cybersecurity educator and security architect. Focus strictly on ethical cybersecurity concepts, cryptography, digital forensics fundamentals, defensive hardening, OWASP mitigation, and secure coding. Refuse malicious exploitation instructions while explaining how defenses protect systems."
    ),
    RESEARCH(
        title = "Research Mode",
        shortTitle = "Research",
        tagline = "Deep technical analysis & source-honest synthesis",
        samplePrompt = "Compare Transformer attention mechanisms with traditional RNNs and clearly distinguish established benchmarks from open research.",
        systemBehavior = "Operate as a meticulous technology researcher. Clearly separate verified computer science fundamentals from assumptions or fast-changing claims. Note explicitly that you do not have live web search access inside this chat session when asked about breaking/real-time post-cutoff news, and NEVER invent or guess URLs, papers, or citations."
    ),
    CAREER_GUIDANCE(
        title = "Career Guidance",
        shortTitle = "Career",
        tagline = "IT roadmaps, portfolios & certification guidance",
        samplePrompt = "Create a 6-month structured roadmap to go from beginner to entry-level Cloud & DevOps Engineer.",
        systemBehavior = "Operate as a global technology career mentor. Provide actionable, realistic skill roadmaps, project ideas, portfolio advice, and recognized industry certification paths tailored to the user's current level."
    );

    companion object {
        fun fromName(raw: String?): NexoraMode =
            entries.firstOrNull { it.name == raw } ?: GENERAL_IT
    }
}

data class NexoraPreferencesData(
    val language: NexoraLanguage = NexoraLanguage.AUTO,
    val themeMode: NexoraThemeMode = NexoraThemeMode.DARK,
    val responseStyle: NexoraResponseStyle = NexoraResponseStyle.BALANCED,
    val simpleExplanations: Boolean = false,
    val defaultMode: NexoraMode = NexoraMode.GENERAL_IT,
    val modelTier: NexoraModelTier = NexoraModelTier.FLASH
)

@Keep
@IgnoreExtraProperties
data class ConversationEntity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "New IT Session",
    val mode: String = NexoraMode.GENERAL_IT.name,
    val language: String = NexoraLanguage.AUTO.name,
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val nexoraMode: NexoraMode get() = NexoraMode.fromName(mode)
    val nexoraLanguage: NexoraLanguage get() = NexoraLanguage.fromName(language)
}

@Keep
@IgnoreExtraProperties
data class ChatMessageEntity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val conversationId: String = "",
    val role: String = "user",
    val content: String = "",
    val mode: String = NexoraMode.GENERAL_IT.name,
    val hasError: Boolean = false,
    val sequenceIndex: Long = 0L,
    val attachedImageHint: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isUser: Boolean get() = role == "user"
    val nexoraMode: NexoraMode get() = NexoraMode.fromName(mode)
}

data class ItTopic(
    val title: String,
    val summary: String,
    val starterPrompt: String,
    val recommendedMode: NexoraMode
)

data class ItCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val accentHex: Long,
    val topics: List<ItTopic>
)

object GlobalItCatalog {
    val categories: List<ItCategory> = listOf(
        ItCategory(
            id = "cs",
            title = "Computer Science",
            subtitle = "Fundamentals, architecture, number systems, logic gates, algorithms & DSA",
            accentHex = 0xFF22D3EE,
            topics = listOf(
                ItTopic("Computer Architecture", "CPU fetch-decode-execute cycle, registers, cache & buses", "Explain CPU architecture, registers, ALU, and L1/L2/L3 cache hierarchy clearly.", NexoraMode.LEARNING),
                ItTopic("Number Systems", "Binary, Octal, Decimal & Hexadecimal conversions", "Teach me binary, hexadecimal, and two's complement signed representation with step-by-step examples.", NexoraMode.LEARNING),
                ItTopic("Boolean Algebra & Logic Gates", "AND, OR, NOT, XOR, NAND, truth tables & simplification", "Explain Boolean algebra laws and how NAND gates can build any digital logic circuit.", NexoraMode.LEARNING),
                ItTopic("Data Structures & Algorithms", "Trees, Graphs, Hash Maps, Sorting & Big-O complexity", "Give a clear guide to core Data Structures (Arrays, HashMaps, Trees, Graphs) with Big-O complexities.", NexoraMode.PROGRAMMING)
            )
        ),
        ItCategory(
            id = "programming",
            title = "Programming Languages",
            subtitle = "Python, Java, JS, TypeScript, C/C++, C#, PHP, SQL, Go & Rust",
            accentHex = 0xFF3B82F6,
            topics = listOf(
                ItTopic("Python & Automation", "Clean syntax, OOP, async, scripting & data tools", "Write a well-structured Python script demonstrating OOP, type hints, and error handling.", NexoraMode.PROGRAMMING),
                ItTopic("TypeScript & JavaScript", "Modern ES6+, async/await, strict types & closures", "Explain closures, promises, async/await, and why TypeScript catches bugs at compile time.", NexoraMode.PROGRAMMING),
                ItTopic("Systems: C, C++ & Rust", "Pointers, memory safety, RAII, ownership & borrowing", "Compare manual memory management in C/C++ with Rust's ownership and borrow checker.", NexoraMode.PROGRAMMING),
                ItTopic("Java, C# & Go", "JVM, .NET, concurrency goroutines & enterprise patterns", "Compare concurrency models in Java virtual threads, C# async/await, and Go goroutines.", NexoraMode.PROGRAMMING),
                ItTopic("SQL & Query Mastery", "Joins, indexes, CTEs, window functions & normalization", "Show practical SQL examples for INNER/LEFT JOIN, CTEs, window functions, and index optimization.", NexoraMode.PROGRAMMING)
            )
        ),
        ItCategory(
            id = "web",
            title = "Web & Full-Stack",
            subtitle = "HTML, CSS, React, Next.js, Node.js, REST & GraphQL APIs",
            accentHex = 0xFF06B6D4,
            topics = listOf(
                ItTopic("HTML5 & Modern CSS", "Semantic HTML, Flexbox, Grid, accessibility & responsive design", "Explain CSS Grid vs Flexbox with clean responsive layout code examples.", NexoraMode.PROGRAMMING),
                ItTopic("React & Next.js", "Hooks, server components, routing & state patterns", "Explain React state, useEffect pitfalls, and when to use Next.js Server Components.", NexoraMode.DEBUGGING),
                ItTopic("Node.js & Backend APIs", "REST design, middleware, JWT auth, rate limiting & validation", "Design a secure REST API architecture in Node.js with input validation and rate limiting.", NexoraMode.PROGRAMMING)
            )
        ),
        ItCategory(
            id = "ai",
            title = "Artificial Intelligence",
            subtitle = "Machine Learning, Deep Learning, Neural Networks, LLMs & Prompting",
            accentHex = 0xFF8B5CF6,
            topics = listOf(
                ItTopic("Machine Learning Foundations", "Supervised vs unsupervised learning, bias-variance & evaluation", "Explain supervised, unsupervised, and reinforcement learning with precision/recall tradeoffs.", NexoraMode.LEARNING),
                ItTopic("Neural Networks & Deep Learning", "Backpropagation, activation functions, CNNs & Transformers", "Explain how neural networks learn using gradient descent and backpropagation.", NexoraMode.RESEARCH),
                ItTopic("Large Language Models & Prompting", "Tokens, embeddings, attention, RAG & prompt engineering", "Explain how LLMs tokenize text, compute self-attention, and use Retrieval-Augmented Generation (RAG).", NexoraMode.RESEARCH)
            )
        ),
        ItCategory(
            id = "cybersecurity",
            title = "Cybersecurity",
            subtitle = "Ethical security, cryptography, forensics & defensive hardening",
            accentHex = 0xFF10B981,
            topics = listOf(
                ItTopic("Cybersecurity Fundamentals", "CIA Triad, threat modeling, least privilege & Zero Trust", "Explain the CIA Triad, Zero Trust architecture, and defense-in-depth principles.", NexoraMode.CYBERSECURITY),
                ItTopic("Cryptography Essentials", "AES, RSA, ECC, hashing (SHA-256/Argon2) & digital signatures", "Explain symmetric vs asymmetric encryption, password hashing with Argon2, and TLS certificates.", NexoraMode.CYBERSECURITY),
                ItTopic("AppSec & OWASP Defense", "Preventing SQLi, XSS, CSRF, SSRF & broken access control", "Walk through the top OWASP web vulnerabilities and exact code-level defensive mitigations.", NexoraMode.CYBERSECURITY),
                ItTopic("Digital Forensics & SOC", "Incident response lifecycle, log correlation & evidence integrity", "Explain the incident response lifecycle and chain-of-custody principles in digital forensics.", NexoraMode.CYBERSECURITY)
            )
        ),
        ItCategory(
            id = "networking",
            title = "Networking",
            subtitle = "TCP/IP, DNS, HTTP/HTTPS, routing, switching & firewalls",
            accentHex = 0xFF38BDF8,
            topics = listOf(
                ItTopic("TCP/IP & OSI Model", "Layers, encapsulation, TCP handshake vs UDP & subnetting", "Explain the OSI vs TCP/IP model and how subnetting with CIDR masks works.", NexoraMode.LEARNING),
                ItTopic("DNS, HTTP/3 & TLS", "Records (A/AAAA/CNAME/MX), HTTP status codes & encryption", "Explain DNS record types and how HTTP/2 & HTTP/3 improve web latency.", NexoraMode.GENERAL_IT),
                ItTopic("Routing, Switching & Firewalls", "VLANs, BGP/OSPF basics, NAT & stateful packet inspection", "Explain VLANs, NAT, routing vs switching, and stateful firewall rule design.", NexoraMode.CYBERSECURITY),
                ItTopic("Network Troubleshooting", "ping, traceroute, dig, tcpdump & diagnosing packet loss", "Give a systematic network troubleshooting checklist using ping, traceroute, dig, and ss/netstat.", NexoraMode.DEBUGGING)
            )
        ),
        ItCategory(
            id = "other",
            title = "Cloud, DevOps, OS & Careers",
            subtitle = "Cloud, Linux, Databases, Data Science, IoT, Mobile, Hardware & Careers",
            accentHex = 0xFFA78BFA,
            topics = listOf(
                ItTopic("Cloud Computing & DevOps", "Containers, Docker, Kubernetes, CI/CD & IaC", "Explain containers vs VMs, Dockerfiles, and a production CI/CD pipeline.", NexoraMode.GENERAL_IT),
                ItTopic("Linux & Operating Systems", "Processes, threads, virtual memory, permissions & bash", "Explain Linux file permissions, processes vs threads, and essential CLI administration commands.", NexoraMode.GENERAL_IT),
                ItTopic("Data Science, IoT & Blockchain", "Pipelines, MQTT embedded sensors & distributed consensus", "Compare relational databases, data warehouses, MQTT for IoT, and consensus fundamentals.", NexoraMode.RESEARCH),
                ItTopic("Mobile, Hardware & IT Careers", "Android/Kotlin, PC hardware troubleshooting & career roadmaps", "Give an honest comparison of IT career tracks (Software, Cybersecurity, Cloud, Data) and how to start.", NexoraMode.CAREER_GUIDANCE)
            )
        )
    )
}
