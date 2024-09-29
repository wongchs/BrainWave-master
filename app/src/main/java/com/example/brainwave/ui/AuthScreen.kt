package com.example.brainwave.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    var isLogin by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        if (isLogin) {
            LoginScreen(onLoginSuccess = onAuthSuccess)
        } else {
            SignupScreen(onSignupSuccess = onAuthSuccess)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = { isLogin = !isLogin }
        ) {
            Text(if (isLogin) "Need an account? Sign up" else "Already have an account? Log in")
        }
    }
}

@Composable
fun SignupScreen(onSignupSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Column {
        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            isError = errorMessage != null
        )
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            isError = errorMessage != null
        )
        if (errorMessage != null) {
            Text(errorMessage!!, color = Color.Red)
        }
        Button(
            onClick = {
                isLoading = true
                errorMessage = null
                if (isValidEmail(email) && isValidPassword(password)) {
                    FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            isLoading = false
                            if (task.isSuccessful) {
                                onSignupSuccess()
                            } else {
                                errorMessage = task.exception?.message ?: "Signup failed"
                            }
                        }
                } else {
                    isLoading = false
                    errorMessage = "Invalid email or password"
                }
            },
            enabled = !isLoading
        ) {
            if (isLoading) CircularProgressIndicator() else Text("Sign Up")
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Column {
        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            isError = errorMessage != null
        )
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            isError = errorMessage != null
        )
        if (errorMessage != null) {
            Text(errorMessage!!, color = Color.Red)
        }
        Button(
            onClick = {
                isLoading = true
                errorMessage = null
                FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        isLoading = false
                        if (task.isSuccessful) {
                            onLoginSuccess()
                        } else {
                            errorMessage = task.exception?.message ?: "Login failed"
                        }
                    }
            },
            enabled = !isLoading
        ) {
            if (isLoading) CircularProgressIndicator() else Text("Log In")
        }
    }
}

fun isValidEmail(email: String): Boolean {
    return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
}

fun isValidPassword(password: String): Boolean {
    return password.length >= 6 // You can add more password strength checks here
}