package com.labourcalc

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import android.provider.Settings
import java.io.File
import jxl.Workbook
import jxl.write.Label
import jxl.write.WritableWorkbook

object SetupManager {

    private const val FOLDER = "worker_data"
    private const val CONFIG_FILE = "app_config.txt"
    private const val EXCEL_FILE = "labour_data.xls"

    fun dataDir(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            FOLDER
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    @SuppressLint("HardwareIds")
    fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    /** Activated if config file exists in worker_data AND was created on this same device. */
    fun isActivated(context: Context): Boolean {
        return try {
            val f = File(dataDir(), CONFIG_FILE)
            if (!f.exists()) return false
            val lines = f.readLines()
            lines.size >= 2 && lines[1].trim() == deviceId(context)
        } catch (e: Exception) {
            false
        }
    }

    fun saveActivation(context: Context, name: String) {
        val f = File(dataDir(), CONFIG_FILE)
        f.writeText(name.trim() + "\n" + deviceId(context))
    }

    fun userName(): String {
        return try {
            val f = File(dataDir(), CONFIG_FILE)
            if (f.exists()) f.readLines().firstOrNull()?.trim() ?: "" else ""
        } catch (e: Exception) {
            ""
        }
    }

    /** Writes ALL entries into Documents/worker_data/labour_data.xls (same file, overwritten). */
    fun exportExcel(labours: List<Labour>) {
        val file = File(dataDir(), EXCEL_FILE)
        val wb: WritableWorkbook = Workbook.createWorkbook(file)
        val sheet = wb.createSheet("Labour Data", 0)

        val headers = listOf(
            "Date", "Place/Site", "No. of Workers", "Cost per Worker",
            "Total", "Amount Paid", "Balance", "Status", "Note"
        )
        headers.forEachIndexed { c, h -> sheet.addCell(Label(c, 0, h)) }

        labours.forEachIndexed { i, l ->
            val r = i + 1
            sheet.addCell(Label(0, r, l.date))
            sheet.addCell(Label(1, r, l.place))
            sheet.addCell(jxl.write.Number(2, r, l.workers.toDouble()))
            sheet.addCell(jxl.write.Number(3, r, l.costPerWorker))
            sheet.addCell(jxl.write.Number(4, r, l.total))
            sheet.addCell(jxl.write.Number(5, r, l.amountPaid))
            sheet.addCell(jxl.write.Number(6, r, l.balance))
            sheet.addCell(Label(7, r, if (l.isPaid) "PAID" else "DUE"))
            sheet.addCell(Label(8, r, l.note))
        }
        wb.write()
        wb.close()
    }

    /** Reads entries back from the Excel file (used after reinstall to restore data). */
    fun importExcel(): MutableList<Labour> {
        val list = mutableListOf<Labour>()
        try {
            val file = File(dataDir(), EXCEL_FILE)
            if (!file.exists()) return list
            val wb = Workbook.getWorkbook(file)
            val sheet = wb.getSheet(0)
            for (r in 1 until sheet.rows) {
                fun cell(c: Int): String =
                    if (c < sheet.columns) sheet.getCell(c, r).contents.trim() else ""
                val place = cell(1)
                if (place.isBlank() && cell(0).isBlank()) continue
                list.add(
                    Labour(
                        id = System.currentTimeMillis() + r,
                        date = cell(0),
                        place = place,
                        workers = cell(2).toDoubleOrNull()?.toInt() ?: 0,
                        costPerWorker = cell(3).toDoubleOrNull() ?: 0.0,
                        note = cell(8),
                        amountPaid = cell(5).toDoubleOrNull() ?: 0.0
                    )
                )
            }
            wb.close()
        } catch (e: Exception) {
            // corrupted or unreadable file - start fresh
        }
        return list
    }
}
