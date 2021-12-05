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

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ExifInterface
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Base64
import android.util.Size
import android.view.View
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.lang.reflect.Type
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.ArrayList


fun StringToBitMap(encodedString: String?): Bitmap {

    val encodeByte: ByteArray = Base64.decode(encodedString, Base64.DEFAULT)
    return BitmapFactory.decodeByteArray(encodeByte, 0, encodeByte.size)

}


class FaceReActivity : AppCompatActivity() {

//    private var isSerializedDataStored = false
//
//    // Serialized data will be stored ( in app's private storage ) with this filename.
//    private val SERIALIZED_DATA_FILENAME = "image_data"
//
//    // Shared Pref key to check if the data was stored.
//    private val SHARED_PREF_IS_DATA_STORED_KEY = "is_data_stored"

    private lateinit var previewView: PreviewView
    private lateinit var frameAnalyser: FrameAnalyser
    private lateinit var fileReader: FileReader
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var progress_circular: ProgressBar
    private lateinit var ContinueIcon: ImageView
    private lateinit var imageView: ImageView
    private lateinit var RetryIcon: ImageView
    private lateinit var PersonName: TextView
    private lateinit var TextRoll: TextView
    private var cameraType = CameraSelector.LENS_FACING_BACK


    companion object {

        lateinit var logTextView: TextView
        private lateinit var myTextProgress: TextView


        var framesToCheck = 10
        var successFramesRequired = 5
        var timeOutTime = 8
        var scoreThreshold = 50

        fun setMessage(message: String) {
            logTextView.text = message
        }

        fun checkCanScrollVertically() {
            while (logTextView.canScrollVertically(1)) {
                logTextView.scrollBy(0, 10);
            }
        }

        lateinit var lastcameraFrameBitmap: Bitmap


        var startTime = Calendar.getInstance() //will be overridden later below

        var noFaceFoundYet = true


        var FaceReCompleted: (() -> Unit)? = null
        var FaceReCompletedRetry: (() -> Unit)? = null

        @SuppressLint("NewApi")
        fun pingFaceMatched(scorex: Double, cameraFrameBitmap: Bitmap, faceMatched: Boolean) {
            framesToCheck -= 1 //keep reducing Frames to be tested value
            score = (scorex * 100).toInt()
            if (score > bestScore) bestScore = score
            myTextProgress.text = "$score%"
            lastcameraFrameBitmap = cameraFrameBitmap
            if (faceMatched) successFramesRequired -= 1

            if (noFaceFoundYet) {
                startTime = Calendar.getInstance()
                noFaceFoundYet = false
                //init timecounter, this will run just once
            } else {
                val diff: Int =
                    ((Calendar.getInstance().timeInMillis - startTime.timeInMillis) / 1000).toInt()

                if ((successFramesRequired <= 0)) {
                    FaceReCompleted!!.invoke()
                } else if ((framesToCheck <= 0) or (diff > timeOutTime)) {
                    // Done! Submit the result
                    if (score > scoreThreshold) {
                        FaceReCompleted!!.invoke()
                        return
                    }
                    FaceReCompletedRetry!!.invoke()
                }
            }

        }


        var bestScore = 0
        var score = 0

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Remove the status bar to have a full screen experience
        // See this answer on SO -> https://stackoverflow.com/a/68152688/10878733
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.windowInsetsController!!
                .hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        } else {
//            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
            //will be handled automatically from manifest from now
            assert(true)
        }
        setContentView(R.layout.activity_facere)

        // Implementation of CameraX preview

        previewView = findViewById(R.id.preview_view)
        logTextView = findViewById(R.id.log_textview)

        progress_circular = findViewById(R.id.myProgress)
        ContinueIcon = findViewById(R.id.ContinueIcon)
        imageView = findViewById(R.id.imageView)
        RetryIcon = findViewById(R.id.RetryIcon)
        PersonName = findViewById(R.id.PersonName)
        TextRoll = findViewById(R.id.TextRoll)
        myTextProgress = findViewById(R.id.myTextProgress)

        ContinueIcon.visibility = View.GONE
        RetryIcon.visibility = View.GONE

        logTextView.movementMethod = ScrollingMovementMethod()
        // Necessary to keep the Overlay above the PreviewView so that the boxes are visible.
        val boundingBoxOverlay = findViewById<BoundingBoxOverlay>(R.id.bbox_overlay)
        boundingBoxOverlay.setWillNotDraw(false)
        boundingBoxOverlay.setZOrderOnTop(true)

        frameAnalyser = FrameAnalyser(this, boundingBoxOverlay)
        fileReader = FileReader(this)


        // We'll only require the CAMERA permission from the user.
        // For scoped storage, particularly for accessing documents, we won't require WRITE_EXTERNAL_STORAGE or
        // READ_EXTERNAL_STORAGE permissions. See https://developer.android.com/training/data-storage


        val sharedPrefs = getSharedPreferences(
            this.packageName, Context.MODE_PRIVATE
        )
        val WantFrontCamera = sharedPrefs.getBoolean("WantFrontCamera", false)
        val WantLogsDisplayed = sharedPrefs.getBoolean("WantLogsDisplayed", false)
        framesToCheck = sharedPrefs.getInt("framesToCheck", 10)
        successFramesRequired = sharedPrefs.getInt("successFramesRequired", 5)
        timeOutTime = sharedPrefs.getInt("timeOutTime", 8)
        scoreThreshold = sharedPrefs.getInt("scoreThreshold", 50)
        val StudentName = sharedPrefs.getString("StudentName", "Student Name")
        val EPSRoll = sharedPrefs.getString("EPSRoll", "EPS Roll")
        PersonName.text = StudentName
        TextRoll.text = EPSRoll

        logTextView.visibility = if (WantLogsDisplayed) View.VISIBLE else View.GONE

        cameraType =
            if (WantFrontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermission()
        } else {
            startCameraPreview()
        }

        val gson = Gson()
        val json = sharedPrefs.getString("facereData", "")

        val type: Type = object : TypeToken<ArrayList<Pair<String, String>>>() {}.type
        val accumulator: ArrayList<Pair<String, String>> =
            gson.fromJson<ArrayList<Pair<String, String>>>(json, type)

        val FaceRearrayList: ArrayList<Pair<String, Bitmap>> = ArrayList()
        for (obj in accumulator) FaceRearrayList.add(
            Pair(
                obj.first,
                StringToBitMap(obj.second)
            )
        )
        fileReader.run(FaceRearrayList, fileReaderCallback)
        imageView.setImageBitmap(FaceRearrayList.first().second)

        fun handleFaceReComplete(failure: Boolean = false) {
            runOnUiThread {
                ContinueIcon.visibility = View.VISIBLE
                if (failure) RetryIcon.visibility = View.VISIBLE
                progress_circular.visibility = View.GONE
                cameraProviderFuture.get().unbindAll()
                if (failure) {
                    ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                        .startTone(ToneGenerator.TONE_CDMA_LOW_L, 2000)
                } else {
                    ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                        .startTone(ToneGenerator.TONE_CDMA_PIP, 1000)
                }
                score
                bestScore
                lastcameraFrameBitmap

            }

        }

        FaceReCompleted = { handleFaceReComplete() }
        FaceReCompletedRetry = { handleFaceReComplete(true) }


        RetryIcon.setOnClickListener {
            recreate()
        }

        ContinueIcon.setOnClickListener {
            val data = Intent()
            data.putExtra("status", "success")
            data.putExtra("score", score)
            data.putExtra("maxScore", bestScore)
            setResult(Activity.RESULT_OK, data)

            //save the last frame locally in private to access in the user app
            var fileName: String? = "myFaceReImage" //no .png or .jpg needed
            try {
                val bytes = ByteArrayOutputStream()
                lastcameraFrameBitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
                val fo: FileOutputStream =
                    applicationContext.openFileOutput(fileName, Context.MODE_PRIVATE)
                fo.write(bytes.toByteArray())
                // remember close file output
                fo.close()

                finish() //Kill the activity from which you will go to next activity
            } catch (e: Exception) {
                e.printStackTrace()
                fileName = null
            }
        }

//        val ss = Gson().fromJson(data, typeOf<Pair<String, Bitmap>>())
//        frameAnalyser.faceList.add(Pair(name, embedding))

//        sharedPreferences = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE)
//        isSerializedDataStored = sharedPreferences.getBoolean(SHARED_PREF_IS_DATA_STORED_KEY, false)
//        if (!isSerializedDataStored) {
//            Logger.log("No serialized data was found. Select the images directory.")
//            showSelectDirectoryDialog()
//        } else {
//            val alertDialog = AlertDialog.Builder(this).apply {
//                setTitle("Serialized Data")
//                setMessage("Existing image data was found on this device. Would you like to load it?")
//                setCancelable(false)
//                setNegativeButton("LOAD") { dialog, which ->
//                    dialog.dismiss()
//                    frameAnalyser.faceList = loadSerializedImageData()
//                    Logger.log("Serialized data loaded.")
//                }
//                setPositiveButton("RESCAN") { dialog, which ->
//                    dialog.dismiss()
//                    launchChooseDirectoryIntent()
//                }
//                create()
//            }
//            alertDialog.show()
//        }

    }

    // ---------------------------------------------- //

    // Attach the camera stream to the PreviewView.
    private fun startCameraPreview() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                bindPreview(cameraProvider)
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview: Preview = Preview.Builder().build()
        val cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(cameraType)
            .build()
        preview.setSurfaceProvider(previewView.surfaceProvider)
        val imageFrameAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(480, 640))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageFrameAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), frameAnalyser)
        cameraProvider.bindToLifecycle(
            this as LifecycleOwner,
            cameraSelector,
            preview,
            imageFrameAnalysis
        )
    }

    // We let the system handle the requestCode. This doesn't require onRequestPermissionsResult and
    // hence makes the code cleaner.
    // See the official docs -> https://developer.android.com/training/permissions/requesting#request-permission
    private fun requestCameraPermission() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCameraPreview()
            } else {
                val alertDialog = AlertDialog.Builder(this).apply {
                    setTitle("Camera Permission")
                    setMessage("The app couldn't function without the camera permission.")
                    setCancelable(false)
                    setPositiveButton("ALLOW") { dialog, _ ->
                        dialog.dismiss()
                        requestCameraPermission()
                    }
                    setNegativeButton("CLOSE") { dialog, _ ->
                        dialog.dismiss()
                        finish()
                    }
                    create()
                }
                alertDialog.show()
            }

        }


    // ---------------------------------------------- //


    // Open File chooser to choose the images directory.
//    private fun showSelectDirectoryDialog() {
//        val alertDialog = AlertDialog.Builder(this).apply {
//            setTitle("Select Images Directory")
//            setMessage("As mentioned in the project\'s README file, please select a directory which contains the images.")
//            setCancelable(false)
//            setPositiveButton("SELECT") { dialog, which ->
//                dialog.dismiss()
//                launchChooseDirectoryIntent()
//            }
//            create()
//        }
//        alertDialog.show()
//    }


//    private fun launchChooseDirectoryIntent() {
//        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
//        // startForActivityResult is deprecated.
//        // See this SO thread -> https://stackoverflow.com/questions/62671106/onactivityresult-method-is-deprecated-what-is-the-alternative
//        directoryAccessLauncher.launch(intent)
//    }


    // Read the contents of the select directory here.
    // The system handles the request code here as well.
    // See this SO question -> https://stackoverflow.com/questions/47941357/how-to-access-files-in-a-directory-given-a-content-uri
//    private val directoryAccessLauncher =
//        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
//            val dirUri = it.data?.data ?: return@registerForActivityResult
//            val childrenUri =
//                DocumentsContract.buildChildDocumentsUriUsingTree(
//                    dirUri,
//                    DocumentsContract.getTreeDocumentId(dirUri)
//                )
//            val tree = DocumentFile.fromTreeUri(this, childrenUri)
//            val images = ArrayList<Pair<String, Bitmap>>()
//            var errorFound = false
//            if (tree!!.listFiles().isNotEmpty()) {
//                for (doc in tree.listFiles()) {
//                    if (doc.isDirectory && !errorFound) {
//                        val name = doc.name!!
//                        for (imageDocFile in doc.listFiles()) {
//                            try {
//                                images.add(Pair(name, getFixedBitmap(imageDocFile.uri)))
//                            } catch (e: Exception) {
//                                errorFound = true
//                                Logger.log(
//                                    "Could not parse an image in $name directory. Make sure that the file structure is " +
//                                            "as described in the README of the project and then restart the app."
//                                )
//                                break
//                            }
//                        }
//                        Logger.log("Found ${doc.listFiles().size} images in $name directory")
//                    } else {
//                        errorFound = true
//                        Logger.log(
//                            "The selected folder should contain only directories. Make sure that the file structure is " +
//                                    "as described in the README of the project and then restart the app."
//                        )
//                    }
//                }
//            } else {
//                errorFound = true
//                Logger.log(
//                    "The selected folder doesn't contain any directories. Make sure that the file structure is " +
//                            "as described in the README of the project and then restart the app."
//                )
//            }
//            if (!errorFound) {
//                fileReader.run(images, fileReaderCallback)
//                Logger.log("Detecting faces in ${images.size} images ...")
//            } else {
//                val alertDialog = AlertDialog.Builder(this).apply {
//                    setTitle("Error while parsing directory")
//                    setMessage(
//                        "There were some errors while parsing the directory. Please see the log below. Make sure that the file structure is " +
//                                "as described in the README of the project and then tap RESELECT"
//                    )
//                    setCancelable(false)
//                    setPositiveButton("RESELECT") { dialog, which ->
//                        dialog.dismiss()
//                        launchChooseDirectoryIntent()
//                    }
//                    setNegativeButton("CANCEL") { dialog, which ->
//                        dialog.dismiss()
//                        finish()
//                    }
//                    create()
//                }
//                alertDialog.show()
//            }
//        }


    // Get the image as a Bitmap from given Uri and fix the rotation using the Exif interface
    // Source -> https://stackoverflow.com/questions/14066038/why-does-an-image-captured-using-camera-intent-gets-rotated-on-some-devices-on-a
    private fun getFixedBitmap(imageFileUri: Uri): Bitmap {
        var imageBitmap = BitmapUtils.getBitmapFromUri(contentResolver, imageFileUri)
        val exifInterface = ExifInterface(contentResolver.openInputStream(imageFileUri)!!)
        imageBitmap =
            when (exifInterface.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> BitmapUtils.rotateBitmap(
                    imageBitmap,
                    90f
                )
                ExifInterface.ORIENTATION_ROTATE_180 -> BitmapUtils.rotateBitmap(
                    imageBitmap,
                    180f
                )
                ExifInterface.ORIENTATION_ROTATE_270 -> BitmapUtils.rotateBitmap(
                    imageBitmap,
                    270f
                )
                else -> imageBitmap
            }
        return imageBitmap
    }


    // ---------------------------------------------- //


    private val fileReaderCallback = object : FileReader.ProcessCallback {
        override fun onProcessCompleted(
            data: ArrayList<Pair<String, FloatArray>>,
            numImagesWithNoFaces: Int
        ) {
            frameAnalyser.faceList = data
            Logger.log("Images parsed. Found $numImagesWithNoFaces images with no faces.")
        }
    }


//    private fun saveSerializedImageData(data: ArrayList<Pair<String, FloatArray>>) {
//        val serializedDataFile = File(filesDir, SERIALIZED_DATA_FILENAME)
//        ObjectOutputStream(FileOutputStream(serializedDataFile)).apply {
//            writeObject(data)
//            flush()
//            close()
//        }
//        sharedPreferences.edit().putBoolean(SHARED_PREF_IS_DATA_STORED_KEY, true).apply()
//    }
//

    override fun onDestroy() {
        super.onDestroy()
        noFaceFoundYet = true
//        startTime =
//        framesToCheck =
//        successFramesRequired =
    }
}
