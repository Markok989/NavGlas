package com.example.naviglas.engine

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.telecom.TelecomManager
import android.telephony.SmsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ActionExecutor {

    /**
     * Izvršava determinističku sistemsku akciju iz prepoznate komande.
     */
    @SuppressLint("MissingPermission")
    fun execute(
        context: Context,
        command: ParsedCommandResult,
        contacts: List<Contact>,
        onResult: (String) -> Unit
    ) {
        val contact = command.contactName?.let { name ->
            contacts.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }

        when (command.action) {
            CommandAction.CALL -> {
                val phoneNumber = contact?.phoneNumber ?: command.contactName
                if (!phoneNumber.isNull_or_blank()) {
                    val intent = Intent(Intent.ACTION_CALL).apply {
                        data = Uri.parse("tel:$phoneNumber")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        context.startActivity(intent)
                        onResult("Pozivam ${contact?.name ?: phoneNumber}.")
                    } catch (e: Exception) {
                        onResult("Greška pri pokretanju poziva.")
                    }
                } else {
                    onResult("Broj telefona nije pronađen.")
                }
            }

            CommandAction.SEND_SMS -> {
                val phoneNumber = contact?.phoneNumber
                val messageText = command.messageText

                if (!phoneNumber.isNull_or_blank() && !messageText.isNull_or_blank()) {
                    try {
                        val smsManager = context.getSystemService(SmsManager::class.java)
                        smsManager.sendTextMessage(phoneNumber, null, messageText, null, null)
                        onResult("Poruka uspešno poslata za ${contact?.name ?: phoneNumber}.")
                    } catch (e: Exception) {
                        onResult("Greška pri slanju SMS poruke.")
                    }
                } else {
                    onResult("Nedostaje primalac ili tekst poruke.")
                }
            }

            CommandAction.ANSWER_CALL -> {
                try {
                    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                    telecomManager.acceptRingingCall()
                    onResult("Poziv je prihvaćen.")
                } catch (e: Exception) {
                    onResult("Greška pri javljanju na poziv.")
                }
            }

            CommandAction.REJECT_CALL -> {
                try {
                    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                    telecomManager.endCall()
                    onResult("Poziv je odbijen.")
                } catch (e: Exception) {
                    onResult("Greška pri odbijanju poziva.")
                }
            }

            CommandAction.SILENCE_CALL -> {
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                    onResult("Zvono je utišano.")
                } catch (e: Exception) {
                    onResult("Greška pri utišavanju zvona.")
                }
            }

            CommandAction.SEND_SOS -> {
                try {
                    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                    val sosText = if (loc != null) {
                        "Hitno! Potrebna pomoć na putu. Moja lokacija: https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
                    } else {
                        "Hitno! Potrebna pomoć na putu!"
                    }

                    val sosContactNumber = contact?.phoneNumber ?: contacts.firstOrNull()?.phoneNumber
                    if (sosContactNumber != null) {
                        val smsManager = context.getSystemService(SmsManager::class.java)
                        smsManager.sendTextMessage(sosContactNumber, null, sosText, null, null)
                        onResult("SOS poruka poslata sa trenutnim koordinatama.")
                    } else {
                        onResult("Nije definisan SOS kontakt.")
                    }
                } catch (e: Exception) {
                    onResult("Greška pri slanju SOS lokacije.")
                }
            }

            CommandAction.WHERE_AM_I -> {
                try {
                    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                    if (loc != null && Geocoder.isPresent()) {
                        val geocoder = Geocoder(context, Locale("sr", "RS"))
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                        val address = addresses?.firstOrNull()
                        if (address != null) {
                            val locality = address.locality ?: address.subAdminArea ?: ""
                            val thoroughfare = address.thoroughfare ?: ""
                            onResult("Nalazite se u mestu $locality, $thoroughfare.")
                        } else {
                            onResult("Nalazite se na koordinatama: ${loc.latitude}, ${loc.longitude}.")
                        }
                    } else {
                        onResult("Trenutna lokacija nije dostupna.")
                    }
                } catch (e: Exception) {
                    onResult("Ne mogu da odredim lokaciju.")
                }
            }

            CommandAction.WHAT_TIME -> {
                val timeFormat = SimpleDateFormat("HH 'sati i' mm 'minuta'", Locale("sr", "RS"))
                val currentTime = timeFormat.format(Date())
                onResult("Sada je $currentTime.")
            }

            else -> {
                onResult("Akcija nije prepoznata.")
            }
        }
    }

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }
}
