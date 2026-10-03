package com.example.features.reports.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.features.reports.domain.model.CompleteOwnerReport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfReportGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun generatePdf(report: CompleteOwnerReport): File = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        val pageWidth = 595 // Standard A4 points at 72dpi
        val pageHeight = 842
        var pageNumber = 1

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(15, 23, 42) // Slate 900
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.rgb(71, 85, 105) // Slate 600
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val sectionPaint = Paint().apply {
            color = Color.rgb(30, 41, 59) // Slate 800
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val labelPaint = Paint().apply {
            color = Color.rgb(100, 116, 139) // Slate 500
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val valuePaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val greenValuePaint = Paint().apply {
            color = Color.rgb(22, 163, 74) // Green
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val amberValuePaint = Paint().apply {
            color = Color.rgb(217, 119, 6) // Amber
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val redValuePaint = Paint().apply {
            color = Color.rgb(220, 38, 38) // Red
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240) // Slate 200
            strokeWidth = 1f
        }

        val cardBgPaint = Paint().apply {
            color = Color.rgb(248, 250, 252) // Slate 50
            style = Paint.Style.FILL
        }

        var y = 40f
        val margin = 36f
        val contentWidth = pageWidth - (margin * 2)

        // 1. Header
        canvas.drawText(report.overview.propertyName, margin, y, titlePaint)
        y += 18f
        canvas.drawText("PG Manager Owner Statement • ${report.overview.reportPeriod}", margin, y, subtitlePaint)
        y += 14f
        canvas.drawText("Generated on ${report.overview.generatedAt}", margin, y, labelPaint)
        y += 15f
        canvas.drawLine(margin, y, margin + contentWidth, y, linePaint)
        y += 20f

        // 2. Occupancy Box
        canvas.drawRoundRect(margin, y, margin + contentWidth, y + 65f, 8f, 8f, cardBgPaint)
        canvas.drawText("OCCUPANCY", margin + 14f, y + 18f, sectionPaint)

        canvas.drawText("Tenants / PG Capacity", margin + 14f, y + 36f, labelPaint)
        canvas.drawText(report.overview.tenantsCapacityRatioText, margin + 14f, y + 52f, valuePaint)

        canvas.drawText("Occupancy Rate", margin + 180f, y + 36f, labelPaint)
        canvas.drawText("${String.format(Locale.US, "%.1f", report.overview.occupancyPercentage)}%", margin + 180f, y + 52f, greenValuePaint)

        canvas.drawText("Vacancies", margin + 340f, y + 36f, labelPaint)
        canvas.drawText("${report.overview.vacanciesCount} Vacanc${if (report.overview.vacanciesCount == 1) "y" else "ies"}", margin + 340f, y + 52f, if (report.overview.vacanciesCount > 0) amberValuePaint else valuePaint)

        y += 85f

        // 3. Money Box
        canvas.drawRoundRect(margin, y, margin + contentWidth, y + 140f, 8f, 8f, cardBgPaint)
        canvas.drawText("FINANCIAL SUMMARY (MONEY)", margin + 14f, y + 20f, sectionPaint)

        // Row 1
        val col1 = margin + 14f
        val col2 = margin + 180f
        val col3 = margin + 340f

        canvas.drawText("Rent Due", col1, y + 42f, labelPaint)
        canvas.drawText("₹${String.format(Locale.US, "%,.2f", report.overview.rentDue)}", col1, y + 58f, valuePaint)

        canvas.drawText("Rent Received for This Month", col2, y + 42f, labelPaint)
        canvas.drawText("₹${String.format(Locale.US, "%,.2f", report.overview.rentReceivedThisMonth)}", col2, y + 58f, greenValuePaint)

        canvas.drawText("Rent Still to Collect", col3, y + 42f, labelPaint)
        canvas.drawText("₹${String.format(Locale.US, "%,.2f", report.overview.rentStillToCollect)}", col3, y + 58f, if (report.overview.rentStillToCollect > 0) redValuePaint else valuePaint)

        // Row 2
        canvas.drawText("Previous Dues Received", col1, y + 80f, labelPaint)
        canvas.drawText("₹${String.format(Locale.US, "%,.2f", report.overview.previousDuesReceived)}", col1, y + 96f, valuePaint)

        canvas.drawText("Advance / Credit", col2, y + 80f, labelPaint)
        canvas.drawText("₹${String.format(Locale.US, "%,.2f", report.overview.advanceCredit)}", col2, y + 96f, valuePaint)

        canvas.drawText("Expenses", col3, y + 80f, labelPaint)
        canvas.drawText("₹${String.format(Locale.US, "%,.2f", report.overview.expenses)}", col3, y + 96f, redValuePaint)

        // Row 3: Money Left After Expenses
        canvas.drawLine(col1, y + 106f, margin + contentWidth - 14f, y + 106f, linePaint)
        canvas.drawText("Money Left After Expenses:", col1, y + 125f, sectionPaint)
        val leftColor = if (report.overview.moneyLeftAfterExpenses >= 0) greenValuePaint else redValuePaint
        canvas.drawText("₹${String.format(Locale.US, "%,.2f", report.overview.moneyLeftAfterExpenses)}", margin + 260f, y + 125f, leftColor)

        y += 160f

        // 4. What Needs Attention
        if (report.attentionItems.isNotEmpty()) {
            canvas.drawText("WHAT NEEDS ATTENTION", margin, y, sectionPaint)
            y += 14f
            report.attentionItems.take(4).forEach { item ->
                canvas.drawCircle(margin + 4f, y + 4f, 3f, amberValuePaint)
                canvas.drawText("${item.title}: ${item.subtitle}", margin + 14f, y + 8f, subtitlePaint)
                y += 16f
            }
            y += 10f
        }

        // 5. Rent Records Table Header
        canvas.drawLine(margin, y, margin + contentWidth, y, linePaint)
        y += 16f
        canvas.drawText("RENT COLLECTION DETAILS", margin, y, sectionPaint)
        y += 16f

        val tableHeadPaint = Paint().apply {
            color = Color.rgb(71, 85, 105)
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        canvas.drawText("Tenant / Room", margin, y, tableHeadPaint)
        canvas.drawText("Due", margin + 160f, y, tableHeadPaint)
        canvas.drawText("Collected", margin + 230f, y, tableHeadPaint)
        canvas.drawText("Remaining", margin + 300f, y, tableHeadPaint)
        canvas.drawText("Status / Basis", margin + 380f, y, tableHeadPaint)
        y += 8f
        canvas.drawLine(margin, y, margin + contentWidth, y, linePaint)
        y += 14f

        val rowPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        report.rentMetrics.tenantRentRecords.take(15).forEach { rec ->
            if (y > pageHeight - 50) {
                pdfDocument.finishPage(page)
                pageNumber++
                val nextInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(nextInfo)
                canvas = page.canvas
                y = 40f
            }

            val tenantRoom = "${rec.tenantName} (${rec.roomNumber})"
            canvas.drawText(tenantRoom.take(24), margin, y, rowPaint)
            canvas.drawText("₹${String.format(Locale.US, "%,.0f", rec.rentDue)}", margin + 160f, y, rowPaint)
            canvas.drawText("₹${String.format(Locale.US, "%,.0f", rec.rentCollected)}", margin + 230f, y, rowPaint)
            canvas.drawText("₹${String.format(Locale.US, "%,.0f", rec.rentStillToCollect)}", margin + 300f, y, rowPaint)
            val statusBasis = "${rec.status} • ${rec.basisExplanation}"
            canvas.drawText(statusBasis.take(30), margin + 380f, y, rowPaint)
            y += 14f
        }

        pdfDocument.finishPage(page)

        // Save PDF to cache
        val cleanName = report.overview.propertyName.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val cleanMonth = report.overview.reportMonth.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val file = File(context.cacheDir, "Owner_Report_${cleanName}_$cleanMonth.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        file
    }
}
