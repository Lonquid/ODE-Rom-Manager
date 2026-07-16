package com.oderommanager.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Converts images to the exact BMP format required by the EZ Flash Omega DE:
 *   - Width:  128 pixels
 *   - Height: 120 pixels
 *   - Bit depth: 24-bit
 *   - Byte order: BGR (NOT RGB — standard BMP format)
 *   - Row order: bottom-up (standard BMP)
 *   - Row stride: padded to 4-byte boundary (128 * 3 = 384, already divisible by 4)
 */
object BmpConverter {

    const val TARGET_WIDTH = 128
    const val TARGET_HEIGHT = 120
    private const val BMP_HEADER_SIZE = 54
    private const val BYTES_PER_PIXEL = 3
    private val ROW_SIZE = ((TARGET_WIDTH * BYTES_PER_PIXEL + 3) / 4) * 4  // = 384, no padding needed

    fun convertToBmp(context: Context, sourceUri: Uri, outputFile: File): Boolean {
        return try {
            val sourceBitmap = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
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
            // Scale to target dimensions using high-quality filtering
            val scaled = Bitmap.createScaledBitmap(
                sourceBitmap, TARGET_WIDTH, TARGET_HEIGHT, true
            )

            FileOutputStream(outputFile).use { fos ->
                writeBmpHeader(fos)
                writeBmpPixels(fos, scaled)
            }

            if (scaled != sourceBitmap) scaled.recycle()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun writeBmpHeader(fos: FileOutputStream) {
        val pixelDataSize = ROW_SIZE * TARGET_HEIGHT
        val fileSize = BMP_HEADER_SIZE + pixelDataSize

        // BMP uses little-endian byte order throughout
        fun Int.le4(): ByteArray = byteArrayOf(
            (this and 0xFF).toByte(),
            ((this shr 8) and 0xFF).toByte(),
            ((this shr 16) and 0xFF).toByte(),
            ((this shr 24) and 0xFF).toByte()
        )
        fun Short.le2(): ByteArray = byteArrayOf(
            (this.toInt() and 0xFF).toByte(),
            ((this.toInt() shr 8) and 0xFF).toByte()
        )

        // BMP file header (14 bytes)
        fos.write(byteArrayOf('B'.code.toByte(), 'M'.code.toByte()))  // signature
        fos.write(fileSize.le4())           // file size
        fos.write(0.toShort().le2())        // reserved 1
        fos.write(0.toShort().le2())        // reserved 2
        fos.write(BMP_HEADER_SIZE.le4())    // pixel data offset

        // DIB header - BITMAPINFOHEADER (40 bytes)
        fos.write(40.le4())                 // header size
        fos.write(TARGET_WIDTH.le4())       // width
        fos.write(TARGET_HEIGHT.le4())      // height (positive = bottom-up storage)
        fos.write(1.toShort().le2())        // color planes = 1
        fos.write(24.toShort().le2())       // bits per pixel = 24
        fos.write(0.le4())                  // compression = none (BI_RGB)
        fos.write(pixelDataSize.le4())      // image size
        fos.write(2835.le4())               // horizontal pixels per meter (~72 DPI)
        fos.write(2835.le4())               // vertical pixels per meter
        fos.write(0.le4())                  // colors in table = 0 (use all)
        fos.write(0.le4())                  // important colors = 0 (all important)
    }

    private fun writeBmpPixels(fos: FileOutputStream, bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height

        // Read all pixels at once — more efficient than per-pixel getPixel()
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val rowBuffer = ByteArray(ROW_SIZE)  // pre-allocate, reuse per row

        // BMP stores rows bottom-up
        for (row in height - 1 downTo 0) {
            var bufIdx = 0
            for (col in 0 until width) {
                // Android ARGB pixel: 0xAARRGGBB
                val pixel = pixels[row * width + col]

                // Extract RGB — use ushr (unsigned shift) to avoid sign extension issues
                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF

                // BMP stores BGR order
                rowBuffer[bufIdx++] = b.toByte()
                rowBuffer[bufIdx++] = g.toByte()
                rowBuffer[bufIdx++] = r.toByte()
            }
            // Pad remaining bytes to 4-byte boundary (already 0 from init, just write)
            fos.write(rowBuffer)
        }
    }
}
