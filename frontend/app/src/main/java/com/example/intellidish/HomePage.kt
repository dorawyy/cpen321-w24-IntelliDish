package com.example.intellidish

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.card.MaterialCardView
import com.example.intellidish.utils.UserManager
import com.google.android.material.snackbar.Snackbar

class HomePage : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        // Make sure UserManager is initialized
        if (!UserManager.isInitialized()) {
            UserManager.init(applicationContext)
        }

        // Check if user is logged in, if not redirect to login
        if (!UserManager.isUserLoggedIn()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // Set welcome message with user's name
        val userName = UserManager.getUserName() ?: "User"
        findViewById<TextView>(R.id.welcome_text).text = "Welcome back, $userName!"

        // Initialize Google Sign-In Client
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Set up button click listeners
        findViewById<MaterialCardView>(R.id.btn_get_recommendations).setOnClickListener {
            startActivity(Intent(this, RecommendationActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.btn_my_recipes).setOnClickListener {
            startActivity(Intent(this, ManageRecipes::class.java))
        }

        findViewById<MaterialCardView>(R.id.btn_manage_friends).setOnClickListener {
            if (UserManager.isUserLoggedIn()) {
                val userEmail = UserManager.getUserEmail()
                startActivity(Intent(this, ManageFriends::class.java).apply {
                    putExtra("userEmail", userEmail)
                })
            } else {
                Snackbar.make(findViewById(android.R.id.content), "Please sign in to access this feature", Snackbar.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }

        findViewById<MaterialCardView>(R.id.btn_potluck).setOnClickListener {
            startActivity(Intent(this, PotluckActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.btn_sign_out).setOnClickListener {
            signOut()
        }
    }

    private fun signOut() {
        googleSignInClient.signOut().addOnCompleteListener {
            UserManager.clearUserInfo() // Clear user info when signing out
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("SHOW_SIGNOUT_SNACKBAR", true) // Send flag to MainActivity
            }
            startActivity(intent)
            finish()
        }
    }
}
