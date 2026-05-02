package com.example.wavebridge

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ReceiverState {
    var stats by mutableStateOf(ReceiverStats())
}
