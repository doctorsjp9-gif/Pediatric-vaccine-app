package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class UserSessionState(
    val firebaseUser: FirebaseUser? = null,
    val mobileNumber: String? = null,
    val isMobileVerified: Boolean = false,
    val authProvider: String = "NONE" // "GOOGLE", "MOBILE", "BOTH", "NONE"
) {
    val isAuthenticated: Boolean
        get() = firebaseUser != null || isMobileVerified

    val displayName: String
        get() = firebaseUser?.displayName
            ?: mobileNumber?.let { "Doctor ($it)" }
            ?: "Clinic Doctor"

    val displayEmailOrPhone: String
        get() = firebaseUser?.email
            ?: mobileNumber
            ?: "Not signed in"
}

class AuthManager(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val credentialManager: CredentialManager = CredentialManager.create(context)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("clinic_auth_prefs", Context.MODE_PRIVATE)

    private val _sessionState = MutableStateFlow(loadInitialSession())
    val sessionState: StateFlow<UserSessionState> = _sessionState.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            updateSession(firebaseAuth.currentUser)
        }
    }

    private fun loadInitialSession(): UserSessionState {
        val savedMobile = prefs.getString("verified_mobile", null)
        val isVerified = prefs.getBoolean("is_mobile_verified", false)
        val currentFirebaseUser = auth.currentUser

        val provider = when {
            currentFirebaseUser != null && isVerified -> "BOTH"
            currentFirebaseUser != null -> "GOOGLE"
            isVerified -> "MOBILE"
            else -> "NONE"
        }

        return UserSessionState(
            firebaseUser = currentFirebaseUser,
            mobileNumber = savedMobile,
            isMobileVerified = isVerified,
            authProvider = provider
        )
    }

    private fun updateSession(currentUser: FirebaseUser?) {
        val savedMobile = prefs.getString("verified_mobile", null)
        val isVerified = prefs.getBoolean("is_mobile_verified", false)

        val provider = when {
            currentUser != null && isVerified -> "BOTH"
            currentUser != null -> "GOOGLE"
            isVerified -> "MOBILE"
            else -> "NONE"
        }

        _sessionState.value = UserSessionState(
            firebaseUser = currentUser,
            mobileNumber = savedMobile,
            isMobileVerified = isVerified,
            authProvider = provider
        )
    }

    fun attemptSilentAutoSignIn(
        scope: CoroutineScope,
        onSuccess: () -> Unit = {},
        onFailure: () -> Unit = {}
    ) {
        if (auth.currentUser != null) {
            onSuccess()
            return
        }

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            onFailure()
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    auth.signInWithCredential(authCredential).await()
                    updateSession(auth.currentUser)
                    onSuccess()
                } else {
                    onFailure()
                }
            } catch (e: Exception) {
                onFailure()
            }
        }
    }

    fun signInWithGoogle(
        activityContext: Context,
        scope: CoroutineScope,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit
    ) {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            onError("Google Sign-In configuration missing: default_web_client_id")
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(activityContext as Activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    auth.signInWithCredential(authCredential).await()
                    updateSession(auth.currentUser)
                    onSuccess()
                } else {
                    onError("Unexpected credential format received")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                onCancelled()
            } catch (e: Exception) {
                Log.e("Auth", "Google Sign-In failed", e)
                onError(e.localizedMessage ?: "Google Sign-In failed")
            }
        }
    }

    fun loginWithMobileOtp(mobileNumber: String, enteredOtp: String, expectedOtp: String): Boolean {
        if (enteredOtp.trim() == expectedOtp.trim() && enteredOtp.length == 6) {
            prefs.edit()
                .putString("verified_mobile", mobileNumber.trim())
                .putBoolean("is_mobile_verified", true)
                .apply()
            updateSession(auth.currentUser)
            return true
        }
        return false
    }

    fun signOut(scope: CoroutineScope, onComplete: () -> Unit = {}) {
        auth.signOut()
        prefs.edit()
            .remove("verified_mobile")
            .putBoolean("is_mobile_verified", false)
            .apply()

        scope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.e("Auth", "Failed to clear credential state", e)
            } finally {
                updateSession(null)
                onComplete()
            }
        }
    }
}
