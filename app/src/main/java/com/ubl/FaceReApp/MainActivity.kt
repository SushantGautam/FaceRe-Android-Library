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
                StudentBitmapFileName = saveBitmap(tempBitmap, applicationContext)!!
            )
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == 514) {
            val bundle: Bundle? = data?.extras
            val status = bundle?.getString("status")
            if (status == "success") {
                val bitmap = BitmapFactory.decodeStream(
                    this.openFileInput("myFaceReImage")
                )
                //access locally saved last analyzed frame if you need
                Log.d("BITMAP VALUE", bitmap.toString())
                successCallbackFunction()
            } else errorCallbackFunction()
        }
    }

    private fun successCallbackFunction() {
        //TODO: write what you want to do after success
        var intent = Intent(this, NewActivity::class.java)
        startActivity(intent)
        return
    }

    private fun errorCallbackFunction() {
        //TODO: Handle error
        return
    }

}
