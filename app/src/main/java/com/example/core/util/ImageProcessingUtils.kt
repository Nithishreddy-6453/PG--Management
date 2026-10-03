package com.example.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max

object ImageProcessingUtils {

    data class ProcessedImageResult(
        val file: File,
        val fileName: String,
        val mimeType: String = "image/jpeg",
        val sizeBytes: Long,
        val width: Int,
        val height: Int
    )

    suspend fun processAndCompressImage(
        context: Context,
        imageUri: Uri,
        maxDimension: Int = 1600,
        quality: Int = 85
    ): ProcessedImageResult = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver

        // 1. Check EXIF orientation
        var orientation = ExifInterface.ORIENTATION_NORMAL
        try {
            contentResolver.openInputStream(imageUri)?.use { input ->
                val exif = ExifInterface(input)
                orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        } catch (_: Exception) {}

        // 2. Decode image bounds
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        contentResolver.openInputStream(imageUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }

        val originalWidth = options.outWidth
        val originalHeight = options.outHeight

        if (originalWidth <= 0 || originalHeight <= 0) {
            throw IllegalArgumentException("Invalid image file or format.")
        }

        // 3. Calculate inSampleSize
        var inSampleSize = 1
        val maxOriginal = max(originalWidth, originalHeight)
        if (maxOriginal > maxDimension) {
            val halfMax = maxOriginal / 2
            while ((halfMax / inSampleSize) >= maxDimension) {
                inSampleSize *= 2
            }
        }

        // 4. Decode bitmap with sample size
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val sampledBitmap = contentResolver.openInputStream(imageUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: throw IllegalStateException("Failed to decode image bitmap.")

        // 5. Apply EXIF rotation and scaling
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }

        // Additional scale down if sampled bitmap is still larger than maxDimension
        val currentMax = max(sampledBitmap.width, sampledBitmap.height)
        if (currentMax > maxDimension) {
            val scale = maxDimension.toFloat() / currentMax.toFloat()
            matrix.postScale(scale, scale)
        }

        val finalBitmap = if (!matrix.isIdentity) {
            val transformed = Bitmap.createBitmap(
                sampledBitmap,
                0,
                0,
                sampledBitmap.width,
                sampledBitmap.height,
                matrix,
                true
            )
            if (transformed != sampledBitmap) {
                sampledBitmap.recycle()
            }
            transformed
        } else {
            sampledBitmap
        }

        // 6. Write compressed JPEG to cache file
        val outputDir = File(context.cacheDir, "tenant_photos").apply { mkdirs() }
        val outputFile = File(outputDir, "img_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}.jpg")

        FileOutputStream(outputFile).use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.flush()
        }

        val resultWidth = finalBitmap.width
        val resultHeight = finalBitmap.height
        finalBitmap.recycle()

        ProcessedImageResult(
            file = outputFile,
            fileName = outputFile.name,
            mimeType = "image/jpeg",
            sizeBytes = outputFile.length(),
            width = resultWidth,
            height = resultHeight
        )
    }
}
