package com.ubl.FaceReApp

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_new.*

class NewActivity : AppCompatActivity() {
    // this activity is just demo to show and visualize the captured data
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new)
        val scoret = intent.getBundleExtra("score")
        val maxScoret = intent.getBundleExtra("maxScore")
        val bitmap = BitmapFactory.decodeStream(
                this.openFileInput("myFaceReImage")
        )
        imageView.setImageBitmap(bitmap)
        score.text = String.format("%.2f", scoret)
        maxScore.text = String.format("%.2f", maxScoret)
    }

}