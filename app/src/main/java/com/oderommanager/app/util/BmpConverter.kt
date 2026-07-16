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
 * DEFINITIVE SPEC — verified against EZ Flash kernel source code and community research:
 *
 * File size: exactly 19,256 bytes
 * Header: 56 bytes (standard 54-byte BMP header + 2 reserved bytes, as Photoshop produces)
 * Pixel data: 19,200 bytes
 * Dimensions: 80 wide × 120 tall pixels (portrait box-art ratio)
 * Color depth: 16-bit per pixel
 * Color format: GBA BGR555 — this is the GBA hardware's native VRAM format:
 *   bit 15 = 0 (unused)
 *   bits 14-10 = Blue (5 bits)
 *   bits 9-5   = Green (5 bits)
 *   bits 4-0   = Red (5 bits)
 *   Stored little-endian: low byte = GGGRRRRR, high byte = 0BBBBBGG
 *
 * Why BGR not RGB: The EZ Flash kernel reads BMP pixel data directly into GBA VRAM.
 * GBA VRAM uses BGR555 natively. A standard Windows 16-bit BMP BI_RGB stores RGB555
 * (R in high bits), which would display with R and B swapped. The Photoshop tutorial
 * explicitly says to "reverse Red and Blue" before saving — that's the fix.
 * So we write B in the high bits, R in the low bits = BGR555 = GBA native format.
 *
 * Row storage: bottom-up (standard BMP)
 * Row stride: 80 × 2 = 160 bytes (already 4-byte aligned, no padding needed)
 *
 * Verification: 56 + (80 × 2 × 120) = 56 + 19200 = 19256 ✓
 */
object BmpConverter {

    const val TARGET_WIDTH = 80
    const val TARGET_HEIGHT = 120
    const val EXPECTED_FILE_SIZE = 19256

    private const val HEADER_SIZE = 56     // 14 file header + 40 DIB header + 2 reserved
    private const val BYTES_PER_PIXEL = 2
    private val ROW_SIZE = TARGET_WIDTH * BYTES_PER_PIXEL  // 160 bytes, 4-byte aligned

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
            val scaled = Bitmap.createScaledBitmap(
                sourceBitmap, TARGET_WIDTH, TARGET_HEIGHT, true
            )
            FileOutputStream(outputFile).use { fos ->
                writeBmpHeader(fos)
                writePixels(fos, scaled)
            }
            if (scaled != sourceBitmap) scaled.recycle()

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
        val pixelDataSize = ROW_SIZE * TARGET_HEIGHT   // 19200
        val fileSize = HEADER_SIZE + pixelDataSize      // 19256

        fun le2(v: Int) {
            fos.write(v and 0xFF)
            fos.write((v ushr 8) and 0xFF)
        }
        fun le4(v: Int) {
            fos.write(v and 0xFF)
            fos.write((v ushr 8) and 0xFF)
            fos.write((v ushr 16) and 0xFF)
            fos.write((v ushr 24) and 0xFF)
        }

        // BMP file header (14 bytes)
        fos.write('B'.code); fos.write('M'.code)  // signature
        le4(fileSize)          // total file size = 19256
        le2(0)                 // reserved 1
        le2(0)                 // reserved 2
        le4(HEADER_SIZE)       // pixel data offset = 56

        // BITMAPINFOHEADER (40 bytes)
        le4(40)                // DIB header size
        le4(TARGET_WIDTH)      // width = 80
        le4(TARGET_HEIGHT)     // height = 120 (positive = bottom-up)
        le2(1)                 // color planes = 1
        le2(16)                // bits per pixel = 16
        le4(0)                 // compression = BI_RGB
        le4(pixelDataSize)     // pixel data size = 19200
        le4(2835)              // ~72 DPI horizontal
        le4(2835)              // ~72 DPI vertical
        le4(0)                 // colors in table = 0
        le4(0)                 // important colors = 0

        // 2 extra reserved bytes (makes 56-byte header as Photoshop produces)
        le2(0)
    }

    private fun writePixels(fos: FileOutputStream, bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val rowBuffer = ByteArray(ROW_SIZE)

        // BMP stores rows bottom-up
        for (row in height - 1 downTo 0) {
            var idx = 0
            for (col in 0 until width) {
                val pixel = pixels[row * width + col]

                // Extract 8-bit channels from Android ARGB (use ushr to avoid sign extension)
                val r8 = (pixel ushr 16) and 0xFF
                val g8 = (pixel ushr 8) and 0xFF
                val b8 = pixel and 0xFF

                // Scale to 5-bit
                val r5 = r8 ushr 3
                val g5 = g8 ushr 3
                val b5 = b8 ushr 3

                // GBA BGR555: B in bits 14-10, G in bits 9-5, R in bits 4-0
                // This is the GBA hardware VRAM format — kernel reads BMP directly into VRAM
                val bgr555 = (b5 shl 10) or (g5 shl 5) or r5

                // Write as little-endian 16-bit word
                rowBuffer[idx++] = (bgr555 and 0xFF).toByte()          // low byte: GGGRRRRR
                rowBuffer[idx++] = ((bgr555 ushr 8) and 0xFF).toByte() // high byte: 0BBBBBGG
            }
            fos.write(rowBuffer)
        }
    }
}
