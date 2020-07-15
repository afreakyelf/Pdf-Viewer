package com.rajat.pdfviewer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        open_pdf.setOnClickListener {
            startActivity(
                PdfViewerActivity.buildIntent(
                    this,
                    "https://www.dbs.com.sg/ibanking/pdf/right_to_cancel.pdf",
                    false,
                    "title",
                    ""
                )
            )
        }

    }

}
