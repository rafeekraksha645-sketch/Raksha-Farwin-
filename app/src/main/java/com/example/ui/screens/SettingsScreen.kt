package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.NexoraLanguage
import com.example.model.NexoraMode
import com.example.model.NexoraModelTier
import com.example.model.NexoraPreferencesData
import com.example.model.NexoraResponseStyle
import com.example.model.NexoraThemeMode
import com.example.ui.components.GlassCard
import com.example.ui.components.GoogleSignInButton

@Composable
fun SettingsScreen(
    preferences: NexoraPreferencesData,
    isAuthenticated: Boolean,
    userDisplayName: String,
    userEmail: String?,
    statusBanner: String?,
    onUpdateLanguage: (NexoraLanguage) -> Unit,
    onUpdateThemeMode: (NexoraThemeMode) -> Unit,
    onUpdateResponseStyle: (NexoraResponseStyle) -> Unit,
    onUpdateSimpleExplanations: (Boolean) -> Unit,
    onUpdateDefaultMode: (NexoraMode) -> Unit,
    onUpdateModelTier: (NexoraModelTier) -> Unit,
    onClearAllHistory: () -> Unit,
    onSignOut: () -> Unit,
    onDismissStatusBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings & Preferences",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Customize language intelligence, appearance, Gemini behavior, and privacy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!statusBanner.isNullOrBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = statusBanner,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissStatusBanner) {
                            Text("OK")
                        }
                    }
                }
            }
        }

        // 1. Account & Cloud Sync
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Account & Data Isolation",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (isAuthenticated) {
                    Text(
                        text = "Signed in as $userDisplayName${if (!userEmail.isNullOrBlank()) " ($userEmail)" else ""}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Your conversations and preferences are securely isolated in your private cloud storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_sign_out_button")
                    ) {
                        Text("Sign Out")
                    }
                } else {
                    Text(
                        text = "Currently using a temporary Guest Session. Sign in with Google to save and sync chat history across devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    authError?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    GoogleSignInButton(
                        onAuthSuccess = { authError = null },
                        onAuthError = { authError = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 2. Language Preferences
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Language Preferences",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "NEXORA AI supports Auto-Detection, English, Tamil (தமிழ்), and Sinhala (සිංහල).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NexoraLanguage.entries.forEach { lang ->
                        val selected = preferences.language == lang
                        Surface(
                            onClick = { onUpdateLanguage(lang) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_lang_${lang.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(lang.nativeLabel, style = MaterialTheme.typography.titleMedium)
                                if (selected) {
                                    Text(
                                        text = "ACTIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Theme Mode
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Theme & Appearance",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NexoraThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = preferences.themeMode == mode,
                            onClick = { onUpdateThemeMode(mode) },
                            label = {
                                Text(
                                    text = when (mode) {
                                        NexoraThemeMode.DARK -> "Dark"
                                        NexoraThemeMode.LIGHT -> "Light"
                                        NexoraThemeMode.SYSTEM -> "System"
                                    }
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_theme_${mode.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // 4. AI & Response Preferences
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "AI & Response Preferences",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Response Depth",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NexoraResponseStyle.entries.forEach { style ->
                        FilterChip(
                            selected = preferences.responseStyle == style,
                            onClick = { onUpdateResponseStyle(style) },
                            label = { Text(style.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Simple Technical Explanations",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Prioritize beginner-friendly analogies and plain terminology.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = preferences.simpleExplanations,
                        onCheckedChange = onUpdateSimpleExplanations,
                        modifier = Modifier.testTag("settings_simple_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Gemini Intelligence Model",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NexoraModelTier.entries.forEach { tier ->
                        val selected = preferences.modelTier == tier
                        Surface(
                            onClick = { onUpdateModelTier(tier) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(tier.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = tier.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Default Startup AI Mode",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NexoraMode.entries.chunked(2).forEach { rowModes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowModes.forEach { mode ->
                                FilterChip(
                                    selected = preferences.defaultMode == mode,
                                    onClick = { onUpdateDefaultMode(mode) },
                                    label = { Text(mode.shortTitle, maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowModes.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // 5. Privacy & Data Management
        item {
            GlassCard(
                borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Privacy & Data Control",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isAuthenticated) {
                        "Permanently delete all your cloud-saved conversations and messages."
                    } else {
                        "Clear your temporary in-memory guest session history."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("delete_all_history_button")
                ) {
                    Text(
                        if (isAuthenticated) "Delete All Cloud Conversations"
                        else "Clear Guest History"
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Confirm History Deletion") },
            text = {
                Text(
                    if (isAuthenticated) {
                        "This will permanently delete all your NEXORA AI cloud conversations. This action cannot be undone."
                    } else {
                        "Clear all temporary guest conversations from this session?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllHistory()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
