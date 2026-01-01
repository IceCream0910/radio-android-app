package com.icecream.simplemediaplayer.ui.components.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.icecream.simplemediaplayer.data.model.RadioStation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBottomSheet(
    isOpen: Boolean,
    initialQuery: String,
    results: List<RadioStation>,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    onClickResult: (RadioStation) -> Unit
) {
    if (!isOpen) return

    val textState = remember { mutableStateOf(TextFieldValue(initialQuery)) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(initialQuery) {
        if (initialQuery != textState.value.text) {
            textState.value = TextFieldValue(initialQuery)
        }
    }
    LaunchedEffect(isOpen) {
        if (isOpen) focusRequester.requestFocus()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    value = textState.value,
                    onValueChange = {
                        textState.value = it
                        onQueryChange(it.text)
                    },
                    singleLine = true,
                    placeholder = { Text("스테이션 검색") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (textState.value.text.isNotEmpty()) {
                            IconButton(onClick = {
                                textState.value = TextFieldValue("")
                                onClear()
                            }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "검색 초기화")
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (results.isEmpty()) {
                Text(
                    text = "검색 결과가 없습니다",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn {
                    items(results) { station ->
                        SearchResultRow(station = station, onClick = { onClickResult(station) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    station: RadioStation,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp)
    ) {
        Text(
            text = station.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
