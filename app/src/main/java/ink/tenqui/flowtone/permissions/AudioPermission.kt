package ink.tenqui.flowtone.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

fun currentAudioPermission(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
}

fun hasAudioPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        currentAudioPermission()
    ) == PackageManager.PERMISSION_GRANTED
}

fun shouldOpenAudioPermissionSettings(
    activity: Activity?,
    hasRequestedPermissionBefore: Boolean
): Boolean {
    return activity != null &&
        hasRequestedPermissionBefore &&
        !ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            currentAudioPermission()
        )
}

fun openAppPermissionSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
    )
}
