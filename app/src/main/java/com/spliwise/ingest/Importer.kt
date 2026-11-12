package com.spliwise.ingest

import android.content.Context
import android.provider.Telephony
import android.telephony.SmsManager
import com.spliwise.data.IngestResult
import com.spliwise.data.Repo
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/** Everything here runs on the phone. The app has no internet permission at all. */
object Importer {

    /** Re-reads the SMS inbox since [since] (catches anything the live receiver missed). */
    suspend fun fromInbox(ctx: Context, repo: Repo, s: BankSettings, since: Long): IngestResult {
        val items = mutableListOf<ParsedTxn>()
        ctx.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?", arrayOf(since.toString()), "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            while (c.moveToNext()) items += parseSms(c.getString(0) ?: "", c.getString(1) ?: "", c.getLong(2), s)
        }
        return repo.ingest(items)
    }

    fun parseSms(sender: String, body: String, ts: Long, s: BankSettings): List<ParsedTxn> {
        val bank = Banks.detect(sender, body, s.allowBodyOnly) ?: return emptyList()
        if (!s.enabled(bank)) return emptyList()
        return if (SmsParser.isMiniStatement(body)) SmsParser.parseMini(sender, body, s.allowBodyOnly)
        else listOfNotNull(SmsParser.parse(sender, body, ts, s.allowBodyOnly))
    }

    /** Sends each enabled bank's statement-request SMS. Returns what was sent / skipped. */
    fun sendRequests(ctx: Context, s: BankSettings): List<String> = s.enabledBanks.map { b ->
        val num = s.requestNumber(b); val txt = s.requestText(b)
        when {
            num.isBlank() || txt.isBlank() -> "${b.name}: no request set"
            '<' in txt -> "${b.name}: fill in ${txt.substringAfter('<').substringBefore('>')} first"
            else -> runCatching {
                ctx.getSystemService(SmsManager::class.java).sendTextMessage(num, null, txt, null, null)
                "${b.name}: requested"
            }.getOrElse { "${b.name}: SMS failed (${it.message})" }
        }
    }

    /** Thrown when a PDF needs a password (or the one given is wrong). */
    class LockedPdf : Exception("This PDF is password protected")

    /**
     * Opens a PDF in memory and returns its text. Nothing is written to disk and the password is never
     * stored or logged: it is used for this one call only.
     */
    fun pdfText(ctx: Context, bytes: ByteArray, password: String = ""): String {
        PDFBoxResourceLoader.init(ctx.applicationContext)
        try {
            return PDDocument.load(bytes, password).use { doc ->
                PDFTextStripper().apply { sortByPosition = true }.getText(doc)
            }
        } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            throw LockedPdf()
        }
    }
}
