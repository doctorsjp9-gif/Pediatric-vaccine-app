package com.example.ui.util

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.example.BuildConfig
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
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
    val isVerifyingLink: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgressPercent: Int = 0,
    val downloadedSizeLabel: String = "",
    val downloadedFileName: String = "",
    val downloadCompleted: Boolean = false,
    val activeDownloadId: Long = -1L,
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

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadMonitorJob: Job? = null

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val _updateState = MutableStateFlow(AppUpdateState())
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pkgInfo = getInstalledPackageVersion(context)
        val installedName = pkgInfo.first
        val installedCode = pkgInfo.second

        val sourceType = UpdateSourceType.fromCode(
            prefs.getString(KEY_SOURCE_TYPE, UpdateSourceType.DIRECT_APK_OR_CLOUD.code)
        )
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
     * Performs a REAL remote update check using the configured source (and always refreshes
     * cloud release metadata directly from Firestore Server first when signed in so devices
     * never read stale cached links).
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

        // First refresh shared release config from Firestore SERVER so secondary clinic devices
        // pick up whichever channel (Direct APK, GitHub, or Manifest) was published most recently.
        syncCloudReleaseConfigIntoPrefs(context, prefs)

        val effectiveSource = UpdateSourceType.fromCode(
            prefs.getString(KEY_SOURCE_TYPE, _updateState.value.sourceType.code)
        )
        val effectiveGithub = prefs.getString(KEY_GITHUB_REPO, _updateState.value.githubRepo).orEmpty()
        val effectiveManifest = prefs.getString(KEY_MANIFEST_URL, _updateState.value.manifestJsonUrl).orEmpty()

        _updateState.value = _updateState.value.copy(
            sourceType = effectiveSource,
            githubRepo = effectiveGithub,
            manifestJsonUrl = effectiveManifest
        )

        try {
            when (effectiveSource) {
                UpdateSourceType.GITHUB_RELEASES -> {
                    val repoSlug = extractGithubRepoSlug(effectiveGithub)
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
                    return@withContext checkGithubLatestRelease(prefs, repoSlug, installedCode, installedName)
                }

                UpdateSourceType.MANIFEST_JSON -> {
                    val manifestUrl = effectiveManifest.trim()
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
                    return@withContext checkManifestJsonRelease(prefs, manifestUrl, installedCode, installedName)
                }

                UpdateSourceType.DIRECT_APK_OR_CLOUD -> {
                    return@withContext checkCloudAndDirectApkRelease(prefs, installedCode, installedName)
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

    /**
     * Fetches the latest release config directly from Firestore SERVER (never stale local cache).
     */
    private suspend fun syncCloudReleaseConfigIntoPrefs(
        context: Context,
        prefs: android.content.SharedPreferences
    ) {
        val uid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
        if (uid.isNullOrBlank()) return
        try {
            val db = FirebaseFirestore.getInstance(
                context.applicationContext.getString(R.string.firestore_database_id)
            )
            val docRef = db.collection("users").document(uid)
                .collection("clinicProfile").document(RELEASE_DOC_ID)
            val doc = try {
                docRef.get(Source.SERVER).await()
            } catch (_: Exception) {
                docRef.get(Source.DEFAULT).await()
            }
            if (!doc.exists()) return

            val cloudSource = doc.getString("sourceType").orEmpty().trim()
            val cloudApkUrl = doc.getString("apkDownloadUrl").orEmpty().trim()
            val cloudVerName = doc.getString("latestVersionName").orEmpty().trim()
            val cloudVerCode = doc.getLong("latestVersionCode")?.toInt()
            val cloudGithub = doc.getString("githubRepo").orEmpty().trim()
            val cloudManifest = doc.getString("manifestJsonUrl").orEmpty().trim()
            val cloudNotes = doc.getString("releaseNotes").orEmpty().trim()
            val cloudDate = doc.getString("releaseDate").orEmpty().trim()

            val editor = prefs.edit()
            if (cloudSource.isNotBlank()) editor.putString(KEY_SOURCE_TYPE, cloudSource)
            if (cloudApkUrl.isNotBlank()) editor.putString(KEY_APK_URL, cloudApkUrl)
            if (cloudVerName.isNotBlank()) editor.putString(KEY_REMOTE_VERSION_NAME, cloudVerName)
            if (cloudVerCode != null && cloudVerCode > 0) editor.putInt(KEY_REMOTE_VERSION_CODE, cloudVerCode)
            if (cloudGithub.isNotBlank()) editor.putString(KEY_GITHUB_REPO, cloudGithub)
            if (cloudManifest.isNotBlank()) editor.putString(KEY_MANIFEST_URL, cloudManifest)
            if (cloudNotes.isNotBlank()) editor.putString(KEY_REMOTE_NOTES, cloudNotes)
            if (cloudDate.isNotBlank()) editor.putString(KEY_REMOTE_DATE, cloudDate)
            editor.apply()
        } catch (e: Exception) {
            Log.w(TAG, "Cloud release config lookup skipped: ${e.localizedMessage}")
        }
    }

    private fun checkGithubLatestRelease(
        prefs: android.content.SharedPreferences,
        repoSlug: String,
        installedCode: Int,
        installedName: String
    ): Boolean {
        val latestUrl = "https://api.github.com/repos/$repoSlug/releases/latest"
        var releaseJson: JSONObject? = null
        var lastHttpCode = 200

        httpClient.newCall(buildGithubRequest(latestUrl)).execute().use { response ->
            lastHttpCode = response.code
            if (response.isSuccessful) {
                releaseJson = JSONObject(response.body?.string().orEmpty())
            }
        }

        // Fallback: if /releases/latest returns 404 (e.g. only pre-releases published), query /releases
        if (releaseJson == null && lastHttpCode == 404) {
            val allReleasesUrl = "https://api.github.com/repos/$repoSlug/releases?per_page=10"
            httpClient.newCall(buildGithubRequest(allReleasesUrl)).execute().use { response ->
                lastHttpCode = response.code
                if (response.isSuccessful) {
                    val arr = JSONArray(response.body?.string().orEmpty())
                    var bestRelease: JSONObject? = null
                    for (i in 0 until arr.length()) {
                        val rel = arr.optJSONObject(i) ?: continue
                        if (rel.optBoolean("draft", false)) continue
                        if (bestRelease == null) bestRelease = rel
                        if (hasApkAsset(rel)) {
                            bestRelease = rel
                            break
                        }
                    }
                    releaseJson = bestRelease
                }
            }
        }

        val json = releaseJson
        if (json == null) {
            val err = if (lastHttpCode == 404) {
                AppLanguageManager.text(
                    "No published GitHub Release found in $repoSlug. Publish a Release with your generated .apk file attached.",
                    "$repoSlug में कोई GitHub Release नहीं मिली। नया .apk अटैच करके Release पब्लिश करें।"
                )
            } else {
                AppLanguageManager.text(
                    "GitHub API returned HTTP $lastHttpCode. Check repository name or public access.",
                    "GitHub से डेटा नहीं मिला (HTTP $lastHttpCode)। रिपॉजिटरी नाम जांचें।"
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

        val rawTag = json.optString("tag_name", "").removePrefix("v").removePrefix("V").trim()
        val releaseName = rawTag.ifBlank { json.optString("name", installedName) }
        val releaseNotes = json.optString("body", "").trim()
        val publishedAt = json.optString("published_at", "").take(10)
        val htmlUrl = json.optString("html_url", "https://github.com/$repoSlug/releases")

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
            downloadCompleted = false,
            updateStatusMessage = if (newer) {
                AppLanguageManager.text(
                    "New APK release v$releaseName found on GitHub! Tap Download New APK.",
                    "GitHub पर नया वर्ज़न v$releaseName मिल गया है! नया APK डाउनलोड करें।"
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

    private fun buildGithubRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "DrPriyankVaccineClinic-Android")
            .header("Cache-Control", "no-cache")
            .build()
    }

    private fun hasApkAsset(releaseJson: JSONObject): Boolean {
        val assets = releaseJson.optJSONArray("assets") ?: return false
        for (i in 0 until assets.length()) {
            val name = assets.optJSONObject(i)?.optString("name", "").orEmpty()
            if (name.endsWith(".apk", ignoreCase = true)) return true
        }
        return false
    }

    private fun checkManifestJsonRelease(
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
                downloadCompleted = false,
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

    private fun checkCloudAndDirectApkRelease(
        prefs: android.content.SharedPreferences,
        installedCode: Int,
        installedName: String
    ): Boolean {
        val remoteCode = prefs.getInt(KEY_REMOTE_VERSION_CODE, installedCode)
        val remoteName = prefs.getString(KEY_REMOTE_VERSION_NAME, installedName) ?: installedName
        val apkUrl = prefs.getString(KEY_APK_URL, "") ?: ""
        val notes = prefs.getString(KEY_REMOTE_NOTES, "") ?: ""
        val date = prefs.getString(KEY_REMOTE_DATE, "") ?: ""

        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_CHECKED_MS, now).apply()

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
            downloadCompleted = false,
            updateStatusMessage = if (newer) {
                AppLanguageManager.text(
                    "New generated APK v$remoteName (Build #$remoteCode) is ready to download!",
                    "नया जनरेट किया गया APK v$remoteName (बिल्ड #$remoteCode) डाउनलोड के लिए तैयार है!"
                )
            } else {
                AppLanguageManager.text(
                    "Saved APK link (v$remoteName) is ready. Tap 'Download New APK' to download a fresh copy.",
                    "नया APK लिंक (v$remoteName) तैयार है। नई फाइल डाउनलोड करने के लिए नीचे 'नया APK डाउनलोड करें' दबाएं।"
                )
            }
        )
        return newer
    }

    /**
     * Saves a newly generated APK download link (or GitHub/Manifest source) locally AND
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
        _updateState.value = _updateState.value.copy(
            isPublishingRelease = true,
            lastError = null,
            downloadCompleted = false,
            downloadProgressPercent = 0,
            downloadedFileName = ""
        )
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

            // Sync release source (Direct APK, GitHub, OR Manifest) to Firebase Cloud if signed in
            var syncedToCloud = false
            val uid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
            val hasAnyConfig = cleanApkUrl.isNotBlank() || cleanGithub.isNotBlank() || cleanManifest.isNotBlank()
            if (!uid.isNullOrBlank() && hasAnyConfig) {
                try {
                    val db = FirebaseFirestore.getInstance(
                        context.applicationContext.getString(R.string.firestore_database_id)
                    )
                    val payload = hashMapOf<String, Any>(
                        "clinicName" to "App Release Metadata",
                        "doctorName" to "Release Publisher",
                        "phone" to "0000000000",
                        "sourceType" to sourceType.code,
                        "apkDownloadUrl" to cleanApkUrl,
                        "latestVersionName" to cleanVerName,
                        "latestVersionCode" to cleanVerCode,
                        "githubRepo" to cleanGithub,
                        "manifestJsonUrl" to cleanManifest,
                        "releaseNotes" to cleanNotes,
                        "releaseDate" to todayStr,
                        "updatedAt" to FieldValue.serverTimestamp()
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
                        "Update source saved & synced to your Cloud devices!",
                        "नया अपडेट लिंक सेव और सभी क्लाउड फ़ोनों पर सिंक हो गया!"
                    )
                } else {
                    AppLanguageManager.text(
                        "Update source saved! Tap 'Download New APK' below.",
                        "अपडेट स्रोत सेव हो गया! अब 'नया APK डाउनलोड करें' दबाएं।"
                    )
                }
            )

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
     * Quick HTTP probe to catch private Google Drive login walls, expired links, or 404s
     * BEFORE Android downloads a corrupt HTML file named `.apk`.
     */
    suspend fun verifyDownloadUrl(rawUrl: String): Result<String> = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeDownloadUrl(rawUrl)
        try {
            val req = Request.Builder()
                .url(normalizedUrl)
                .header("Range", "bytes=0-1023")
                .header("User-Agent", "DrPriyankVaccineClinic-Android")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 206) {
                    val msg = if (resp.code == 401 || resp.code == 403) {
                        AppLanguageManager.text(
                            "Access denied (HTTP ${resp.code}). On Google Drive, set sharing to 'Anyone with the link'.",
                            "लिंक बंद है (HTTP ${resp.code})। Google Drive पर शेयरिंग 'Anyone with the link' सेट करें।"
                        )
                    } else if (resp.code == 404) {
                        AppLanguageManager.text(
                            "APK file not found at this link (HTTP 404). Please paste the newest APK link.",
                            "इस लिंक पर APK फाइल नहीं मिली (HTTP 404)। नया APK लिंक पेस्ट करें।"
                        )
                    } else {
                        AppLanguageManager.text(
                            "Download server returned HTTP ${resp.code}. Try 'Open Link in Browser' below.",
                            "सर्वर ने HTTP ${resp.code} दिया। नीचे 'ब्राउज़र में खोलें' का उपयोग करें।"
                        )
                    }
                    return@withContext Result.failure(IllegalStateException(msg))
                }

                val contentType = resp.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                if (contentType.contains("text/html")) {
                    val snippet = resp.body?.string().orEmpty().take(1500).lowercase(Locale.ROOT)
                    if (snippet.contains("accounts.google.com") ||
                        snippet.contains("servicelogin") ||
                        snippet.contains("access denied") ||
                        snippet.contains("request access")
                    ) {
                        return@withContext Result.failure(
                            IllegalStateException(
                                AppLanguageManager.text(
                                    "Google Drive link is private! Open Google Drive -> Share -> 'Anyone with the link', then try again.",
                                    "Google Drive लिंक प्राइवेट है! Drive में Share -> 'Anyone with the link' चालू करें, फिर डाउनलोड करें।"
                                )
                            )
                        )
                    }
                    // GitHub release HTML page (no direct .apk attached): tell caller to open in browser
                    if (normalizedUrl.contains("github.com") && normalizedUrl.contains("/releases")) {
                        return@withContext Result.failure(
                            IllegalStateException("OPEN_IN_BROWSER_HTML_PAGE")
                        )
                    }
                }
                Result.success(normalizedUrl)
            }
        } catch (e: Exception) {
            // Network errors during probe shouldn't block DownloadManager completely if transient,
            // but surface clear message if DNS/URL is malformed.
            Log.w(TAG, "Link probe warning: ${e.localizedMessage}")
            Result.success(normalizedUrl)
        }
    }

    /**
     * Downloads the newly generated APK directly into the user's Downloads folder with a
     * unique versioned filename (`DrPriyankClinic_v2.7.0_20261121_101500.apk`) using Android's
     * built-in DownloadManager (zero extra permissions on API 29+, strictly Play Policy compliant —
     * NO REQUEST_INSTALL_PACKAGES and NO Dynamic Code Loading).
     *
     * Live download progress updates `AppUpdateState` in real time. Once finished, the user
     * taps "Open Downloads to Install" and taps the exact newly downloaded file at the top.
     */
    suspend fun startDirectApkDownload(
        context: Context,
        urlOverride: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val rawUrl = (urlOverride ?: _updateState.value.apkDownloadUrl).trim()
        if (rawUrl.isBlank()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    AppLanguageManager.text(
                        "Please enter your new APK download link first.",
                        "कृपया पहले नया APK डाउनलोड लिंक दर्ज करें।"
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
            return@withContext false
        }

        _updateState.value = _updateState.value.copy(
            isVerifyingLink = true,
            isDownloading = false,
            downloadCompleted = false,
            downloadProgressPercent = 0,
            downloadedSizeLabel = "",
            lastError = null,
            updateStatusMessage = AppLanguageManager.text(
                "Verifying new APK link...",
                "नए APK लिंक की जांच हो रही है..."
            )
        )

        val verifyResult = verifyDownloadUrl(rawUrl)
        if (verifyResult.isFailure) {
            val err = verifyResult.exceptionOrNull()?.message.orEmpty()
            if (err == "OPEN_IN_BROWSER_HTML_PAGE") {
                _updateState.value = _updateState.value.copy(
                    isVerifyingLink = false,
                    updateStatusMessage = AppLanguageManager.text(
                        "Opening GitHub release page in browser to pick the .apk file...",
                        ".apk फाइल चुनने के लिए GitHub रिलीज़ पेज ब्राउज़र में खुल रहा है..."
                    )
                )
                return@withContext withContext(Dispatchers.Main) {
                    openRealApkDownload(context, rawUrl)
                }
            }
            _updateState.value = _updateState.value.copy(
                isVerifyingLink = false,
                lastError = err,
                updateStatusMessage = err
            )
            return@withContext false
        }

        val normalizedUrl = verifyResult.getOrDefault(normalizeDownloadUrl(rawUrl))

        // On Android 9 (API 28) and below, DownloadManager.setDestinationInExternalPublicDir
        // requires WRITE_EXTERNAL_STORAGE. To keep permissions minimal and Play Policy compliant,
        // hand off cleanly to the system browser on API < 29.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            _updateState.value = _updateState.value.copy(
                isVerifyingLink = false,
                updateStatusMessage = AppLanguageManager.text(
                    "Opening download in system browser...",
                    "ब्राउज़र में नया APK डाउनलोड शुरू हो रहा है..."
                )
            )
            return@withContext withContext(Dispatchers.Main) {
                openRealApkDownload(context, normalizedUrl)
            }
        }

        val versionTag = _updateState.value.latestAvailableVersion
            .replace(Regex("[^0-9A-Za-z._-]"), "")
            .ifBlank { "update" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val uniqueFileName = "DrPriyankClinic_v${versionTag}_${stamp}.apk"

        return@withContext try {
            val dm = context.applicationContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                ?: return@withContext withContext(Dispatchers.Main) {
                    _updateState.value = _updateState.value.copy(isVerifyingLink = false)
                    openRealApkDownload(context, normalizedUrl)
                }

            val dmRequest = DownloadManager.Request(Uri.parse(normalizedUrl)).apply {
                setTitle("Dr. Priyank Clinic Update (v$versionTag)")
                setDescription(uniqueFileName)
                setMimeType("application/vnd.android.package-archive")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, uniqueFileName)
            }

            val downloadId = dm.enqueue(dmRequest)
            _updateState.value = _updateState.value.copy(
                isVerifyingLink = false,
                isDownloading = true,
                downloadCompleted = false,
                downloadProgressPercent = 0,
                downloadedSizeLabel = "",
                downloadedFileName = uniqueFileName,
                activeDownloadId = downloadId,
                lastError = null,
                updateStatusMessage = AppLanguageManager.text(
                    "Downloading $uniqueFileName to Downloads...",
                    "$uniqueFileName डाउनलोड हो रहा है..."
                )
            )

            monitorDownloadProgress(context.applicationContext, dm, downloadId, uniqueFileName)
            true
        } catch (e: Exception) {
            Log.w(TAG, "DownloadManager unavailable, falling back to browser: ${e.localizedMessage}")
            _updateState.value = _updateState.value.copy(
                isVerifyingLink = false,
                isDownloading = false,
                updateStatusMessage = AppLanguageManager.text(
                    "Opening download link in browser...",
                    "डाउनलोड लिंक ब्राउज़र में खुल रहा है..."
                )
            )
            withContext(Dispatchers.Main) {
                openRealApkDownload(context, normalizedUrl)
            }
        }
    }

    private fun monitorDownloadProgress(
        appContext: Context,
        dm: DownloadManager,
        downloadId: Long,
        fileName: String
    ) {
        downloadMonitorJob?.cancel()
        downloadMonitorJob = managerScope.launch {
            var finished = false
            var consecutiveMissing = 0
            while (!finished) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                try {
                    dm.query(query)?.use { cursor ->
                        if (!cursor.moveToFirst()) {
                            consecutiveMissing++
                            if (consecutiveMissing >= 5) {
                                _updateState.value = _updateState.value.copy(
                                    isDownloading = false,
                                    updateStatusMessage = AppLanguageManager.text(
                                        "Download cancelled or cleared from system Downloads.",
                                        "डाउनलोड रद्द हो गया। आप नीचे 'ब्राउज़र में खोलें' भी आज़मा सकते हैं।"
                                    )
                                )
                                finished = true
                            }
                        } else {
                            consecutiveMissing = 0
                            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            val bytesSoFar = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                            val pct = if (bytesTotal > 0L) {
                                ((bytesSoFar * 100L) / bytesTotal).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            val sizeLabel = if (bytesTotal > 0L) {
                                String.format(
                                    Locale.getDefault(),
                                    "%.1f / %.1f MB",
                                    bytesSoFar / (1024.0 * 1024.0),
                                    bytesTotal / (1024.0 * 1024.0)
                                )
                            } else if (bytesSoFar > 0L) {
                                String.format(Locale.getDefault(), "%.1f MB", bytesSoFar / (1024.0 * 1024.0))
                            } else {
                                ""
                            }

                            when (status) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    _updateState.value = _updateState.value.copy(
                                        isDownloading = false,
                                        downloadCompleted = true,
                                        downloadProgressPercent = 100,
                                        downloadedSizeLabel = sizeLabel,
                                        downloadedFileName = fileName,
                                        lastError = null,
                                        updateStatusMessage = AppLanguageManager.text(
                                            "Download complete! Tap 'Open Downloads to Install' and select '$fileName' at the top.",
                                            "डाउनलोड पूरा हो गया! नीचे 'Downloads खोलें' दबाएं और सबसे ऊपर '$fileName' पर टैप करके अपडेट इंस्टॉल करें।"
                                        )
                                    )
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            appContext,
                                            AppLanguageManager.text(
                                                "Downloaded $fileName! Open Downloads to install.",
                                                "$fileName डाउनलोड हो गया! इंस्टॉल करने के लिए Downloads खोलें।"
                                            ),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    finished = true
                                }

                                DownloadManager.STATUS_FAILED -> {
                                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                                    val err = AppLanguageManager.text(
                                        "Direct download failed (code $reason). Tap 'Open Link in Browser' below to download via Chrome.",
                                        "सीधा डाउनलोड विफल रहा (कोड $reason)। नीचे 'ब्राउज़र में खोलें' से Chrome में डाउनलोड करें।"
                                    )
                                    _updateState.value = _updateState.value.copy(
                                        isDownloading = false,
                                        downloadCompleted = false,
                                        lastError = err,
                                        updateStatusMessage = err
                                    )
                                    finished = true
                                }

                                else -> {
                                    _updateState.value = _updateState.value.copy(
                                        isDownloading = true,
                                        downloadProgressPercent = pct,
                                        downloadedSizeLabel = sizeLabel
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Download monitor error: ${e.localizedMessage}")
                    finished = true
                }
                if (!finished) delay(400L)
            }
        }
    }

    /**
     * Opens Android's system Downloads folder so the user sees the freshly downloaded,
     * uniquely named APK file right at the top and can tap it to update smoothly.
     */
    fun openDownloadsFolder(context: Context): Boolean {
        return try {
            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload"),
                        "vnd.android.document/directory"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                true
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    AppLanguageManager.text(
                        "Open your phone's 'Files' -> 'Downloads' folder and tap ${_updateState.value.downloadedFileName}",
                        "अपने फोन के 'Files' -> 'Downloads' फ़ोल्डर में जाकर ${_updateState.value.downloadedFileName} पर टैप करें।"
                    ),
                    Toast.LENGTH_LONG
                ).show()
                false
            }
        }
    }

    /**
     * Opens the real APK download URL in the system browser (Play Policy compliant fallback).
     * Automatically converts Google Drive / Dropbox / GitHub links into direct file download URLs.
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
     * Converts common cloud file-sharing links (Google Drive, Dropbox, GitHub blob) into direct
     * binary download URLs. Uses Google Drive's modern `drive.usercontent.google.com` endpoint
     * with `confirm=t` so large APKs bypass Drive's virus-scan HTML warning and Drive app preview!
     */
    fun normalizeDownloadUrl(rawUrl: String): String {
        val url = ensureHttps(rawUrl.trim())

        // Google Drive format 1: https://drive.google.com/file/d/<FILE_ID>/view?usp=sharing
        val driveFileRegex = Regex("https?://drive\\.google\\.com/file/d/([a-zA-Z0-9_-]+)")
        driveFileRegex.find(url)?.let { match ->
            val fileId = match.groupValues[1]
            return "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
        }

        // Google Drive format 2: https://drive.google.com/open?id=<FILE_ID> or /uc?...id=<FILE_ID>
        val driveIdParamRegex = Regex("https?://drive\\.(?:usercontent\\.)?google\\.com/(?:open|uc|download)\\?[^\\s]*id=([a-zA-Z0-9_-]+)")
        driveIdParamRegex.find(url)?.let { match ->
            val fileId = match.groupValues[1]
            return "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
        }

        // Dropbox: force dl=1 for direct binary download
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

        // GitHub blob URL -> raw content URL (leave /releases/download/ untouched)
        val ghBlobRegex = Regex("https?://github\\.com/([^/]+/[^/]+)/blob/(.+)")
        ghBlobRegex.find(url)?.let { match ->
            return "https://raw.githubusercontent.com/${match.groupValues[1]}/${match.groupValues[2]}"
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

    internal fun extractGithubRepoSlug(input: String): String {
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
