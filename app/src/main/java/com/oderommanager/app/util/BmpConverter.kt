package com.oderommanager.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Converts images to the exact BMP format required by the EZ Flash Omega DE.
 *
 * CONFIRMED SPEC (from GBAtemp community reverse-engineering):
 *   - File size: exactly 19,256 bytes
 *   - Dimensions: 80 x 120 pixels (portrait, matches box art ratio)
 *   - Color depth: 16-bit
 *   - Header: 56-byte (BITMAPV3INFOHEADER: 14 file + 40 DIB + 2 extra)
 *   - Color format: RGB555 stored in LITTLE-ENDIAN 16-bit words
 *   - IMPORTANT: The EZ Flash reads RGB order, NOT standard BMP BGR order.
 *     The Photoshop tutorial explicitly says to swap R and B channels.
 *     So we store: bits[14:10]=R, bits[9:5]=G, bits[4:0]=B (RGB555)
 *   - Row stride: 80 * 2 = 160 bytes (already 4-byte aligned, no padding needed)
 *   - Row order: bottom-up (standard BMP)
 *
 * Verification: 56 + (80 * 2 * 120) = 56 + 19200 = 19256 bytes ✓
 */
object BmpConverter {

    const val TARGET_WIDTH = 80
    const val TARGET_HEIGHT = 120
    const val EXPECTED_FILE_SIZE = 19256

    private const val FILE_HEADER_SIZE = 14
    private const val DIB_HEADER_SIZE = 40
    private const val EXTRA_BYTES = 2        // makes it 56-byte header as Photoshop produces
    private const val HEADER_SIZE = FILE_HEADER_SIZE + DIB_HEADER_SIZE + EXTRA_BYTES  // 56
    private const val BITS_PER_PIXEL = 16
    private const val BYTES_PER_PIXEL = 2
    private val ROW_SIZE = TARGET_WIDTH * BYTES_PER_PIXEL  // 160, already 4-byte aligned

    fun convertToBmp(context: Context, sourceUri: Uri, outputFile: File): Boolean {
        return try {
            val sourceBitmap = context.contentResolver.openInputStream(sourceUri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return false
            val result = convertToBmp(sourceBitmap, outputFile)
            sourceBitmap.recycle()
            result
        } catch (e: Exception) {
            false
        }
    }

    fun convertToBmp(sourceBitmap: Bitmap, outputFile: File): Boolean {
        return try {
            val scaled = Bitmap.createScaledBitmap(sourceBitmap, TARGET_WIDTH, TARGET_HEIGHT, true)

            FileOutputStream(outputFile).use { fos ->
                writeBmpHeader(fos)
                writePixels(fos, scaled)
            }

            if (scaled != sourceBitmap) scaled.recycle()

            // Verify exact file size
            val actualSize = outputFile.length()
            if (actualSize != EXPECTED_FILE_SIZE.toLong()) {
                outputFile.delete()
                return false
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun writeBmpHeader(fos: FileOutputStream) {
        val pixelDataSize = ROW_SIZE * TARGET_HEIGHT  // 19200
        val fileSize = HEADER_SIZE + pixelDataSize    // 19256

        fun writeLE2(value: Int) {
            fos.write(value and 0xFF)
            fos.write((value shr 8) and 0xFF)
        }
        fun writeLE4(value: Int) {
            fos.write(value and 0xFF)
            fos.write((value shr 8) and 0xFF)
            fos.write((value shr 16) and 0xFF)
            fos.write((value shr 24) and 0xFF)
        }

        // BMP file header (14 bytes)
        fos.write('B'.code)
        fos.write('M'.code)
        writeLE4(fileSize)          // total file size = 19256
        writeLE2(0)                 // reserved 1
        writeLE2(0)                 // reserved 2
        writeLE4(HEADER_SIZE)       // pixel data offset = 56

        // BITMAPINFOHEADER (40 bytes)
        writeLE4(40)                // DIB header size
        writeLE4(TARGET_WIDTH)      // width = 80
        writeLE4(TARGET_HEIGHT)     // height = 120 (positive = bottom-up)
        writeLE2(1)                 // color planes = 1
        writeLE2(BITS_PER_PIXEL)    // bits per pixel = 16
        writeLE4(0)                 // compression = BI_RGB (no compression)
        writeLE4(pixelDataSize)     // pixel data size = 19200
        writeLE4(2835)              // horizontal pixels per meter (~72 DPI)
        writeLE4(2835)              // vertical pixels per meter
        writeLE4(0)                 // colors in table = 0
        writeLE4(0)                 // important colors = 0

        // 2 extra bytes to make 56-byte header (as Photoshop produces for 16-bit BMP)
        writeLE2(0)
    }

    private fun writePixels(fos: FileOutputStream, bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val rowBuffer = ByteArray(ROW_SIZE)

        // BMP rows are stored bottom-up
        for (row in height - 1 downTo 0) {
            var bufIdx = 0
            for (col in 0 until width) {
                val pixel = pixels[row * width + col]

                // Extract 8-bit channels from ARGB int
                // Use ushr (unsigned shift right) to avoid sign extension
                val r8 = (pixel ushr 16) and 0xFF
                val g8 = (pixel ushr 8) and 0xFF
                val b8 = pixel and 0xFF

                // Convert to 5-bit channels (RGB555)
                val r5 = r8 ushr 3  // 8-bit -> 5-bit
                val g5 = g8 ushr 3
                val b5 = b8 ushr 3

                // EZ Flash expects RGB order (R in high bits, B in low bits)
                // This is opposite to standard BMP BGR convention
                // 16-bit word: bit15=0(reserved), bits14-10=R, bits9-5=G, bits4-0=B
                val rgb555 = (r5 shl 10) or (g5 shl 5) or b5

                // Write as little-endian 16-bit
                rowBuffer[bufIdx++] = (rgb555 and 0xFF).toByte()
                rowBuffer[bufIdx++] = ((rgb555 shr 8) and 0xFF).toByte()
            }
            fos.write(rowBuffer)
        }
    }
}
