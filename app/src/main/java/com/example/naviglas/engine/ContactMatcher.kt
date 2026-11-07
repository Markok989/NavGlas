package com.example.naviglas.engine

import android.content.Context
import android.provider.ContactsContract
import java.util.Locale

data class Contact(
    val name: String,
    val phoneNumber: String,
    val nickname: String? = null
)

enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW
}

data class ContactMatchResult(
    val contact: Contact?,
    val matchedName: String?,
    val confidence: ConfidenceLevel
)

object ContactMatcher {

    // Default VIP nadimci za brže mečovanje u vožnji
    private val defaultVipNicknames = mapOf(
        "zena" to "Žena",
        "supruga" to "Žena",
        "mama" to "Mama",
        "majka" to "Mama",
        "tata" to "Tata",
        "otac" to "Tata",
        "kum" to "Kum",
        "kuma" to "Kuma",
        "brat" to "Brat",
        "sestra" to "Sestra",
        "sef" to "Šef"
    )

    /**
     * Pronalazi najbolji meč za izgovoreno ime u listi kontakata.
     */
    fun findBestMatch(
        spokenName: String,
        contacts: List<Contact>,
        customVipMap: Map<String, String> = emptyMap()
    ): ContactMatchResult {
        val trimmed = spokenName.trim()
        if (trimmed.isEmpty() || contacts.isEmpty()) {
            return ContactMatchResult(null, null, ConfidenceLevel.LOW)
        }

        val lemmas = SerbianCaseLemmatizer.getCandidateLemmas(trimmed)

        // 1. Provera VIP nadimaka (npr. "Žena", "Mama", "Kum")
        val normalizedSpoken = SerbianTextNormalizer.stripDiacritics(trimmed).lowercase(Locale.ROOT)
        val vipTarget = customVipMap[normalizedSpoken] ?: defaultVipNicknames[normalizedSpoken]
        if (vipTarget != null) {
            val vipMatch = contacts.firstOrNull {
                SerbianTextNormalizer.stripDiacritics(it.name).lowercase(Locale.ROOT)
                    .contains(SerbianTextNormalizer.stripDiacritics(vipTarget).lowercase(Locale.ROOT))
            }
            if (vipMatch != null) {
                return ContactMatchResult(vipMatch, vipMatch.name, ConfidenceLevel.HIGH)
            }
        }

        // 2. Tačno i fleksibilno mečovanje nad lematizovanim imenima
        for (lemma in lemmas) {
            val normLemma = SerbianTextNormalizer.stripDiacritics(lemma).lowercase(Locale.ROOT)

            // Potpuni meč
            val exact = contacts.firstOrNull {
                SerbianTextNormalizer.stripDiacritics(it.name).lowercase(Locale.ROOT) == normLemma
            }
            if (exact != null) {
                return ContactMatchResult(exact, exact.name, ConfidenceLevel.HIGH)
            }

            // Prva reč meč (npr. "Marko" u "Marko Petrović")
            val firstWordMatch = contacts.firstOrNull {
                val firstName = SerbianTextNormalizer.stripDiacritics(it.name)
                    .split(" ").firstOrNull()?.lowercase(Locale.ROOT) ?: ""
                firstName == normLemma
            }
            if (firstWordMatch != null) {
                return ContactMatchResult(firstWordMatch, firstWordMatch.name, ConfidenceLevel.HIGH)
            }
        }

        // 3. Delimično mečovanje (Sadrži reč)
        for (lemma in lemmas) {
            val normLemma = SerbianTextNormalizer.stripDiacritics(lemma).lowercase(Locale.ROOT)
            val partial = contacts.firstOrNull {
                SerbianTextNormalizer.stripDiacritics(it.name).lowercase(Locale.ROOT).contains(normLemma)
            }
            if (partial != null) {
                return ContactMatchResult(partial, partial.name, ConfidenceLevel.MEDIUM)
            }
        }

        // 4. Levenshtein fuzzy distance mečovanje
        var minDistance = Int.MAX_VALUE
        var bestFuzzyMatch: Contact? = null

        for (lemma in lemmas) {
            val normLemma = SerbianTextNormalizer.stripDiacritics(lemma).lowercase(Locale.ROOT)
            for (contact in contacts) {
                val normContact = SerbianTextNormalizer.stripDiacritics(contact.name).lowercase(Locale.ROOT)
                val dist = levenshteinDistance(normLemma, normContact)
                if (dist < minDistance && dist <= 2) {
                    minDistance = dist
                    bestFuzzyMatch = contact
                }
            }
        }

        if (bestFuzzyMatch != null) {
            return ContactMatchResult(bestFuzzyMatch, bestFuzzyMatch.name, ConfidenceLevel.MEDIUM)
        }

        return ContactMatchResult(null, trimmed, ConfidenceLevel.LOW)
    }

    /**
     * Očitava kontakte iz telefona.
     */
    fun loadContactsFromPhone(context: Context): List<Contact> {
        val list = mutableListOf<Contact>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else ""
                    val number = if (numberIndex >= 0) cursor.getString(numberIndex) else ""
                    if (name.isNotBlank() && number.isNotBlank()) {
                        list.add(Contact(name, number))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return list.distinctBy { it.name }
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
