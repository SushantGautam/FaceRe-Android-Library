package com.ubl.FaceReApp

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.ubl.FaceRe.saveBitmap
import com.ubl.FaceRe.startFaceReActivity


class MainActivity : AppCompatActivity() {

    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        button = findViewById(R.id.button)
        button.setOnClickListener {

            val tempBitmap = BitmapFactory.decodeResource(resources, R.drawable.bidhan)

            startFaceReActivity(
                    callerClass = this,
                    StudentName = "Student Name",
                    StudentID = "Student ID",
                    StudentBitmapFileName = saveBitmap(tempBitmap, applicationContext)!!,
                    camera = "front"
            )
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == 514) {
            val bundle: Bundle? = data?.extras
            val status = bundle?.getString("status")
            if (status == "success") {
                val score = bundle.getDouble("score")
                val maxScore = bundle.getDouble("maxScore")
                val bitmap = BitmapFactory.decodeStream(
                        this.openFileInput("myFaceReImage")
                )

                //"bitmap variable contains the image obtained after recognition"
                //"score variable contains the average score obtained after recognition"
                //"maxScore variable contains the max score obtained after recognition"
                Log.d("New Bitmap Received", bitmap.toString())
                score.let { Log.d("Score", it.toString()) }
                maxScore.let { Log.d("maxScore", it.toString()) }

                successCallbackFunction(score, maxScore)
            } else errorCallbackFunction()
        }
    }

    private fun successCallbackFunction(score: Double, maxScore: Double) {
        //TODO: write what you want to do after success
        val intent = Intent(this, NewActivity::class.java)
        intent.putExtra("score", score)
        intent.putExtra("maxScore", maxScore)
        startActivity(intent)
        return
    }

    private fun errorCallbackFunction() {
        //TODO: Handle error
        return
    }

}
