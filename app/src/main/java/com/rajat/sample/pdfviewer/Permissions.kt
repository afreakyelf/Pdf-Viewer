package com.rajat.sample.pdfviewer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import androidx.core.content.ContextCompat
import com.vmadalin.easypermissions.EasyPermissions

fun Context.isPermissionGranted(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

@SuppressLint("MissingPermission")
fun View.isPermissionGranted(permission: String): Boolean {
    return context.isPermissionGranted(permission)
}


fun Activity.hasPermission(permission: String) = EasyPermissions.hasPermissions(
    this,
    permission
)

fun Activity.requestPermission(permission: String, requestCode: Int, rationalText: String) {
    EasyPermissions.requestPermissions(
        this,
        rationalText,
        requestCode,
        permission
    )
}

fun Activity.enableRequestPermission(permission: String): Boolean {
    return EasyPermissions.somePermissionPermanentlyDenied(this, listOf(permission))
}