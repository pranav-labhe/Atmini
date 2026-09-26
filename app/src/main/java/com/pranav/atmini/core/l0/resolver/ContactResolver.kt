package com.pranav.atmini.core.l0.resolver

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.pranav.atmini.core.l0.model.ResolutionResult

data class ContactCandidate(
    val name: String,
    val number: String,
    val score: Double
)

class ContactResolver(private val context: Context) {

    fun resolveContact(query: String): ResolutionResult<ContactCandidate> {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return ResolutionResult.NoMatch
        }

        val resolver = context.contentResolver
        val cursor = try {
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                null
            )
        } catch (_: Exception) {
            null
        } ?: return ResolutionResult.NoMatch

        val candidates = mutableListOf<ContactCandidate>()

        cursor.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (nameIdx >= 0 && numIdx >= 0) {
                while (it.moveToNext()) {
                    val name = it.getString(nameIdx) ?: continue
                    val number = it.getString(numIdx) ?: continue
                    val score = if (name.equals(query, ignoreCase = true)) 1.0
                    else if (name.contains(query, ignoreCase = true)) 0.85 else 0.0
                    if (score > 0.70) {
                        candidates.add(ContactCandidate(name, number, score))
                    }
                }
            }
        }

        val sorted = candidates.sortedByDescending { it.score }
        if (sorted.isEmpty()) return ResolutionResult.NoMatch
        if (sorted.size == 1 || sorted[0].score == 1.0) return ResolutionResult.SingleMatch(sorted[0])

        return if ((sorted[0].score - sorted[1].score) < 0.15) {
            ResolutionResult.Ambiguous(sorted.take(3), query)
        } else {
            ResolutionResult.SingleMatch(sorted[0])
        }
    }
}
