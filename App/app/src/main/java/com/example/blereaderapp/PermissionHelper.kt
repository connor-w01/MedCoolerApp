package com.example.blereaderapp

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build  //
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionHelper {

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+ (API 31+)
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        // Android 10 and below (API 29-)
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    fun checkPermissions(activity: Activity): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestPermissions(activity: Activity, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, permissions, requestCode)
    }
}
