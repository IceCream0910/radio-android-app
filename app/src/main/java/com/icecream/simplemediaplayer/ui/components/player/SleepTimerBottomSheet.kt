package com.icecream.simplemediaplayer.ui.components.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.icecream.simplemediaplayer.ui.SleepTimerState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerBottomSheet(
    timerState: SleepTimerState,
    remainingTimeString: String,
    onDismiss: () -> Unit,
    onStartTimer: (hours: Int, minutes: Int) -> Unit,
    onCancelTimer: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }

    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            if (timerState.isRunning) {
                // Show running timer
                RunningTimerContent(
                    remainingTimeString = remainingTimeString,
                    onCancel = {
                        onCancelTimer()
                        onDismiss()
                    }
                )
            } else {
                // Show timer picker
                TimerPickerContent(
                    onStartTimer = { hours, minutes ->
                        onStartTimer(hours, minutes)
                        onDismiss()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RunningTimerContent(
    remainingTimeString: String,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "타이머 작동 중",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = remainingTimeString,
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 56.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "남은 시간",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("타이머 취소")
        }
    }
}

@Composable
private fun TimerPickerContent(
    onStartTimer: (hours: Int, minutes: Int) -> Unit
) {
    var selectedHours by remember { mutableStateOf(0) }
    var selectedMinutes by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "설정한 시간이 지나면 라디오를 자동으로 꺼줄게요.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hours picker
            TimePickerColumn(
                value = selectedHours,
                range = 0..23,
                label = "시간",
                onValueChange = { selectedHours = it }
            )

            Spacer(modifier = Modifier.width(32.dp))

            // Minutes picker
            TimePickerColumn(
                value = selectedMinutes,
                range = 0..59,
                label = "분",
                onValueChange = { selectedMinutes = it }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickSelectButton(
                text = "+5분",
                modifier = Modifier.weight(1f),
                onClick = {
                    val totalMinutes = selectedHours * 60 + selectedMinutes + 5
                    selectedHours = (totalMinutes / 60).coerceAtMost(23)
                    selectedMinutes = (totalMinutes % 60).coerceAtMost(59)
                }
            )
            QuickSelectButton(
                text = "+30분",
                modifier = Modifier.weight(1f),
                onClick = {
                    val totalMinutes = selectedHours * 60 + selectedMinutes + 30
                    selectedHours = (totalMinutes / 60).coerceAtMost(23)
                    selectedMinutes = (totalMinutes % 60).coerceAtMost(59)
                }
            )
            QuickSelectButton(
                text = "+1시간",
                modifier = Modifier.weight(1f),
                onClick = {
                    selectedHours = (selectedHours + 1).coerceAtMost(23)
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onStartTimer(selectedHours, selectedMinutes) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = selectedHours > 0 || selectedMinutes > 0
        ) {
            Text("타이머 시작")
        }
    }
}

@Composable
private fun TimePickerColumn(
    value: Int,
    range: IntRange,
    label: String,
    onValueChange: (Int) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var textValue by remember { mutableStateOf(String.format(Locale.getDefault(), "%02d", value)) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(value) {
        if (!isEditing) {
            textValue = String.format(Locale.getDefault(), "%02d", value)
        }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = {
                if (value < range.last) onValueChange(value + 1)
            }
        ) {
            Text(
                text = "▲",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        Surface(
            modifier = Modifier
                .width(80.dp)
                .height(60.dp)
                .clickable {
                    isEditing = true
                },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                if (isEditing) {
                    TextField(
                        value = textValue,
                        onValueChange = { newValue ->
                            if (newValue.length <= 2 && newValue.all { it.isDigit() }) {
                                textValue = newValue
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            focusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            unfocusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val newValue = textValue.toIntOrNull() ?: value
                                val coercedValue = newValue.coerceIn(range)
                                onValueChange(coercedValue)
                                isEditing = false
                                focusManager.clearFocus()
                            }
                        ),
                        singleLine = true
                    )
                } else {
                    Text(
                        text = String.format(Locale.getDefault(), "%02d", value),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        IconButton(
            onClick = {
                if (value > range.first) onValueChange(value - 1)
            }
        ) {
            Text(
                text = "▼",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickSelectButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(40.dp)
    ) {
        Text(text, fontSize = 13.sp)
    }
}

