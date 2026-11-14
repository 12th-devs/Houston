package com.astroboii47.commander

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakePrefs : SharedPreferences {
    private val data = HashMap<String, String>()
    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = HashMap<String, String?>()
        private var clearAll = false
        override fun putBoolean(k: String, v: Boolean) = apply { pending[k] = v.toString() }
        override fun putFloat(k: String, v: Float) = apply { pending[k] = v.toString() }
        override fun putInt(k: String, v: Int) = apply { pending[k] = v.toString() }
        override fun putLong(k: String, v: Long) = apply { pending[k] = v.toString() }
        override fun putString(k: String, v: String?) = apply { pending[k] = v }
        override fun putStringSet(k: String, v: MutableSet<String>?) = apply { pending[k] = v?.joinToString(",") }
        override fun remove(k: String) = apply { pending[k] = null }
        override fun clear() = apply { clearAll = true }
        override fun commit(): Boolean {
            if (clearAll) { data.clear(); clearAll = false }
            pending.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
            pending.clear()
            return true
        }
        override fun apply() {
            commit()
        }
    }

    override fun contains(key: String) = data.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor()
    override fun getAll(): Map<String, *> = data.toMap()
    override fun getBoolean(key: String, defValue: Boolean) = data[key]?.toBooleanStrictOrNull() ?: defValue
    override fun getFloat(key: String, defValue: Float) = data[key]?.toFloatOrNull() ?: defValue
    override fun getInt(key: String, defValue: Int) = data[key]?.toIntOrNull() ?: defValue
    override fun getLong(key: String, defValue: Long) = data[key]?.toLongOrNull() ?: defValue
    override fun getString(key: String, defValue: String?) = data[key] ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?) =
        data[key]?.split(",")?.toMutableSet() ?: defValues
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}

/** Minimal Context stub: only SharedPreferences + package name work; everything else throws. */
private class FakeContext(private val prefs: SharedPreferences) : Context() {
    override fun getAssets(): android.content.res.AssetManager = throw UnsupportedOperationException()
    override fun getResources(): android.content.res.Resources = throw UnsupportedOperationException()
    override fun getPackageManager(): android.content.pm.PackageManager = throw UnsupportedOperationException()
    override fun getContentResolver(): android.content.ContentResolver = throw UnsupportedOperationException()
    override fun getMainLooper(): android.os.Looper = throw UnsupportedOperationException()
    override fun getApplicationContext(): Context = this
    override fun setTheme(resid: Int) = Unit
    override fun getTheme(): android.content.res.Resources.Theme = throw UnsupportedOperationException()
    override fun getClassLoader(): ClassLoader = javaClass.classLoader!!
    override fun getPackageName(): String = "com.astroboii47.commander.test"
    override fun getApplicationInfo(): android.content.pm.ApplicationInfo = throw UnsupportedOperationException()
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = prefs
    override fun getSystemService(name: String): Any = throw UnsupportedOperationException()
    override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
        android.content.pm.PackageManager.PERMISSION_DENIED
    override fun checkCallingPermission(permission: String): Int =
        android.content.pm.PackageManager.PERMISSION_DENIED
    override fun checkCallingOrSelfPermission(permission: String): Int =
        android.content.pm.PackageManager.PERMISSION_DENIED
    override fun checkUriPermission(
        uri: android.net.Uri?, pid: Int, uid: Int, modeFlags: Int,
    ): Int = android.content.pm.PackageManager.PERMISSION_DENIED
    override fun checkUriPermission(
        uri: android.net.Uri?, readPermission: String?, writePermission: String?, pid: Int, uid: Int, modeFlags: Int,
    ): Int = android.content.pm.PackageManager.PERMISSION_DENIED
    override fun checkCallingUriPermission(uri: android.net.Uri, modeFlags: Int): Int =
        android.content.pm.PackageManager.PERMISSION_DENIED
    override fun checkCallingOrSelfUriPermission(uri: android.net.Uri, modeFlags: Int): Int =
        android.content.pm.PackageManager.PERMISSION_DENIED
    override fun enforcePermission(permission: String, pid: Int, uid: Int, message: String?) = throw SecurityException(message)
    override fun enforceCallingPermission(permission: String, message: String?) = throw SecurityException(message)
    override fun enforceCallingOrSelfPermission(permission: String, message: String?) = throw SecurityException(message)
    override fun enforceUriPermission(
        uri: android.net.Uri?, pid: Int, uid: Int, modeFlags: Int, message: String?,
    ) = throw SecurityException(message)
    override fun enforceUriPermission(
        uri: android.net.Uri?, readPermission: String?, writePermission: String?, pid: Int, uid: Int, modeFlags: Int, message: String?,
    ) = throw SecurityException(message)
    override fun enforceCallingUriPermission(uri: android.net.Uri, modeFlags: Int, message: String?) =
        throw SecurityException(message)
    override fun enforceCallingOrSelfUriPermission(uri: android.net.Uri, modeFlags: Int, message: String?) =
        throw SecurityException(message)
    override fun grantUriPermission(toPackage: String, uri: android.net.Uri, modeFlags: Int) = Unit
    override fun revokeUriPermission(uri: android.net.Uri, modeFlags: Int) = Unit
    override fun revokeUriPermission(toPackage: String, uri: android.net.Uri, modeFlags: Int) = Unit
    override fun createPackageContext(packageName: String, flags: Int): Context = throw UnsupportedOperationException()
    override fun createConfigurationContext(overrideConfiguration: android.content.res.Configuration): Context =
        throw UnsupportedOperationException()
    override fun createDisplayContext(display: android.view.Display): Context = throw UnsupportedOperationException()
    override fun createDeviceProtectedStorageContext(): Context = throw UnsupportedOperationException()
    override fun moveSharedPreferencesFrom(sourceContext: Context, name: String): Boolean =
        throw UnsupportedOperationException()
    override fun deleteSharedPreferences(name: String): Boolean = throw UnsupportedOperationException()
    override fun openFileInput(name: String): java.io.FileInputStream = throw UnsupportedOperationException()
    override fun openFileOutput(name: String, mode: Int): java.io.FileOutputStream = throw UnsupportedOperationException()
    override fun deleteFile(name: String): Boolean = throw UnsupportedOperationException()
    override fun fileList(): Array<String> = throw UnsupportedOperationException()
    override fun getFilesDir(): java.io.File = throw UnsupportedOperationException()
    override fun getNoBackupFilesDir(): java.io.File = throw UnsupportedOperationException()
    override fun getExternalFilesDir(type: String?): java.io.File = throw UnsupportedOperationException()
    override fun getExternalFilesDirs(type: String?): Array<java.io.File> = throw UnsupportedOperationException()
    override fun getObbDir(): java.io.File = throw UnsupportedOperationException()
    override fun getObbDirs(): Array<java.io.File> = throw UnsupportedOperationException()
    override fun getCacheDir(): java.io.File = throw UnsupportedOperationException()
    override fun getCodeCacheDir(): java.io.File = throw UnsupportedOperationException()
    override fun getExternalCacheDir(): java.io.File = throw UnsupportedOperationException()
    override fun getExternalCacheDirs(): Array<java.io.File> = throw UnsupportedOperationException()
    override fun getExternalMediaDirs(): Array<java.io.File> = throw UnsupportedOperationException()
    override fun getDir(name: String, mode: Int): java.io.File = throw UnsupportedOperationException()
    override fun getDataDir(): java.io.File = throw UnsupportedOperationException()
    override fun openOrCreateDatabase(
        name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?,
    ): android.database.sqlite.SQLiteDatabase = throw UnsupportedOperationException()
    override fun openOrCreateDatabase(
        name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?,
        errorHandler: android.database.DatabaseErrorHandler?,
    ): android.database.sqlite.SQLiteDatabase = throw UnsupportedOperationException()
    override fun deleteDatabase(name: String): Boolean = throw UnsupportedOperationException()
    override fun getDatabasePath(name: String): java.io.File = throw UnsupportedOperationException()
    override fun databaseList(): Array<String> = throw UnsupportedOperationException()
    @Deprecated("Deprecated in Java")
    override fun getWallpaper(): android.graphics.drawable.Drawable = throw UnsupportedOperationException()
    @Deprecated("Deprecated in Java")
    override fun peekWallpaper(): android.graphics.drawable.Drawable = throw UnsupportedOperationException()
    @Deprecated("Deprecated in Java")
    override fun getWallpaperDesiredMinimumWidth(): Int = throw UnsupportedOperationException()
    @Deprecated("Deprecated in Java")
    override fun getWallpaperDesiredMinimumHeight(): Int = throw UnsupportedOperationException()
    @Deprecated("Deprecated in Java")
    override fun setWallpaper(bitmap: android.graphics.Bitmap) = throw UnsupportedOperationException()
    @Deprecated("Deprecated in Java")
    override fun setWallpaper(data: java.io.InputStream) = throw UnsupportedOperationException()
    @Deprecated("Deprecated in Java")
    override fun clearWallpaper() = throw UnsupportedOperationException()
    override fun checkSelfPermission(permission: String): Int =
        android.content.pm.PackageManager.PERMISSION_DENIED
    override fun createContextForSplit(splitName: String): Context = throw UnsupportedOperationException()
    override fun getFileStreamPath(name: String): java.io.File = throw UnsupportedOperationException()
    override fun getPackageCodePath(): String = throw UnsupportedOperationException()
    override fun getPackageResourcePath(): String = throw UnsupportedOperationException()
    override fun getSystemServiceName(serviceClass: Class<*>): String? = throw UnsupportedOperationException()
    override fun isDeviceProtectedStorage(): Boolean = throw UnsupportedOperationException()
    override fun moveDatabaseFrom(sourceContext: Context, name: String): Boolean = throw UnsupportedOperationException()
    override fun bindService(service: android.content.Intent, conn: android.content.ServiceConnection, flags: Int): Boolean =
        throw UnsupportedOperationException()
    override fun registerReceiver(receiver: android.content.BroadcastReceiver?, filter: android.content.IntentFilter): android.content.Intent? =
        throw UnsupportedOperationException()
    override fun registerReceiver(receiver: android.content.BroadcastReceiver?, filter: android.content.IntentFilter, flags: Int): android.content.Intent? =
        throw UnsupportedOperationException()
    override fun registerReceiver(
        receiver: android.content.BroadcastReceiver, filter: android.content.IntentFilter, broadcastPermission: String?,
        scheduler: android.os.Handler?,
    ): android.content.Intent? = throw UnsupportedOperationException()
    override fun registerReceiver(
        receiver: android.content.BroadcastReceiver, filter: android.content.IntentFilter, broadcastPermission: String?,
        scheduler: android.os.Handler?, flags: Int,
    ): android.content.Intent? = throw UnsupportedOperationException()
    override fun removeStickyBroadcast(intent: android.content.Intent) = throw UnsupportedOperationException()
    override fun removeStickyBroadcastAsUser(intent: android.content.Intent, user: android.os.UserHandle) =
        throw UnsupportedOperationException()
    override fun sendBroadcast(intent: android.content.Intent) = throw UnsupportedOperationException()
    override fun sendBroadcast(intent: android.content.Intent, receiverPermission: String?) =
        throw UnsupportedOperationException()
    override fun sendBroadcastAsUser(intent: android.content.Intent, user: android.os.UserHandle) =
        throw UnsupportedOperationException()
    override fun sendBroadcastAsUser(intent: android.content.Intent, user: android.os.UserHandle, receiverPermission: String?) =
        throw UnsupportedOperationException()
    override fun sendOrderedBroadcast(intent: android.content.Intent, receiverPermission: String?) =
        throw UnsupportedOperationException()
    override fun sendOrderedBroadcast(
        intent: android.content.Intent, receiverPermission: String?, resultReceiver: android.content.BroadcastReceiver?,
        scheduler: android.os.Handler?, initialCode: Int, initialData: String?, initialExtras: android.os.Bundle?,
    ) = throw UnsupportedOperationException()
    override fun sendOrderedBroadcastAsUser(
        intent: android.content.Intent, user: android.os.UserHandle, receiverPermission: String?,
        resultReceiver: android.content.BroadcastReceiver, scheduler: android.os.Handler?, initialCode: Int,
        initialData: String?, initialExtras: android.os.Bundle?,
    ) = throw UnsupportedOperationException()
    override fun sendStickyBroadcast(intent: android.content.Intent) = throw UnsupportedOperationException()
    override fun sendStickyBroadcastAsUser(intent: android.content.Intent, user: android.os.UserHandle) =
        throw UnsupportedOperationException()
    override fun sendStickyOrderedBroadcast(
        intent: android.content.Intent, resultReceiver: android.content.BroadcastReceiver,
        scheduler: android.os.Handler?, initialCode: Int, initialData: String?, initialExtras: android.os.Bundle?,
    ) = throw UnsupportedOperationException()
    override fun sendStickyOrderedBroadcastAsUser(
        intent: android.content.Intent, user: android.os.UserHandle, resultReceiver: android.content.BroadcastReceiver,
        scheduler: android.os.Handler?, initialCode: Int, initialData: String?, initialExtras: android.os.Bundle?,
    ) = throw UnsupportedOperationException()
    override fun startActivities(intents: Array<out android.content.Intent>) = throw UnsupportedOperationException()
    override fun startActivities(intents: Array<out android.content.Intent>, options: android.os.Bundle) =
        throw UnsupportedOperationException()
    override fun startActivity(intent: android.content.Intent) = throw UnsupportedOperationException()
    override fun startActivity(intent: android.content.Intent, options: android.os.Bundle?) =
        throw UnsupportedOperationException()
    override fun startForegroundService(service: android.content.Intent): android.content.ComponentName? =
        throw UnsupportedOperationException()
    override fun startInstrumentation(className: android.content.ComponentName, profileFile: String?, arguments: android.os.Bundle?): Boolean =
        throw UnsupportedOperationException()
    override fun startIntentSender(
        intent: android.content.IntentSender, fillInIntent: android.content.Intent?, flagsMask: Int, flagsValues: Int,
        extraFlags: Int,
    ) = throw UnsupportedOperationException()
    override fun startIntentSender(
        intent: android.content.IntentSender, fillInIntent: android.content.Intent?, flagsMask: Int, flagsValues: Int,
        extraFlags: Int, options: android.os.Bundle?,
    ) = throw UnsupportedOperationException()
    override fun startService(service: android.content.Intent): android.content.ComponentName? =
        throw UnsupportedOperationException()
    override fun stopService(service: android.content.Intent): Boolean = throw UnsupportedOperationException()
    override fun unbindService(conn: android.content.ServiceConnection) = throw UnsupportedOperationException()
    override fun unregisterReceiver(receiver: android.content.BroadcastReceiver) = throw UnsupportedOperationException()
}

class HubRemovalTest {
    @Before
    fun resetRegistry() {
        ActionRegistry.resetForTests()
        ActionRegistry.registerDefaults()
    }

    @Test
    fun contactModel_hasNoHubNotificationKey() {
        val fields = ContactResult::class.java.declaredFields.map { it.name }
        assertTrue(fields.none { it.contains("hub", true) })
    }

    @Test
    fun notificationSearch_returnsEmpty() {
        val context = FakeContext(FakePrefs())
        assertTrue(NotificationSearchSettings.live(context).isEmpty())
    }

    @Test
    fun systemActions_haveNoHubActions() {
        val ids = ActionRegistry.all().map { it.id }
        assertTrue(ids.none { it.contains("hub", true) })
        assertTrue(ids.none { it == "comms.recent" })
        // Core non-Hub actions still registered.
        listOf("comms.call", "comms.message.compose", "settings.open", "web.search").forEach {
            assertTrue("missing $it", it in ids)
        }
    }

    @Test
    fun searchApps_keepsWebFallbackSignature() {
        val overload = CommandEngine::class.java.declaredMethods.first { it.name == "searchApps" }
        val params = overload.parameters
        assertTrue(params.any { it.name == "includeWebFallback" || it.type == Boolean::class.java })
        assertEquals(3, params.size)
    }

    @Test
    fun messagingRevision_isStateFlow() {
        val flow: StateFlow<Int> = CommanderMessagingContacts.revisionFlow
        assertTrue(flow.value >= 0)
    }

    @Test
    fun commandEngine_hasNoHubReplySurface() {
        val names = CommandEngine::class.java.declaredMethods.map { it.name }
        assertTrue(names.none { it.contains("hub", true) || it.contains("reply", true) })
    }
}
