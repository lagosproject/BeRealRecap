package com.berealrecop.app.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.berealrecop.app.data.BeRealItem
import java.io.File

object FrameRenderer {

    const val VIDEO_WIDTH = 1080
    const val VIDEO_HEIGHT = 1920
    private const val PHOTO_TARGET_HEIGHT = 1440
    private const val TOP_BAR_HEIGHT = 240

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 110f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        // Subtle shadow for extra crispness
        setShadowLayer(6f, 0f, 2f, Color.argb(120, 0, 0, 0))
    }

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /**
     * Creates a 1080x1920 Bitmap with:
     * - Solid black background
     * - Scaled and centered photo in 1080x1440 area (Y: 240 to 1680)
     * - Day number cleanly rendered in the top black margin (centered around Y: 135)
     */
    fun renderFrameBitmap(
        context: Context,
        item: BeRealItem,
        reusableBitmap: Bitmap? = null
    ): Bitmap {
        val outputBitmap = reusableBitmap ?: Bitmap.createBitmap(VIDEO_WIDTH, VIDEO_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)

        // 1. Draw solid black background
        canvas.drawColor(Color.BLACK)

        // 2. Decode source photo
        val srcBitmap = decodeSampledBitmap(context, item, VIDEO_WIDTH, PHOTO_TARGET_HEIGHT)
        if (srcBitmap != null) {
            val srcRect = Rect(0, 0, srcBitmap.width, srcBitmap.height)
            // Center the image in the [0, 240, 1080, 1680] slot
            val destRect = calculateCenterInsideRect(
                srcWidth = srcBitmap.width,
                srcHeight = srcBitmap.height,
                slotWidth = VIDEO_WIDTH,
                slotHeight = PHOTO_TARGET_HEIGHT,
                topOffset = TOP_BAR_HEIGHT
            )
            canvas.drawBitmap(srcBitmap, srcRect, destRect, bitmapPaint)
            if (srcBitmap != outputBitmap) {
                srcBitmap.recycle()
            }
        }

        // 3. Draw day number in top bar
        val dayText = "${item.dayNumber}"
        val textBounds = Rect()
        textPaint.getTextBounds(dayText, 0, dayText.length, textBounds)
        // Center vertically in the 240px top bar
        val textY = (TOP_BAR_HEIGHT / 2f) + (textBounds.height() / 2f) - 4f
        val textX = VIDEO_WIDTH / 2f

        canvas.drawText(dayText, textX, textY, textPaint)

        return outputBitmap
    }

    private fun calculateCenterInsideRect(
        srcWidth: Int,
        srcHeight: Int,
        slotWidth: Int,
        slotHeight: Int,
        topOffset: Int
    ): RectF {
        val srcRatio = srcWidth.toFloat() / srcHeight.toFloat()
        val slotRatio = slotWidth.toFloat() / slotHeight.toFloat()

        val drawWidth: Float
        val drawHeight: Float

        if (srcRatio > slotRatio) {
            drawWidth = slotWidth.toFloat()
            drawHeight = drawWidth / srcRatio
        } else {
            drawHeight = slotHeight.toFloat()
            drawWidth = drawHeight * srcRatio
        }

        val left = (slotWidth - drawWidth) / 2f
        val top = topOffset + (slotHeight - drawHeight) / 2f
        return RectF(left, top, left + drawWidth, top + drawHeight)
    }

    private fun decodeSampledBitmap(context: Context, item: BeRealItem, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            if (item.filePath != null && File(item.filePath).exists()) {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(item.filePath, options)
                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false
                BitmapFactory.decodeFile(item.filePath, options)
            } else {
                val uri = item.uri ?: return null
                val input1 = context.contentResolver.openInputStream(uri) ?: return null
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(input1, null, options)
                input1.close()

                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false

                val input2 = context.contentResolver.openInputStream(uri) ?: return null
                val bmp = BitmapFactory.decodeStream(input2, null, options)
                input2.close()
                bmp
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
