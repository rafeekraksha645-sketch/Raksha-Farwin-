package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.NexoraLanguage
import com.example.model.NexoraMode
import com.example.ui.components.LanguagePill
import com.example.ui.components.NexoraMarkdownText
import com.example.ui.theme.DeepBlack
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SpaceGroteskFamily
import com.example.viewmodel.ExhibitionUiState

private val EXHIBITION_DEMO_PROMPTS = listOf(
    "Explain how Artificial Intelligence Neural Networks learn in simple terms." to NexoraMode.LEARNING,
    "Write a Python algorithm for Dijkstra's shortest path and explain its complexity." to NexoraMode.PROGRAMMING,
    "How does HTTPS TLS encryption protect users on public Wi-Fi?" to NexoraMode.CYBERSECURITY,
    "What skills will define Cloud & AI Engineering over the next 5 years?" to NexoraMode.CAREER_GUIDANCE
)

@Composable
fun ExhibitionScreen(
    state: ExhibitionUiState,
    language: NexoraLanguage,
    onRunPrompt: (String, NexoraMode?) -> Unit,
    onStopGeneration: () -> Unit,
    onOpenLanguagePicker: () -> Unit,
    onExitExhibition: () -> Unit,
    modifier: Modifier = Modifier,
    onRetryPrompt: () -> Unit = { onRunPrompt(state.prompt, state.mode) }
) {
    BackHandler { onExitExhibition() }

    var customPrompt by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepBlack)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_nexora_exhibition_bg),
            contentDescription = "NEXORA AI Exhibition backdrop",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.65f
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            DeepBlack.copy(alpha = 0.5f),
                            MidnightNavy.copy(alpha = 0.78f),
                            DeepBlack.copy(alpha = 0.92f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Minimal Presentation Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.img_nexora_icon),
                        contentDescription = "NEXORA AI logo",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "NEXORA AI",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = SpaceGroteskFamily,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = "EXHIBITION & KEYNOTE MODE",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LanguagePill(
                        currentLanguage = language,
                        onClick = onOpenLanguagePicker
                    )
                    Surface(
                        onClick = {
                            onStopGeneration()
                            onExitExhibition()
                        },
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        modifier = Modifier.testTag("exit_exhibition_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Exit exhibition mode",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Exit",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Stage Area (Projector-Friendly Large Typography)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 900.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MidnightNavy.copy(alpha = 0.72f))
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (state.prompt.isBlank() && state.response.isBlank() && state.errorMessage == null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Discover More.\nUnderstand Better.\nThink Beyond.",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 38.sp,
                                lineHeight = 46.sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = "Your Global Gateway to IT Intelligence — Ready for live audience demonstrations in English, தமிழ், and සිංහල.",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFFCBD5E1)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "TAP A LIVE DEMO TOPIC BELOW OR ASK ANYTHING:",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ElectricBlue.copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "LIVE AUDIENCE QUESTION · ${state.mode.title.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonCyan
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = state.prompt,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White
                                )
                            }
                        }

                        if (state.errorMessage != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = state.errorMessage,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFFFCA5A5)
                                )
                                OutlinedButton(
                                    onClick = onRetryPrompt,
                                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.7f)),
                                    modifier = Modifier.testTag("exhibition_retry_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Retry exhibition prompt",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry", color = Color.White)
                                }
                            }
                        } else if (state.isStreaming && state.response.isBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .testTag("exhibition_loading_indicator")
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp,
                                    color = NeonCyan
                                )
                                Text(
                                    text = state.statusText ?: "Connecting to NEXORA AI...",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFFE2E8F0)
                                )
                            }
                        } else {
                            NexoraMarkdownText(
                                markdown = state.response,
                                textColor = Color(0xFFF1F5F9),
                                baseTextStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 18.sp,
                                    lineHeight = 28.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Demo Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 900.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EXHIBITION_DEMO_PROMPTS.forEachIndexed { idx, (demoPrompt, demoMode) ->
                    AssistChip(
                        onClick = { onRunPrompt(demoPrompt, demoMode) },
                        label = {
                            Text(
                                text = "Demo ${idx + 1}: ${demoMode.shortTitle}",
                                color = Color.White
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MidnightNavy.copy(alpha = 0.85f)
                        ),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.35f)),
                        modifier = Modifier.testTag("exhibition_demo_${idx + 1}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Minimal Presentation Prompt Input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 900.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customPrompt,
                    onValueChange = { customPrompt = it },
                    placeholder = {
                        Text(
                            text = "Ask a live demonstration question...",
                            color = Color(0xFF94A3B8)
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = MidnightNavy.copy(alpha = 0.85f),
                        unfocusedContainerColor = MidnightNavy.copy(alpha = 0.7f),
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = ElectricBlue.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("exhibition_prompt_input")
                )

                if (state.isStreaming) {
                    IconButton(
                        onClick = onStopGeneration,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("exhibition_stop_button")
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = "Stop exhibition stream")
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (customPrompt.isNotBlank()) {
                                val q = customPrompt
                                customPrompt = ""
                                onRunPrompt(q, null)
                            }
                        },
                        enabled = customPrompt.isNotBlank(),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = NeonCyan,
                            contentColor = DeepBlack
                        ),
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("exhibition_send_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Present AI answer")
                    }
                }
            }
        }
    }
}
