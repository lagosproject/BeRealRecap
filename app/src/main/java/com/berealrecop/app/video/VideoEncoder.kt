package com.berealrecop.app.video

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.berealrecop.app.data.BeRealItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import kotlin.math.roundToInt

class VideoEncoder(private val context: Context) {

    companion object {
        private const val TAG = "VideoEncoder"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val WIDTH = 1080
        private const val HEIGHT = 1920
        private const val BIT_RATE = 12_000_000 // 12 Mbps
        private const val FRAME_RATE = 30
        private const val I_FRAME_INTERVAL = 1
        private const val TIMEOUT_USEC = 10_000L
    }

    suspend fun encodeVideo(
        items: List<BeRealItem>,
        durationPerImageSec: Float,
        onProgress: (progress: Float, current: Int, total: Int) -> Unit
    ): Uri? = withContext(Dispatchers.IO) {
        val includedItems = items.filter { it.isIncluded }
        if (includedItems.isEmpty()) return@withContext null

        val tempOutputFile = File(context.cacheDir, "bereal_recap_${System.currentTimeMillis()}.mp4")
        if (tempOutputFile.exists()) tempOutputFile.delete()

        val format = MediaFormat.createVideoFormat(MIME_TYPE, WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        }

        val encoder = MediaCodec.createEncoderByType(MIME_TYPE)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        val eglCore = EglCore(inputSurface, WIDTH, HEIGHT)
        val muxer = MediaMuxer(tempOutputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val reusableBitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)

        val bufferInfo = MediaCodec.BufferInfo()
        var trackIndex = -1
        var muxerStarted = false

        fun drain(endOfStream: Boolean) {
            if (endOfStream) {
                try {
                    encoder.signalEndOfInputStream()
                } catch (e: Exception) {
                    Log.w(TAG, "signalEndOfInputStream: ${e.message}")
                }
            }

            while (true) {
                val encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!endOfStream) break
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) {
                        throw RuntimeException("Format changed twice")
                    }
                    val newFormat = encoder.outputFormat
                    trackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (encoderStatus >= 0) {
                    val encodedData = encoder.getOutputBuffer(encoderStatus)
                        ?: throw RuntimeException("EncoderOutputBuffer $encoderStatus was null")

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0) {
                        if (!muxerStarted) {
                            throw RuntimeException("Muxer hasn't started")
                        }
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(encoderStatus, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }

        try {
            val framesPerItem = maxOf(1, (durationPerImageSec * FRAME_RATE).roundToInt())
            var globalFrameIndex = 0L

            includedItems.forEachIndexed { index, item ->
                // Render frame onto bitmap
                FrameRenderer.renderFrameBitmap(context, item, reusableBitmap)
                eglCore.loadBitmapTexture(reusableBitmap)

                // Output N frames
                for (f in 0 until framesPerItem) {
                    val ptsNs = (globalFrameIndex * 1_000_000_000L) / FRAME_RATE
                    eglCore.drawFrame(ptsNs)
                    drain(false)
                    globalFrameIndex++
                }

                val progress = (index + 1).toFloat() / includedItems.size.toFloat()
                onProgress(progress, index + 1, includedItems.size)
            }

            drain(true)

            // Release resources
            encoder.stop()
            encoder.release()
            eglCore.release()

            if (muxerStarted) {
                muxer.stop()
            }
            muxer.release()
            reusableBitmap.recycle()

            val finalUri = saveToGallery(tempOutputFile)
            tempOutputFile.delete()
            return@withContext finalUri

        } catch (e: Exception) {
            Log.e(TAG, "Error encoding video", e)
            try { encoder.release() } catch (_: Exception) {}
            try { eglCore.release() } catch (_: Exception) {}
            try { muxer.release() } catch (_: Exception) {}
            try { reusableBitmap.recycle() } catch (_: Exception) {}
            tempOutputFile.delete()
            throw e
        }
    }

    private fun saveToGallery(sourceFile: File): Uri? {
        val resolver = context.contentResolver
        val fileName = "BeReal_Recap_${System.currentTimeMillis()}.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/BeRealRecap")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val itemUri = resolver.insert(collection, values) ?: return null

        try {
            resolver.openOutputStream(itemUri)?.use { out: OutputStream ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            }

            MediaScannerConnection.scanFile(context, arrayOf(sourceFile.absolutePath), arrayOf("video/mp4"), null)
            return itemUri
        } catch (e: Exception) {
            resolver.delete(itemUri, null, null)
            Log.e(TAG, "Failed to save video to MediaStore", e)
            return null
        }
    }
}
