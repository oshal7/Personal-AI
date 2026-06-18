package com.personalai.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.personalai.app.R
import com.personalai.app.data.db.ChatMessageEntity
import com.personalai.app.data.db.MessageRole
import com.personalai.app.ui.theme.userBubble
import com.personalai.app.voice.SpeechInputManager
import com.personalai.llama.LlamaSession
import com.personalai.llama.isBusy

private data class ChatCategory(
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val starterPrompt: String,
)

private val categories = listOf(
    ChatCategory(R.string.category_summarize_title, R.string.category_summarize_subtitle, Icons.Filled.Summarize, "Summarize this: "),
    ChatCategory(R.string.category_talk_title, R.string.category_talk_subtitle, Icons.Filled.Translate, ""),
    ChatCategory(R.string.category_plan_title, R.string.category_plan_subtitle, Icons.Filled.Alarm, "Remind me to "),
    ChatCategory(R.string.category_calculate_title, R.string.category_calculate_subtitle, Icons.Filled.Calculate, "What is "),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenTasks: () -> Unit,
) {
    val messages by viewModel.messages.collectAsState()
    val streamingReply by viewModel.streamingReply.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val taskSuggestion by viewModel.taskSuggestion.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val listState = rememberLazyListState()

    val context = LocalContext.current
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startVoiceInput()
    }

    LaunchedEffect(messages.size, streamingReply) {
        if (messages.isNotEmpty() || streamingReply.isNotEmpty()) {
            listState.scrollToItem(messages.size)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.chat_open_history))
                    }
                },
                actions = {
                    IconButton(onClick = onNewChat) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.chat_new_chat))
                    }
                    IconButton(onClick = onOpenTasks) {
                        Icon(Icons.Filled.Checklist, contentDescription = stringResource(R.string.chat_open_tasks))
                    }
                },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (messages.isEmpty() && streamingReply.isEmpty()) {
                EmptyState(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onCategoryTap = { prompt -> viewModel.onInputChange(prompt) },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(message, modifier = Modifier.animateItem())
                    }
                    if (streamingReply.isNotEmpty()) {
                        item(key = "streaming") {
                            MessageBubble(
                                ChatMessageEntity(sessionId = 0, role = MessageRole.ASSISTANT, content = streamingReply, timestampMillis = 0),
                                modifier = Modifier.animateItem(),
                            )
                        }
                    } else if (sessionState is LlamaSession.State.ProcessingUserPrompt) {
                        item(key = "typing") { TypingIndicator(modifier = Modifier.animateItem()) }
                    }
                }
            }

            AnimatedVisibility(
                visible = taskSuggestion != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                taskSuggestion?.let { suggestion ->
                    TaskSuggestionCard(
                        suggestion = suggestion,
                        onConfirm = viewModel::confirmTaskSuggestion,
                        onDismiss = viewModel::dismissTaskSuggestion,
                    )
                }
            }

            if (sessionState.isBusy && sessionState !is LlamaSession.State.Generating && sessionState !is LlamaSession.State.ProcessingUserPrompt) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(20.dp))
                    Text(stringResource(R.string.model_loading))
                }
            }

            if (voiceState is SpeechInputManager.State.Listening) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(R.string.voice_listening), color = MaterialTheme.colorScheme.primary)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = viewModel::onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
                    shape = MaterialTheme.shapes.large,
                )

                AnimatedContent(targetState = inputText.isNotBlank(), label = "sendOrMic") { hasText ->
                    if (hasText) {
                        IconButton(
                            onClick = viewModel::sendMessage,
                            enabled = sessionState is LlamaSession.State.ModelReady,
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = stringResource(R.string.chat_send))
                        }
                    } else {
                        val isListening = voiceState is SpeechInputManager.State.Listening
                        IconButton(
                            onClick = {
                                if (isListening) {
                                    viewModel.stopVoiceInput()
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                        PackageManager.PERMISSION_GRANTED
                                    if (granted) viewModel.startVoiceInput() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                                contentDescription = stringResource(if (isListening) R.string.voice_stop else R.string.voice_input),
                                tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, onCategoryTap: (String) -> Unit) {
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.chat_greeting),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.chat_greeting_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height(220.dp),
        ) {
            gridItems(categories) { category ->
                CategoryCard(category, onClick = { onCategoryTap(category.starterPrompt) })
            }
        }
    }
}

@Composable
private fun CategoryCard(category: ChatCategory, onClick: () -> Unit) {
    Card(
        modifier = Modifier.aspectRatio(1.4f).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Icon(category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(category.titleRes), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(category.subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TaskSuggestionCard(suggestion: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stringResource(R.string.task_suggestion_title), style = MaterialTheme.typography.labelLarge)
            Text(suggestion, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.task_suggestion_dismiss)) }
                TextButton(onClick = onConfirm) { Text(stringResource(R.string.task_suggestion_add)) }
            }
        }
    }
}

@Composable
private fun TypingIndicator(modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { index -> TypingDot(delayMillis = index * 150) }
            }
        }
    }
}

@Composable
private fun TypingDot(delayMillis: Int) {
    val transition = rememberInfiniteTransition(label = "typingDot")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, delayMillis = delayMillis),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), CircleShape)
    )
}

@Composable
private fun MessageBubble(message: ChatMessageEntity, modifier: Modifier = Modifier) {
    val isUser = message.role == MessageRole.USER
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.userBubble else MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.large,
        ) {
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = message.content,
                    color = if (isUser) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
