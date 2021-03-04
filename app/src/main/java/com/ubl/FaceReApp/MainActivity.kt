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
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Start the FaceRe Activity After Start Button is pressed
        button = findViewById(R.id.button)
        button.setOnClickListener {

            // You need to pass various options as specified here
            // Student Name, Student ID and Bitmap of Student Face
            // onActivityResult is called after successfully completion of the Face Recognition

            val tempBitmap = BitmapFactory.decodeResource(resources, R.drawable.bidhan)

            startFaceReActivity(
                callerClass = this,
                StudentName = "Student Name",
                StudentID = "Student ID",
                StudentBitmapFileName = saveBitmap(tempBitmap, applicationContext)!!,
                camera = if (CameraSwitch.isChecked) "front" else "back"
            )
        }


//        //temp delete me
//
//        val tempBitmap = BitmapFactory.decodeResource(resources, R.drawable.sushantylatest)
//
//        startFaceReActivity(
//            callerClass = this,
//            StudentName = "Student Name",
//            StudentID = "Student ID",
//            StudentBitmapFileName = saveBitmap(tempBitmap, applicationContext)!!,
//            camera = if (CameraSwitch.isChecked) "front" else "back"
//        )
//        //temp delete me
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        //  called after successfully completion of the Face Recognition

        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == 514) {
            val bundle: Bundle? = data?.extras
            val status = bundle?.getString("status")
            if (status == "success") {

                //"score variable contains the average score obtained after recognition"
                //"maxScore variable contains the max score obtained after recognition"
                //"bitmap variable contains the image obtained after recognition"

                val score = bundle.getDouble("score")
                val maxScore = bundle.getDouble("maxScore")

                // image is already saved on the device after recognition with name: "myFaceReImage" . .  get get it back
                val bitmap = BitmapFactory.decodeStream(
                    this.openFileInput("myFaceReImage")
                )


                Log.d("New Bitmap Received", bitmap.toString())
                score.let { Log.d("Score", it.toString()) }
                maxScore.let { Log.d("maxScore", it.toString()) }

                //now you need to handle the data obtained.
                // Save the data through REST or something.
                // For a sample, I have shown the data in  NewActivity through successCallbackFunction function
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
