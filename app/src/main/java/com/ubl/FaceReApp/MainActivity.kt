package com.ubl.FaceReApp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatCheckBox
import com.google.gson.Gson
import com.ubl.FaceRe.FaceReActivity
import java.io.ByteArrayOutputStream

fun BitMapToString(bitmap: Bitmap): String {
    val baos = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
    val b: ByteArray = baos.toByteArray()
    return Base64.encodeToString(b, Base64.DEFAULT)
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        val button: Button = findViewById<View>(R.id.button) as Button




        button.setOnClickListener {
            val intent = Intent(this, FaceReActivity::class.java)
            val faceList = ArrayList<Pair<String, String>>()
            val assetManager = applicationContext.assets
            for (file in assetManager.list("")!!) {
                if (file.endsWith(".jpg")) {
//                items.add(file)
                    faceList.add(
                        Pair(
                            file.toString(),
                            BitMapToString(BitmapFactory.decodeStream(assets.open(file)))
                        )
                    )
                }
            }


            val sharedPrefs = getSharedPreferences(
                this.packageName, Context.MODE_PRIVATE
            )
            val editor: SharedPreferences.Editor = sharedPrefs.edit()
            val gson = Gson()
            val json = gson.toJson(faceList)
            editor.putString("facereData", json).apply()
            val checkbx = findViewById<View>(R.id.checkbx) as AppCompatCheckBox
            editor.putBoolean("WantFrontCamera", checkbx.isChecked).apply()
            editor.putBoolean("WantLogsDisplayed", true).apply()
            editor.putInt("framesToCheck", 10).apply()
            editor.putInt("successFramesRequired", 5).apply()
            editor.putInt("timeOutTime", 8).apply()
            editor.putInt("scoreThreshold", 50).apply()
            editor.putString("StudentName", "Student Name").apply()
            editor.putString("EPSRoll", "EPS Rpoll").apply()

            launchFaceRe.launch(intent)

        }

    }

    val launchFaceRe =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent = result.data!!
                val bundle: Bundle? = data.extras
                val score = bundle?.getInt("score")
                val maxScore = bundle?.getInt("maxScore")
                val bitmap = BitmapFactory.decodeStream(
                    this.openFileInput("myFaceReImage")
                )

                Toast.makeText(
                    this@MainActivity,
                    "FaceReComplete with score:" + score.toString(),
                    Toast.LENGTH_SHORT
                ).show()

            }
        }


}