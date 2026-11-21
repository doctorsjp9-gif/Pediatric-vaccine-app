package com.example.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.example.BuildConfig
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class UpdateSourceType(val code: String) {
    DIRECT_APK_OR_CLOUD("DIRECT_APK"),
    GITHUB_RELEASES("GITHUB"),
    MANIFEST_JSON("MANIFEST");

    companion object {
        fun fromCode(code: String?): UpdateSourceType {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: DIRECT_APK_OR_CLOUD
        }
    }
}

data class AppUpdateState(
    val installedPackageVersion: String = BuildConfig.VERSION_NAME,
    val installedVersionCode: Long = BuildConfig.VERSION_CODE.toLong(),
    val currentModuleVersion: String = BuildConfig.VERSION_NAME,
    val latestAvailableVersion: String = BuildConfig.VERSION_NAME,
    val latestBuildNumber: Int = BuildConfig.VERSION_CODE,
    val isUpdateAvailable: Boolean = false,
    val isChecking: Boolean = false,
    val isPublishingRelease: Boolean = false,
    val updateStatusMessage: String = "",
    val lastError: String? = null,
    val lastCheckedTimestamp: Long = 0L,
    val lastBackupTimestamp: Long = 0L,
    val autoCheckOnStartup: Boolean = true,
    val autoBackupBeforeUpdate: Boolean = true,
    val sourceType: UpdateSourceType = UpdateSourceType.DIRECT_APK_OR_CLOUD,
    val apkDownloadUrl: String = "",
    val githubRepo: String = "",
    val manifestJsonUrl: String = "",
    val releaseDate: String = "",
    val releaseNotesText: String = "",
    val apkFileName: String = "",
    val apkFileSizeMb: String = ""
) {
    val hasDownloadableLink: Boolean
        get() = apkDownloadUrl.isNotBlank()
}

object AppUpdateManager {

    private const val TAG = "AppUpdateManager"
    private const val PREFS_NAME = "clinic_app_update_prefs_v2"
    private const val KEY_LAST_CHECKED_MS = "last_checked_ms"
    private const val KEY_LAST_BACKUP_MS = "last_backup_ms"
    private const val KEY_AUTO_CHECK = "auto_check_startup"
    private const val KEY_AUTO_BACKUP = "auto_backup_before_update"
    private const val KEY_SOURCE_TYPE = "update_source_type"
    private const val KEY_APK_URL = "direct_apk_download_url"
    private const val KEY_GITHUB_REPO = "github_release_repo"
    private const val KEY_MANIFEST_URL = "manifest_json_url"
    private const val KEY_REMOTE_VERSION_NAME = "remote_version_name"
    private const val KEY_REMOTE_VERSION_CODE = "remote_version_code"
    private const val KEY_REMOTE_NOTES = "remote_release_notes"
    private const val KEY_REMOTE_DATE = "remote_release_date"

    private const val RELEASE_DOC_ID = "release_config"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val _updateState = MutableStateFlow(AppUpdateState())
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pkgInfo = getInstalledPackageVersion(context)
        val installedName = pkgInfo.first
        val installedCode = pkgInfo.second

        val sourceType = UpdateSourceType.fromCode(prefs.getString(KEY_SOURCE_TYPE, UpdateSourceType.DIRECT_APK_OR_CLOUD.code))
        val savedApkUrl = prefs.getString(KEY_APK_URL, "") ?: ""
        val savedGithubRepo = prefs.getString(KEY_GITHUB_REPO, "") ?: ""
        val savedManifestUrl = prefs.getString(KEY_MANIFEST_URL, "") ?: ""
        val savedRemoteName = prefs.getString(KEY_REMOTE_VERSION_NAME, installedName) ?: installedName
        val savedRemoteCode = prefs.getInt(KEY_REMOTE_VERSION_CODE, installedCode.toInt())
        val savedNotes = prefs.getString(KEY_REMOTE_NOTES, "") ?: ""
        val savedDate = prefs.getString(KEY_REMOTE_DATE, "") ?: ""

        val hasRealUpdate = savedApkUrl.isNotBlank() && isRemoteNewer(
            remoteVersionCode = savedRemoteCode,
            remoteVersionName = savedRemoteName,
            installedVersionCode = installedCode.toInt(),
            installedVersionName = installedName
        )

        _updateState.value = _updateState.value.copy(
            installedPackageVersion = installedName,
            installedVersionCode = installedCode,
            currentModuleVersion = installedName,
            latestAvailableVersion = if (hasRealUpdate) savedRemoteName else installedName,
            latestBuildNumber = if (hasRealUpdate) savedRemoteCode else installedCode.toInt(),
            isUpdateAvailable = hasRealUpdate,
            lastCheckedTimestamp = prefs.getLong(KEY_LAST_CHECKED_MS, 0L),
            lastBackupTimestamp = prefs.getLong(KEY_LAST_BACKUP_MS, 0L),
            autoCheckOnStartup = prefs.getBoolean(KEY_AUTO_CHECK, true),
            autoBackupBeforeUpdate = prefs.getBoolean(KEY_AUTO_BACKUP, true),
            sourceType = sourceType,
            apkDownloadUrl = savedApkUrl,
            githubRepo = savedGithubRepo,
            manifestJsonUrl = savedManifestUrl,
            releaseNotesText = savedNotes,
            releaseDate = savedDate
        )
    }

    private fun getInstalledPackageVersion(context: Context): Pair<String, Long> {
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            val vName = info.versionName ?: BuildConfig.VERSION_NAME
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            vName to vCode
        } catch (_: Exception) {
            BuildConfig.VERSION_NAME to BuildConfig.VERSION_CODE.toLong()
        }
    }

    /**
     * Performs a REAL remote update check using the configured source:
     * 1. GitHub Releases API (fetches latest tag + real .apk asset browser_download_url)
     * 2. Custom JSON Manifest URL (fetches versionCode, versionName, apkUrl, releaseNotes)
     * 3. Firebase Cloud Release Config + saved Direct APK Link (syncs latest generated APK link across clinic devices)
     */
    suspend fun checkForUpdates(context: Context): Boolean = withContext(Dispatchers.IO) {
        val pkgInfo = getInstalledPackageVersion(context)
        val installedName = pkgInfo.first
        val installedCode = pkgInfo.second.toInt()
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        _updateState.value = _updateState.value.copy(
            installedPackageVersion = installedName,
            installedVersionCode = installedCode.toLong(),
            currentModuleVersion = installedName,
            isChecking = true,
            lastError = null,
            updateStatusMessage = AppLanguageManager.text(
                "Checking for newly generated APK release...",
                "नए जनरेट किए गए ऐप वर्ज़न (APK) की जांच हो रही है..."
            )
        )

        val currentState = _updateState.value

        try {
            when (currentState.sourceType) {
                UpdateSourceType.GITHUB_RELEASES -> {
                    val repoSlug = extractGithubRepoSlug(currentState.githubRepo)
                    if (repoSlug.isBlank()) {
                        val msg = AppLanguageManager.text(
                            "Enter your GitHub repository (e.g. username/repo) below to check & download new APK releases.",
                            "नया APK डाउनलोड करने के लिए नीचे अपना GitHub रिपॉजिटरी (उदा. username/repo) दर्ज करें।"
                        )
                        _updateState.value = _updateState.value.copy(
                            isChecking = false,
                            isUpdateAvailable = false,
                            lastError = msg,
                            updateStatusMessage = msg
                        )
                        return@withContext false
                    }
                    return@withContext checkGithubLatestRelease(context, prefs, repoSlug, installedCode, installedName)
                }

                UpdateSourceType.MANIFEST_JSON -> {
                    val manifestUrl = currentState.manifestJsonUrl.trim()
                    if (manifestUrl.isBlank()) {
                        val msg = AppLanguageManager.text(
                            "Enter a valid version.json URL below to check for new APK builds.",
                            "नए APK की जांच के लिए नीचे अपना version.json लिंक दर्ज करें।"
                        )
                        _updateState.value = _updateState.value.copy(
                            isChecking = false,
                            isUpdateAvailable = false,
                            lastError = msg,
                            updateStatusMessage = msg
                        )
                        return@withContext false
                    }
                    return@withContext checkManifestJsonRelease(context, prefs, manifestUrl, installedCode, installedName)
                }

                UpdateSourceType.DIRECT_APK_OR_CLOUD -> {
                    return@withContext checkCloudAndDirectApkRelease(context, prefs, installedCode, installedName)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
            val errMsg = AppLanguageManager.text(
                "Could not check update server: ${e.localizedMessage ?: "Network error"}",
                "अपडेट सर्वर से संपर्क नहीं हो सका: ${e.localizedMessage ?: "इंटरनेट जांचें"}"
            )
            _updateState.value = _updateState.value.copy(
                isChecking = false,
                lastError = errMsg,
                updateStatusMessage = errMsg
            )
            false
        }
    }

    private fun checkGithubLatestRelease(
        context: Context,
        prefs: android.content.SharedPreferences,
        repoSlug: String,
        installedCode: Int,
        installedName: String
    ): Boolean {
        val apiUrl = "https://api.github.com/repos/$repoSlug/releases/latest"
        val req = Request.Builder()
            .url(apiUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "DrPriyankVaccineClinic-Android")
            .build()

        httpClient.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                val err = if (response.code == 404) {
                    AppLanguageManager.text(
                        "No published GitHub Release found in $repoSlug. Publish a Release with your generated .apk file attached.",
                        "$repoSlug में कोई GitHub Release नहीं मिली। नया .apk अटैच करके Release पब्लिश करें।"
                    )
                } else {
                    AppLanguageManager.text(
                        "GitHub API returned HTTP ${response.code}. Check repository name or public access.",
                        "GitHub से डेटा नहीं मिला (HTTP ${response.code})। रिपॉजिटरी नाम जांचें।"
                    )
                }
                _updateState.value = _updateState.value.copy(
                    isChecking = false,
                    isUpdateAvailable = false,
                    lastError = err,
                    updateStatusMessage = err
                )
                return false
            }

            val bodyStr = response.body?.string().orEmpty()
            val json = JSONObject(bodyStr)
            val rawTag = json.optString("tag_name", "").removePrefix("v").removePrefix("V").trim()
            val releaseName = rawTag.ifBlank { json.optString("name", installedName) }
            val releaseNotes = json.optString("body", "").trim()
            val publishedAt = json.optString("published_at", "").take(10)
            val htmlUrl = json.optString("html_url", "https://github.com/$repoSlug/releases/latest")

            var apkUrl = ""
            var apkName = ""
            var apkSizeMb = ""
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", "")
                        apkName = name
                        val sizeBytes = asset.optLong("size", 0L)
                        if (sizeBytes > 0L) {
                            apkSizeMb = String.format(Locale.getDefault(), "%.1f MB", sizeBytes / (1024.0 * 1024.0))
                        }
                        break
                    }
                }
            }
            // Fallback to release page if no .apk asset attached directly
            val finalDownloadUrl = apkUrl.ifBlank { htmlUrl }

            val newer = isRemoteNewer(
                remoteVersionCode = installedCode,
                remoteVersionName = releaseName,
                installedVersionCode = installedCode,
                installedVersionName = installedName
            )

            val now = System.currentTimeMillis()
            prefs.edit()
                .putLong(KEY_LAST_CHECKED_MS, now)
                .putString(KEY_REMOTE_VERSION_NAME, releaseName)
                .putString(KEY_APK_URL, finalDownloadUrl)
                .putString(KEY_REMOTE_NOTES, releaseNotes)
                .putString(KEY_REMOTE_DATE, publishedAt)
                .apply()

            _updateState.value = _updateState.value.copy(
                isChecking = false,
                latestAvailableVersion = releaseName,
                isUpdateAvailable = newer,
                apkDownloadUrl = finalDownloadUrl,
                apkFileName = apkName,
                apkFileSizeMb = apkSizeMb,
                releaseNotesText = releaseNotes,
                releaseDate = publishedAt,
                lastCheckedTimestamp = now,
                lastError = null,
                updateStatusMessage = if (newer) {
                    AppLanguageManager.text(
                        "New APK release v$releaseName found on GitHub! Tap Download to install.",
                        "GitHub पर नया वर्ज़न v$releaseName मिल गया है! इंस्टॉल करने के लिए डाउनलोड दबाएं।"
                    )
                } else {
                    AppLanguageManager.text(
                        "You already have the latest version (v$installedName). GitHub latest is v$releaseName.",
                        "आपके पास पहले से नवीनतम वर्ज़न (v$installedName) है। GitHub पर नवीनतम v$releaseName है।"
                    )
                }
            )
            return newer
        }
    }

    private fun checkManifestJsonRelease(
        context: Context,
        prefs: android.content.SharedPreferences,
        manifestUrl: String,
        installedCode: Int,
        installedName: String
    ): Boolean {
        val formattedUrl = ensureHttps(manifestUrl)
        val req = Request.Builder()
            .url(formattedUrl)
            .header("Cache-Control", "no-cache")
            .build()

        httpClient.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                val err = AppLanguageManager.text(
                    "Manifest URL returned HTTP ${response.code}. Ensure the JSON link is publicly accessible.",
                    "Manifest लिंक से डेटा नहीं मिला (HTTP ${response.code})।"
                )
                _updateState.value = _updateState.value.copy(
                    isChecking = false,
                    isUpdateAvailable = false,
                    lastError = err,
                    updateStatusMessage = err
                )
                return false
            }

            val json = JSONObject(response.body?.string().orEmpty())
            val remoteCode = json.optInt("versionCode", installedCode)
            val remoteName = json.optString("versionName", installedName).removePrefix("v").trim()
            val rawApkUrl = json.optString("apkUrl", json.optString("downloadUrl", "")).trim()
            val notes = json.optString("releaseNotes", "").trim()
            val date = json.optString("releaseDate", "").trim()

            val newer = rawApkUrl.isNotBlank() && isRemoteNewer(
                remoteVersionCode = remoteCode,
                remoteVersionName = remoteName,
                installedVersionCode = installedCode,
                installedVersionName = installedName
            )

            val now = System.currentTimeMillis()
            prefs.edit()
                .putLong(KEY_LAST_CHECKED_MS, now)
                .putInt(KEY_REMOTE_VERSION_CODE, remoteCode)
                .putString(KEY_REMOTE_VERSION_NAME, remoteName)
                .putString(KEY_APK_URL, rawApkUrl)
                .putString(KEY_REMOTE_NOTES, notes)
                .putString(KEY_REMOTE_DATE, date)
                .apply()

            _updateState.value = _updateState.value.copy(
                isChecking = false,
                latestAvailableVersion = remoteName,
                latestBuildNumber = remoteCode,
                isUpdateAvailable = newer,
                apkDownloadUrl = rawApkUrl,
                releaseNotesText = notes,
                releaseDate = date,
                lastCheckedTimestamp = now,
                lastError = null,
                updateStatusMessage = if (newer) {
                    AppLanguageManager.text(
                        "New build v$remoteName (Build #$remoteCode) is ready to download!",
                        "नया वर्ज़न v$remoteName (बिल्ड #$remoteCode) डाउनलोड के लिए तैयार है!"
                    )
                } else {
                    AppLanguageManager.text(
                        "Installed app v$installedName (Build #$installedCode) is already up to date.",
                        "आपका ऐप v$installedName (बिल्ड #$installedCode) पहले से नवीनतम वर्ज़न पर है।"
                    )
                }
            )
            return newer
        }
    }

    private suspend fun checkCloudAndDirectApkRelease(
        context: Context,
        prefs: android.content.SharedPreferences,
        installedCode: Int,
        installedName: String
    ): Boolean {
        var remoteCode = prefs.getInt(KEY_REMOTE_VERSION_CODE, installedCode)
        var remoteName = prefs.getString(KEY_REMOTE_VERSION_NAME, installedName) ?: installedName
        var apkUrl = prefs.getString(KEY_APK_URL, "") ?: ""
        var notes = prefs.getString(KEY_REMOTE_NOTES, "") ?: ""
        var date = prefs.getString(KEY_REMOTE_DATE, "") ?: ""

        // Pull latest published APK link from Firebase Cloud if signed in
        val uid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
        if (!uid.isNullOrBlank()) {
            try {
                val db = FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id))
                val doc = db.collection("users").document(uid)
                    .collection("clinicProfile").document(RELEASE_DOC_ID)
                    .get().await()
                if (doc.exists()) {
                    val cloudApkUrl = doc.getString("apkDownloadUrl").orEmpty().trim()
                    val cloudVerName = doc.getString("latestVersionName").orEmpty().trim()
                    val cloudVerCode = doc.getLong("latestVersionCode")?.toInt() ?: installedCode
                    val cloudNotes = doc.getString("releaseNotes").orEmpty().trim()
                    val cloudDate = doc.getString("releaseDate").orEmpty().trim()
                    if (cloudApkUrl.isNotBlank()) {
                        apkUrl = cloudApkUrl
                        remoteName = cloudVerName.ifBlank { remoteName }
                        remoteCode = cloudVerCode
                        notes = cloudNotes
                        date = cloudDate
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cloud release config lookup skipped: ${e.localizedMessage}")
            }
        }

        val now = System.currentTimeMillis()
        prefs.edit()
            .putLong(KEY_LAST_CHECKED_MS, now)
            .putInt(KEY_REMOTE_VERSION_CODE, remoteCode)
            .putString(KEY_REMOTE_VERSION_NAME, remoteName)
            .putString(KEY_APK_URL, apkUrl)
            .putString(KEY_REMOTE_NOTES, notes)
            .putString(KEY_REMOTE_DATE, date)
            .apply()

        if (apkUrl.isBlank()) {
            val msg = AppLanguageManager.text(
                "No new APK download link configured yet. Paste your newly generated APK link (Google Drive / GitHub / Web URL) below to download it.",
                "अभी कोई नया APK डाउनलोड लिंक सेट नहीं है। अपना नया जनरेट किया गया APK लिंक (Google Drive / GitHub / Direct URL) नीचे सेव करें।"
            )
            _updateState.value = _updateState.value.copy(
                isChecking = false,
                isUpdateAvailable = false,
                apkDownloadUrl = "",
                lastCheckedTimestamp = now,
                lastError = null,
                updateStatusMessage = msg
            )
            return false
        }

        val newer = isRemoteNewer(
            remoteVersionCode = remoteCode,
            remoteVersionName = remoteName,
            installedVersionCode = installedCode,
            installedVersionName = installedName
        )

        _updateState.value = _updateState.value.copy(
            isChecking = false,
            latestAvailableVersion = remoteName,
            latestBuildNumber = remoteCode,
            isUpdateAvailable = newer,
            apkDownloadUrl = apkUrl,
            releaseNotesText = notes,
            releaseDate = date,
            lastCheckedTimestamp = now,
            lastError = null,
            updateStatusMessage = if (newer) {
                AppLanguageManager.text(
                    "New generated APK v$remoteName (Build #$remoteCode) is ready to download!",
                    "नया जनरेट किया गया APK v$remoteName (बिल्ड #$remoteCode) डाउनलोड के लिए तैयार है!"
                )
            } else {
                AppLanguageManager.text(
                    "Saved APK link (v$remoteName) is ready. Tap 'Download New APK' anytime to re-download the latest generated file.",
                    "नया APK लिंक (v$remoteName) तैयार है। नई फाइल डाउनलोड करने के लिए नीचे 'नया APK डाउनलोड करें' दबाएं।"
                )
            }
        )
        return newer
    }

    /**
     * Saves a newly generated APK download link (and optional version number) locally AND
     * publishes it to Firebase Cloud (if signed in) so every clinic phone gets the exact new APK.
     */
    suspend fun saveAndPublishReleaseSource(
        context: Context,
        sourceType: UpdateSourceType,
        apkDownloadUrl: String,
        newVersionName: String,
        newVersionCode: Int,
        githubRepo: String,
        manifestJsonUrl: String,
        releaseNotes: String
    ): Result<String> = withContext(Dispatchers.IO) {
        _updateState.value = _updateState.value.copy(isPublishingRelease = true, lastError = null)
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val pkgInfo = getInstalledPackageVersion(context)
            val installedName = pkgInfo.first
            val installedCode = pkgInfo.second.toInt()

            val cleanApkUrl = apkDownloadUrl.trim()
            val cleanGithub = githubRepo.trim()
            val cleanManifest = manifestJsonUrl.trim()
            val cleanVerName = newVersionName.trim().removePrefix("v").removePrefix("V").ifBlank { installedName }
            val cleanVerCode = newVersionCode.coerceAtLeast(installedCode)
            val cleanNotes = releaseNotes.trim()
            val todayStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

            prefs.edit()
                .putString(KEY_SOURCE_TYPE, sourceType.code)
                .putString(KEY_APK_URL, cleanApkUrl)
                .putString(KEY_GITHUB_REPO, cleanGithub)
                .putString(KEY_MANIFEST_URL, cleanManifest)
                .putString(KEY_REMOTE_VERSION_NAME, cleanVerName)
                .putInt(KEY_REMOTE_VERSION_CODE, cleanVerCode)
                .putString(KEY_REMOTE_NOTES, cleanNotes)
                .putString(KEY_REMOTE_DATE, todayStr)
                .apply()

            // Sync release link to Firebase Cloud if signed in (respects existing clinicProfile rule)
            var syncedToCloud = false
            val uid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
            if (!uid.isNullOrBlank() && cleanApkUrl.isNotBlank()) {
                try {
                    val db = FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id))
                    val payload = hashMapOf<String, Any>(
                        "clinicName" to "App Release Metadata",
                        "doctorName" to "Release Publisher",
                        "phone" to "0000000000",
                        "apkDownloadUrl" to cleanApkUrl,
                        "latestVersionName" to cleanVerName,
                        "latestVersionCode" to cleanVerCode,
                        "githubRepo" to cleanGithub,
                        "manifestJsonUrl" to cleanManifest,
                        "releaseNotes" to cleanNotes,
                        "releaseDate" to todayStr
                    )
                    db.collection("users").document(uid)
                        .collection("clinicProfile").document(RELEASE_DOC_ID)
                        .set(payload).await()
                    syncedToCloud = true
                } catch (e: Exception) {
                    Log.w(TAG, "Could not sync release link to Firestore: ${e.localizedMessage}")
                }
            }

            val hasUpdate = cleanApkUrl.isNotBlank() && isRemoteNewer(
                remoteVersionCode = cleanVerCode,
                remoteVersionName = cleanVerName,
                installedVersionCode = installedCode,
                installedVersionName = installedName
            )

            _updateState.value = _updateState.value.copy(
                isPublishingRelease = false,
                sourceType = sourceType,
                apkDownloadUrl = cleanApkUrl,
                githubRepo = cleanGithub,
                manifestJsonUrl = cleanManifest,
                latestAvailableVersion = cleanVerName,
                latestBuildNumber = cleanVerCode,
                releaseNotesText = cleanNotes,
                releaseDate = todayStr,
                isUpdateAvailable = hasUpdate,
                updateStatusMessage = if (syncedToCloud) {
                    AppLanguageManager.text(
                        "New APK link saved & synced to your Cloud devices!",
                        "नया APK लिंक सेव और क्लाउड पर सिंक हो गया है!"
                    )
                } else {
                    AppLanguageManager.text(
                        "Update source saved! Tap 'Check Now' or 'Download New APK'.",
                        "अपडेट स्रोत सेव हो गया! अब 'नया APK डाउनलोड करें' पर टैप करें।"
                    )
                }
            )

            // If GitHub or Manifest source was saved, immediately run a real check
            if (sourceType == UpdateSourceType.GITHUB_RELEASES && cleanGithub.isNotBlank()) {
                checkForUpdates(context)
            } else if (sourceType == UpdateSourceType.MANIFEST_JSON && cleanManifest.isNotBlank()) {
                checkForUpdates(context)
            }

            Result.success(_updateState.value.updateStatusMessage)
        } catch (e: Exception) {
            _updateState.value = _updateState.value.copy(
                isPublishingRelease = false,
                lastError = e.localizedMessage
            )
            Result.failure(e)
        }
    }

    /**
     * Opens the real APK download URL in the system browser / Android downloader (Play Policy compliant).
     * Automatically converts Google Drive / Dropbox share links into direct file download URLs
     * so Android downloads the newly generated APK instead of opening an old preview page.
     */
    fun openRealApkDownload(context: Context, urlOverride: String? = null): Boolean {
        val rawUrl = (urlOverride ?: _updateState.value.apkDownloadUrl).trim()
        if (rawUrl.isBlank()) {
            Toast.makeText(
                context,
                AppLanguageManager.text(
                    "Please enter your new APK download link first.",
                    "कृपया पहले नया APK डाउनलोड लिंक दर्ज करें।"
                ),
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        val normalizedUrl = normalizeDownloadUrl(rawUrl)
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(
                context,
                AppLanguageManager.text(
                    "Unable to open download link: ${e.localizedMessage}",
                    "डाउनलोड लिंक नहीं खुल सका: ${e.localizedMessage}"
                ),
                Toast.LENGTH_LONG
            ).show()
            false
        }
    }

    /**
     * Converts common cloud file-sharing links (Google Drive, Dropbox) into direct download links
     * so tapping Update downloads the actual newly generated APK file immediately.
     */
    fun normalizeDownloadUrl(rawUrl: String): String {
        val url = ensureHttps(rawUrl.trim())

        // Google Drive format 1: https://drive.google.com/file/d/<FILE_ID>/view?usp=sharing
        val driveFileRegex = Regex("https?://drive\\.google\\.com/file/d/([a-zA-Z0-9_-]+)")
        driveFileRegex.find(url)?.let { match ->
            val fileId = match.groupValues[1]
            return "https://drive.google.com/uc?export=download&id=$fileId"
        }

        // Google Drive format 2: https://drive.google.com/open?id=<FILE_ID>
        val driveOpenRegex = Regex("https?://drive\\.google\\.com/open\\?id=([a-zA-Z0-9_-]+)")
        driveOpenRegex.find(url)?.let { match ->
            val fileId = match.groupValues[1]
            return "https://drive.google.com/uc?export=download&id=$fileId"
        }

        // Dropbox: change dl=0 to dl=1 for direct download
        if (url.contains("dropbox.com", ignoreCase = true)) {
            return if (url.contains("dl=0")) {
                url.replace("dl=0", "dl=1")
            } else if (!url.contains("dl=1")) {
                val sep = if (url.contains("?")) "&" else "?"
                "$url${sep}dl=1"
            } else {
                url
            }
        }

        return url
    }

    private fun ensureHttps(url: String): String {
        return if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
            url
        } else {
            "https://$url"
        }
    }

    private fun extractGithubRepoSlug(input: String): String {
        val trimmed = input.trim().removeSuffix("/")
        if (trimmed.isBlank()) return ""
        val regex = Regex("github\\.com/([^/]+/[^/]+)")
        val match = regex.find(trimmed)
        if (match != null) {
            return match.groupValues[1].removeSuffix(".git")
        }
        return if (trimmed.count { it == '/' } == 1 && !trimmed.contains(" ")) {
            trimmed.removeSuffix(".git")
        } else {
            ""
        }
    }

    fun isRemoteNewer(
        remoteVersionCode: Int,
        remoteVersionName: String,
        installedVersionCode: Int,
        installedVersionName: String
    ): Boolean {
        if (remoteVersionCode > installedVersionCode) return true
        return compareSemVer(remoteVersionName, installedVersionName) > 0
    }

    private fun compareSemVer(v1: String, v2: String): Int {
        val parts1 = v1.removePrefix("v").removePrefix("V").split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        val parts2 = v2.removePrefix("v").removePrefix("V").split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val a = parts1.getOrElse(i) { 0 }
            val b = parts2.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    fun recordBackupTimestamp(context: Context) {
        val now = System.currentTimeMillis()
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_BACKUP_MS, now).apply()
        _updateState.value = _updateState.value.copy(lastBackupTimestamp = now)
    }

    fun setAutoCheckOnStartup(context: Context, enabled: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_CHECK, enabled).apply()
        _updateState.value = _updateState.value.copy(autoCheckOnStartup = enabled)
    }

    fun setAutoBackupBeforeUpdate(context: Context, enabled: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply()
        _updateState.value = _updateState.value.copy(autoBackupBeforeUpdate = enabled)
    }

    fun openGooglePlayStoreUpdate(context: Context) {
        val pkg = context.packageName
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
        } catch (_: ActivityNotFoundException) {
            try {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    AppLanguageManager.text("Unable to open Play Store: ${e.localizedMessage}", "Play Store नहीं खुल सका"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0L) return AppLanguageManager.text("Never", "कभी नहीं")
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
