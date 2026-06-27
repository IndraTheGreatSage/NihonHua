@file:Suppress("DEPRECATION")
package com.example

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AnimeViewModel
import com.example.ui.MainAppNavigation
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class MainActivity : ComponentActivity() {

    @Suppress("DEPRECATION")
    private var googleSignInClient: GoogleSignInClient? = null
    private lateinit var animeViewModel: AnimeViewModel

    @Suppress("DEPRECATION")
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                // Kirim ID Token ke ViewModel untuk diproses Firebase Auth
                animeViewModel.handleFirebaseGoogleLogin(idToken) { _, msg ->
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Gagal mendapatkan token Google.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            Toast.makeText(this, "Google Sign-In gagal: ${e.statusCode}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deleteSharedPreferences("crash_logs")

        enableEdgeToEdge()

        // Setup Google Sign-In safely
        try {
            @SuppressLint("DiscouragedApi")
            val clientId = getString(resources.getIdentifier("default_web_client_id", "string", packageName))
            @Suppress("DEPRECATION")
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(clientId)
                .requestEmail()
                .build()
            @Suppress("DEPRECATION")
            googleSignInClient = GoogleSignIn.getClient(this, gso)
        } catch (e: Exception) {
            Toast.makeText(this, "Google Sign-In tidak tersedia: ${e.message}", Toast.LENGTH_LONG).show()
        }

        setContent {
            animeViewModel = viewModel()
            val isDarkMode by animeViewModel.isDarkMode.collectAsState()

            MyApplicationTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppNavigation(
                        viewModel = animeViewModel,
                        onGoogleSignInRequest = { launchGoogleSignIn() },
                        onBackPressed = { finish() }
                    )
                }
            }
        }
    }

    private fun launchGoogleSignIn() {
        val client = googleSignInClient
        if (client == null) {
            Toast.makeText(this, "Google Sign-In belum siap. Coba lagi.", Toast.LENGTH_SHORT).show()
            return
        }
        val signInIntent = client.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }
}
