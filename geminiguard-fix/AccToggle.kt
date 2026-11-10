package com.gemini.guard

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/** Toggle only Gemini Guard's own accessibility service. */
object AccToggle {
    private fun ownComponent(c: Context) = ComponentName(c, MiAiWatcher::class.java)

    private fun entries(raw: String?): List<String> =
        (raw ?: "").split(":").map { it.trim() }.filter { it.isNotEmpty() }

    private fun isOurs(c: Context, entry: String): Boolean =
        ComponentName.unflattenFromString(entry) == ownComponent(c)

    fun isOn(c: Context): Boolean = entries(
        Settings.Secure.getString(c.contentResolver, Restorer.ACC_KEY)
    ).any { isOurs(c, it) }

    /** Preserve other services, update both settings and verify read-back. */
    fun setEnabled(c: Context, enable: Boolean): Boolean {
        if (!Restorer.hasPermission(c)) return false
        val r = c.contentResolver
        return try {
            val before = Settings.Secure.getString(r, Restorer.ACC_KEY)
            val current = entries(before)
            val wanted = current.filterNot { isOurs(c, it) }.toMutableList()
            if (enable) wanted.add(ownComponent(c).flattenToString())
            val newValue = wanted.distinct().joinToString(":")
            if (!Settings.Secure.putString(r, Restorer.ACC_KEY, newValue)) return false
            val global = if (wanted.isEmpty()) 0 else 1
            val globalOk = Settings.Secure.putInt(r, "accessibility_enabled", global)
            val verified = globalOk && isOn(c) == enable &&
                Settings.Secure.getInt(r, "accessibility_enabled", -1) == global
            if (!verified) {
                Settings.Secure.putString(r, Restorer.ACC_KEY, before ?: "")
                Settings.Secure.putInt(r, "accessibility_enabled", if (current.isEmpty()) 0 else 1)
            }
            verified
        } catch (_: Throwable) { false }
    }

    /** Save user's OFF decision before any change to avoid races with observers. */
    fun toggle(c: Context): String {
        if (!Restorer.hasPermission(c))
            return L.tr("Chưa cấp quyền WRITE_SECURE_SETTINGS (cấp bằng ADB trước).")
        val pr = Restorer.prefs(c)
        val nowOn = isOn(c)
        val desired = !nowOn
        val previousOff = pr.getBoolean("acc_tile_off", false)
        if (!pr.edit().putBoolean("acc_tile_off", !desired).commit())
            return "Không lưu được lựa chọn Trợ năng."
        if (!setEnabled(c, desired)) {
            pr.edit().putBoolean("acc_tile_off", previousOff).commit()
            return "Hệ thống chưa xác nhận thay đổi Trợ năng; trạng thái cũ được giữ nguyên."
        }
        return if (desired) L.tr("Đã bật Trợ năng Gemini Guard")
               else "Đã tắt Trợ năng Gemini Guard; nút nguồn/Home vẫn có thể gọi Gemini."
    }
}
