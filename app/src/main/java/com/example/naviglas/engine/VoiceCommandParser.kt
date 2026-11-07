package com.example.naviglas.engine

import java.util.Locale

enum class CommandAction {
    CALL,
    SEND_SMS,
    ANSWER_CALL,
    REJECT_CALL,
    SILENCE_CALL,
    REJECT_WITH_SMS,
    QUICK_ETA,
    SEND_SOS,
    WHERE_AM_I,
    WHAT_TIME,
    CANCEL,
    UNKNOWN
}

data class ParsedCommandResult(
    val action: CommandAction,
    val contactName: String? = null,
    val messageText: String? = null,
    val confidence: ConfidenceLevel = ConfidenceLevel.HIGH,
    val spokenFeedback: String
) {
    /**
     * Vraća izlaz u definisanom JSON formatu za eksternu sistemsku komunikaciju.
     */
    fun toJsonString(): String {
        val contactJson = if (contactName != null) "\"$contactName\"" else "null"
        val messageJson = if (messageText != null) "\"$messageText\"" else "null"
        return """
        {
          "action": "${action.name}",
          "contact_name": $contactJson,
          "message_text": $messageJson,
          "confidence": "${confidence.name}",
          "spoken_feedback": "$spokenFeedback"
        }
        """.trimIndent()
    }
}

object VoiceCommandParser {

    private val callKeywords = listOf("pozovi", "okreni", "cuj se sa", "zovi", "pozovite")
    private val smsKeywords = listOf("posalji poruku", "napisi poruku", "pusti poruku", "baci sms", "poruku", "napisi", "posalji")
    private val answerKeywords = listOf("javi se", "prihvati", "prihvati poziv", "alo", "javi")
    private val rejectKeywords = listOf("odbij", "odbij poziv", "prekini", "ne mogu sad")
    private val silenceKeywords = listOf("utisaj", "utisaj zvono", "zanemari")
    private val cancelKeywords = listOf("otkazi", "stop", "nemoj", "prekini radnju", "odustani")
    private val sosKeywords = listOf("salji sos", "sos", "pomoc na putu", "kvar na putu")
    private val locationKeywords = listOf("gde sam", "gde se nalazim", "moja lokacija")
    private val timeKeywords = listOf("koliko je sati", "koje je vreme", "tacno vreme")

    /**
     * Analizira sirovi izgovoreni tekst vozača i pretvara ga u determinističku komandu.
     */
    fun parseInput(
        rawInput: String,
        contacts: List<Contact> = emptyList(),
        customVipMap: Map<String, String> = emptyMap()
    ): ParsedCommandResult {
        val cleanedInput = rawInput.trim()
        val lowerInput = SerbianTextNormalizer.stripDiacritics(cleanedInput).lowercase(Locale.ROOT)

        if (cleanedInput.isEmpty()) {
            return ParsedCommandResult(
                action = CommandAction.UNKNOWN,
                confidence = ConfidenceLevel.LOW,
                spokenFeedback = "Nisam razumeo komandu."
            )
        }

        // 1. Otkazivanje (CANCEL)
        if (cancelKeywords.any { lowerInput.startsWith(it) || lowerInput == it }) {
            return ParsedCommandResult(
                action = CommandAction.CANCEL,
                confidence = ConfidenceLevel.HIGH,
                spokenFeedback = "Radnja je otkazana."
            )
        }

        // 2. SOS komanda
        if (sosKeywords.any { lowerInput.contains(it) }) {
            return ParsedCommandResult(
                action = CommandAction.SEND_SOS,
                confidence = ConfidenceLevel.HIGH,
                spokenFeedback = "Šaljem SOS poruku i vašu trenutnu lokaciju."
            )
        }

        // 3. Provera lokacije (WHERE_AM_I)
        if (locationKeywords.any { lowerInput.contains(it) }) {
            return ParsedCommandResult(
                action = CommandAction.WHERE_AM_I,
                confidence = ConfidenceLevel.HIGH,
                spokenFeedback = "Proveravam vašu trenutnu lokaciju na mapi."
            )
        }

        // 4. Provera vremena (WHAT_TIME)
        if (timeKeywords.any { lowerInput.contains(it) }) {
            return ParsedCommandResult(
                action = CommandAction.WHAT_TIME,
                confidence = ConfidenceLevel.HIGH,
                spokenFeedback = "Proveravam tačno vreme."
            )
        }

        // 5. Odgovor na poziv (ANSWER_CALL)
        if (answerKeywords.any { lowerInput.contains(it) }) {
            return ParsedCommandResult(
                action = CommandAction.ANSWER_CALL,
                confidence = ConfidenceLevel.HIGH,
                spokenFeedback = "Javljam se na poziv."
            )
        }

        // 6. Utišavanje zvona (SILENCE_CALL)
        if (silenceKeywords.any { lowerInput.contains(it) }) {
            return ParsedCommandResult(
                action = CommandAction.SILENCE_CALL,
                confidence = ConfidenceLevel.HIGH,
                spokenFeedback = "Utišavam zvono poziva."
            )
        }

        // 7. Odbijanje poziva (REJECT_CALL)
        if (rejectKeywords.any { lowerInput.contains(it) }) {
            return ParsedCommandResult(
                action = CommandAction.REJECT_CALL,
                confidence = ConfidenceLevel.HIGH,
                spokenFeedback = "Odbijam poziv."
            )
        }

        // 8. Slanje SMS poruke (SEND_SMS)
        val matchedSmsKeyword = smsKeywords.firstOrNull { lowerInput.contains(it) }
        if (matchedSmsKeyword != null) {
            val kwIndex = lowerInput.indexOf(matchedSmsKeyword)
            val afterKeyword = cleanedInput.substring(kwIndex + matchedSmsKeyword.length).trim()

            // Tražimo "da" ili "reci" kao separator izgovorenog teksta (npr. "Tijani da stižem za 10 minuta")
            val daSplitter = Regex("(?i)\\bda\\b|\\breci\\b|\\btekst\\b")
            val parts = daSplitter.split(afterKeyword, 2)

            val spokenContactCandidate = parts.firstOrNull()?.trim() ?: ""
            val messageTextRaw = if (parts.size > 1) parts[1].trim() else null

            // Mečujemo ime kontakta
            val match = ContactMatcher.findBestMatch(spokenContactCandidate, contacts, customVipMap)
            val resolvedName = match.contact?.name ?: match.matchedName ?: spokenContactCandidate

            val formattedMessage = messageTextRaw?.let { formatMessageText(it) }

            if (formattedMessage.isNullOrBlank()) {
                return ParsedCommandResult(
                    action = CommandAction.UNKNOWN,
                    contactName = resolvedName,
                    confidence = ConfidenceLevel.LOW,
                    spokenFeedback = "Poruka nema sadržaj. Koju poruku želite da pošaljete?"
                )
            }

            return ParsedCommandResult(
                action = CommandAction.SEND_SMS,
                contactName = resolvedName,
                messageText = formattedMessage,
                confidence = match.confidence,
                spokenFeedback = "Šaljem poruku za $resolvedName: $formattedMessage"
            )
        }

        // 9. Telefonski poziv (CALL)
        val matchedCallKeyword = callKeywords.firstOrNull { lowerInput.contains(it) }
        if (matchedCallKeyword != null) {
            val kwIndex = lowerInput.indexOf(matchedCallKeyword)
            val spokenContactCandidate = cleanedInput.substring(kwIndex + matchedCallKeyword.length).trim()

            val match = ContactMatcher.findBestMatch(spokenContactCandidate, contacts, customVipMap)
            val resolvedName = match.contact?.name ?: match.matchedName ?: spokenContactCandidate

            return ParsedCommandResult(
                action = CommandAction.CALL,
                contactName = resolvedName,
                confidence = match.confidence,
                spokenFeedback = "Pozivam $resolvedName."
            )
        }

        // 10. Brzi odgovori (QUICK_ETA)
        if (lowerInput.contains("stizem") || lowerInput.contains("vozim")) {
            return ParsedCommandResult(
                action = CommandAction.QUICK_ETA,
                messageText = "Stižem za desetak minuta, u gužvi sam u vožnji.",
                confidence = ConfidenceLevel.HIGH,
                spokenFeedback = "Spremna je brza poruka: Stižem za desetak minuta, u gužvi sam u vožnji."
            )
        }

        return ParsedCommandResult(
            action = CommandAction.UNKNOWN,
            confidence = ConfidenceLevel.LOW,
            spokenFeedback = "Nisam prepoznao nalog. Ponovite komandu."
        )
    }

    private fun formatMessageText(rawText: String): String {
        var text = rawText.trim()
        if (text.isEmpty()) return ""
        text = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        if (!text.endsWith(".") && !text.endsWith("!") && !text.endsWith("?")) {
            text += "."
        }
        return text
    }
}
