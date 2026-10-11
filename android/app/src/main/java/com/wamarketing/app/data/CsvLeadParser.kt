package com.wamarketing.app.data

import android.content.Context
import android.net.Uri
import com.wamarketing.app.model.ContactItem
import com.wamarketing.app.model.TagItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object CsvLeadParser {

    /**
     * Reads and parses a CSV or text file directly from an Android content Uri.
     * Binary files (.xlsx, .xls, .pdf) are automatically skipped from plain-text parsing.
     */
    fun parseFromUri(context: Context, uri: Uri): List<ContactItem> {
        val content = try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // First read 8 bytes to verify it's not binary
                val head = ByteArray(8)
                val read = inputStream.read(head)
                if (read >= 4 && isBinaryHeader(head)) {
                    return emptyList()
                }
                // Reopen or read remainder as text
                null
            }
        } catch (e: Exception) {
            null
        }

        // Re-read full stream if not binary
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).readText()
            }
        } catch (_: Exception) {
            null
        }

        return if (!text.isNullOrBlank() && !isBinaryContent(text)) {
            parseFromText(text)
        } else {
            emptyList()
        }
    }

    /**
     * Reads files (CSV, Excel .xlsx, .xls, PDF .pdf) and parses them via the
     * official WA Marketing backend preview endpoint (/api/v1/contacts/preview)
     * with instant local high-precision fallback.
     */
    suspend fun parseFromUriWithBackend(
        context: Context,
        uri: Uri,
        fileName: String,
        backendUrl: String = AppConfig.DEFAULT_BACKEND_URL
    ): List<ContactItem> = withContext(Dispatchers.IO) {
        val fileBytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } ?: return@withContext emptyList()

        // 1. First, attempt backend high-fidelity parser (handles XLSX, XLS, PDF, and server-side CSV with pandas)
        try {
            val boundary = "==WAMarketingBoundary==" + UUID.randomUUID().toString()
            val url = URL("$backendUrl/api/v1/contacts/preview")
            val conn = url.openConnection() as HttpURLConnection
            AppConfig.applyStandardHeaders(conn)
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.connectTimeout = 6000
            conn.readTimeout = 8000

            DataOutputStream(conn.outputStream).use { dos ->
                dos.writeBytes("--$boundary\r\n")
                dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
                dos.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
                dos.write(fileBytes)
                dos.writeBytes("\r\n")
                dos.writeBytes("--$boundary--\r\n")
                dos.flush()
            }

            if (conn.responseCode in 200..299) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(resp)
                val recordsArray = json.optJSONArray("records")
                if (recordsArray != null && recordsArray.length() > 0) {
                    val parsed = mutableListOf<ContactItem>()
                    for (i in 0 until recordsArray.length()) {
                        val r = recordsArray.getJSONObject(i)
                        val name = r.optString("name", "Lead ${i + 1}").trim()
                        val parts = name.split(" ")
                        val firstName = parts.firstOrNull() ?: "Lead"
                        val lastName = if (parts.size > 1) parts.drop(1).joinToString(" ") else ""
                        val phone = r.optString("phone", "").trim()
                        val email = r.optString("email", "").ifBlank { null }
                        val company = r.optString("company", "Enterprise")
                        val city = r.optString("city", "Global")
                        val country = r.optString("country", "Global")
                        val status = r.optString("status", "VALID")

                        if (phone.isNotBlank() && status != "INVALID") {
                            parsed.add(
                                ContactItem(
                                    id = (3000..9999).random() + i,
                                    firstName = firstName,
                                    lastName = lastName,
                                    phoneNumber = phone,
                                    whatsappNumber = phone,
                                    email = email,
                                    company = company,
                                    city = city,
                                    country = country,
                                    notes = "Imported from $fileName ($status)",
                                    leadStatus = "Imported Lead",
                                    customerStatus = "Lead",
                                    optInStatus = "opted_in",
                                    tags = listOf(TagItem(30 + i, if (fileName.endsWith(".pdf", true)) "PDF Import" else if (fileName.endsWith(".csv", true)) "CSV Import" else "Excel Import", "#3B82F6"))
                                )
                            )
                        }
                    }
                    if (parsed.isNotEmpty()) {
                        return@withContext parsed
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. High-precision local parsing for CSV, TXT, or text-extractable documents
        val text = try {
            // Try UTF-8 with BOM stripped, or Latin1/Windows-1252
            val raw = String(fileBytes, Charsets.UTF_8).removePrefix("\uFEFF")
            if (isBinaryContent(raw)) {
                ""
            } else {
                raw
            }
        } catch (_: Exception) {
            try {
                String(fileBytes, Charsets.ISO_8859_1)
            } catch (_: Exception) {
                ""
            }
        }

        if (text.isNotBlank()) {
            parseFromText(text)
        } else {
            emptyList()
        }
    }

    private fun isBinaryHeader(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        // Zip magic: PK\x03\x04
        if (bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) return true
        // Legacy XLS magic: 0xD0 0xCF 0x11 0xE0
        if (bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() && bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()) return true
        // PDF magic: %PDF
        if (bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte()) return true
        return false
    }

    private fun isBinaryContent(rawText: String): Boolean {
        if (rawText.startsWith("PK\u0003\u0004") || rawText.startsWith("PK") || rawText.startsWith("%PDF")) return true
        var nonPrintable = 0
        val checkLen = minOf(rawText.length, 256)
        for (i in 0 until checkLen) {
            val c = rawText[i]
            if (c.code < 32 && c != '\n' && c != '\r' && c != '\t') {
                nonPrintable++
            }
        }
        return nonPrintable > 8
    }

    /**
     * Highly resilient parser for CSV or delimited text content into ContactItem list.
     * Automatically handles:
     * - UTF-8 BOM (\uFEFF)
     * - Comma, semicolon, tab, pipe delimiters
     * - Single-column phone number lists (e.g. pasted or raw phone sheets)
     * - Headerless CSV files
     * - Arbitrary column orders (detects phone column by looking at cell contents)
     * - Quoted values with embedded delimiters
     */
    fun parseFromText(rawText: String): List<ContactItem> {
        val sanitized = rawText.removePrefix("\uFEFF").replace("\r\n", "\n").replace("\r", "\n")
        val lines = sanitized.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.isEmpty()) return emptyList()

        val results = mutableListOf<ContactItem>()

        // 1. Auto-detect delimiter from the first non-empty lines
        val sample = lines.take(5).joinToString("\n")
        val delimiter = when {
            sample.count { it == '\t' } >= 2 -> "\t"
            sample.count { it == ';' } >= 2 -> ";"
            sample.count { it == '|' } >= 2 -> "|"
            else -> ","
        }

        val firstLine = lines.first()
        val firstTokens = splitLine(firstLine, delimiter).map { it.lowercase().trim() }

        // Check if row 0 has headers
        var headerRowIndex = -1
        var nameColIdx = -1
        var phoneColIdx = -1
        var companyColIdx = -1
        var cityColIdx = -1
        var emailColIdx = -1

        val hasHeaders = firstTokens.any { token ->
            token.contains("name") || token.contains("phone") || token.contains("mobile") ||
                    token.contains("contact") || token.contains("number") || token.contains("company") ||
                    token.contains("cell") || token.contains("email") || token.contains("city")
        }

        if (hasHeaders) {
            headerRowIndex = 0
            firstTokens.forEachIndexed { idx, token ->
                when {
                    token.contains("phone") || token.contains("mobile") || token.contains("whatsapp") ||
                            token.contains("cell") || token.contains("number") || token.contains("contact_no") -> if (phoneColIdx == -1) phoneColIdx = idx
                    token.contains("name") || token.contains("full_name") || token.contains("lead") ||
                            token.contains("client") || token.contains("person") -> if (nameColIdx == -1) nameColIdx = idx
                    token.contains("email") -> if (emailColIdx == -1) emailColIdx = idx
                    token.contains("company") || token.contains("organization") || token.contains("business") -> if (companyColIdx == -1) companyColIdx = idx
                    token.contains("city") || token.contains("location") || token.contains("country") -> if (cityColIdx == -1) cityColIdx = idx
                }
            }
        }

        val dataLines = if (headerRowIndex == 0) lines.drop(1) else lines

        dataLines.forEachIndexed { index, line ->
            val tokens = splitLine(line, delimiter)
            if (tokens.isNotEmpty()) {
                // Determine phone column dynamically if not fixed
                var rawPhone = ""
                var rawName = ""
                var rawCompany = "Enterprise"
                var rawCity = "Global"
                var rawEmail: String? = null

                if (tokens.size == 1) {
                    // Single-column: entire token is phone number
                    rawPhone = tokens[0]
                    rawName = "Lead ${index + 1}"
                } else {
                    // Multi-column
                    // 1. Locate phone token
                    val targetPhoneIdx = if (phoneColIdx != -1 && phoneColIdx < tokens.size && isPhoneLike(tokens[phoneColIdx])) {
                        phoneColIdx
                    } else {
                        tokens.indices.firstOrNull { isPhoneLike(tokens[it]) } ?: -1
                    }

                    if (targetPhoneIdx != -1) {
                        rawPhone = tokens[targetPhoneIdx]
                    }

                    // 2. Locate name token
                    val targetNameIdx = if (nameColIdx != -1 && nameColIdx < tokens.size && nameColIdx != targetPhoneIdx) {
                        nameColIdx
                    } else {
                        tokens.indices.firstOrNull { it != targetPhoneIdx && !isPhoneLike(tokens[it]) && !tokens[it].contains("@") } ?: -1
                    }

                    if (targetNameIdx != -1) {
                        rawName = tokens[targetNameIdx]
                    }

                    if (companyColIdx != -1 && companyColIdx < tokens.size && companyColIdx != targetPhoneIdx && companyColIdx != targetNameIdx) {
                        rawCompany = tokens[companyColIdx].ifBlank { "Enterprise" }
                    }

                    if (cityColIdx != -1 && cityColIdx < tokens.size && cityColIdx != targetPhoneIdx && cityColIdx != targetNameIdx) {
                        rawCity = tokens[cityColIdx].ifBlank { "Global" }
                    }

                    if (emailColIdx != -1 && emailColIdx < tokens.size) {
                        rawEmail = tokens[emailColIdx].ifBlank { null }
                    }
                }

                val normalizedPhone = cleanPhoneNumber(rawPhone)
                if (normalizedPhone.length >= 7) {
                    val cleanName = if (rawName.isBlank() || isPhoneLike(rawName)) "Lead ${index + 1}" else rawName
                    val nameParts = cleanName.split(" ")
                    val firstName = nameParts.firstOrNull() ?: "Lead"
                    val lastName = if (nameParts.size > 1) nameParts.drop(1).joinToString(" ") else ""

                    results.add(
                        ContactItem(
                            id = (3000..9999).random() + index,
                            firstName = firstName,
                            lastName = lastName,
                            phoneNumber = normalizedPhone,
                            whatsappNumber = normalizedPhone,
                            email = rawEmail ?: "${firstName.lowercase().replace(" ", "")}@${rawCompany.lowercase().replace(" ", "")}.com",
                            company = rawCompany.ifBlank { "Enterprise" },
                            city = rawCity.ifBlank { "Global" },
                            country = "Global",
                            notes = "Imported from device CSV file",
                            leadStatus = "Imported Lead",
                            customerStatus = "Lead",
                            optInStatus = "opted_in",
                            tags = listOf(TagItem(20 + index, "CSV Import", "#10B981"))
                        )
                    )
                }
            }
        }

        return results
    }

    private fun isPhoneLike(str: String): Boolean {
        val clean = str.replace(" ", "").replace("-", "").replace("(", "").replace(")", "").replace(".", "")
        val digits = clean.filter { it.isDigit() }
        return digits.length in 7..15
    }

    private fun cleanPhoneNumber(phone: String): String {
        val clean = phone.replace(" ", "").replace("-", "").replace("(", "").replace(")", "").replace(".", "").trim()
        val digits = clean.filter { it.isDigit() }
        if (digits.length < 7) return ""

        return if (clean.startsWith("+")) {
            clean
        } else if (clean.startsWith("00")) {
            "+" + clean.drop(2)
        } else if (clean.startsWith("03") && clean.length == 11) {
            // Pakistan local format 03XXXXXXXXX -> +923XXXXXXXXX
            "+92" + clean.drop(1)
        } else if (clean.length in 10..15 && !clean.startsWith("0")) {
            // Full international format with country code already present
            "+$clean"
        } else {
            if (clean.isNotBlank()) "+$clean" else ""
        }
    }

    private fun splitLine(line: String, delimiter: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val current = StringBuilder()

        for (ch in line) {
            if (ch == '\"') {
                inQuotes = !inQuotes
            } else if (ch.toString() == delimiter && !inQuotes) {
                result.add(current.toString().trim().removeSurrounding("\""))
                current.clear()
            } else {
                current.append(ch)
            }
        }
        result.add(current.toString().trim().removeSurrounding("\""))
        return result
    }
}
