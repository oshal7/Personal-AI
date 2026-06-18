package com.personalai.app.ui.tasks

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.personalai.app.R
import com.personalai.app.data.db.TaskEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(viewModel: TasksViewModel, onBack: () -> Unit) {
    val tasks by viewModel.tasks.collectAsState()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tasks_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tasks_add_title))
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (tasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.tasks_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            } else {
                Column {
                    if (!viewModel.canScheduleExactAlarms()) {
                        ExactAlarmBanner(onOpenSettings = {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                            context.startActivity(intent)
                        })
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(tasks, key = { it.id }) { task ->
                            TaskRow(
                                task = task,
                                onToggle = { viewModel.setDone(task.id, !task.isDone) },
                                onDelete = { viewModel.delete(task.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTaskDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, reminderAtMillis ->
                showAddDialog = false
                if (reminderAtMillis != null &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                viewModel.addTask(title, reminderAtMillis)
            },
        )
    }
}

@Composable
private fun ExactAlarmBanner(onOpenSettings: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.tasks_exact_alarm_banner),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.tasks_exact_alarm_action))
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskEntity, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = task.isDone, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
                Text(
                    text = task.title,
                    style = if (task.isDone) {
                        MaterialTheme.typography.bodyLarge.copy(textDecoration = TextDecoration.LineThrough)
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                if (task.reminderAtMillis != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(
                            Icons.Filled.Alarm,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = formatReminder(task.reminderAtMillis),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.tasks_delete))
            }
        }
    }
}

private fun formatReminder(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
