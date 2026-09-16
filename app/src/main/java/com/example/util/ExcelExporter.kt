package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.data.local.model.ProductWithLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExcelExporter {

    private data class EmbeddedImageInfo(
        val rowIndex: Int, // 1-based data row in Excel
        val colIndex: Int, // 0-indexed column in DrawingML: Col B = 1, Col C = 2, Col D = 3
        val imageBytes: ByteArray,
        val extension: String = "png"
    )

    suspend fun exportToExcel(
        context: Context,
        products: List<ProductWithLocation>
    ): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val excelFile = File(exportDir, "Inventaris_Lokal_$timeStamp.xlsx")

        // 1. Process images for products that have them across the 3 slots
        val embeddedImages = mutableListOf<EmbeddedImageInfo>()
        for ((index, item) in products.withIndex()) {
            val rowIndex = index + 2 // Row 1 is header, data starts at Row 2
            val p = item.product

            val uris = listOf(
                Pair(1, p.imageUri),
                Pair(2, p.imageUri2),
                Pair(3, p.imageUri3)
            )

            for ((colIdx, uriStr) in uris) {
                if (!uriStr.isNullOrBlank()) {
                    val bytes = loadAndCompressThumbnail(context, uriStr)
                    if (bytes != null && bytes.isNotEmpty()) {
                        embeddedImages.add(
                            EmbeddedImageInfo(
                                rowIndex = rowIndex,
                                colIndex = colIdx,
                                imageBytes = bytes
                            )
                        )
                    }
                }
            }
        }

        // 2. Build OpenXML .xlsx Zip archive
        FileOutputStream(excelFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                val hasImages = embeddedImages.isNotEmpty()

                // [Content_Types].xml
                writeZipEntry(zos, "[Content_Types].xml", buildContentTypesXml(hasImages, embeddedImages.size))

                // _rels/.rels
                writeZipEntry(zos, "_rels/.rels", buildPackageRelsXml())

                // xl/workbook.xml
                writeZipEntry(zos, "xl/workbook.xml", buildWorkbookXml())

                // xl/_rels/workbook.xml.rels
                writeZipEntry(zos, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml())

                // xl/styles.xml
                writeZipEntry(zos, "xl/styles.xml", buildStylesXml())

                // xl/worksheets/sheet1.xml
                writeZipEntry(zos, "xl/worksheets/sheet1.xml", buildSheetXml(products, hasImages))

                if (hasImages) {
                    // xl/worksheets/_rels/sheet1.xml.rels
                    writeZipEntry(zos, "xl/worksheets/_rels/sheet1.xml.rels", buildSheetRelsXml())

                    // xl/drawings/drawing1.xml
                    writeZipEntry(zos, "xl/drawings/drawing1.xml", buildDrawingXml(embeddedImages))

                    // xl/drawings/_rels/drawing1.xml.rels
                    writeZipEntry(zos, "xl/drawings/_rels/drawing1.xml.rels", buildDrawingRelsXml(embeddedImages.size))

                    // xl/media/image{N}.png
                    for (i in embeddedImages.indices) {
                        val img = embeddedImages[i]
                        val entry = ZipEntry("xl/media/image${i + 1}.png")
                        zos.putNextEntry(entry)
                        zos.write(img.imageBytes)
                        zos.closeEntry()
                    }
                }
            }
        }

        excelFile
    }

    private fun writeZipEntry(zos: ZipOutputStream, path: String, content: String) {
        val entry = ZipEntry(path)
        zos.putNextEntry(entry)
        val writer = OutputStreamWriter(zos, StandardCharsets.UTF_8)
        writer.write(content)
        writer.flush()
        zos.closeEntry()
    }

    private fun loadAndCompressThumbnail(context: Context, uriStr: String): ByteArray? {
        return try {
            val bitmap = when {
                uriStr.startsWith("content://") || uriStr.startsWith("file://") -> {
                    val uri = Uri.parse(uriStr)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
                else -> {
                    val file = File(uriStr)
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                }
            } ?: return null

            // Scale to max 220x220 keeping aspect ratio
            val maxDim = 220
            val width = bitmap.width
            val height = bitmap.height
            val scale = (maxDim.toFloat() / maxOf(width, height)).coerceAtMost(1.0f)
            val targetW = (width * scale).toInt().coerceAtLeast(1)
            val targetH = (height * scale).toInt().coerceAtLeast(1)

            val scaledBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            } else {
                bitmap
            }

            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.PNG, 85, baos)
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            bitmap.recycle()
            baos.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    private fun escapeXml(str: String): String {
        return str
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun buildContentTypesXml(hasImages: Boolean, imageCount: Int): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        sb.append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        sb.append("""<Default Extension="xml" ContentType="application/xml"/>""")
        if (hasImages) {
            sb.append("""<Default Extension="png" ContentType="image/png"/>""")
            sb.append("""<Default Extension="jpg" ContentType="image/jpeg"/>""")
            sb.append("""<Default Extension="jpeg" ContentType="image/jpeg"/>""")
            sb.append("""<Override PartName="/xl/drawings/drawing1.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>""")
        }
        sb.append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        sb.append("""<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        sb.append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        sb.append("""</Types>""")
        return sb.toString()
    }

    private fun buildPackageRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
    }

    private fun buildWorkbookXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Data Inventaris" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""
    }

    private fun buildWorkbookRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
    }

    private fun buildStylesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font><sz val="10"/><color rgb="FF000000"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><b/><sz val="10"/><color rgb="FF1E3A8A"/><name val="Calibri"/></font>
  </fonts>
  <fills count="4">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1E3A8A"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFF1F5F9"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FFCBD5E1"/></left>
      <right style="thin"><color rgb="FFCBD5E1"/></right>
      <top style="thin"><color rgb="FFCBD5E1"/></top>
      <bottom style="thin"><color rgb="FFCBD5E1"/></bottom>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="6">
    <!-- 0: Regular Left -->
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1">
      <alignment horizontal="left" vertical="center" wrapText="1"/>
    </xf>
    <!-- 1: Header -->
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center" wrapText="1"/>
    </xf>
    <!-- 2: Regular Center -->
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
    <!-- 3: Bold Center (Article) -->
    <xf numFmtId="0" fontId="2" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
    <!-- 4: Stock (Bold Center highlight) -->
    <xf numFmtId="0" fontId="2" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
    <!-- 5: Tanpa Foto (Muted Center) -->
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
  </cellXfs>
</styleSheet>"""
    }

    private fun buildSheetXml(products: List<ProductWithLocation>, hasImages: Boolean): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")

        // Column widths:
        // Col 1 (No): 6
        // Col 2 (Gambar 1): 16
        // Col 3 (Gambar 2): 16
        // Col 4 (Gambar 3): 16
        // Col 5 (Artikel): 18
        // Col 6 (Nama): 32
        // Col 7 (Stok): 14
        // Col 8 (Departemen): 20
        // Col 9 (Kode Komuditi): 18
        // Col 10 (Alamat Komuditi): 26
        // Col 11 (Deskripsi): 34
        sb.append("""<cols>""")
        sb.append("""<col min="1" max="1" width="6" customWidth="1"/>""")
        sb.append("""<col min="2" max="2" width="16" customWidth="1"/>""")
        sb.append("""<col min="3" max="3" width="16" customWidth="1"/>""")
        sb.append("""<col min="4" max="4" width="16" customWidth="1"/>""")
        sb.append("""<col min="5" max="5" width="18" customWidth="1"/>""")
        sb.append("""<col min="6" max="6" width="32" customWidth="1"/>""")
        sb.append("""<col min="7" max="7" width="14" customWidth="1"/>""")
        sb.append("""<col min="8" max="8" width="20" customWidth="1"/>""")
        sb.append("""<col min="9" max="9" width="18" customWidth="1"/>""")
        sb.append("""<col min="10" max="10" width="26" customWidth="1"/>""")
        sb.append("""<col min="11" max="11" width="34" customWidth="1"/>""")
        sb.append("""</cols>""")

        sb.append("""<sheetData>""")

        // Header Row (Row 1)
        sb.append("""<row r="1" ht="28" customHeight="1">""")
        sb.append("""<c r="A1" t="inlineStr" s="1"><is><t>No</t></is></c>""")
        sb.append("""<c r="B1" t="inlineStr" s="1"><is><t>Gambar 1</t></is></c>""")
        sb.append("""<c r="C1" t="inlineStr" s="1"><is><t>Gambar 2</t></is></c>""")
        sb.append("""<c r="D1" t="inlineStr" s="1"><is><t>Gambar 3</t></is></c>""")
        sb.append("""<c r="E1" t="inlineStr" s="1"><is><t>Nomor Artikel</t></is></c>""")
        sb.append("""<c r="F1" t="inlineStr" s="1"><is><t>Nama Produk</t></is></c>""")
        sb.append("""<c r="G1" t="inlineStr" s="1"><is><t>Jumlah Stok</t></is></c>""")
        sb.append("""<c r="H1" t="inlineStr" s="1"><is><t>Departemen</t></is></c>""")
        sb.append("""<c r="I1" t="inlineStr" s="1"><is><t>Kode Komuditi</t></is></c>""")
        sb.append("""<c r="J1" t="inlineStr" s="1"><is><t>Alamat Komuditi</t></is></c>""")
        sb.append("""<c r="K1" t="inlineStr" s="1"><is><t>Deskripsi</t></is></c>""")
        sb.append("""</row>""")

        // Data Rows
        for ((index, item) in products.withIndex()) {
            val rowNum = index + 2
            val p = item.product
            val hasPhoto1 = !p.imageUri.isNullOrBlank()
            val hasPhoto2 = !p.imageUri2.isNullOrBlank()
            val hasPhoto3 = !p.imageUri3.isNullOrBlank()

            sb.append("""<row r="$rowNum" ht="68" customHeight="1">""")
            // Col A: No
            sb.append("""<c r="A$rowNum" t="n" s="2"><v>${index + 1}</v></c>""")

            // Col B: Gambar 1
            if (!hasPhoto1) {
                sb.append("""<c r="B$rowNum" t="inlineStr" s="5"><is><t>Tanpa Foto</t></is></c>""")
            } else {
                sb.append("""<c r="B$rowNum" s="0"/>""")
            }

            // Col C: Gambar 2
            if (!hasPhoto2) {
                sb.append("""<c r="C$rowNum" t="inlineStr" s="5"><is><t>-</t></is></c>""")
            } else {
                sb.append("""<c r="C$rowNum" s="0"/>""")
            }

            // Col D: Gambar 3
            if (!hasPhoto3) {
                sb.append("""<c r="D$rowNum" t="inlineStr" s="5"><is><t>-</t></is></c>""")
            } else {
                sb.append("""<c r="D$rowNum" s="0"/>""")
            }

            // Col E: Nomor Artikel
            sb.append("""<c r="E$rowNum" t="inlineStr" s="3"><is><t>${escapeXml(p.articleNumber)}</t></is></c>""")

            // Col F: Nama Produk
            sb.append("""<c r="F$rowNum" t="inlineStr" s="0"><is><t>${escapeXml(p.name)}</t></is></c>""")

            // Col G: Jumlah Stok
            sb.append("""<c r="G$rowNum" t="n" s="4"><v>${p.stockQuantity}</v></c>""")

            // Col H: Departemen
            sb.append("""<c r="H$rowNum" t="inlineStr" s="2"><is><t>${escapeXml(item.departmentName)}</t></is></c>""")

            // Col I: Kode Komuditi
            sb.append("""<c r="I$rowNum" t="inlineStr" s="2"><is><t>${escapeXml(item.sectionCode)}</t></is></c>""")

            // Col J: Alamat Komuditi
            sb.append("""<c r="J$rowNum" t="inlineStr" s="0"><is><t>${escapeXml(item.sectionAddress)}</t></is></c>""")

            // Col K: Deskripsi
            sb.append("""<c r="K$rowNum" t="inlineStr" s="0"><is><t>${escapeXml(p.description)}</t></is></c>""")

            sb.append("""</row>""")
        }

        sb.append("""</sheetData>""")

        if (hasImages) {
            sb.append("""<drawing r:id="rId1"/>""")
        }

        sb.append("""</worksheet>""")
        return sb.toString()
    }

    private fun buildSheetRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing1.xml"/>
</Relationships>"""
    }

    private fun buildDrawingXml(images: List<EmbeddedImageInfo>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">""")

        for (i in images.indices) {
            val img = images[i]
            val picId = i + 1
            val relId = i + 1
            // Col index in DrawingML: 0-indexed. Col B is index 1.
            // Row index in DrawingML: 0-indexed. Row 1 is header (index 0). Row 2 (first data row) is index 1.
            val rowZeroIndexed = img.rowIndex - 1

            sb.append("""<xdr:twoCellAnchor editAs="oneCell">""")
            sb.append("""<xdr:from>""")
            sb.append("""<xdr:col>${img.colIndex}</xdr:col>""")
            sb.append("""<xdr:colOff>36000</xdr:colOff>""") // ~4px margin
            sb.append("""<xdr:row>$rowZeroIndexed</xdr:row>""")
            sb.append("""<xdr:rowOff>36000</xdr:rowOff>""")
            sb.append("""</xdr:from>""")
            sb.append("""<xdr:to>""")
            sb.append("""<xdr:col>${img.colIndex + 1}</xdr:col>""")
            sb.append("""<xdr:colOff>-36000</xdr:colOff>""")
            sb.append("""<xdr:row>${rowZeroIndexed + 1}</xdr:row>""")
            sb.append("""<xdr:rowOff>-36000</xdr:rowOff>""")
            sb.append("""</xdr:to>""")
            sb.append("""<xdr:pic>""")
            sb.append("""<xdr:nvPicPr>""")
            sb.append("""<xdr:cNvPr id="$picId" name="FotoProduk_$picId"/>""")
            sb.append("""<xdr:cNvPicPr><a:picLocks noChangeAspect="1"/></xdr:cNvPicPr>""")
            sb.append("""</xdr:nvPicPr>""")
            sb.append("""<xdr:blipFill>""")
            sb.append("""<a:blip xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" r:embed="rId$relId"/>""")
            sb.append("""<a:stretch><a:fillRect/></a:stretch>""")
            sb.append("""</xdr:blipFill>""")
            sb.append("""<xdr:spPr>""")
            sb.append("""<a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/></a:xfrm>""")
            sb.append("""<a:prstGeom prst="rect"><a:avLst/></a:prstGeom>""")
            sb.append("""</xdr:spPr>""")
            sb.append("""</xdr:pic>""")
            sb.append("""<xdr:clientData/>""")
            sb.append("""</xdr:twoCellAnchor>""")
        }

        sb.append("""</xdr:wsDr>""")
        return sb.toString()
    }

    private fun buildDrawingRelsXml(imageCount: Int): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..imageCount) {
            sb.append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image$i.png"/>""")
        }
        sb.append("""</Relationships>""")
        return sb.toString()
    }
}
