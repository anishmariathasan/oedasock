package com.example.cuff.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.example.cuff.MainActivity
import com.example.cuff.R

class LoginActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var weightEditText: EditText
    private lateinit var heightEditText: EditText
    private lateinit var ageEditText: EditText
    private lateinit var genderEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)

        emailEditText = findViewById(R.id.emailEditText)
        weightEditText = findViewById(R.id.weightEditText)
        heightEditText = findViewById(R.id.heightEditText)
        ageEditText = findViewById(R.id.ageEditText)
        genderEditText = findViewById(R.id.genderEditText)
        loginButton = findViewById(R.id.loginButton)

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val weight = weightEditText.text.toString().trim().toFloatOrNull()
            val height = heightEditText.text.toString().trim().toFloatOrNull()
            val age = ageEditText.text.toString().trim().toIntOrNull()
            val gender = genderEditText.text.toString().trim()

            if (email.isEmpty() || weight == null || height == null || age == null || gender.isEmpty()) {
                Toast.makeText(this, "Please fill all fields correctly", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save user details to Firestore under the "users" collection
            val db = FirebaseFirestore.getInstance()
            val user = hashMapOf(
                "email" to email,
                "weight" to weight,
                "height" to height,
                "age" to age,
                "gender" to gender,
                "timestamp" to System.currentTimeMillis()
            )
            db.collection("users").document(email)
                .set(user)
                .addOnSuccessListener {
                    // Save user email locally to remember login state
                    prefs.edit().putString("userEmail", email).apply()
                    Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
