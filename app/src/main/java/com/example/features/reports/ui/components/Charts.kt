package com.example.features.reports.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.features.reports.domain.model.ChartPoint

@Composable
fun SimpleBarChart(
    data: List<ChartPoint>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    if (data.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No data available", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val maxVal = data.maxOfOrNull { it.value } ?: 0.0
    if (maxVal == 0.0) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No activity", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Canvas(modifier = modifier) {
        val barWidth = size.width / (data.size * 2f)
        val spacing = barWidth

        data.forEachIndexed { index, point ->
            val barHeight = (point.value / maxVal) * size.height
            val xOffset = (index * (barWidth + spacing)) + (spacing / 2f)
            val yOffset = size.height - barHeight.toFloat()

            drawRoundRect(
                color = barColor,
                topLeft = Offset(xOffset, yOffset),
                size = Size(barWidth, barHeight.toFloat()),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // Optional: Draw text label below bar
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.GRAY
                textSize = 30f
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                point.label.take(3),
                xOffset + (barWidth / 2f),
                size.height + 40f,
                paint
            )
        }
    }
}
