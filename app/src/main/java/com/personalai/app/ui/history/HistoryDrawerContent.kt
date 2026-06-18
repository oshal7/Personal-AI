package com.personalai.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personalai.app.R
import com.personalai.app.data.db.ChatSessionEntity

@Composable
fun HistoryDrawerContent(
    viewModel: HistoryViewModel,
    activeSessionId: Long,
    onNewChat: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onOpenSpaces: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val sessions by viewModel.sessions.collectAsState()

    ModalDrawerSheet {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.chat_new_chat)) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                selected = activeSessionId == 0L,
                onClick = onNewChat,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            if (sessions.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.history_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    items(sessions, key = { it.id }) { session ->
                        SessionRow(
                            session = session,
                            isSelected = session.id == activeSessionId,
                            onClick = { onSelectSession(session.id) },
                            onDelete = { viewModel.deleteSession(session.id) },
                        )
                    }
                }
            }

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.chat_open_spaces)) },
                icon = { Icon(Icons.Filled.Workspaces, contentDescription = null) },
                selected = false,
                onClick = onOpenSpaces,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.settings_open)) },
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                selected = false,
                onClick = onOpenSettings,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SessionRow(
    session: ChatSessionEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    NavigationDrawerItem(
        label = {
            Column {
                Text(session.title, maxLines = 1)
                if (session.lastMessagePreview.isNotBlank()) {
                    Text(
                        text = session.lastMessagePreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        },
        selected = isSelected,
        onClick = onClick,
        badge = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.history_delete))
            }
        },
        colors = NavigationDrawerItemDefaults.colors(),
        modifier = Modifier.fillMaxWidth(),
    )
}
