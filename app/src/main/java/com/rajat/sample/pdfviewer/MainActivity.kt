package com.rajat.sample.pdfviewer

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rajat.pdfviewer.PdfViewerActivity
import com.rajat.pdfviewer.databinding.ActivityMainBinding
import com.vmadalin.easypermissions.EasyPermissions

class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {

    private lateinit var binding: ActivityMainBinding

    private var download_file_url =
        "https://github.com/afreakyelf/afreakyelf/raw/main/Log4_Shell_Mid_Term_final.pdf"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.openPdf.setOnClickListener {
            requestFilesPermission()
        }
    }

    private fun launchPdf() {
        startActivity(
            PdfViewerActivity.launchPdfFromUrl(
                context = this, pdfUrl = download_file_url,
                pdfTitle = "Title", directoryName = "dir", enableDownload = true
            )
        )
    }

    private fun requestFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (hasPermission(READ_MEDIA_IMAGES)) {
                launchPdf()
            } else if (enableRequestPermission(READ_MEDIA_IMAGES)) {
                requestPermission(READ_MEDIA_IMAGES, 1, "")
            }
        } else {
            if (this.hasPermission(READ_EXTERNAL_STORAGE)) {
                launchPdf()
            } else if (enableRequestPermission(READ_EXTERNAL_STORAGE)) {
                requestPermission(READ_EXTERNAL_STORAGE, 1, "")
            }
        }
    }

    override fun onPermissionsDenied(requestCode: Int, perms: List<String>) {
    }

    override fun onPermissionsGranted(requestCode: Int, perms: List<String>) {
        if (requestCode == 1) {
            launchPdf()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            launchPdf()
        }
    }
}
