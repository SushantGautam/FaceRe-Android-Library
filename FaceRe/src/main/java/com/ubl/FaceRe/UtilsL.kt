package com.ubl.FaceRe

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.ActivityCompat.startActivityForResult

fun bitmapToNV21(bitmap: Bitmap): ByteArray {
    val argb = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(argb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val yuv = ByteArray(
        bitmap.height * bitmap.width + 2 * Math.ceil(bitmap.height / 2.0).toInt()
                * Math.ceil(bitmap.width / 2.0).toInt()
    )
    encodeYUV420SP(yuv, argb, bitmap.width, bitmap.height)
    return yuv
}

private fun encodeYUV420SP(yuv420sp: ByteArray, argb: IntArray, width: Int, height: Int) {
    val frameSize = width * height
    var yIndex = 0
    var uvIndex = frameSize
    var R: Int
    var G: Int
    var B: Int
    var Y: Int
    var U: Int
    var V: Int
    var index = 0
    for (j in 0 until height) {
        for (i in 0 until width) {
            R = argb[index] and 0xff0000 shr 16
            G = argb[index] and 0xff00 shr 8
            B = argb[index] and 0xff shr 0
            Y = (66 * R + 129 * G + 25 * B + 128 shr 8) + 16
            U = (-38 * R - 74 * G + 112 * B + 128 shr 8) + 128
            V = (112 * R - 94 * G - 18 * B + 128 shr 8) + 128
            yuv420sp[yIndex++] = (if (Y < 0) 0 else if (Y > 255) 255 else Y).toByte()
            if (j % 2 == 0 && index % 2 == 0) {
                yuv420sp[uvIndex++] = (if (V < 0) 0 else if (V > 255) 255 else V).toByte()
                yuv420sp[uvIndex++] = (if (U < 0) 0 else if (U > 255) 255 else U).toByte()
            }
            index++
        }
    }
}


fun NormToAccuracy(l2score: Float): Float {
    val minTh = 2f
    val maxTh = 6f
    val clampedL2 = maxTh - l2score.coerceIn(minTh, maxTh)
    val percentage = (clampedL2) * 100 / (maxTh - minTh)
    return percentage
}


fun CosineSimilarityToAccuracy(cosScore: Float): Float {
    val minTh = .30f
    val maxTh = .90f

    if (cosScore > maxTh) return cosScore * 100
    else if (cosScore < minTh) return cosScore * 100

    val clampedL2 = (cosScore).coerceIn(minTh, 1f)
    val percentage = (clampedL2 - minTh) * 100 / (1 - minTh)
    return percentage.coerceIn(minTh * 100f, 100f)
}


interface CallBackFunction<T, U> {

}


fun StartFaceReActivity(
    callerClass: Activity,
    StudentName: String,
    StudentID: String,
    StudentBitmap: String
) {
    val intent = Intent(callerClass, FaceReActivity::class.java)
    intent.putExtra("StudentName", StudentName)
    intent.putExtra("StudentID", StudentID)
    intent.putExtra("StudentBitmap", StudentBitmap)

    startActivityForResult(callerClass, intent, 514, null)
}