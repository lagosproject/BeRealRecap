package com.berealrecop.app.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

object BeRealParser {

    data class DateInfo(
        val day: Int,
        val monthName: String,
        val year: Int,
        val hour: Int = 0,
        val minute: Int = 0,
        val second: Int = 0
    )

    // Matches day-first with multilingual connectors:
    // Spanish: bereal-10_de_octubre_de_2026_14_45_59.jpeg, bereal-1_de_octubre_de_2026...
    // French: bereal-1_septembre_2026_12_21_28.jpeg, bereal-1_d'octobre_2026...
    // English / Standard: bereal-10_october_2026_14_45_59.jpeg, bereal-10-10-2026...
    private val PATTERN_DAY_FIRST = Pattern.compile(
        """(?:bereal[-_])?(\d{1,2})(?:\s*(?:de|d'|di|do|da)\s*|[-_\s])+([a-zA-ZáéíóúÁÉÍÓÚñÑäöüÄÖÜûêîôâÛÊÎÔÂçÇ]+|\d{1,2})(?:\s*(?:de|d'|di|do|da)\s*|[-_\s])+(\d{4})(?:[-_\s](\d{1,2})[-_\s](\d{1,2})[-_\s](\d{1,2}))?""",
        Pattern.CASE_INSENSITIVE
    )

    // Matches month-first format (e.g. bereal-october-10-2026_14_45_59.jpeg)
    private val PATTERN_MONTH_FIRST = Pattern.compile(
        """(?:bereal[-_])?([a-zA-ZáéíóúÁÉÍÓÚñÑäöüÄÖÜûêîôâÛÊÎÔÂçÇ]+)(?:\s*(?:de|d'|di|do|da)\s*|[-_\s])+(\d{1,2})(?:\s*(?:de|d'|di|do|da)\s*|[-_\s])+(\d{4})(?:[-_\s](\d{1,2})[-_\s](\d{1,2})[-_\s](\d{1,2}))?""",
        Pattern.CASE_INSENSITIVE
    )

    // Matches ISO date: bereal-2026-09-01-12-21-28.jpeg, 2026-10-31_13_05_49...
    private val PATTERN_ISO_DATE = Pattern.compile(
        """(?:bereal[-_])?(\d{4})[-_](\d{1,2})[-_](\d{1,2})(?:[-_](\d{1,2})[-_](\d{1,2})[-_](\d{1,2}))?""",
        Pattern.CASE_INSENSITIVE
    )

    fun parseFromFileName(fileName: String): DateInfo? {
        // 1. Try PATTERN_DAY_FIRST (e.g. bereal-10_de_octubre_de_2026... or bereal-1_septembre_2026...)
        val matcher1 = PATTERN_DAY_FIRST.matcher(fileName)
        if (matcher1.find()) {
            val day = matcher1.group(1)?.toIntOrNull() ?: 1
            val monthStr = matcher1.group(2) ?: ""
            val year = matcher1.group(3)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
            val hour = matcher1.group(4)?.toIntOrNull() ?: 0
            val min = matcher1.group(5)?.toIntOrNull() ?: 0
            val sec = matcher1.group(6)?.toIntOrNull() ?: 0

            return DateInfo(
                day = day,
                monthName = monthStr,
                year = year,
                hour = hour,
                minute = min,
                second = sec
            )
        }

        // 2. Try PATTERN_MONTH_FIRST (e.g. bereal-october-10-2026...)
        val matcherMonth = PATTERN_MONTH_FIRST.matcher(fileName)
        if (matcherMonth.find()) {
            val monthStr = matcherMonth.group(1) ?: ""
            val day = matcherMonth.group(2)?.toIntOrNull() ?: 1
            val year = matcherMonth.group(3)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
            val hour = matcherMonth.group(4)?.toIntOrNull() ?: 0
            val min = matcherMonth.group(5)?.toIntOrNull() ?: 0
            val sec = matcherMonth.group(6)?.toIntOrNull() ?: 0

            return DateInfo(
                day = day,
                monthName = monthStr,
                year = year,
                hour = hour,
                minute = min,
                second = sec
            )
        }

        // 3. Try PATTERN_ISO_DATE (e.g. bereal-2026-09-01...)
        val matcher2 = PATTERN_ISO_DATE.matcher(fileName)
        if (matcher2.find()) {
            val year = matcher2.group(1)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
            val month = matcher2.group(2)?.toIntOrNull() ?: 1
            val day = matcher2.group(3)?.toIntOrNull() ?: 1
            val hour = matcher2.group(4)?.toIntOrNull() ?: 0
            val min = matcher2.group(5)?.toIntOrNull() ?: 0
            val sec = matcher2.group(6)?.toIntOrNull() ?: 0

            return DateInfo(
                day = day,
                monthName = month.toString(),
                year = year,
                hour = hour,
                minute = min,
                second = sec
            )
        }

        return null
    }

    fun parseItem(
        context: Context,
        uri: Uri,
        filePath: String? = null,
        givenFileName: String? = null
    ): BeRealItem {
        val fileName = givenFileName ?: resolveFileName(context, uri) ?: filePath?.let { File(it).name } ?: "unknown.jpg"

        val parsed = parseFromFileName(fileName)
        if (parsed != null) {
            return BeRealItem(
                id = uri.toString(),
                uri = uri,
                filePath = filePath,
                fileName = fileName,
                dayNumber = parsed.day,
                monthName = parsed.monthName,
                year = parsed.year,
                hour = parsed.hour,
                minute = parsed.minute,
                second = parsed.second
            )
        }

        // Fallbacks: 1) EXIF date, 2) MediaStore date, 3) File modification date, 4) Current system time
        val dateFallback = tryReadExifDate(context, uri)
            ?: tryReadMediaStoreDate(context, uri)
            ?: filePath?.let {
                val f = File(it)
                if (f.exists() && f.lastModified() > 0L) f.lastModified() else null
            }
            ?: System.currentTimeMillis()

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
            .replace("è", "e")
            .replace("ê", "e")
            .replace("ë", "e")
            .replace("á", "a")
            .replace("à", "a")
            .replace("ä", "a")
            .replace("â", "a")
            .replace("í", "i")
            .replace("ì", "i")
            .replace("î", "i")
            .replace("ó", "o")
            .replace("ò", "o")
            .replace("ö", "o")
            .replace("ô", "o")
            .replace("ú", "u")
            .replace("ù", "u")
            .replace("ü", "u")
            .replace("û", "u")
            .replace("ç", "c")

        return when {
            // Enero, January, Janvier, Gennaio, Janeiro, Januar
            lower.startsWith("jan") || lower.startsWith("ene") || lower.startsWith("gen") -> 1

            // Febrero, February, Février, Febbraio, Fevereiro, Februar
            lower.startsWith("feb") || lower.startsWith("fev") -> 2

            // Marzo, March, Mars, Março, März
            lower.startsWith("mar") || lower.startsWith("mae") -> 3

            // Abril, April, Avril, Aprile
            lower.startsWith("abr") || lower.startsWith("apr") || lower.startsWith("avr") -> 4

            // Mayo, May, Mai, Maggio, Maio
            lower.startsWith("may") || lower.startsWith("mai") || lower.startsWith("mag") -> 5

            // Junio, June, Juin, Giugno, Junho, Juni
            lower.startsWith("juin") || lower.startsWith("jun") || lower.startsWith("giu") -> 6

            // Julio, July, Juillet, Luglio, Julho, Juli
            lower.startsWith("juil") || lower.startsWith("jul") || lower.startsWith("lug") -> 7

            // Fallback for jui if neither juin nor juil matched:
            lower.startsWith("jui") -> 6

            // Agosto, August, Août, Agosto
            lower.startsWith("ago") || lower.startsWith("aug") || lower.startsWith("aou") -> 8

            // Septiembre, Setiembre, September, Septembre, Settembre, Setembro
            lower.startsWith("sep") || lower.startsWith("set") -> 9

            // Octubre, October, Octobre, Ottobre, Outubro, Oktober
            lower.startsWith("oct") || lower.startsWith("ott") || lower.startsWith("out") || lower.startsWith("okt") -> 10

            // Noviembre, November, Novembre, Novembro
            lower.startsWith("nov") -> 11

            // Diciembre, December, Décembre, Dicembre, Dezembro, Dezember
            lower.startsWith("dic") || lower.startsWith("dec") || lower.startsWith("dez") -> 12

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
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
                if (dateStr != null) {
                    val sdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                    sdf.parse(dateStr)?.time
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun tryReadMediaStoreDate(context: Context, uri: Uri): Long? {
        if (uri.scheme != "content") return null
        return try {
            val projection = arrayOf(
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.DATE_ADDED
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val takenIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                    if (takenIdx != -1) {
                        val dateTaken = cursor.getLong(takenIdx)
                        if (dateTaken > 0L) return dateTaken
                    }
                    val modIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                    if (modIdx != -1) {
                        val dateMod = cursor.getLong(modIdx)
                        if (dateMod > 0L) return dateMod * 1000L
                    }
                    val addedIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                    if (addedIdx != -1) {
                        val dateAdded = cursor.getLong(addedIdx)
                        if (dateAdded > 0L) return dateAdded * 1000L
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}

