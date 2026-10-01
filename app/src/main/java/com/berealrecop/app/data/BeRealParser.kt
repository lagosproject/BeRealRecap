package com.berealrecop.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

object BeRealParser {

    // Matches: bereal-1_septembre_2026_12_21_28.jpeg or bereal_1_septembre_2026...
    // Also matches: bereal-2026-09-01-12-21-28.jpeg
    private val PATTERN_NAMED_DATE = Pattern.compile(
        """bereal[-_](\d{1,2})_([a-zA-ZáéíóúÁÉÍÓÚñÑ]+|\d{1,2})_(\d{4})(?:_(\d{1,2})_(\d{1,2})_(\d{1,2}))?""",
        Pattern.CASE_INSENSITIVE
    )

    private val PATTERN_ISO_DATE = Pattern.compile(
        """bereal[-_](\d{4})[-_](\d{1,2})[-_](\d{1,2})(?:[-_](\d{1,2})[-_](\d{1,2})[-_](\d{1,2}))?""",
        Pattern.CASE_INSENSITIVE
    )

    fun parseItem(
        context: Context,
        uri: Uri,
        filePath: String? = null,
        givenFileName: String? = null
    ): BeRealItem {
        val fileName = givenFileName ?: resolveFileName(context, uri) ?: filePath?.let { File(it).name } ?: "unknown.jpg"

        // 1. Try PATTERN_NAMED_DATE (e.g. bereal-1_septembre_2026_12_21_28.jpeg)
        val matcher1 = PATTERN_NAMED_DATE.matcher(fileName)
        if (matcher1.find()) {
            val day = matcher1.group(1)?.toIntOrNull() ?: 1
            val monthStr = matcher1.group(2) ?: ""
            val year = matcher1.group(3)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
            val hour = matcher1.group(4)?.toIntOrNull() ?: 0
            val min = matcher1.group(5)?.toIntOrNull() ?: 0
            val sec = matcher1.group(6)?.toIntOrNull() ?: 0

            return BeRealItem(
                id = uri.toString(),
                uri = uri,
                filePath = filePath,
                fileName = fileName,
                dayNumber = day,
                monthName = monthStr,
                year = year,
                hour = hour,
                minute = min,
                second = sec
            )
        }

        // 2. Try PATTERN_ISO_DATE (e.g. bereal-2026-09-01...)
        val matcher2 = PATTERN_ISO_DATE.matcher(fileName)
        if (matcher2.find()) {
            val year = matcher2.group(1)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
            val month = matcher2.group(2)?.toIntOrNull() ?: 1
            val day = matcher2.group(3)?.toIntOrNull() ?: 1
            val hour = matcher2.group(4)?.toIntOrNull() ?: 0
            val min = matcher2.group(5)?.toIntOrNull() ?: 0
            val sec = matcher2.group(6)?.toIntOrNull() ?: 0

            return BeRealItem(
                id = uri.toString(),
                uri = uri,
                filePath = filePath,
                fileName = fileName,
                dayNumber = day,
                monthName = month.toString(),
                year = year,
                hour = hour,
                minute = min,
                second = sec
            )
        }

        // 3. Fallback: EXIF date or Last Modified Date
        val dateFallback = tryReadExifDate(context, uri) ?: filePath?.let { File(it).lastModified() } ?: System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = dateFallback }
        return BeRealItem(
            id = uri.toString(),
            uri = uri,
            filePath = filePath,
            fileName = fileName,
            dayNumber = cal.get(Calendar.DAY_OF_MONTH),
            monthName = (cal.get(Calendar.MONTH) + 1).toString(),
            year = cal.get(Calendar.YEAR),
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            second = cal.get(Calendar.SECOND)
        )
    }

    fun monthToNumber(monthStr: String): Int {
        val num = monthStr.toIntOrNull()
        if (num != null && num in 1..12) return num

        val lower = monthStr.lowercase(Locale.ROOT)
            .replace("é", "e")
            .replace("û", "u")
            .replace("ô", "o")

        return when {
            lower.startsWith("jan") || lower.startsWith("ene") || lower.startsWith("gen") -> 1
            lower.startsWith("fev") || lower.startsWith("feb") -> 2
            lower.startsWith("mar") -> 3
            lower.startsWith("avr") || lower.startsWith("abr") || lower.startsWith("apr") -> 4
            lower.startsWith("mai") || lower.startsWith("may") -> 5
            lower.startsWith("jui") || lower.startsWith("jun") -> 6
            lower.startsWith("jul") || lower.startsWith("juil") -> 7
            lower.startsWith("aou") || lower.startsWith("ago") || lower.startsWith("aug") -> 8
            lower.startsWith("sep") || lower.startsWith("set") -> 9
            lower.startsWith("oct") || lower.startsWith("ott") -> 10
            lower.startsWith("nov") -> 11
            lower.startsWith("dec") || lower.startsWith("dic") -> 12
            else -> 1
        }
    }

    private fun resolveFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) return cursor.getString(idx)
                    }
                }
            } catch (_: Exception) {}
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun tryReadExifDate(context: Context, uri: Uri): Long? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            inputStream.use { stream ->
                val exif = ExifInterface(stream)
                val dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                if (dateStr != null) {
                    val sdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                    sdf.parse(dateStr)?.time
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
