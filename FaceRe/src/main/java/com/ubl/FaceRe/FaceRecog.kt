package com.ubl.FaceRe

import android.app.Application

//package com.ubt.FaceRe

//
//import android.app.ProgressDialog
//import android.graphics.Bitmap
//import android.graphics.BitmapFactory
//import android.os.Bundle
//import android.os.PersistableBundle
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import com.google.android.gms.tasks.OnSuccessListener
//import com.google.mlkit.vision.common.InputImage
//import com.google.mlkit.vision.common.InputImage.IMAGE_FORMAT_NV21
//import com.google.mlkit.vision.face.Face
//import com.google.mlkit.vision.face.FaceDetection.getClient
//import com.google.mlkit.vision.face.FaceDetectorOptions
//import java.io.InputStream
//import java.net.HttpURLConnection
//import java.net.URL
//
//class com.ubt.FaceRe.FaceRecog : AppCompatActivity(){
//
//    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
//        super.onCreate(savedInstanceState, persistentState)
//    }
//
//    fun LoadImageToCompare(ulrn: URL, FaceName: String, imageLabelPairs: ArrayList<Pair<Bitmap, String>>, imageData: ArrayList<Pair<String, FloatArray>>) {
//
//        val s = Thread {
//            try {
//                val con = ulrn.openConnection() as HttpURLConnection
//                val iss: InputStream = con.getInputStream()
//                val bmp = BitmapFactory.decodeStream(iss)
//                if (null != bmp) {
//                    imageLabelPairs.add(
//                        Pair(bmp, FaceName)
//                    )
//                    print("Success")
//                }
//
//            } catch (e: java.lang.Exception) {
//                print("Error")
//                e.printStackTrace()
//            } finally {
//                scanImage(0, imageLabelPairs, imageData)
//
//            }
//        }
//
//        s.start()
//
//    }
//
//    private fun scanImage(counter: Int, imageLabelPairs: ArrayList<Pair<Bitmap, String>>, imageData: ArrayList<Pair<String, FloatArray>>) {
//        val sample = imageLabelPairs[counter]
//
//        val inputImage = InputImage.fromByteArray(
//            bitmapToNV21(sample.first),
//            sample.first.width,
//            sample.first.height,
//            0,
//            IMAGE_FORMAT_NV21
//        )
//        val successListener = OnSuccessListener<List<Face?>> { faces ->
//            if (faces.isNotEmpty()) {
//                imageData.add(
//                    Pair(
//                        sample.second,
//                        if (cropWithBBoxes) {
//                            model!!.getFaceEmbedding(sample.first, faces[0]!!.boundingBox, false)
//                        } else {
//                            model!!.getFaceEmbeddingWithoutBBox(sample.first, false)
//                        }
//                    )
//                )
//            }
//            if (counter + 1 == imageLabelPairs.size) {
////                Toast.makeText(
////                    this@MainActivity,
////                    "Processing completed. ${imageData.size}",
////                    Toast.LENGTH_LONG
////                ).show()
//                progressDialog?.dismiss()
//                frameAnalyser.faceList = imageData
//            } else {
//                progressDialog?.setMessage("Processed ${counter + 1} images")
//                scanImage(counter + 1, imageLabelPairs)
//            }
//        }
//        detector.process(inputImage).addOnSuccessListener(successListener)
//    }
//
//}

class FaceRecog {

    fun init(application: Application?) {
    }

}

