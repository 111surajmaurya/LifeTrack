package com.lifetrack.app.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * CSV, the format everything opens. Tables follow one another: a title line, the header,
 * the rows, then a blank line. Starts with a byte-order mark, which is what tells Excel the
 * file is UTF-8 - without it "µg" and food names in Hindi turn to mojibake.
 */
object CsvWriter {

    fun write(doc: ExportDoc, out: OutputStream) {
        val w = out.bufferedWriter(Charsets.UTF_8)
        w.write("﻿")
        line(w, listOf(doc.title))
        line(w, listOf(doc.subtitle))
        doc.tables.forEach { table ->
            w.write("\r\n")
            line(w, listOf(table.title))
            line(w, table.headers)
            table.rows.forEach { line(w, it) }
        }
        w.flush()
    }

    private fun line(w: java.io.Writer, cells: List<Any>) {
        w.write(cells.joinToString(",") { escape(cellText(it)) })
        w.write("\r\n")
    }

    /** RFC 4180: quote a cell holding a comma, quote or line break, doubling inner quotes. */
    fun escape(text: String): String =
        if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\""
        else text
}

/**
 * A real .xlsx - one sheet per table, a bold header row, numbers stored as numbers - written by
 * hand rather than through a spreadsheet library, which would add megabytes to a small app for
 * five XML files in a zip. Strings are inline, so there is no shared-strings table to keep.
 */
object XlsxWriter {

    fun write(doc: ExportDoc, out: OutputStream) {
        val names = sheetNames(doc.tables.map { it.title })
        val zip = ZipOutputStream(out)
        fun put(path: String, xml: String) {
            zip.putNextEntry(ZipEntry(path))
            zip.write(xml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        put("[Content_Types].xml", contentTypes(doc.tables.size))
        put("_rels/.rels", ROOT_RELS)
        put("xl/workbook.xml", workbook(names))
        put("xl/_rels/workbook.xml.rels", workbookRels(doc.tables.size))
        put("xl/styles.xml", STYLES)
        doc.tables.forEachIndexed { i, table -> put("xl/worksheets/sheet${i + 1}.xml", sheet(table)) }
        zip.finish()
        zip.flush()
    }

    /** Excel's rules: at most 31 characters, none of []:*?/\, and no two the same. */
    fun sheetNames(titles: List<String>): List<String> {
        val used = HashSet<String>()
        return titles.map { title ->
            val base = title.replace(Regex("""[\[\]:*?/\\]"""), " ").trim().ifEmpty { "Sheet" }.take(31)
            var name = base
            var n = 2
            while (!used.add(name.lowercase())) name = base.take(31 - " ($n)".length) + " (${n++})"
            name
        }
    }

    /** "A", "B", ... "Z", "AA" - the column letters a cell reference needs. */
    fun column(index: Int): String {
        var i = index
        val sb = StringBuilder()
        do {
            sb.insert(0, 'A' + i % 26)
            i = i / 26 - 1
        } while (i >= 0)
        return sb.toString()
    }

    private fun sheet(table: ExportTable): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        // The header row stays put while the rows scroll under it.
        sb.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        sb.append("<cols>")
        table.headers.indices.forEach { c ->
            val longest = (listOf(table.headers[c]) + table.rows.map { cellText(it.getOrElse(c) { "" }) })
                .maxOf { it.length }
            val width = (longest + 2).coerceIn(8, 50)
            sb.append("""<col min="${c + 1}" max="${c + 1}" width="$width" customWidth="1"/>""")
        }
        sb.append("</cols><sheetData>")
        row(sb, 1, table.headers, header = true)
        table.rows.forEachIndexed { r, cells -> row(sb, r + 2, cells, header = false) }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun row(sb: StringBuilder, number: Int, cells: List<Any>, header: Boolean) {
        sb.append("""<row r="$number">""")
        cells.forEachIndexed { c, value ->
            val ref = column(c) + number
            val style = if (header) """ s="1"""" else ""
            if (value is Number && !header) {
                sb.append("""<c r="$ref"$style><v>${cellText(value)}</v></c>""")
            } else {
                sb.append("""<c r="$ref" t="inlineStr"$style><is><t xml:space="preserve">${xml(cellText(value))}</t></is></c>""")
            }
        }
        sb.append("</row>")
    }

    private fun xml(text: String): String = buildString {
        text.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                // Control characters other than tab and line breaks are not allowed in XML 1.0.
                else -> if (ch >= ' ' || ch == '\t' || ch == '\n' || ch == '\r') append(ch)
            }
        }
    }

    private fun contentTypes(sheets: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        (1..sheets).forEach {
            append("""<Override PartName="/xl/worksheets/sheet$it.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun workbook(names: List<String>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        names.forEachIndexed { i, name ->
            append("""<sheet name="${xml(name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(sheets: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        (1..sheets).forEach {
            append("""<Relationship Id="rId$it" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$it.xml"/>""")
        }
        append("""<Relationship Id="rId${sheets + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private const val ROOT_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            """</Relationships>"""

    /** Two cell styles: 0 plain, 1 bold on a light grey fill for the header row. */
    private const val STYLES =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
            """<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>""" +
            """<fill><patternFill patternType="solid"><fgColor rgb="FFE8ECEF"/><bgColor indexed="64"/></patternFill></fill></fills>""" +
            """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
            """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
            """<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
            """<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/></cellXfs>""" +
            """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
            """</styleSheet>"""
}

/** How a cell reads as text: whole numbers without ".0", others as written. */
internal fun cellText(value: Any): String = when (value) {
    is Double -> if (value == Math.floor(value) && !value.isInfinite() && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString()
    is Float -> cellText(value.toDouble())
    else -> value.toString()
}
