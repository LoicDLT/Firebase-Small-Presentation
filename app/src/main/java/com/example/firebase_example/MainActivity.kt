package com.example.firebase_example

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore


class MainActivity : AppCompatActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var auth: FirebaseAuth
    private lateinit var emailTextView: TextView
    private lateinit var profileImageView: ImageView
    private lateinit var logoutButton: ImageButton
    private lateinit var displayNameEditText: EditText
    private lateinit var editDisplayNameButton: ImageButton
    private lateinit var saveDisplayNameButton: ImageButton
    private lateinit var cancelDisplayNameButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        emailTextView = findViewById(R.id.emailTextView)
        profileImageView = findViewById(R.id.profileImageView)
        logoutButton = findViewById(R.id.logoutButton)
        displayNameEditText = findViewById(R.id.displayNameEditText)
        editDisplayNameButton = findViewById(R.id.editDisplayNameButton)
        saveDisplayNameButton = findViewById(R.id.saveDisplayNameButton)
        cancelDisplayNameButton = findViewById(R.id.cancelDisplayNameButton)

        logoutButton.setOnClickListener {
            signOut()
        }

        editDisplayNameButton.setOnClickListener {
            toggleEditing(true)
        }

        saveDisplayNameButton.setOnClickListener {
            val displayName = displayNameEditText.text.toString()
            updateDisplayName(displayName)
            toggleEditing(false)
        }

        cancelDisplayNameButton.setOnClickListener {
            toggleEditing(false)
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)


        findViewById<SignInButton>(R.id.googleSignInButton).setOnClickListener {
            signIn()
        }


        val currentUser = auth.currentUser
        updateUI(currentUser)
    }


    private fun toggleEditing(enable: Boolean) {
        displayNameEditText.isEnabled = enable
        editDisplayNameButton.visibility = if (enable) View.GONE else View.VISIBLE
        saveDisplayNameButton.visibility = if (enable) View.VISIBLE else View.GONE
        cancelDisplayNameButton.visibility = if (enable) View.VISIBLE else View.GONE
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            emailTextView.text = user.email
            emailTextView.visibility = View.VISIBLE
            profileImageView.visibility = View.VISIBLE
            logoutButton.visibility = View.VISIBLE
            displayNameEditText.visibility = View.VISIBLE
            editDisplayNameButton.visibility = View.VISIBLE
            findViewById<SignInButton>(R.id.googleSignInButton).visibility = View.GONE

            user.photoUrl?.let { uri ->
                Glide.with(this).load(uri).into(profileImageView)
            }

            fetchDisplayName(user.uid)

            toggleEditing(false)
        } else {
            emailTextView.visibility = View.GONE
            profileImageView.visibility = View.GONE
            logoutButton.visibility = View.GONE
            displayNameEditText.visibility = View.GONE
            editDisplayNameButton.visibility = View.GONE
            saveDisplayNameButton.visibility = View.GONE
            cancelDisplayNameButton.visibility = View.GONE
            findViewById<SignInButton>(R.id.googleSignInButton).visibility = View.VISIBLE
        }
    }

    private fun fetchDisplayName(userId: String) {
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(userId)

        userRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val displayName = document.getString("displayName") ?: "Username"
                    displayNameEditText.setText(displayName)
                } else {
                    displayNameEditText.setText("Username")
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to fetch display name: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private fun updateDisplayName(displayName: String) {
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(auth.currentUser!!.uid)

        userRef.update("displayName", displayName)
            .addOnSuccessListener {
                Toast.makeText(this, "Name updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update name: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account)
            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun signOut() {
        auth.signOut()
        googleSignInClient.signOut().addOnCompleteListener(this) {
            updateUI(null)
        }
    }


    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let {
                        saveUserToFirestore(it)
                    }
                    updateUI(user)
                } else {
                    updateUI(null)
                }
            }
    }

    private fun saveUserToFirestore(user: FirebaseUser) {
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(user.uid)

        userRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    Toast.makeText(this, "Successfully logged in!", Toast.LENGTH_SHORT).show()
                } else {
                    val userData = hashMapOf(
                        "email" to user.email,
                        "profileImageUrl" to user.photoUrl.toString(),
                        "displayName" to "Username"
                    )

                    userRef.set(userData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Successfully registered", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Registration failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to check user: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }




    companion object {
        private const val RC_SIGN_IN = 9001
    }
}
