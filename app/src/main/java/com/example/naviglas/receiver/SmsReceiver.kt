package com.example.naviglas.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val sender = sms.originatingAddress ?: "Nepoznat broj"
                val body = sms.messageBody ?: ""

                val serviceIntent = Intent(context, com.example.naviglas.service.VoiceAssistantService::class.java).apply {
                    action = "ACTION_INCOMING_SMS"
                    putExtra("EXTRA_SMS_SENDER", sender)
                    putExtra("EXTRA_SMS_BODY", body)
                }
                context.startService(serviceIntent)
            }
        }
    }
}
