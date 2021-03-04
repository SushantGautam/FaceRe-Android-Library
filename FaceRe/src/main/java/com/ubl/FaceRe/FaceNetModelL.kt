package com.ubl.FaceRe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import org.opencv.android.Utils
import org.opencv.core.Core.NORM_MINMAX
import org.opencv.core.Core.normalize
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc.getRotationMatrix2D
import org.opencv.imgproc.Imgproc.warpAffine
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.lang.Math.atan2
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.sqrt


// Utility class for FaceNet model
class FaceNetModel(context: Context) {

    // TFLiteInterpreter used for running the FaceNet model.
    private var interpreter: Interpreter
    val desiredFaceCropArea = 0.250

//
////     Input image size for VarGFaceNet model.
//    private val imgSize = 112
//    val outPutSize = 512
//    val filename = "Vargfacenet_int8_quant.tflite"


    // Input image size for FaceNet model.
    private val imgSize = 160
    val outPutSize = 128
    val filename = "facenet_int8_quant.tflite"


    init {
        // Initialize TFLiteInterpreter
        val interpreterOptions = Interpreter.Options().apply {
            setNumThreads(4)
        }
        interpreter = Interpreter(
            FileUtil.loadMappedFile(context, filename),
            interpreterOptions
        )
    }


    // Gets an face embedding using FaceNet
    fun getFaceEmbedding(image: Bitmap, face: Face?, preRotate: Boolean): FloatArray {

        val alignedImage = alignImage(image, face)
        return runFaceNet(
            convertBitmapToBuffer(
//                cropRectFromBitmap(alignedImage, face!!.boundingBox, preRotate)
                alignedImage
            )
        )[0]
    }

    private fun alignImage(image: Bitmap, face: Face?): Bitmap {
        val desiredLeftEye = listOf(desiredFaceCropArea, desiredFaceCropArea)

        val rightEyeCenter = face!!.getLandmark(FaceLandmark.RIGHT_EYE)!!.position
        val leftEyeCenter = face.getLandmark(FaceLandmark.LEFT_EYE)!!.position
        val dY = rightEyeCenter.y - leftEyeCenter.y
        val dX = rightEyeCenter.x - leftEyeCenter.x
        val angle = (atan2(dY.toDouble(), dX.toDouble())) * 57.29577951

        val desiredRightEyeX = 1.0 - desiredLeftEye[0]
        val dist = sqrt(dX.pow(2) + dY.pow(2))
        var desiredDist = (desiredRightEyeX - desiredLeftEye[0])
        desiredDist *= imgSize
        val scale = desiredDist / dist


//        # compute center (x, y)-coordinates (i.e., the median point)
//        # between the two eyes in the input image
        val eyesCenter = listOf(
            (leftEyeCenter.x + rightEyeCenter.x) / 2,
            (leftEyeCenter.y + rightEyeCenter.y) / 2
        )

        //# grab the rotation matrix for rotating and scaling the face
        val M = getRotationMatrix2D(
            Point(
                eyesCenter.get(0).toDouble(),
                eyesCenter.get(1).toDouble()
            ), angle, scale
        )

//        # update the translation component of the matrix
        val tX = imgSize * 0.5
        val tY = imgSize * desiredLeftEye.get(1)

        M.put(0, 2, M.get(0, 2)[0] + (tX - eyesCenter[0]))
        M.put(1, 2, M.get(1, 2)[0] + (tY - eyesCenter[1]))

        val imageMat = Mat()
        Utils.bitmapToMat(image, imageMat);
        val outputMat = Mat()

        warpAffine(
            imageMat,
            outputMat,
            M,
            Size(imgSize.toDouble(), imgSize.toDouble())
        )

        normalize(outputMat, outputMat, 0.0, 255.0, NORM_MINMAX)

        val bitmap =
            Bitmap.createBitmap(imgSize, imgSize, Bitmap.Config.ARGB_8888);

        Utils.matToBitmap(outputMat, bitmap)
        return bitmap
    }

    fun getFaceEmbeddingWithoutBBox(image: Bitmap, preRotate: Boolean): FloatArray {
        val s = runFaceNet(
            convertBitmapToBuffer(
                Bitmap.createScaledBitmap(image, 160, 160, false)
            )
        )

        return s[0]
    }

    // Run the FaceNet model.
    private fun runFaceNet(inputs: Any): Array<FloatArray> {
        val t1 = System.currentTimeMillis()
        val outputs = Array(1) {
            FloatArray(outPutSize)
        }
        interpreter.run(inputs, outputs)
        Log.i("Performance", "FaceNet Inference Speed in ms : ${System.currentTimeMillis() - t1}")
        return outputs
    }

    // Resize the given bitmap and convert it to a ByteBuffer
    private fun convertBitmapToBuffer(image: Bitmap): ByteBuffer {
        val imageByteBuffer = ByteBuffer.allocateDirect(1 * imgSize * imgSize * 3 * 4)
        imageByteBuffer.order(ByteOrder.nativeOrder())
        val resizedImage = Bitmap.createScaledBitmap(image, imgSize, imgSize, true)
        for (x in 0 until imgSize) {
            for (y in 0 until imgSize) {
                val pixelValue = resizedImage.getPixel(x, y)
                imageByteBuffer.putFloat((((pixelValue shr 16 and 0xFF) - 128f) / 128f))
                imageByteBuffer.putFloat((((pixelValue shr 8 and 0xFF) - 128f) / 128f))
                imageByteBuffer.putFloat((((pixelValue and 0xFF) - 128f) / 128f))
            }
        }
        return imageByteBuffer
    }

    // Crop the given bitmap with the given rect.
    private fun cropRectFromBitmap(source: Bitmap, rect: Rect, preRotate: Boolean): Bitmap {
        Log.e("App", "rect ${source.width} , ${rect.left + rect.width()} ${rect.toShortString()}")
        var width = rect.width()
        var height = rect.height()
        if ((rect.left + width) > source.width) {
            width = source.width - rect.left
        }
        if ((rect.top + height) > source.height) {
            height = source.height - rect.top
        }
        val croppedBitmap = Bitmap.createBitmap(
            if (preRotate) rotateBitmap(source, 270f)!! else source,
            rect.left,
            rect.top,
            width,
            height
        )
        return croppedBitmap

    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap? {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, false)
    }

}