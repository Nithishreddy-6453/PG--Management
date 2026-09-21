package com.example.features.excel.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

object ExcelReader {

    fun readFirstSheet(inputStream: InputStream): List<List<String>> {
        val zipIn = ZipInputStream(inputStream)
        val entries = mutableMapOf<String, ByteArray>()

        var entry = zipIn.nextEntry
        while (entry != null) {
            val name = entry.name.replace("\\", "/")
            if (name == "xl/sharedStrings.xml" || name.startsWith("xl/worksheets/sheet")) {
                entries[name] = zipIn.readBytes()
            }
            zipIn.closeEntry()
            entry = zipIn.nextEntry
        }

        if (entries.isEmpty()) {
            throw IllegalArgumentException("Invalid or empty Excel (.xlsx) file.")
        }

        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()

        val sheetEntryName = entries.keys
            .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
            .sorted()
            .firstOrNull() ?: throw IllegalArgumentException("No worksheet found in Excel file.")

        val sheetBytes = entries[sheetEntryName] ?: throw IllegalArgumentException("Worksheet data could not be read.")
        return parseWorksheet(sheetBytes, sharedStrings)
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val list = mutableListOf<String>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

        var eventType = parser.eventType
        var currentString = StringBuilder()
        var insideSi = false
        var insideT = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "si" -> {
                            insideSi = true
                            currentString = StringBuilder()
                        }
                        "t" -> {
                            if (insideSi) insideT = true
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideT) {
                        currentString.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "t" -> insideT = false
                        "si" -> {
                            insideSi = false
                            list.add(currentString.toString())
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return list
    }

    private fun parseWorksheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

        var eventType = parser.eventType
        val currentRowCells = mutableMapOf<Int, String>()
        var currentCellCol = 0
        var currentCellType: String? = null
        var currentCellValue = StringBuilder()
        var insideV = false
        var insideIsT = false
        var insideRow = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> {
                            insideRow = true
                            currentRowCells.clear()
                            currentCellCol = 0
                        }
                        "c" -> {
                            val ref = parser.getAttributeValue(null, "r")
                            currentCellCol = if (ref != null) colRefToIndex(ref) else currentCellCol
                            currentCellType = parser.getAttributeValue(null, "t")
                            currentCellValue = StringBuilder()
                        }
                        "v" -> insideV = true
                        "is" -> { /* Container for inline string */ }
                        "t" -> {
                            if (!insideV) insideIsT = true
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideV || insideIsT) {
                        currentCellValue.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v" -> insideV = false
                        "t" -> insideIsT = false
                        "c" -> {
                            val rawValue = currentCellValue.toString().trim()
                            val formattedValue = when (currentCellType) {
                                "s" -> {
                                    val index = rawValue.toIntOrNull()
                                    if (index != null && index in sharedStrings.indices) sharedStrings[index] else rawValue
                                }
                                "b" -> if (rawValue == "1") "TRUE" else "FALSE"
                                else -> rawValue
                            }
                            currentRowCells[currentCellCol] = formattedValue
                            currentCellCol++
                        }
                        "row" -> {
                            insideRow = false
                            if (currentRowCells.isNotEmpty()) {
                                val maxCol = currentRowCells.keys.maxOrNull() ?: 0
                                val rowList = ArrayList<String>(maxCol + 1)
                                for (i in 0..maxCol) {
                                    rowList.add(currentRowCells[i] ?: "")
                                }
                                // Only add if row has at least one non-blank cell
                                if (rowList.any { it.isNotBlank() }) {
                                    rows.add(rowList)
                                }
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return rows
    }

    private fun colRefToIndex(ref: String): Int {
        var colLetters = ""
        for (c in ref) {
            if (c.isLetter()) {
                colLetters += c.uppercaseChar()
            } else {
                break
            }
        }
        if (colLetters.isEmpty()) return 0
        var result = 0
        for (c in colLetters) {
            result = result * 26 + (c - 'A' + 1)
        }
        return (result - 1).coerceAtLeast(0)
    }
}
