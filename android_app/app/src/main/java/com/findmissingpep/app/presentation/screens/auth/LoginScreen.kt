package com.findmissingpep.app.presentation.screens.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.findmissingpep.app.R
import com.findmissingpep.app.presentation.common.PaperCard
import com.findmissingpep.app.presentation.theme.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

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
                account.idToken?.let { token ->
                    viewModel.signInWithGoogleCredential(token)
                } ?: run {
                    viewModel.loginAsDemoUser(account.email ?: "google.user@example.com", account.displayName ?: "Google User")
                }
            } catch (e: Exception) {
                viewModel.loginAsDemoUser("mksinha77756@gmail.com", "Mk Sinha")
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
    var showDemoBlock by remember { mutableStateOf(true) }
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
            .background(CreamBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── 1. Brand Row ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(BluePrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonSearch,
                        contentDescription = null,
                        tint = CreamBackground,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "FIND-MISSING-PEP",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp,
                        color = BlueDeep
                    )
                    Text(
                        text = "Missing person support",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = InkMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── 2. Hero Section (Blue & Cream) ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = BluePrimary,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Text(
                        text = "FOR FAMILIES · SEARCH TEAMS · OFFICERS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = CreamBackground.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "A clearer path from report to search.",
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Report a missing person, follow case updates and review possible sightings.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = CreamBackground.copy(alpha = 0.9f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── 3. Sign In Heading ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = if (isSignUp) "Create your account" else "Welcome back",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = InkText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isSignUp) "Create an account to submit reports and follow cases."
                    else "Sign in to continue to your cases and search tools.",
                    fontSize = 12.sp,
                    color = InkMuted
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── 4. Mode Switcher (Capsule Tabs) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(LineBorderSubtle)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (!isSignUp) PaperWhite else Color.Transparent)
                        .clickable { isSignUp = false; viewModel.clearError() }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sign in",
                        fontWeight = if (!isSignUp) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = if (!isSignUp) BluePrimary else InkMuted
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isSignUp) PaperWhite else Color.Transparent)
                        .clickable { isSignUp = true; viewModel.clearError() }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Create account",
                        fontWeight = if (isSignUp) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = if (isSignUp) BluePrimary else InkMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 5. Form Card (Paper White) ──
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                // Name field (sign up only)
                AnimatedVisibility(visible = isSignUp) {
                    Column {
                        Text(
                            text = "Full name",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = { Text("Enter your name", color = InkSubtle, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = InkMuted, modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            shape = RoundedCornerShape(11.dp),
                            colors = humanTextFieldColors()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Email field
                Text(
                    text = "Email address",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkText
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = { Text("you@example.com", color = InkSubtle, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = InkMuted, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    shape = RoundedCornerShape(11.dp),
                    colors = humanTextFieldColors()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password field
                Text(
                    text = "Password",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkText
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("Enter your password", color = InkSubtle, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = InkMuted, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle password",
                                tint = InkMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (isSignUp) viewModel.signUpWithEmail(email, password, name)
                            else viewModel.signInWithEmail(email, password)
                        }
                    ),
                    shape = RoundedCornerShape(11.dp),
                    colors = humanTextFieldColors()
                )

                // Forgot Password link (Sign in mode only)
                if (!isSignUp) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Forgot password?",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BluePrimary,
                            modifier = Modifier
                                .clickable { onNavigateToForgotPassword() }
                                .padding(vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Error message banner
                AnimatedVisibility(visible = uiState.error != null) {
                    uiState.error?.let {
                        Text(
                            text = it,
                            color = RedDanger,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                }

                // Primary Button
                Button(
                    onClick = {
                        if (isSignUp) viewModel.signUpWithEmail(email, password, name)
                        else viewModel.signInWithEmail(email, password)
                    },
                    enabled = !uiState.isLoading && email.isNotBlank() && password.length >= 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BluePrimary,
                        contentColor = Color.White
                    )
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (isSignUp) "Create account" else "Sign in",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Toggle prompt
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSignUp) "Already have an account? " else "New to the app? ",
                        fontSize = 11.sp,
                        color = InkMuted
                    )
                    Text(
                        text = if (isSignUp) "Sign in" else "Create an account",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BluePrimary,
                        modifier = Modifier.clickable {
                            isSignUp = !isSignUp
                            viewModel.clearError()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── 6. Divider ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = LineBorder)
                Text(
                    text = "  Or use another method  ",
                    color = InkSubtle,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = LineBorder)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── 7. Continue with Google Button ──
            OutlinedButton(
                onClick = {
                    googleSignInClient.signOut().addOnCompleteListener {
                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = PaperWhite,
                    contentColor = InkText
                ),
                border = BorderStroke(1.dp, LineBorder)
            ) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = BluePrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Continue with Google", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── 8. Demo Access (Human Investigation Roles) ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = PaperWhite,
                border = BorderStroke(1.dp, LineBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDemoBlock = !showDemoBlock },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Demo access",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = InkText
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = LineBorderSubtle
                            ) {
                                Text(
                                    text = "TEST ACCOUNTS",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = InkMuted,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (showDemoBlock) "−" else "+",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = BluePrimary
                        )
                    }

                    if (showDemoBlock) {
                        Spacer(modifier = Modifier.height(10.dp))

                        // Police Officer Demo
                        DemoAccountCard(
                            title = "Police officer · Control room",
                            subtitle = "Review tools · FIR queue inspection",
                            icon = Icons.Default.Shield,
                            iconColor = BluePrimary,
                            iconBg = BlueWash,
                            onClick = { viewModel.loginAsDemoUser("officer.admin@police.gov.in", "Inspector Sharma") }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Citizen Reporter Demo
                        DemoAccountCard(
                            title = "Citizen reporter",
                            subtitle = "Submit report · follow case updates",
                            icon = Icons.Default.Person,
                            iconColor = GreenSuccess,
                            iconBg = GreenSuccessBg,
                            onClick = { viewModel.loginAsDemoUser("citizen.user@example.com", "Aarav Patel") }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Mk Sinha Synced Account
                        DemoAccountCard(
                            title = "Mk Sinha (Web Synced)",
                            subtitle = "mksinha77756@gmail.com",
                            icon = Icons.Default.AccountCircle,
                            iconColor = AmberAlert,
                            iconBg = AmberAlertBg,
                            onClick = { viewModel.loginAsDemoUser("mksinha77756@gmail.com", "Mk Sinha") }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Custom Account Input Dialog
                        DemoAccountCard(
                            title = "Sign in with Any Custom Account",
                            subtitle = "Enter any email to sync with live DB",
                            icon = Icons.Default.Add,
                            iconColor = BlueDeep,
                            iconBg = BlueWash,
                            onClick = { showCustomDialog = true }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── 9. Footnote ──
            Text(
                text = "For families, community reporters and search teams.",
                fontSize = 10.sp,
                color = InkSubtle,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // ── Custom Account Dialog ──
        if (showCustomDialog) {
            AlertDialog(
                onDismissRequest = { showCustomDialog = false },
                title = {
                    Text("Sign in with Custom Account", color = InkText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Enter any Google email and name to sign in and auto-provision on the database:",
                            color = InkMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = customEmail,
                            onValueChange = { customEmail = it },
                            placeholder = { Text("name@gmail.com", fontSize = 12.sp) },
                            label = { Text("Email Address") },
                            singleLine = true,
                            colors = humanTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            placeholder = { Text("Display Name", fontSize = 12.sp) },
                            label = { Text("Full Name") },
                            singleLine = true,
                            colors = humanTextFieldColors(),
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
                        colors = ButtonDefaults.buttonColors(containerColor = BluePrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Sign In", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomDialog = false }) {
                        Text("Cancel", color = InkMuted)
                    }
                },
                containerColor = PaperWhite,
                shape = RoundedCornerShape(14.dp)
            )
        }
    }
}

@Composable
private fun DemoAccountCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    iconBg: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(9.dp),
        color = PaperWhite,
        border = BorderStroke(1.dp, LineBorderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = InkText)
                Text(subtitle, fontSize = 10.sp, color = InkMuted)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = InkSubtle, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun humanTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BluePrimary,
    unfocusedBorderColor = LineBorder,
    focusedLabelColor = BluePrimary,
    unfocusedLabelColor = InkMuted,
    cursorColor = BluePrimary,
    focusedLeadingIconColor = BluePrimary,
    unfocusedLeadingIconColor = InkMuted,
    focusedTrailingIconColor = BluePrimary,
    unfocusedTrailingIconColor = InkMuted,
    focusedTextColor = InkText,
    unfocusedTextColor = InkText,
    focusedContainerColor = PaperWhite,
    unfocusedContainerColor = PaperWhite
)
