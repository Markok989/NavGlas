package com.example.naviglas.receiver

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.naviglas.service.VoiceAssistantService

class BluetoothConnectReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val serviceIntent = Intent(context, VoiceAssistantService::class.java).apply {
                    this.action = VoiceAssistantService.ACTION_START_SERVICE
                }
                context.startForegroundService(serviceIntent)
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val serviceIntent = Intent(context, VoiceAssistantService::class.java).apply {
                    this.action = VoiceAssistantService.ACTION_STOP_SERVICE
                }
                context.startService(serviceIntent)
            }
        }
    }
}
