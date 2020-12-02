package com.ubl.FaceReApp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ubl.FaceRe.StartFaceReActivity

class MainActivity : AppCompatActivity() {

    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        button = findViewById(R.id.button)
        button.setOnClickListener {
            StartFaceReActivity(
                callerClass = this,
                StudentName = "Student Name",
                StudentID = "Student ID",
                StudentBitmap = "String Change to Bitmap"
            )
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == 514) {
            val bundle: Bundle? = data?.getExtras()
            val status = bundle?.getString("status")
            if (status == "success") {
                Toast.makeText(this, "Success", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun successCallbackFunction() {
        return
    }

    private fun errorCallbackFunction() {
        return
    }

}
