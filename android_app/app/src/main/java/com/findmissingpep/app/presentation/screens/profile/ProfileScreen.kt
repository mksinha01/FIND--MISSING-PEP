package com.findmissingpep.app.presentation.screens.profile

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.findmissingpep.app.BuildConfig
import com.findmissingpep.app.domain.model.UserRole
import com.findmissingpep.app.presentation.common.PaperCard
import com.findmissingpep.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSignOutDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = CreamBackground,
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = PaperWhite,
                border = BorderStroke(1.dp, LineBorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = InkText
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Your account",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = InkText
                        )
                        Text(
                            text = "Profile & preferences",
                            fontSize = 11.sp,
                            color = InkMuted
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Profile Header Card
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(BlueWash),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = BluePrimary,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.user?.name ?: "User Account",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = InkText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = uiState.user?.email ?: "email@example.com",
                            fontSize = 12.sp,
                            color = InkMuted
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = BlueWash
                        ) {
                            Text(
                                text = if (uiState.user?.role == UserRole.OFFICER) "POLICE OFFICER · CONTROL ROOM" else "CITIZEN REPORTER",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlueDeep,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Language Preference Card
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Language preference",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkText
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isEnglish = uiState.selectedLanguage == "en"
                    Surface(
                        onClick = { viewModel.setLanguage("en") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(9.dp),
                        color = if (isEnglish) BlueWash else PaperWhite,
                        border = BorderStroke(1.dp, if (isEnglish) BluePrimary else LineBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("English", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = InkText)
                            Text("अंग्रेज़ी", fontSize = 10.sp, color = InkMuted)
                            Text(if (isEnglish) "Selected" else "Choose", fontSize = 9.sp, color = if (isEnglish) BluePrimary else InkSubtle)
                        }
                    }

                    val isHindi = uiState.selectedLanguage == "hi"
                    Surface(
                        onClick = { viewModel.setLanguage("hi") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(9.dp),
                        color = if (isHindi) BlueWash else PaperWhite,
                        border = BorderStroke(1.dp, if (isHindi) BluePrimary else LineBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("हिन्दी", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = InkText)
                            Text("Hindi", fontSize = 10.sp, color = InkMuted)
                            Text(if (isHindi) "Selected" else "Choose", fontSize = 9.sp, color = if (isHindi) BluePrimary else InkSubtle)
                        }
                    }
                }
            }

            // System & Connection Details
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "System connection",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkText
                )
                Spacer(modifier = Modifier.height(8.dp))

                DetailRow(label = "Database Server", value = BuildConfig.API_BASE_URL)
                DetailRow(label = "Application Version", value = "1.0.0 (Production)")
                DetailRow(label = "Status", value = "Connected to Official Registry")
            }

            // Sign Out Button
            OutlinedButton(
                onClick = { showSignOutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = PaperWhite,
                    contentColor = RedDanger
                ),
                border = BorderStroke(1.dp, RedDanger.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null, tint = RedDanger, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sign out of account", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = RedDanger)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out", color = InkText, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text("Are you sure you want to sign out of your account?", color = InkMuted, fontSize = 13.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        viewModel.signOut { onSignOut() }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedDanger, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Sign out", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Stay signed in", color = InkMuted)
                }
            },
            containerColor = PaperWhite,
            shape = RoundedCornerShape(14.dp)
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 11.sp, color = InkMuted)
        Text(text = value, fontSize = 11.sp, color = InkText, fontWeight = FontWeight.Bold)
    }
}
