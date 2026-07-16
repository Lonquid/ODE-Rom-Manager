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
 * CONFIRMED SPEC — from the EZ Flash Omega kernel firmware XML definitions:
 *   <bitmap width="120" height="80" color="16">Not Found Image</bitmap>
 *
 * This is the thumbnail placeholder in the firmware, which defines the exact
 * display area. All thumbnails must match this size.
 *
 * File size: exactly 19,256 bytes
 *   = 56-byte header + (120 × 80 × 2) pixel bytes
 *   = 56 + 19200 = 19256 ✓
 *
 * Dimensions: 120 wide × 80 tall (LANDSCAPE — matches the GBA screen ratio)
 * Color depth: 16-bit
 * Color format: GBA BGR555 (B in bits 14-10, G in bits 9-5, R in bits 4-0)
 *   — kernel reads BMP directly into GBA VRAM which uses BGR555 natively
 * Header: 56 bytes (14 file + 40 DIB + 2 reserved, as Photoshop produces)
 * Row storage: BOTTOM-UP (standard BMP — first row in file = bottom row of image)
 * Row stride: 120 × 2 = 240 bytes (already 4-byte aligned)
 */
object BmpConverter {

    const val TARGET_WIDTH = 120
    const val TARGET_HEIGHT = 80
    const val EXPECTED_FILE_SIZE = 19256

    private const val HEADER_SIZE = 56
    private const val BYTES_PER_PIXEL = 2
    private val ROW_SIZE = TARGET_WIDTH * BYTES_PER_PIXEL  // 240 bytes

    fun convertToBmp(context: Context, sourceUri: Uri, outputFile: File): Boolean {
        return try {
            // Decode with EXIF orientation correction
            val sourceBitmap = decodeWithOrientation(context, sourceUri) ?: return false
            val result = convertToBmp(sourceBitmap, outputFile)
            sourceBitmap.recycle()
            result
        } catch (e: Exception) {
            false
        }
    }

    private fun decodeWithOrientation(context: Context, uri: Uri): android.graphics.Bitmap? {
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it)
        } ?: return null

        // Read EXIF orientation to correct any rotation/flip from camera or download
        val exif = try {
            context.contentResolver.openInputStream(uri)?.use {
                androidx.exifinterface.media.ExifInterface(it)
            }
        } catch (e: Exception) { null }

        val orientation = exif?.getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
        ) ?: androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL

        val matrix = android.graphics.Matrix()
        when (orientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap // no transform needed
        }

        return android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it != bitmap) bitmap.recycle() }
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

        fun le2(v: Int) { fos.write(v and 0xFF); fos.write((v ushr 8) and 0xFF) }
        fun le4(v: Int) {
            fos.write(v and 0xFF); fos.write((v ushr 8) and 0xFF)
            fos.write((v ushr 16) and 0xFF); fos.write((v ushr 24) and 0xFF)
        }

        // BMP file header (14 bytes)
        fos.write('B'.code); fos.write('M'.code)
        le4(fileSize)           // total = 19256
        le2(0); le2(0)          // reserved
        le4(HEADER_SIZE)        // pixel data offset = 56

        // BITMAPINFOHEADER (40 bytes)
        le4(40)                 // DIB header size
        le4(TARGET_WIDTH)       // width = 120
        le4(TARGET_HEIGHT)      // height = 80
        le2(1)                  // color planes = 1
        le2(16)                 // bits per pixel = 16
        le4(0)                  // compression = BI_RGB (no compression)
        le4(pixelDataSize)      // pixel data size = 19200
        le4(2835); le4(2835)    // ~72 DPI
        le4(0); le4(0)          // color table entries

        // 2 extra reserved bytes → 56-byte header
        le2(0)
    }

    private fun writePixels(fos: FileOutputStream, bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val rowBuffer = ByteArray(ROW_SIZE)

        // EZ Flash reads pixel data top-down into VRAM
        for (row in 0 until height) {
            var idx = 0
            for (col in 0 until width) {
                val pixel = pixels[row * width + col]

                // Android ARGB → 5-bit channels (ushr avoids sign extension)
                val r5 = (pixel ushr 16) and 0xFF ushr 3
                val g5 = (pixel ushr 8) and 0xFF ushr 3
                val b5 = pixel and 0xFF ushr 3

                // GBA BGR555: B in bits 14-10, G in bits 9-5, R in bits 4-0
                val bgr555 = (b5 shl 10) or (g5 shl 5) or r5

                // Little-endian 16-bit word
                rowBuffer[idx++] = (bgr555 and 0xFF).toByte()
                rowBuffer[idx++] = ((bgr555 ushr 8) and 0xFF).toByte()
            }
            fos.write(rowBuffer)
        }
    }
}
