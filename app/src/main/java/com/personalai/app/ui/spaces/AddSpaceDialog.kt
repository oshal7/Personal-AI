package com.personalai.app.ui.spaces

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personalai.app.R

@Composable
fun AddSpaceDialog(onDismiss: () -> Unit, onConfirm: (name: String, fileName: String?, content: String) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf<String?>(null) }
    var fileContent by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            fileName = queryDisplayName(uri, context)
            fileContent = readTextContent(uri, context)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spaces_add_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.spaces_add_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.padding(top = 12.dp)) {
                    AssistChip(
                        onClick = { filePickerLauncher.launch(arrayOf("text/*")) },
                        label = { Text(fileName ?: stringResource(R.string.spaces_add_attach_file)) },
                    )
                }
                if (fileName != null) {
                    Text(
                        text = stringResource(R.string.spaces_add_file_attached),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name, fileName, fileContent) },
            ) { Text(stringResource(R.string.spaces_add_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.spaces_add_cancel)) }
        },
    )
}

private fun queryDisplayName(uri: Uri, context: android.content.Context): String? =
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
    }

private fun readTextContent(uri: Uri, context: android.content.Context): String =
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
