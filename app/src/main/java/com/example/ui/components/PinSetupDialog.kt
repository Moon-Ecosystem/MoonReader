package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class PinDialogMode {
    SET_NEW,
    CHANGE_PIN,
    DISABLE_PIN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSetupDialog(
    mode: PinDialogMode,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
    verifyCurrentPin: (String) -> Boolean,
    saveNewPin: (String) -> Unit,
    disableLock: () -> Unit
) {
    // Steps:
    // SET_NEW: 0 = Enter New, 1 = Confirm New
    // CHANGE_PIN: 0 = Enter Current, 1 = Enter New, 2 = Confirm New
    // DISABLE_PIN: 0 = Enter Current
    var step by remember { mutableStateOf(0) }
    var currentEnteredPin by remember { mutableStateOf("") }
    var firstNewPin by remember { mutableStateOf("") }
    var enteredInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val title = when (mode) {
        PinDialogMode.SET_NEW -> if (step == 0) "Create 4-Digit Passcode" else "Confirm Passcode"
        PinDialogMode.CHANGE_PIN -> when (step) {
            0 -> "Enter Current Passcode"
            1 -> "Enter New Passcode"
            else -> "Confirm New Passcode"
        }
        PinDialogMode.DISABLE_PIN -> "Enter Passcode to Disable"
    }

    val subtitle = when (mode) {
        PinDialogMode.SET_NEW -> if (step == 0) "Choose a numeric PIN to lock MangaReader" else "Re-enter the PIN to confirm"
        PinDialogMode.CHANGE_PIN -> when (step) {
            0 -> "Verify your identity"
            1 -> "Choose your new 4-digit PIN"
            else -> "Re-enter new PIN to confirm"
        }
        PinDialogMode.DISABLE_PIN -> "Confirm your current passcode to remove App Lock"
    }

    LaunchedEffect(enteredInput) {
        if (enteredInput.length == 4) {
            when (mode) {
                PinDialogMode.SET_NEW -> {
                    if (step == 0) {
                        firstNewPin = enteredInput
                        step = 1
                        enteredInput = ""
                        errorMessage = null
                    } else {
                        if (enteredInput == firstNewPin) {
                            saveNewPin(enteredInput)
                            onSuccess()
                        } else {
                            errorMessage = "Passcodes do not match. Try again."
                            delay(400)
                            step = 0
                            enteredInput = ""
                            firstNewPin = ""
                        }
                    }
                }
                PinDialogMode.CHANGE_PIN -> {
                    if (step == 0) {
                        if (verifyCurrentPin(enteredInput)) {
                            step = 1
                            enteredInput = ""
                            errorMessage = null
                        } else {
                            errorMessage = "Incorrect passcode"
                            delay(400)
                            enteredInput = ""
                        }
                    } else if (step == 1) {
                        firstNewPin = enteredInput
                        step = 2
                        enteredInput = ""
                        errorMessage = null
                    } else {
                        if (enteredInput == firstNewPin) {
                            saveNewPin(enteredInput)
                            onSuccess()
                        } else {
                            errorMessage = "Passcodes do not match. Try again."
                            delay(400)
                            step = 1
                            enteredInput = ""
                            firstNewPin = ""
                        }
                    }
                }
                PinDialogMode.DISABLE_PIN -> {
                    if (verifyCurrentPin(enteredInput)) {
                        disableLock()
                        onSuccess()
                    } else {
                        errorMessage = "Incorrect passcode"
                        delay(400)
                        enteredInput = ""
                    }
                }
            }
        }
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("pin_setup_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 4 PIN Dots indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredInput.length
                        val dotColor = if (errorMessage != null) {
                            MaterialTheme.colorScheme.error
                        } else if (isFilled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        }

                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                                .border(
                                    width = 2.dp,
                                    color = if (errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                Box(modifier = Modifier.height(26.dp), contentAlignment = Alignment.Center) {
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Numeric keypad
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "backspace")
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (row in keys) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (key in row) {
                                when (key) {
                                    "" -> {
                                        Spacer(modifier = Modifier.size(56.dp))
                                    }
                                    "backspace" -> {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    if (enteredInput.isNotEmpty()) {
                                                        enteredInput = enteredInput.dropLast(1)
                                                        errorMessage = null
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                                contentDescription = "Backspace",
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    else -> {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                                .clickable {
                                                    if (enteredInput.length < 4) {
                                                        enteredInput += key
                                                        errorMessage = null
                                                    }
                                                }
                                                .testTag("dialog_pin_key_$key"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = key,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
