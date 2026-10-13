package com.findmissingpep.app.presentation.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.findmissingpep.app.R
import com.findmissingpep.app.presentation.common.GlassCard
import com.findmissingpep.app.presentation.theme.*

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToForgotPassword: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val webClientId = stringResource(R.string.default_web_client_id)

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (!idToken.isNullOrBlank()) {
                    viewModel.signInWithGoogleCredential(idToken)
                } else {
                    viewModel.setAuthError("Google Sign-In completed, but no ID token was received.")
                }
            } catch (e: ApiException) {
                viewModel.setAuthError("Google Sign-In failed (${e.statusCode}): ${e.localizedMessage}")
            }
        }
    }

    val googleSignInClient = remember(webClientId) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var customEmail by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }

    // Navigate on successful auth
    LaunchedEffect(uiState.isAuthenticated) {
        if (uiState.isAuthenticated) onLoginSuccess()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Slate950, Slate900, Slate950)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            // ── App Logo & Title ──
            Icon(
                imageVector = Icons.Default.PersonSearch,
                contentDescription = null,
                tint = CyanPrimary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "AI-Powered Missing Person Detection",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(48.dp))

            // ── Login Card ──
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isSignUp) "Create Account" else "Welcome Back",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Name field (sign up only)
                AnimatedVisibility(visible = isSignUp) {
                    Column {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.full_name)) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            colors = glassTextFieldColors()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Email
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    colors = glassTextFieldColors()
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Password
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = "Toggle password"
                            )
                        }
                    },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (isSignUp) viewModel.signUpWithEmail(email, password, name)
                            else viewModel.signInWithEmail(email, password)
                        }
                    ),
                    colors = glassTextFieldColors()
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Error message
                AnimatedVisibility(visible = uiState.error != null) {
                    uiState.error?.let {
                        Text(
                            text = it,
                            color = RosePrimary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }

                // Sign In / Sign Up Button
                Button(
                    onClick = {
                        if (isSignUp) viewModel.signUpWithEmail(email, password, name)
                        else viewModel.signInWithEmail(email, password)
                    },
                    enabled = !uiState.isLoading && email.isNotBlank() && password.length >= 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = Slate950
                    )
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Slate950,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (isSignUp) "Create Account" else "Sign In",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Toggle sign-in/sign-up
                TextButton(
                    onClick = {
                        isSignUp = !isSignUp
                        viewModel.clearError()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isSignUp) "Already have an account? Sign In"
                               else "Don't have an account? Create One",
                        color = CyanPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Forgot Password link
                if (!isSignUp) {
                    TextButton(
                        onClick = onNavigateToForgotPassword,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Forgot Password?",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Quick Demo Login Options ──
            OutlinedButton(
                onClick = { viewModel.loginAsDemoUser("mksinha77756@gmail.com", "Mk Sinha") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = AmberPrimary.copy(alpha = 0.15f),
                    contentColor = AmberPrimary
                ),
                border = BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.6f))
            ) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = AmberPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("1-Click Login: Mk Sinha (Synced Web Account)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { viewModel.loginAsDemoUser("officer.admin@police.gov.in", "Inspector Sharma") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CyanPrimary.copy(alpha = 0.12f),
                    contentColor = CyanPrimary
                ),
                border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.5f))
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = CyanPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("1-Click Demo Login (Officer / Control Room)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { viewModel.loginAsDemoUser("citizen.user@example.com", "Aarav Patel") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Slate800.copy(alpha = 0.5f),
                    contentColor = TextPrimary
                ),
                border = BorderStroke(1.dp, Slate700)
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = TextSecondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("1-Click Demo Login (Citizen Reporter)", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { showCustomDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Slate900.copy(alpha = 0.6f),
                    contentColor = CyanPrimary
                ),
                border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.4f))
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = CyanPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign in with Any Custom Google Account", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Divider ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Slate700)
                Text(
                    text = "  OR  ",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Slate700)
            }
            Spacer(modifier = Modifier.height(20.dp))

            // ── Real Google Sign-In Button ──
            OutlinedButton(
                onClick = {
                    googleSignInClient.signOut().addOnCompleteListener {
                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextPrimary
                ),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true)
            ) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = TextPrimary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Continue with Google", fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(48.dp))
        }

        if (showCustomDialog) {
            AlertDialog(
                onDismissRequest = { showCustomDialog = false },
                title = {
                    Text("Sign in with Custom Google Account", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Enter any Google email and name to sign in and auto-provision on the production database:",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = customEmail,
                            onValueChange = { customEmail = it },
                            label = { Text("Google Email (e.g. user@gmail.com)") },
                            singleLine = true,
                            colors = glassTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            colors = glassTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (customEmail.isNotBlank()) {
                                showCustomDialog = false
                                viewModel.loginAsDemoUser(
                                    customEmail.trim(),
                                    customName.trim().ifBlank { customEmail.substringBefore("@") }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Slate950)
                    ) {
                        Text("Sign In", fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = Slate900,
                tonalElevation = 8.dp
            )
        }
    }
}

@Composable
private fun glassTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CyanPrimary,
    unfocusedBorderColor = Slate700,
    focusedLabelColor = CyanPrimary,
    unfocusedLabelColor = TextTertiary,
    cursorColor = CyanPrimary,
    focusedLeadingIconColor = CyanPrimary,
    unfocusedLeadingIconColor = TextTertiary,
    focusedTrailingIconColor = TextSecondary,
    unfocusedTrailingIconColor = TextTertiary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
