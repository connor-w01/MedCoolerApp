
package com.example.blereaderapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BleViewModel : ViewModel() {
    // Use MutableStateFlow to hold the characteristic value as a Float
    private val _characteristicValue = MutableStateFlow<Float?>(null)
    val characteristicValue: StateFlow<Float?> get() = _characteristicValue

    // Function to update the characteristic value
    fun updateCharacteristicValue(newValue: Float) {
        viewModelScope.launch {
            _characteristicValue.value = newValue
        }
    }
}

