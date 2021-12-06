/*
 * Copyright 2022 Sushant Gautam
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ubl.FaceRe

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.media.Image
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.ubl.FaceRe.BitmapUtils.Companion.flipBitmap
import com.ubl.FaceRe.BitmapUtils.Companion.rotateBitmap
import com.ubl.FaceRe.model.FaceNetModel
import com.ubl.FaceRe.model.Models
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.pow
import kotlin.math.sqrt

// Analyser class to process frames and produce detections.
class FrameAnalyser(
    private var context: Context,
    private var boundingBoxOverlay: BoundingBoxOverlay, private var IsFrontLens: Boolean
) : ImageAnalysis.Analyzer {

    private val realTimeOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .build()
    private val detector = FaceDetection.getClient(realTimeOpts)

    // You may the change the models here.
    // Use the model configs in Models.kt
    // Default is Models.FACENET ; Quantized models are faster
    private val model = FaceNetModel(context, Models.FACENET_512_QUANTIZED)

    private val nameScoreHashmap = HashMap<String, ArrayList<Float>>()
    private var subject = FloatArray(model.embeddingDim)

    // Used to determine whether the incoming frame should be dropped or processed.
    private var isProcessing = false

    // Store the face embeddings in a ( String , FloatArray ) ArrayList.
    // Where String -> name of the person and FloatArray -> Embedding of the face.
    var faceList = ArrayList<Pair<String, FloatArray>>()

    // Use any one of the two metrics, "cosine" or "l2"
    private val metricToBeUsed = "cosine"

    // Use this variable to enable/disable mask detection.
    private val isMaskDetectionOn = false
//    private val maskDetectionModel = MaskDetectionModel(context)


    init {
        boundingBoxOverlay.drawMaskLabel = isMaskDetectionOn
    }


    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {

        fun YuvImage.toBitmap(): Bitmap? {
            val out = ByteArrayOutputStream()
            if (!compressToJpeg(Rect(0, 0, width, height), 100, out))
                return null
            val imageBytes: ByteArray = out.toByteArray()
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }


        fun imageToByteBuffer(image: ImageProxy, outputBuffer: ByteArray, pixelCount: Int) {
            assert(image.format == ImageFormat.YUV_420_888)

            val imageCrop = image.cropRect
            val imagePlanes = image.planes

            imagePlanes.forEachIndexed { planeIndex, plane ->
                // How many values are read in input for each output value written
                // Only the Y plane has a value for every pixel, U and V have half the resolution i.e.
                //
                // Y Plane            U Plane    V Plane
                // ===============    =======    =======
                // Y Y Y Y Y Y Y Y    U U U U    V V V V
                // Y Y Y Y Y Y Y Y    U U U U    V V V V
                // Y Y Y Y Y Y Y Y    U U U U    V V V V
                // Y Y Y Y Y Y Y Y    U U U U    V V V V
                // Y Y Y Y Y Y Y Y
                // Y Y Y Y Y Y Y Y
                // Y Y Y Y Y Y Y Y
                val outputStride: Int

                // The index in the output buffer the next value will be written at
                // For Y it's zero, for U and V we start at the end of Y and interleave them i.e.
                //
                // First chunk        Second chunk
                // ===============    ===============
                // Y Y Y Y Y Y Y Y    V U V U V U V U
                // Y Y Y Y Y Y Y Y    V U V U V U V U
                // Y Y Y Y Y Y Y Y    V U V U V U V U
                // Y Y Y Y Y Y Y Y    V U V U V U V U
                // Y Y Y Y Y Y Y Y
                // Y Y Y Y Y Y Y Y
                // Y Y Y Y Y Y Y Y
                var outputOffset: Int

                when (planeIndex) {
                    0 -> {
                        outputStride = 1
                        outputOffset = 0
                    }
                    1 -> {
                        outputStride = 2
                        // For NV21 format, U is in odd-numbered indices
                        outputOffset = pixelCount + 1
                    }
                    2 -> {
                        outputStride = 2
                        // For NV21 format, V is in even-numbered indices
                        outputOffset = pixelCount
                    }
                    else -> {
                        // Image contains more than 3 planes, something strange is going on
                        return@forEachIndexed
                    }
                }

                val planeBuffer = plane.buffer
                val rowStride = plane.rowStride
                val pixelStride = plane.pixelStride

                // We have to divide the width and height by two if it's not the Y plane
                val planeCrop = if (planeIndex == 0) {
                    imageCrop
                } else {
                    Rect(
                        imageCrop.left / 2,
                        imageCrop.top / 2,
                        imageCrop.right / 2,
                        imageCrop.bottom / 2
                    )
                }

                val planeWidth = planeCrop.width()
                val planeHeight = planeCrop.height()

                // Intermediate buffer used to store the bytes of each row
                val rowBuffer = ByteArray(plane.rowStride)

                // Size of each row in bytes
                val rowLength = if (pixelStride == 1 && outputStride == 1) {
                    planeWidth
                } else {
                    // Take into account that the stride may include data from pixels other than this
                    // particular plane and row, and that could be between pixels and not after every
                    // pixel:
                    //
                    // |---- Pixel stride ----|                    Row ends here --> |
                    // | Pixel 1 | Other Data | Pixel 2 | Other Data | ... | Pixel N |
                    //
                    // We need to get (N-1) * (pixel stride bytes) per row + 1 byte for the last pixel
                    (planeWidth - 1) * pixelStride + 1
                }

                for (row in 0 until planeHeight) {
                    // Move buffer position to the beginning of this row
                    planeBuffer.position(
                        (row + planeCrop.top) * rowStride + planeCrop.left * pixelStride
                    )

                    if (pixelStride == 1 && outputStride == 1) {
                        // When there is a single stride value for pixel and output, we can just copy
                        // the entire row in a single step
                        planeBuffer.get(outputBuffer, outputOffset, rowLength)
                        outputOffset += rowLength
                    } else {
                        // When either pixel or output have a stride > 1 we must copy pixel by pixel
                        planeBuffer.get(rowBuffer, 0, rowLength)
                        for (col in 0 until planeWidth) {
                            outputBuffer[outputOffset] = rowBuffer[col * pixelStride]
                            outputOffset += outputStride
                        }
                    }
                }
            }
        }


        fun yuv420888ToNv21(image: ImageProxy): ByteArray {
            val pixelCount = image.cropRect.width() * image.cropRect.height()
            val pixelSizeBits = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)
            val outputBuffer = ByteArray(pixelCount * pixelSizeBits / 8)
            imageToByteBuffer(image, outputBuffer, pixelCount)
            return outputBuffer
        }

        fun ImageProxy.toBitmap(): Bitmap? {
            val nv21 = yuv420888ToNv21(this)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            return yuvImage.toBitmap()
        }


        // If the previous frame is still being processed, then skip this frame
        if (isProcessing || faceList.size == 0) {
            image.close()
            return
        } else {
            isProcessing = true

            // Rotated bitmap for the FaceNet model

            val frameBitmap: Bitmap

            if (android.os.Build.MODEL == "ET1012T") {
//                #forT i-ScreamTablet
                frameBitmap = image.toBitmap()
                    ?.let { rotateBitmap(it, image.imageInfo.rotationDegrees.toFloat()) }
                    ?.let { flipBitmap(it) }!!
            } else {
                frameBitmap =
                    BitmapUtils.imageToBitmap(image.image!!, image.imageInfo.rotationDegrees)
            }
            // Configure frameHeight and frameWidth for output2overlay transformation matrix.
            if (!boundingBoxOverlay.areDimsInit) {
                boundingBoxOverlay.frameHeight = frameBitmap.height
                boundingBoxOverlay.frameWidth = frameBitmap.width
            }

            val inputImage =
                InputImage.fromMediaImage(image.image as Image, image.imageInfo.rotationDegrees)
            detector.process(inputImage)
                .addOnSuccessListener { faces ->
                    CoroutineScope(Dispatchers.Default).launch {
                        frameBitmap.let { runModel(faces, it) }
                    }
                }
                .addOnCompleteListener {
                    image.close()
                }
        }
    }

    private suspend fun runModel(faces: List<Face>, cameraFrameBitmap: Bitmap) {
        withContext(Dispatchers.Default) {
            val predictions = ArrayList<Prediction>()
            for (face in faces) {
                try {
                    // Crop the frame using face.boundingBox.
                    // Convert the cropped Bitmap to a ByteBuffer.
                    // Finally, feed the ByteBuffer to the FaceNet model.
                    val croppedBitmap =
                        BitmapUtils.cropRectFromBitmap(cameraFrameBitmap, face.boundingBox)
                    subject = model.getFaceEmbedding(croppedBitmap)

                    // Perform face mask detection on the cropped frame Bitmap.
                    val maskLabel = "no mask"
//                    if (isMaskDetectionOn) {
////                        maskLabel = maskDetectionModel.detectMask(croppedBitmap)
//                    }

                    // Continue with the recognition if the user is not wearing a face mask
                    if (maskLabel == maskLabel) {
                        // Perform clustering ( grouping )
                        // Store the clusters in a HashMap. Here, the key would represent the 'name'
                        // of that cluster and ArrayList<Float> would represent the collection of all
                        // L2 norms/ cosine distances.
                        for (i in 0 until faceList.size) {
                            // If this cluster ( i.e an ArrayList with a specific key ) does not exist,
                            // initialize a new one.
                            if (nameScoreHashmap[faceList[i].first] == null) {
                                // Compute the L2 norm and then append it to the ArrayList.
                                val p = ArrayList<Float>()
                                if (metricToBeUsed == "cosine") {
                                    p.add(cosineSimilarity(subject, faceList[i].second))
                                } else {
                                    p.add(L2Norm(subject, faceList[i].second))
                                }
                                nameScoreHashmap[faceList[i].first] = p
                            }
                            // If this cluster exists, append the L2 norm/cosine score to it.
                            else {
                                if (metricToBeUsed == "cosine") {
                                    nameScoreHashmap[faceList[i].first]?.add(
                                        cosineSimilarity(
                                            subject,
                                            faceList[i].second
                                        )
                                    )
                                } else {
                                    nameScoreHashmap[faceList[i].first]?.add(
                                        L2Norm(
                                            subject,
                                            faceList[i].second
                                        )
                                    )
                                }
                            }
                        }

                        // Compute the average of all scores norms for each cluster.
                        val avgScores = nameScoreHashmap.values.map { scores ->
                            scores.toFloatArray().average()
                        }
                        Logger.log("Average score for each user : $nameScoreHashmap")

                        val names = nameScoreHashmap.keys.toTypedArray()
                        nameScoreHashmap.clear()

                        // Calculate the minimum L2 distance from the stored average L2 norms.
                        val bestScoreUserName: String = if (metricToBeUsed == "cosine") {
                            // In case of cosine similarity, choose the highest value.
                            if (avgScores.maxOrNull()!! > model.model.cosineThreshold) {
                                names[avgScores.indexOf(avgScores.maxOrNull()!!)]
                            } else {
                                "Unknown"
                            }
                        } else {
                            // In case of L2 norm, choose the lowest value.
                            if (avgScores.minOrNull()!! > model.model.l2Threshold) {
                                "Unknown"
                            } else {
                                names[avgScores.indexOf(avgScores.minOrNull()!!)]
                            }
                        }


                        val faceMatched =
                            if (arrayOf("Unknown").contains(bestScoreUserName)) false else true

                        FaceReActivity.pingFaceMatched(
                            avgScores.maxOrNull()!!,
                            cameraFrameBitmap,
                            faceMatched
                        ) //informFaceMatched

                        Logger.log("Person identified as $bestScoreUserName")
                        predictions.add(
                            Prediction(
                                face.boundingBox,
                                bestScoreUserName,
                                maskLabel
                            )
                        )
                    } else {
                        // Inform the user to remove the mask
                        predictions.add(
                            Prediction(
                                face.boundingBox,
                                "Please remove the mask",
                                maskLabel
                            )
                        )
                    }
                } catch (e: Exception) {
                    // If any exception occurs with this box and continue with the next boxes.
                    Log.e("Model", "Exception in FrameAnalyser : ${e.message}")
                    continue
                }
            }
//            find scores of bestScoreUserName
//            bestScoreUserName
            withContext(Dispatchers.Main) {
                // Clear the BoundingBoxOverlay and set the new results ( boxes ) to be displayed.
                boundingBoxOverlay.faceBoundingBoxes = predictions
                boundingBoxOverlay.IsFrontLens = IsFrontLens
                boundingBoxOverlay.invalidate()

                isProcessing = false
            }
        }
    }


    // Compute the L2 norm of ( x2 - x1 )
    private fun L2Norm(x1: FloatArray, x2: FloatArray): Float {
        return sqrt(x1.mapIndexed { i, xi -> (xi - x2[i]).pow(2) }.sum())
    }


    // Compute the cosine of the angle between x1 and x2.
    private fun cosineSimilarity(x1: FloatArray, x2: FloatArray): Float {
        val mag1 = sqrt(x1.map { it * it }.sum())
        val mag2 = sqrt(x2.map { it * it }.sum())
        val dot = x1.mapIndexed { i, xi -> xi * x2[i] }.sum()
        return dot / (mag1 * mag2)
    }

}