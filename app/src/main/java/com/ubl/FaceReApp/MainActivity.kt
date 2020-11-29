package com.ubl.FaceReApp

import android.Manifest
import android.app.ProgressDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.os.Environment
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.OnSuccessListener
import com.google.mlkit.vision.common.InputImage.*
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection.getClient
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.ubl.FaceRe.*
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import com.google.mlkit.vision.common.InputImage.fromByteArray as fromByteArray1


class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    private lateinit var cameraTextureView: TextureView

    //library bata
    private lateinit var frameAnalyser: FrameAnalyser

    // Use Firebase MLKit to crop faces from images present in "/images" folder.
    private val cropWithBBoxes: Boolean = true

    // Initialize Firebase MLKit Face Detector
    private val accurateOps = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .build()
    private val detector = getClient(accurateOps)

    // Create an empty ( String , FloatArray ) Hashmap for storing the data.
    private var imageData = ArrayList<Pair<String, FloatArray>>()
    private var imageLabelPairs = ArrayList<Pair<Bitmap, String>>()

    // Declare the FaceNet model variable.
    private var model: FaceNetModel? = null

    private var progressDialog: ProgressDialog? = null

    // For testing purposes only!
    companion object {
        // This view's VISIBILITY is set to View.GONE in activity_main.xml
        lateinit var logTextView: TextView
        fun setMessage(message: String) {
            logTextView.text = message
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FaceRecog().init(this.application)
        setContentView(R.layout.activity_main)


        // Implementation of CameraX preview
        cameraTextureView = findViewById(R.id.camera_textureView)
        val boundingBoxOverlay = findViewById<BoundingBoxOverlay>(R.id.bbox_overlay)
        logTextView = findViewById(R.id.logTextView)

        if (allPermissionsGranted()) {
            cameraTextureView.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
        cameraTextureView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTransform()
        }

        // Necessary to keep the Overlay above the TextureView so that the boxes are visible.
        boundingBoxOverlay.setWillNotDraw(false)
        boundingBoxOverlay.setZOrderOnTop(true)
        frameAnalyser = FrameAnalyser(this, boundingBoxOverlay)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            // Read image data
            scanStorageForImages()
        }
    }

    private fun scanStorageForImages() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
//            progressDialog = ProgressDialog(this)
//            progressDialog?.setMessage("Loading images ...")
//            progressDialog?.setCancelable(false)
//            progressDialog?.show()
            model = FaceNetModel(this)
            val imagesDir =
                File(Environment.getExternalStorageDirectory()!!.absolutePath + "/images")
            val imageSubDirs = imagesDir.listFiles()

            LoadImageToCompare(
                URL("https://4.bp.blogspot.com/-HBz-6BgylPc/WJArnxlNSZI/AAAAAAAAAZw/IHM5Ug2KmLcCmyKd9BsGo7f-p0kIc_M5gCLcB/s1600/Hd%2BBlur%2BEditor2016_11_06_22_17_35.jpg"),
                "Sushant"
            )


//
//            if (imageSubDirs == null) {
//                Toast.makeText(
//                    this,
//                    "Could not read images. Make sure you've have a folder as described in the README",
//                    Toast.LENGTH_LONG
//                ).show()
//                return
//            } else {
//
//                for (imageSubDir in imagesDir.listFiles()) {
//                    for (image in imageSubDir.listFiles()) {
//                        imageLabelPairs.add(
//                            Pair(BitmapFactory.decodeFile(image.absolutePath), imageSubDir.name)
//                        )
//                    }
//                }
//
//            }


        }
    }

    private fun LoadImageToCompare(ulrn: URL, FaceName: String) {

        val s = Thread {
            try {
                val con = ulrn.openConnection() as HttpURLConnection
                val iss: InputStream = con.getInputStream()
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

        val inputImage = fromByteArray1(
            bitmapToNV21(sample.first),
            sample.first.width,
            sample.first.height,
            0,
            IMAGE_FORMAT_NV21
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
                Toast.makeText(
                    this@MainActivity,
                    "Processing completed. ${imageData.size}",
                    Toast.LENGTH_LONG
                ).show()
                progressDialog?.dismiss()
                frameAnalyser.faceList = imageData
            } else {
                progressDialog?.setMessage("Processed ${counter + 1} images")
                scanImage(counter + 1)
            }
        }
        detector.process(inputImage).addOnSuccessListener(successListener)
    }

    // Start the camera preview once the permissions are granted.
    private fun startCamera() {
        val previewConfig = PreviewConfig.Builder().apply {
            setLensFacing(CameraX.LensFacing.FRONT)
        }.build()
        val preview = Preview(previewConfig)
        preview.setOnPreviewOutputUpdateListener {
            val parent = cameraTextureView.parent as ViewGroup
            parent.removeView(cameraTextureView)
            parent.addView(cameraTextureView, 0)
            cameraTextureView.surfaceTexture = it.surfaceTexture
            updateTransform()
        }

        // FrameAnalyser -> fetches camera frames and makes them in the analyse() method.
        val analyzerConfig = ImageAnalysisConfig.Builder().apply {
            setImageReaderMode(
                ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE
            )
            setLensFacing(CameraX.LensFacing.FRONT)

        }.build()
        val analyzerUseCase = ImageAnalysis(analyzerConfig).apply {
            setAnalyzer(Executors.newSingleThreadExecutor(), frameAnalyser)
        }

        // Bind the preview and frameAnalyser.
        CameraX.bindToLifecycle(this, preview, analyzerUseCase)
    }

    private fun updateTransform() {
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                cameraTextureView.post { startCamera() }
                scanStorageForImages()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }


}
