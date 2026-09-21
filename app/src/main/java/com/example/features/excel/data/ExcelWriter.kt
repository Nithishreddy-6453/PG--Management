package com.example.features.excel.data

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExcelSheetData(
    val title: String,
    val headers: List<String>,
    val rows: List<List<Any?>>
)

object ExcelWriter {

    fun writeWorkbook(sheets: List<ExcelSheetData>, outputStream: OutputStream) {
        val zipOut = ZipOutputStream(outputStream)

        // 1. [Content_Types].xml
        zipOut.putNextEntry(ZipEntry("[Content_Types].xml"))
        zipOut.write(buildContentTypesXml(sheets.size).toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()

        // 2. _rels/.rels
        zipOut.putNextEntry(ZipEntry("_rels/.rels"))
        zipOut.write(buildRootRelsXml().toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()

        // 3. xl/workbook.xml
        zipOut.putNextEntry(ZipEntry("xl/workbook.xml"))
        zipOut.write(buildWorkbookXml(sheets).toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()

        // 4. xl/_rels/workbook.xml.rels
        zipOut.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
        zipOut.write(buildWorkbookRelsXml(sheets.size).toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()

        // 5. xl/styles.xml
        zipOut.putNextEntry(ZipEntry("xl/styles.xml"))
        zipOut.write(buildStylesXml().toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()

        // 6. Worksheets: xl/worksheets/sheet1.xml ...
        sheets.forEachIndexed { index, sheet ->
            val sheetNumber = index + 1
            zipOut.putNextEntry(ZipEntry("xl/worksheets/sheet$sheetNumber.xml"))
            zipOut.write(buildWorksheetXml(sheet).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()
        }

        zipOut.finish()
        zipOut.flush()
    }

    private fun escapeXml(str: String?): String {
        if (str == null) return ""
        val sb = StringBuilder()
        for (c in str) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> {
                    // Filter out invalid XML characters
                    if (c.code in 0x20..0xD7FF || c == '\t' || c == '\n' || c == '\r') {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun getColumnLetter(colIndex: Int): String {
        var temp = colIndex + 1
        val colLetter = StringBuilder()
        while (temp > 0) {
            val rem = (temp - 1) % 26
            colLetter.insert(0, ('A'.code + rem).toChar())
            temp = (temp - 1) / 26
        }
        return colLetter.toString()
    }

    private fun buildContentTypesXml(sheetCount: Int): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        sb.append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        sb.append("""<Default Extension="xml" ContentType="application/xml"/>""")
        sb.append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        sb.append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..sheetCount) {
            sb.append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        sb.append("""</Types>""")
        return sb.toString()
    }

    private fun buildRootRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
    }

    private fun buildWorkbookXml(sheets: List<ExcelSheetData>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        sb.append("""<sheets>""")
        sheets.forEachIndexed { index, sheet ->
            val sheetNumber = index + 1
            val safeName = escapeXml(sheet.title.take(31))
            sb.append("""<sheet name="$safeName" sheetId="$sheetNumber" r:id="rId$sheetNumber"/>""")
        }
        sb.append("""</sheets>""")
        sb.append("""</workbook>""")
        return sb.toString()
    }

    private fun buildWorkbookRelsXml(sheetCount: Int): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheetCount) {
            sb.append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        val stylesId = sheetCount + 1
        sb.append("""<Relationship Id="rId$stylesId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        sb.append("""</Relationships>""")
        return sb.toString()
    }

    private fun buildStylesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font>
      <sz val="11"/>
      <color theme="1"/>
      <name val="Calibri"/>
      <family val="2"/>
    </font>
    <font>
      <b/>
      <sz val="11"/>
      <color rgb="FFFFFFFF"/>
      <name val="Calibri"/>
      <family val="2"/>
    </font>
  </fonts>
  <fills count="3">
    <fill>
      <patternFill patternType="none"/>
    </fill>
    <fill>
      <patternFill patternType="gray125"/>
    </fill>
    <fill>
      <patternFill patternType="solid">
        <fgColor rgb="FF1E3A8A"/>
      </patternFill>
    </fill>
  </fills>
  <borders count="1">
    <border>
      <left/>
      <right/>
      <top/>
      <bottom/>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
  </cellXfs>
</styleSheet>"""
    }

    private fun buildWorksheetXml(sheet: ExcelSheetData): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")

        // Column widths
        val maxCols = maxOf(sheet.headers.size, sheet.rows.maxOfOrNull { it.size } ?: 0)
        if (maxCols > 0) {
            sb.append("""<cols>""")
            for (c in 1..maxCols) {
                sb.append("""<col min="$c" max="$c" width="22" customWidth="1"/>""")
            }
            sb.append("""</cols>""")
        }

        sb.append("""<sheetData>""")

        var currentRowIndex = 1

        // Headers row if available
        if (sheet.headers.isNotEmpty()) {
            sb.append("""<row r="$currentRowIndex">""")
            sheet.headers.forEachIndexed { colIdx, headerText ->
                val cellRef = "${getColumnLetter(colIdx)}$currentRowIndex"
                val escaped = escapeXml(headerText)
                sb.append("""<c r="$cellRef" t="inlineStr" s="1"><is><t>$escaped</t></is></c>""")
            }
            sb.append("""</row>""")
            currentRowIndex++
        }

        // Data rows
        sheet.rows.forEach { rowValues ->
            sb.append("""<row r="$currentRowIndex">""")
            rowValues.forEachIndexed { colIdx, value ->
                val cellRef = "${getColumnLetter(colIdx)}$currentRowIndex"
                when (value) {
                    null -> {
                        // Empty cell
                    }
                    is Number -> {
                        val numStr = if (value is Double || value is Float) {
                            if (value.toDouble() % 1.0 == 0.0) value.toLong().toString() else value.toString()
                        } else {
                            value.toString()
                        }
                        sb.append("""<c r="$cellRef"><v>$numStr</v></c>""")
                    }
                    is Boolean -> {
                        val boolVal = if (value) "1" else "0"
                        sb.append("""<c r="$cellRef" t="b"><v>$boolVal</v></c>""")
                    }
                    else -> {
                        val strVal = escapeXml(value.toString())
                        sb.append("""<c r="$cellRef" t="inlineStr"><is><t>$strVal</t></is></c>""")
                    }
                }
            }
            sb.append("""</row>""")
            currentRowIndex++
        }

        sb.append("""</sheetData>""")
        sb.append("""</worksheet>""")
        return sb.toString()
    }
}
