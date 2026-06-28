package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AnimeViewModel
import com.example.ui.MainAppNavigation
import com.example.ui.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var animeViewModel: AnimeViewModel

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                animeViewModel.handleFirebaseGoogleLogin(idToken) { success, msg ->
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
        enableEdgeToEdge()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            animeViewModel = viewModel()
            val isDarkMode by animeViewModel.isDarkMode.collectAsState()

            // Splash gate — starts true, flips to false when animation finishes
            var splashDone by remember { mutableStateOf(false) }

            MyApplicationTheme(darkTheme = isDarkMode) {
                if (!splashDone) {
                    // Full-screen splash, no Surface wrapping so it owns the whole window
                    SplashScreen(onSplashComplete = { splashDone = true })
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color    = MaterialTheme.colorScheme.background
                    ) {
                        MainAppNavigation(
                            viewModel          = animeViewModel,
                            onGoogleSignInRequest = { launchGoogleSignIn() }
                        )
                    }
                }
            }
        }
    }

    private fun launchGoogleSignIn() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }
}