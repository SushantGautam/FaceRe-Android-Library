package com.ubl.FaceRe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.TextView
import com.google.android.gms.tasks.OnSuccessListener
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.reflect.KFunction0


class FaceRe {

    private var imageData = ArrayList<Pair<String, FloatArray>>()

    // Use Firebase MLKit to crop faces from images present in "/images" folder.
    private val cropWithBBoxes: Boolean = true

    // Declare the FaceNet model variable.
    private var model: FaceNetModel? = null


    lateinit var frameAnalyser: FrameAnalyser
    var rearCamera = false

    var imageLabelPairs = ArrayList<Pair<Bitmap, String>>()

    // Initialize Firebase MLKit Face Detector
    private val accurateOps = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .build()
    private val detector = FaceDetection.getClient(accurateOps)

    fun initializeModel(context: Context, rearCamera: Boolean = false): FaceNetModel? {
        model = FaceNetModel(context)
        this.rearCamera = rearCamera
        return model
    }

    lateinit var successCallback: KFunction0<Unit>
    lateinit var activityResources: Map<String, View>

    fun initializeFrame(
        boundingBoxOverlay: BoundingBoxOverlay,
        context: Context,
        callbackFunc: KFunction0<Unit>,
        resources: Map<String, TextView>
    ): FrameAnalyser {
        successCallback = callbackFunc
        frameAnalyser = FrameAnalyser(context, boundingBoxOverlay, this)
        activityResources = resources
        return frameAnalyser
    }

    fun loadBitmapToCompare(bmp: Bitmap, FaceName: String) {
        try {
            imageLabelPairs.add(
                Pair(bmp, FaceName)
            )
        } finally {
            scanImage(0)
        }
    }

    fun loadImageUrlToCompare(ulrn: URL, FaceName: String) {
        val s = Thread {
            try {
                val con = ulrn.openConnection() as HttpURLConnection
                val iss: InputStream = con.inputStream
                val bmp = BitmapFactory.decodeStream(iss)
                if (null != bmp) {
                    imageLabelPairs.add(
                        Pair(bmp, FaceName)
                    )
                    print("Success")
                }

            } catch (e: java.lang.Exception) {
                print("Error")
                e.printStackTrace()
            } finally {
                scanImage(0)
            }
        }
        s.start()
    }

    private fun scanImage(counter: Int) {
        val sample = imageLabelPairs[counter]
        val inputImage = InputImage.fromByteArray(
            bitmapToNV21(sample.first),
            sample.first.width,
            sample.first.height,
            0,
            InputImage.IMAGE_FORMAT_NV21
        )
        val successListener = OnSuccessListener<List<Face?>> { faces ->
            if (faces.isNotEmpty()) {
                imageData.add(
                    Pair(
                        sample.second,
                        if (cropWithBBoxes) {
                            model!!.getFaceEmbedding(sample.first, faces[0]!!.boundingBox, false)
                        } else {
                            model!!.getFaceEmbeddingWithoutBBox(sample.first, false)
                        }
                    )
                )
            }
            if (counter + 1 == imageLabelPairs.size) {
                frameAnalyser.faceList = imageData
            } else {
                scanImage(counter + 1)
            }
        }
        detector.process(inputImage).addOnSuccessListener(successListener)
    }

    fun updateTransform(cameraTextureView: TextureView) {
        val matrix = Matrix()
        val centerX = cameraTextureView.width.div(2f)
        val centerY = cameraTextureView.height.div(2f)
        val rotationDegrees = when (cameraTextureView.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)
        matrix.postScale(2f, 1f)
        matrix.postTranslate(-centerX, 0f)
        cameraTextureView.setTransform(matrix)
    }

}


