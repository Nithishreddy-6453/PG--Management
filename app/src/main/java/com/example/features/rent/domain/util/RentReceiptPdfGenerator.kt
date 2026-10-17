package com.example.features.rent.domain.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RentReceiptPdfGenerator {

    fun generateReceiptPdf(
        outputStream: OutputStream,
        receiptNumber: String,
        tenantName: String,
        propertyName: String,
        roomNumber: String,
        billingMonth: String,
        amount: String,
        paymentDate: String,
        paymentMode: String,
        transactionReference: String
    ) {
        val pdfDocument = PdfDocument()
        try {
            // Standard A4 dimensions in PostScript points (72 points per inch)
            val pageWidth = 595
            val pageHeight = 842
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // Page Background
            val pageBgPaint = Paint().apply {
                color = Color.rgb(248, 250, 252) // Slate 50
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), pageBgPaint)

            // Card Container
            val cardLeft = 48f
            val cardRight = pageWidth - 48f
            val cardTop = 44f
            val cardBottom = 798f
            val cardWidth = cardRight - cardLeft

            val cardBgPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val cardBorderPaint = Paint().apply {
                color = Color.rgb(226, 232, 240) // Slate 200
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
                isAntiAlias = true
            }
            val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)
            canvas.drawRoundRect(cardRect, 18f, 18f, cardBgPaint)
            canvas.drawRoundRect(cardRect, 18f, 18f, cardBorderPaint)

            // Accent Top Stripe
            val topBarPaint = Paint().apply {
                color = Color.rgb(8, 102, 245) // Receipt Blue
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val topBarRect = RectF(cardLeft + 1.2f, cardTop + 1.2f, cardRight - 1.2f, cardTop + 8f)
            canvas.drawRoundRect(topBarRect, 6f, 6f, topBarPaint)

            val innerLeft = cardLeft + 24f
            val innerRight = cardRight - 24f
            var y = cardTop + 40f

            // 1. Header Branding
            val brandTitlePaint = Paint().apply {
                color = Color.rgb(16, 35, 79) // Receipt Navy
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("PG MANAGER", innerLeft, y, brandTitlePaint)

            val dateText = "Generated: ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date())}"
            val metaPaint = Paint().apply {
                color = Color.rgb(100, 116, 139) // Slate 500
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }
            val metaWidth = metaPaint.measureText(dateText)
            canvas.drawText(dateText, innerRight - metaWidth, y - 2f, metaPaint)

            y += 16f
            val brandSubPaint = Paint().apply {
                color = Color.rgb(100, 116, 139)
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }
            canvas.drawText("Simpler Property Management", innerLeft, y, brandSubPaint)

            y += 18f
            val dividerPaint = Paint().apply {
                color = Color.rgb(226, 232, 240)
                strokeWidth = 1f
                isAntiAlias = true
            }
            canvas.drawLine(innerLeft, y, innerRight, y, dividerPaint)

            // 2. Receipt Title & Status Badge
            y += 28f
            val receiptTitlePaint = Paint().apply {
                color = Color.rgb(16, 35, 79)
                textSize = 15f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("RENT PAYMENT RECEIPT", innerLeft, y, receiptTitlePaint)

            // Payment Recorded Badge (Right-aligned)
            val badgeBgPaint = Paint().apply {
                color = Color.rgb(225, 247, 235) // #E1F7EB
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val badgeTextPaint = Paint().apply {
                color = Color.rgb(10, 155, 85) // #0A9B55
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val badgeText = "PAYMENT RECORDED"
            val badgeTextWidth = badgeTextPaint.measureText(badgeText)
            val badgeRect = RectF(
                innerRight - badgeTextWidth - 18f,
                y - 14f,
                innerRight,
                y + 6f
            )
            canvas.drawRoundRect(badgeRect, 10f, 10f, badgeBgPaint)
            canvas.drawText(badgeText, badgeRect.left + 9f, y - 0.5f, badgeTextPaint)

            // 3. Receipt Number Highlight Box
            y += 22f
            val boxHeight = 36f
            val boxRect = RectF(innerLeft, y, innerRight, y + boxHeight)
            val boxBgPaint = Paint().apply {
                color = Color.rgb(240, 246, 254) // #F0F6FE
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawRoundRect(boxRect, 8f, 8f, boxBgPaint)

            val boxLabelPaint = Paint().apply {
                color = Color.rgb(100, 116, 139)
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }
            val boxValPaint = Paint().apply {
                color = Color.rgb(8, 102, 245)
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("Receipt Number", innerLeft + 14f, y + 22f, boxLabelPaint)
            val recNumWidth = boxValPaint.measureText(receiptNumber)
            canvas.drawText(receiptNumber, innerRight - 14f - recNumWidth, y + 23f, boxValPaint)

            // 4. Tenant & Property Details Section
            y += boxHeight + 20f

            val rowLabelPaint = Paint().apply {
                color = Color.rgb(100, 116, 139)
                textSize = 10.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }
            val rowValuePaint = Paint().apply {
                color = Color.rgb(16, 35, 79)
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val lightLinePaint = Paint().apply {
                color = Color.rgb(241, 245, 249) // Slate 100
                strokeWidth = 1f
                isAntiAlias = true
            }

            drawDetailRow(canvas, "Tenant Name", tenantName.ifBlank { "Not provided" }, y, innerLeft, innerRight, rowLabelPaint, rowValuePaint, lightLinePaint)
            y += 32f

            drawDetailRow(canvas, "Property", propertyName.ifBlank { "Not provided" }, y, innerLeft, innerRight, rowLabelPaint, rowValuePaint, lightLinePaint)
            y += 32f

            drawDetailRow(canvas, "Room Number", roomNumber.ifBlank { "—" }, y, innerLeft, innerRight, rowLabelPaint, rowValuePaint, lightLinePaint)
            y += 32f

            drawDetailRow(canvas, "Billing Month", billingMonth.ifBlank { "—" }, y, innerLeft, innerRight, rowLabelPaint, rowValuePaint, lightLinePaint)
            y += 36f

            // 5. Amount Received Hero Card
            val amountBoxHeight = 62f
            val amountBoxRect = RectF(innerLeft, y, innerRight, y + amountBoxHeight)
            val amountBgPaint = Paint().apply {
                color = Color.rgb(237, 244, 254) // #EDF4FE
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val amountBorderPaint = Paint().apply {
                color = Color.rgb(209, 226, 253)
                style = Paint.Style.STROKE
                strokeWidth = 1f
                isAntiAlias = true
            }
            canvas.drawRoundRect(amountBoxRect, 10f, 10f, amountBgPaint)
            canvas.drawRoundRect(amountBoxRect, 10f, 10f, amountBorderPaint)

            val amountLabelPaint = Paint().apply {
                color = Color.rgb(71, 85, 105)
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }
            canvas.drawText("Amount Received", innerLeft + 16f, y + 26f, amountLabelPaint)

            val amountSubPaint = Paint().apply {
                color = Color.rgb(100, 116, 139)
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }
            canvas.drawText("Recorded Payment", innerLeft + 16f, y + 44f, amountSubPaint)

            val amountValPaint = Paint().apply {
                color = Color.rgb(8, 102, 245)
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val amountWidth = amountValPaint.measureText(amount)
            canvas.drawText(amount, innerRight - 16f - amountWidth, y + 38f, amountValPaint)

            // 6. Payment Metadata Rows
            y += amountBoxHeight + 24f

            drawDetailRow(canvas, "Payment Date", paymentDate.ifBlank { "—" }, y, innerLeft, innerRight, rowLabelPaint, rowValuePaint, lightLinePaint)
            y += 32f

            drawDetailRow(canvas, "Payment Method", paymentMode.ifBlank { "—" }, y, innerLeft, innerRight, rowLabelPaint, rowValuePaint, lightLinePaint)
            y += 32f

            val refText = transactionReference.trim().ifBlank { "Not provided" }
            drawDetailRow(canvas, "Transaction Reference", refText, y, innerLeft, innerRight, rowLabelPaint, rowValuePaint, lightLinePaint)
            y += 44f

            // 7. Divider before Footer
            canvas.drawLine(innerLeft, y, innerRight, y, dividerPaint)
            y += 28f

            // 8. Thank you message and Footer
            val thankYouPaint = Paint().apply {
                color = Color.rgb(16, 35, 79)
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            val centerX = cardLeft + (cardWidth / 2f)
            canvas.drawText("Thank you for your payment.", centerX, y, thankYouPaint)

            y += 18f
            val footerNotePaint = Paint().apply {
                color = Color.rgb(100, 116, 139)
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText("This is an electronically generated receipt issued via PG Manager.", centerX, y, footerNotePaint)

            pdfDocument.finishPage(page)
            pdfDocument.writeTo(outputStream)
        } finally {
            pdfDocument.close()
        }
    }

    private fun drawDetailRow(
        canvas: Canvas,
        label: String,
        value: String,
        y: Float,
        left: Float,
        right: Float,
        labelPaint: Paint,
        valuePaint: Paint,
        linePaint: Paint
    ) {
        canvas.drawText(label, left + 4f, y, labelPaint)

        val maxWidth = right - left - 150f
        var displayValue = value
        if (valuePaint.measureText(displayValue) > maxWidth) {
            var truncated = displayValue
            while (truncated.length > 3 && valuePaint.measureText("$truncated...") > maxWidth) {
                truncated = truncated.dropLast(1)
            }
            displayValue = "$truncated..."
        }

        val valWidth = valuePaint.measureText(displayValue)
        canvas.drawText(displayValue, right - 4f - valWidth, y, valuePaint)
        canvas.drawLine(left, y + 10f, right, y + 10f, linePaint)
    }
}
