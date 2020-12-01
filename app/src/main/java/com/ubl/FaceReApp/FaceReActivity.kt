package com.ubl.FaceReApp

import android.Manifest
import android.app.ProgressDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.TextureView
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ubl.FaceRe.BoundingBoxOverlay
import com.ubl.FaceRe.FaceRe
import java.net.URL
import java.util.concurrent.Executors


class FaceReActivity : AppCompatActivity() {

    var faceRe = FaceRe()
    private val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS =
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    private lateinit var cameraTextureView: TextureView

    private var progressDialog: ProgressDialog? = null


//    // For testing purposes only!
//    companion object {
//        // This view's VISIBILITY is set to View.GONE in activity_main.xml
//        lateinit var logTextView: TextView
//        fun setMessage(message: String) {
//            logTextView.text = message
//        }
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_re)

        faceRe.IntializeModel(this, rearCamera = false)

        // Implementation of CameraX preview
        cameraTextureView = findViewById(R.id.camera_textureView)
        val boundingBoxOverlay = findViewById<BoundingBoxOverlay>(R.id.bbox_overlay)
//        logTextView = findViewById(R.id.logTextView)

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
//        frameAnalyser = FrameAnalyser(this, boundingBoxOverlay)
        faceRe.InitializeFrame(boundingBoxOverlay, this, ::SuccessCallbackFunction)


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            // Read image data
            LoadImageToCompare()
        }
    }

    fun SuccessCallbackFunction() {
        Log.d("Bidhan", "My nameis bidhan")
        return
    }

    private fun LoadImageToCompare() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        ) {

//            faceRe.LoadImageUrlToCompare(
////                URL("https://static.toiimg.com/photo/msid-71536423/71536423.jpg?374649"),
//                URL("https://4.bp.blogspot.com/-HBz-6BgylPc/WJArnxlNSZI/AAAAAAAAAZw/IHM5Ug2KmLcCmyKd9BsGo7f-p0kIc_M5gCLcB/s1600/Hd%2BBlur%2BEditor2016_11_06_22_17_35.jpg"),
//                "Sushant"
//            )

//            faceRe.LoadImageUrlToCompare(
////                URL("https://static.toiimg.com/photo/msid-71536423/71536423.jpg?374649"),
//                URL("https://o.remove.bg/uploads/ac88cbcf-bd11-4da2-8b91-b13ff5e384c2/image.png"),
//                "Sushantback"
//            )

//            faceRe.LoadImageUrlToCompare(
//                URL("https://static.toiimg.com/photo/msid-71536423/71536423.jpg?374649"),
////                URL("https://4.bp.blogspot.com/-HBz-6BgylPc/WJArnxlNSZI/AAAAAAAAAZw/IHM5Ug2KmLcCmyKd9BsGo7f-p0kIc_M5gCLcB/s1600/Hd%2BBlur%2BEditor2016_11_06_22_17_35.jpg"),
//                "Heroine"
//            )
//            faceRe.LoadImageUrlToCompare(
//                URL("https://scontent.fktm4-1.fna.fbcdn.net/v/t1.0-9/67763407_2360690547356826_8110921931768725504_n.jpg?_nc_cat=108&ccb=2&_nc_sid=ad2b24&_nc_ohc=ZM8zUA2yl08AX8VfIGP&_nc_ht=scontent.fktm4-1.fna&oh=9cf82aaa55e4b2f4f949fb053da32a7f&oe=5FE9AAA6"),
//                "Sushant Facebook"
//            )

//            faceRe.LoadImageUrlToCompare(
//                URL("https://www.biography.com/.image/t_share/MTE4MDAzNDEwNzg5ODI4MTEw/barack-obama-12782369-1-402.jpg"),
//                "Omaba"
//            )
            faceRe.LoadImageUrlToCompare(
                URL("https://o.remove.bg/uploads/94fc7e0a-b8a3-497e-8839-1f15c6d26e14/image.png"),
                "Sushant Latest"
            )


            //load bitmap example
//            faceRe.LoadBitmapToCompare(bmp: Bitmap, FaceName: "Sushant")

        }
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
            faceRe.updateTransform(cameraTextureView)
        }

        // FrameAnalyser -> fetches camera frames and makes them in the analyse() method.
        val analyzerConfig = ImageAnalysisConfig.Builder().apply {
            setImageReaderMode(
                ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE
            )
            setLensFacing(CameraX.LensFacing.FRONT)
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
                LoadImageToCompare()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }


}
