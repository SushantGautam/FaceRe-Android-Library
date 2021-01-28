package com.ubl.FaceRe

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.TextureView
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.FileNotFoundException
import java.util.concurrent.Executors


class FaceReActivity : AppCompatActivity() {

    private var faceRe = FaceRe()
    private val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS =
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    private lateinit var cameraTextureView: TextureView

    private lateinit var studentName: String
    private lateinit var studentID: String
    private lateinit var studentBitmapFileName: String
    private lateinit var studentBitmap: Bitmap
    private lateinit var cameraBackorFront: CameraX.LensFacing
    private lateinit var StudentImageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_re)
        studentName = intent.getStringExtra("StudentName").toString()
        studentID = intent.getStringExtra("StudentID").toString()
        studentBitmapFileName = intent.getStringExtra("StudentBitmapFileName").toString()

        StudentImageView = findViewById(R.id.StudentImageView)

        // choose camera
        val cameraBackorFront_tmp = intent.getStringExtra("cameraBackorFront").toString()
        if (cameraBackorFront_tmp == "front" || cameraBackorFront_tmp == "Front") {
            cameraBackorFront = CameraX.LensFacing.FRONT
        } else cameraBackorFront = CameraX.LensFacing.BACK


        //retrieving student image bitmap for comparing with camera frames (images)
        try {
            studentBitmap = BitmapFactory.decodeStream(openFileInput(studentBitmapFileName))
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }

        StudentImageView.setImageBitmap(studentBitmap)

        findViewById<TextView>(R.id.StudentName).text = studentName
        findViewById<TextView>(R.id.StudentID).text = studentID

        faceRe.initializeModel(this, rearCamera = false)

        // Implementation of CameraX preview
        cameraTextureView = findViewById(R.id.camera_textureView)
        val boundingBoxOverlay = findViewById<BoundingBoxOverlay>(R.id.bbox_overlay)

        if (allPermissionsGranted()) {
            cameraTextureView.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
        cameraTextureView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            faceRe.updateTransform(cameraTextureView)
        }

        // Necessary to keep the Overlay above the TextureView so that the boxes are visible.
        boundingBoxOverlay.setWillNotDraw(false)
        boundingBoxOverlay.setZOrderOnTop(true)


        val resources = mapOf(
            "accuracy" to findViewById(R.id.latestaccuracy) as TextView,
            "retry" to findViewById(R.id.retry) as Button,
            "skip" to findViewById(R.id.skip) as Button
        )

        resources["retry"]?.setOnClickListener {
            finish()
            startActivity(intent)
        }

        resources["skip"]?.setOnClickListener {
            navigateToNewActivity()
        }
        boundingBoxOverlay.cameraBackorFront = cameraBackorFront
        faceRe.initializeFrame(boundingBoxOverlay, this, ::successCallbackFunction, resources)


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            // Read image data
            loadImageToCompare()
        }
    }


    private fun successCallbackFunction() {
        if (faceRe.frameAnalyser.finalAverage < faceRe.frameAnalyser.maxThreshold) {
            CameraX.unbindAll()
        } else {
            val toastMessage = Toast.makeText(this, "Success", Toast.LENGTH_LONG)
            toastMessage.show()
            navigateToNewActivity()
        }
        return
    }

    private fun navigateToNewActivity() {
        val data = Intent()
        data.putExtra("status", "success")
        data.putExtra("score", faceRe.frameAnalyser.finalAverage)
        data.putExtra("maxScore", faceRe.frameAnalyser.maxScore)
        setResult(Activity.RESULT_OK, data)
        finish() //Kill the activity from which you will go to next activity
    }

    private fun loadImageToCompare() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            // Log.d("USER INPUT BIMAP", studentBitmap.toString())
//            faceRe.loadImageUrlToCompare(
//                URL("https://4.bp.blogspot.com/-HBz-6BgylPc/WJArnxlNSZI/AAAAAAAAAZw/IHM5Ug2KmLcCmyKd9BsGo7f-p0kIc_M5gCLcB/s1600/Hd%2BBlur%2BEditor2016_11_06_22_17_35.jpg"),
//                "Sushant"
//            )
            faceRe.loadBitmapToCompare(
                studentBitmap,
                "Bidhan"
            )
        }
    }

    // Start the camera preview once the permissions are granted.
    private fun startCamera() {
        val previewConfig = PreviewConfig.Builder().apply {
            setLensFacing(cameraBackorFront)
        }.build()

        val preview = Preview(previewConfig)
        preview.setOnPreviewOutputUpdateListener {
            val parent = cameraTextureView.parent as ViewGroup
            parent.removeView(cameraTextureView)
            parent.addView(cameraTextureView, 0)
            cameraTextureView.setSurfaceTexture(it.surfaceTexture)
            faceRe.updateTransform(cameraTextureView)
        }
        // FrameAnalyser -> fetches camera frames and makes them in the analyse() method.
        val analyzerConfig = ImageAnalysisConfig.Builder().apply {
            setImageReaderMode(
                ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE
            )
            setLensFacing(cameraBackorFront)
        }.build()

        val analyzerUseCase = ImageAnalysis(analyzerConfig).apply {
            setAnalyzer(Executors.newSingleThreadExecutor(), faceRe.frameAnalyser)
        }

        // Bind the preview and frameAnalyser.
        CameraX.bindToLifecycle(this, preview, analyzerUseCase)
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                cameraTextureView.post { startCamera() }
                loadImageToCompare()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

}
