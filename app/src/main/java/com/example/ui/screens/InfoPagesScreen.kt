package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CoPresent
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.NexoraMode
import com.example.ui.components.GlassCard
import com.example.ui.components.NexoraBrandMark
import com.example.ui.components.iconVector

enum class InfoSection {
    FEATURES,
    ABOUT,
    PRIVACY,
    TERMS,
    CONTACT
}

@Composable
fun MoreHubScreen(
    onOpenSettings: () -> Unit,
    onOpenExhibition: () -> Unit,
    onOpenSection: (InfoSection) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "NEXORA AI Hub",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Platform tools, presentation mode, settings, and governance.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            HubRowCard(
                icon = Icons.Rounded.Settings,
                title = "Settings & Preferences",
                subtitle = "Language (EN/தமிழ்/සිංහල), Theme, AI Model & Account",
                onClick = onOpenSettings,
                testTag = "hub_settings"
            )
        }
        item {
            HubRowCard(
                icon = Icons.Rounded.CoPresent,
                title = "Exhibition Mode",
                subtitle = "Full-screen keynote & school demonstration stage",
                onClick = onOpenExhibition,
                testTag = "hub_exhibition"
            )
        }
        item {
            HubRowCard(
                icon = Icons.Rounded.AutoAwesome,
                title = "Features & Capabilities",
                subtitle = "7 specialized AI modes, code intelligence & multilingual engine",
                onClick = { onOpenSection(InfoSection.FEATURES) },
                testTag = "hub_features"
            )
        }
        item {
            HubRowCard(
                icon = Icons.Rounded.Info,
                title = "About NEXORA AI",
                subtitle = "Global IT intelligence vision, accuracy & trust principles",
                onClick = { onOpenSection(InfoSection.ABOUT) },
                testTag = "hub_about"
            )
        }
        item {
            HubRowCard(
                icon = Icons.Rounded.PrivacyTip,
                title = "Privacy Policy",
                subtitle = "Data isolation, authentication, and AI request handling",
                onClick = { onOpenSection(InfoSection.PRIVACY) },
                testTag = "hub_privacy"
            )
        }
        item {
            HubRowCard(
                icon = Icons.Rounded.Description,
                title = "Terms of Service",
                subtitle = "Acceptable educational, coding & defensive security usage",
                onClick = { onOpenSection(InfoSection.TERMS) },
                testTag = "hub_terms"
            )
        }
        item {
            HubRowCard(
                icon = Icons.Rounded.Email,
                title = "Contact & Feedback",
                subtitle = "Platform assistance and technical verification notes",
                onClick = { onOpenSection(InfoSection.CONTACT) },
                testTag = "hub_contact"
            )
        }
    }
}

@Composable
private fun HubRowCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    GlassCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun InfoDetailScreen(
    section: InfoSection,
    onBack: () -> Unit,
    onLaunchChatMode: (NexoraMode) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val title = when (section) {
        InfoSection.FEATURES -> "Features & Capabilities"
        InfoSection.ABOUT -> "About NEXORA AI"
        InfoSection.PRIVACY -> "Privacy Policy"
        InfoSection.TERMS -> "Terms of Service"
        InfoSection.CONTACT -> "Contact & Support"
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("info_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Navigate back"
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        when (section) {
            InfoSection.FEATURES -> {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Real-Time Gemini AI Architecture",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "NEXORA AI streams live responses through Firebase AI Logic (Gemini 3.8 Flash & Gemini 3.5 Flash-Lite) protected by Firebase App Check. Multi-turn conversation history, screenshot debugging via Android Photo Picker, syntax-highlighted code blocks, and full-screen/exhibition modes work together seamlessly.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    Text(
                        text = "7 Specialized AI Modes",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                NexoraMode.entries.forEach { mode ->
                    item {
                        GlassCard(
                            onClick = { onLaunchChatMode(mode) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = mode.iconVector(),
                                    contentDescription = mode.title,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(mode.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = mode.tagline,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            InfoSection.ABOUT -> {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        NexoraBrandMark(showImageEmblem = true, iconSize = 40.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Discover More. Understand Better. Think Beyond.",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "NEXORA AI is built to make global Information Technology and Computer Science knowledge accessible to students, programmers, cybersecurity learners, and engineers everywhere—with native support for English, Tamil (தமிழ்), and Sinhala (සිංහල).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Accuracy & Truth Commitment", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• Never fabricates facts, benchmarks, or citations.\n• Distinguishes verified computer science fundamentals from assumptions.\n• Explicitly notes when code has not been executed or live verification is unavailable.\n• Promotes ethical, defensive cybersecurity practices.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            InfoSection.PRIVACY -> {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text("How NEXORA AI Protects Your Data", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "1. Guest Sessions: When using NEXORA AI without signing in, conversations stay strictly in temporary device memory and are cleared when the session ends.\n\n" +
                                "2. Authenticated Cloud Sync: When signed in with Google via Jetpack Credential Manager, conversations and preferences are stored under your isolated user path in Cloud Firestore, enforced by zero-trust security rules.\n\n" +
                                "3. AI Processing: Prompts and optional screenshots attached via Android Photo Picker are transmitted securely via Firebase App Check to Gemini models to generate responses. No broad device storage permissions are requested.\n\n" +
                                "4. Full Deletion Control: You can delete individual chats or wipe all cloud history at any time in Settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            InfoSection.TERMS -> {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Terms of Service", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "1. Educational & Professional Guidance: NEXORA AI provides automated AI-generated technical explanations, code examples, and study guidance. Always review and test generated code before deploying to production.\n\n" +
                                "2. Ethical Cybersecurity Use: Cybersecurity Mode is restricted to defensive security, secure coding, and authorized educational learning. Using the platform to plan or execute unauthorized access is prohibited.\n\n" +
                                "3. No Warranty of Unverified Output: AI models may occasionally make mistakes. Verify critical architectural, legal, or security decisions independently.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            InfoSection.CONTACT -> {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Platform Support & Inquiries", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "NEXORA AI operates as an automated AI-powered IT intelligence platform.\n\n" +
                                "• For technical questions, troubleshooting, or learning support, start a session in the AI Assistant tab.\n" +
                                "• For classroom or keynote demonstrations, launch Exhibition Mode from the Home or More tab.\n" +
                                "• No external corporate street address or unverified contact channels are listed because NEXORA AI is delivered directly through this application.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
