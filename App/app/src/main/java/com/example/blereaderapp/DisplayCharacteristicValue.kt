
package com.example.blereaderapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp


@Composable
fun DisplayCharacteristicValue(value: ByteArray?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (value != null) {
            val interpretedValue = interpretCharacteristicValue(value)
            Text(
                text = interpretedValue,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        } else {
            Text(
                text = "No value received",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
        }
    }
}

fun interpretCharacteristicValue(value: ByteArray): String {
    // Decode the byte array into a string using UTF-8 encoding
    return value.toString(Charsets.UTF_8)
}

