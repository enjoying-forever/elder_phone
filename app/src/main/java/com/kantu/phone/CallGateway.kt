package com.kantu.phone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Extension boundary: a future calling provider must still require explicit UI confirmation. */
interface CallGateway {
    /** Returns a user-facing error, or null when the system accepted the call intent. */
    fun call(number: String): String?
}

class SystemCallGateway(private val context: Context) : CallGateway {
    override fun call(number: String): String? {
        // Contacts/history are external input. Never launch service codes or arbitrary URI schemes.
        val normalized = PhonePolicy.dialableNumber(number) ?: return "这个号码不能拨打，请让家人检查"
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED) return "请让家人允许拨打电话"
        val airplane = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        }.getOrDefault(false)
        if (airplane) return "手机开了飞行模式，请让家人关闭后再试"
        return try {
            context.startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", normalized, null)))
            null
        } catch (_: SecurityException) {
            "暂时不能拨打，请让家人检查电话权限"
        } catch (_: android.content.ActivityNotFoundException) {
            "这台设备没有可用的电话应用"
        } catch (_: Exception) {
            "电话没有拨出去，请让家人检查手机"
        }
    }
}
