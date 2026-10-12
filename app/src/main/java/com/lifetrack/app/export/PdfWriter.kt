package com.lifetrack.app.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

/**
 * A printable report through Android's own [PdfDocument] - no library, no network. Each table
 * gets a heading and a header row that repeats at the top of every page it runs onto. Columns
 * are sized to their contents and squeezed to fit the page, and a cell too long for its column
 * is cut with an ellipsis rather than spilling over its neighbour. Wide tables (the meal log has
 * twelve columns) turn the whole document landscape.
 */
object PdfWriter {

    private const val A4_SHORT = 595   // points
    private const val A4_LONG = 842
    private const val MARGIN = 36f
    private const val ROW_H = 16f
    private const val PAD = 4f

    fun write(doc: ExportDoc, out: OutputStream) {
        val landscape = doc.tables.any { it.headers.size > 6 }
        val pageW = if (landscape) A4_LONG else A4_SHORT
        val pageH = if (landscape) A4_SHORT else A4_LONG

        val titlePaint = paint(18f, bold = true)
        val subPaint = paint(10f).apply { color = Color.rgb(0x5F, 0x6B, 0x78) }
        val headingPaint = paint(13f, bold = true)
        val headPaint = paint(8.5f, bold = true)
        val cellPaint = paint(8.5f)
        val shade = Paint().apply { color = Color.rgb(0xE8, 0xEC, 0xEF) }
        val zebra = Paint().apply { color = Color.rgb(0xF6, 0xF8, 0xF9) }

        val pdf = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun newPage() {
            page?.let(pdf::finishPage)
            pageNo++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            y = MARGIN
            // Page number, bottom right.
            val label = "LifeTrack  ·  page $pageNo"
            page!!.canvas.drawText(label, pageW - MARGIN - subPaint.measureText(label), pageH - MARGIN / 2, subPaint)
        }
        fun room(needed: Float) = y + needed <= pageH - MARGIN

        newPage()
        page!!.canvas.drawText(doc.title, MARGIN, y + 18f, titlePaint)
        y += 26f
        page!!.canvas.drawText(doc.subtitle, MARGIN, y + 10f, subPaint)
        y += 22f

        val usable = pageW - 2 * MARGIN
        doc.tables.forEach { table ->
            val widths = columnWidths(table, usable, headPaint, cellPaint)
            fun header() {
                val c = page!!.canvas
                c.drawRect(MARGIN, y, MARGIN + usable, y + ROW_H, shade)
                var x = MARGIN
                table.headers.forEachIndexed { i, h ->
                    c.drawText(fit(h, widths[i] - 2 * PAD, headPaint), x + PAD, y + ROW_H - 4.5f, headPaint)
                    x += widths[i]
                }
                y += ROW_H
            }

            // A heading never sits alone at the bottom of a page.
            if (!room(22f + ROW_H * 2)) newPage()
            y += 8f
            page!!.canvas.drawText(table.title, MARGIN, y + 13f, headingPaint)
            y += 20f
            header()
            if (table.rows.isEmpty()) {
                page!!.canvas.drawText("Nothing recorded in this period.", MARGIN + PAD, y + ROW_H - 4.5f, cellPaint)
                y += ROW_H
            }
            table.rows.forEachIndexed { r, cells ->
                if (!room(ROW_H)) {
                    newPage()
                    header()
                }
                val c = page!!.canvas
                if (r % 2 == 1) c.drawRect(MARGIN, y, MARGIN + usable, y + ROW_H, zebra)
                var x = MARGIN
                cells.forEachIndexed { i, value ->
                    val w = widths.getOrElse(i) { 0f }
                    val text = fit(cellText(value), w - 2 * PAD, cellPaint)
                    // Numbers line up on the right, the way a column of figures should.
                    val tx = if (value is Number) x + w - PAD - cellPaint.measureText(text) else x + PAD
                    c.drawText(text, tx, y + ROW_H - 4.5f, cellPaint)
                    x += w
                }
                y += ROW_H
            }
            y += 10f
        }
        page?.let(pdf::finishPage)
        pdf.writeTo(out)
        pdf.close()
    }

    /** Natural widths (widest cell, capped), scaled down together if they overrun the page. */
    private fun columnWidths(table: ExportTable, usable: Float, head: Paint, cell: Paint): FloatArray {
        val natural = FloatArray(table.headers.size) { i ->
            val widest = table.rows.take(500).maxOfOrNull { cell.measureText(cellText(it.getOrElse(i) { "" })) } ?: 0f
            maxOf(head.measureText(table.headers[i]), widest).coerceAtMost(usable * 0.4f) + 2 * PAD
        }
        val total = natural.sum()
        // Spread spare room evenly, or shrink everything in proportion.
        return if (total <= usable) {
            val extra = (usable - total) / natural.size
            FloatArray(natural.size) { natural[it] + extra }
        } else {
            FloatArray(natural.size) { natural[it] * usable / total }
        }
    }

    private fun fit(text: String, width: Float, paint: Paint): String {
        if (width <= 0f) return ""
        if (paint.measureText(text) <= width) return text
        val keep = paint.breakText(text, true, width - paint.measureText("…"), null)
        return text.take(keep.coerceAtLeast(0)) + "…"
    }

    private fun paint(size: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        color = Color.rgb(0x11, 0x16, 0x1C)
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
}
