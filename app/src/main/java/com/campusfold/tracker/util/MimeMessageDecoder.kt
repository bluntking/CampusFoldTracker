package com.campusfold.tracker.util

import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.internet.MimePart
import javax.mail.internet.MimeUtility

/**
 * Robust MIME Message Decoder for parsing email alert contents.
 * Decodes quoted-printable, base64, multipart (text/plain and text/html), and HTML entities.
 */
object MimeMessageDecoder {

    fun extractCleanBodyText(part: Part): String {
        return try {
            when {
                part.isMimeType("text/plain") -> {
                    readStream(part)
                }
                part.isMimeType("text/html") -> {
                    htmlToPlainText(readStream(part))
                }
                part.isMimeType("multipart/*") -> {
                    val mp = try {
                        part.content as? Multipart
                    } catch (_: Exception) {
                        null
                    }
                    if (mp != null) {
                        val plainSb = StringBuilder()
                        val htmlSb = StringBuilder()
                        for (i in 0 until mp.count) {
                            val bodyPart = mp.getBodyPart(i)
                            if (bodyPart.isMimeType("text/plain")) {
                                plainSb.append(readStream(bodyPart)).append("\n")
                            } else if (bodyPart.isMimeType("text/html")) {
                                htmlSb.append(readStream(bodyPart)).append("\n")
                            } else if (bodyPart.isMimeType("multipart/*")) {
                                val nested = extractCleanBodyText(bodyPart)
                                if (nested.isNotBlank()) plainSb.append(nested).append("\n")
                            }
                        }
                        if (plainSb.isNotBlank()) plainSb.toString().trim()
                        else if (htmlSb.isNotBlank()) htmlToPlainText(htmlSb.toString())
                        else ""
                    } else {
                        readStream(part)
                    }
                }
                else -> {
                    readStream(part)
                }
            }
        } catch (e: Exception) {
            try { readStream(part) } catch (_: Exception) { "" }
        }
    }

    private fun readStream(part: Part): String {
        val encoding: String? = (part as? MimePart)?.encoding
            ?: part.getHeader("Content-Transfer-Encoding")?.firstOrNull()
        val inputStream = part.inputStream
        val decodedStream = if (!encoding.isNullOrBlank()) {
            try {
                MimeUtility.decode(inputStream, encoding)
            } catch (_: Exception) {
                inputStream
            }
        } else {
            inputStream
        }
        val charset = parseCharset(part.contentType)
        return decodedStream.bufferedReader(charset).use { it.readText() }
    }

    fun parseCharset(contentType: String?): Charset {
        if (contentType.isNullOrBlank()) return StandardCharsets.UTF_8
        val match = Regex("(?i)charset=[\"']?([^\"';\\s]+)").find(contentType)
        return if (match != null) {
            try {
                Charset.forName(match.groupValues[1])
            } catch (_: Exception) {
                StandardCharsets.UTF_8
            }
        } else {
            StandardCharsets.UTF_8
        }
    }

    fun decodeQuotedPrintable(input: String): String {
        return try {
            val bais = java.io.ByteArrayInputStream(input.toByteArray(StandardCharsets.ISO_8859_1))
            val qpStream = MimeUtility.decode(bais, "quoted-printable")
            qpStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            input
        }
    }

    fun decodeBase64(input: String): String {
        return try {
            val bais = java.io.ByteArrayInputStream(input.toByteArray(StandardCharsets.ISO_8859_1))
            val b64Stream = MimeUtility.decode(bais, "base64")
            b64Stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            input
        }
    }

    fun htmlToPlainText(html: String): String {
        var text = html
        text = text.replace(Regex("(?is)<style.*?>.*?</style>"), "")
        text = text.replace(Regex("(?is)<script.*?>.*?</script>"), "")
        text = text.replace(Regex("(?i)<br\\s*/?>"), "\n")
        text = text.replace(Regex("(?i)</p>"), "\n\n")
        text = text.replace(Regex("(?i)</div>"), "\n")
        text = text.replace(Regex("(?i)</tr>"), "\n")
        text = text.replace(Regex("(?i)</td>"), " ")
        text = text.replace(Regex("<[^>]+>"), "")
        // Unescape standard HTML entities
        text = text.replace("&nbsp;", " ")
        text = text.replace("&#160;", " ")
        text = text.replace("&amp;", "&")
        text = text.replace("&lt;", "<")
        text = text.replace("&gt;", ">")
        text = text.replace("&quot;", "\"")
        text = text.replace("&#39;", "'")
        text = text.replace("&apos;", "'")
        text = text.replace("&#8377;", "₹")
        text = text.replace("&inr;", "₹")
        return text.lines().map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n")
    }
}
