package com.example.features.startup

import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.core.designsystem.LocalSpacing
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(
    onContinueWithEmail: () -> Unit,
    onGoogleSignInSuccess: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Web OAuth client ID from google-services.json / strings.xml
    val fallbackClientId = "660420696992-12ihv29oqr5i89tqdbdei02pj5kvmct8.apps.googleusercontent.com"
    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    val webClientId = if (resId != 0 && context.getString(resId).isNotBlank()) context.getString(resId) else fallbackClientId

    var isLoading by remember { mutableStateOf(false) }
    var showNoAccountDialog by remember { mutableStateOf(false) }
    val selectedLanguage by com.example.core.language.AppLanguageManager.languageFlow.collectAsState()

    if (showNoAccountDialog) {
        AlertDialog(
            onDismissRequest = { showNoAccountDialog = false },
            title = { Text("No Google Account Found", fontWeight = FontWeight.Bold) },
            text = { Text("No Google account is currently signed in on this device. You can add a Google account in Android Settings, or sign in using Email.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNoAccountDialog = false
                        try {
                            val intent = Intent(Settings.ACTION_ADD_ACCOUNT).apply {
                                putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
                            }
                            context.startActivity(intent)
                        } catch (ex: Exception) {
                            Toast.makeText(context, "Please add a Google account in Settings", Toast.LENGTH_LONG).show()
                        }
                    }
                ) {
                    Text("Add Account", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNoAccountDialog = false
                        onContinueWithEmail()
                    }
                ) {
                    Text("Use Email")
                }
            }
        )
    }

    fun doGoogleSignIn() {
        if (webClientId.isEmpty() || webClientId == "null") {
            Log.e("GoogleSignIn", "Web Client ID is missing or null.")
            Toast.makeText(context, "Google Sign In not configured properly (missing web_client_id)", Toast.LENGTH_LONG).show()
            return
        }

        coroutineScope.launch {
            isLoading = true
            Log.d("GoogleSignIn", "Initiating CredentialManager request. ServerClientId: $webClientId")
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            try {
                val result = credentialManager.getCredential(context = context, request = request)
                val credential = result.credential
                Log.d("GoogleSignIn", "Credential returned successfully of type: ${credential.type}")
                if (credential is androidx.credentials.CustomCredential &&
                    (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
                     credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL)) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    Log.d("GoogleSignIn", "GoogleIdTokenCredential created successfully. ID Token present.")
                    onGoogleSignInSuccess(googleIdTokenCredential.idToken)
                } else {
                    Log.e("GoogleSignIn", "Unexpected credential type received: ${credential.type}")
                    Toast.makeText(context, "Unexpected credential type: ${credential.type}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: GetCredentialCancellationException) {
                Log.i("GoogleSignIn", "Google Sign-In flow cancelled by user.")
                Toast.makeText(context, "Sign in cancelled", Toast.LENGTH_SHORT).show()
            } catch (e: androidx.credentials.exceptions.NoCredentialException) {
                Log.w("GoogleSignIn", "NoCredentialException caught: No Google account on device. Showing account prompt.")
                showNoAccountDialog = true
            } catch (e: GetCredentialException) {
                Log.w("GoogleSignIn", "GetCredentialException caught: [Type: ${e.type}] Message: ${e.message}")
                if (e.type.contains("TYPE_NO_CREDENTIAL")) {
                    showNoAccountDialog = true
                } else if (e.message?.contains("DEVELOPER_ERROR") == true || e.type.contains("DEVELOPER_ERROR") || e.message?.contains("10:") == true) {
                    Toast.makeText(context, "Developer Error (10): Ensure SHA-1 fingerprint is registered in Firebase Console", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Google Sign In Failed: ${e.message ?: e.type}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.w("GoogleSignIn", "Unexpected exception during Google Sign-In: [Class: ${e.javaClass.simpleName}] Message: ${e.message}")
                Toast.makeText(context, "Google Sign In Exception: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isLoading = false
            }
        }
    }

    val pageBackground = Color(0xFFF9F8F6)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pageBackground)
            .testTag("welcome_screen_container")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Section: Language Selector at Top-Right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LanguageSelectorPill(
                    selectedLanguage = selectedLanguage,
                    onLanguageSelect = { com.example.core.language.AppLanguageManager.setLanguage(it) }
                )
            }

            // 2. Center Branding Section: Logo, Name, Accent Bar, Tagline
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                PgLogoGraphic(
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("welcome_brand_icon")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "PG ",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            color = Color(0xFF2563EB),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp,
                            letterSpacing = (-0.5).sp
                        )
                    )
                    Text(
                        text = "Manager",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            color = Color(0xFF111827),
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            letterSpacing = (-0.5).sp
                        )
                    )
                }

                // Subtle blue accent bar
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 6.dp)
                        .width(32.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF3B82F6))
                )

                Text(
                    text = if (selectedLanguage == "తెలుగు") {
                        "మీ PG కోసం ప్రతిదీ.\nఒకే చోట."
                    } else {
                        "Everything for your PG.\nIn one place."
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF4B5563),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 19.sp,
                        fontSize = 14.sp
                    ),
                    modifier = Modifier.testTag("welcome_header_subtitle")
                )
            }

            // 3. PG Building Hero Visual Card (Flexible weight to fit any screen height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                PgBuildingHeroGraphic(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                )
            }

            // 4. Authentication Action Controls & Bottom Supporting Text
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(28.dp)
                            .padding(bottom = 8.dp),
                        color = Color(0xFF2563EB),
                        strokeWidth = 3.dp
                    )
                }

                // Primary Authentication Button: Continue with Google
                Surface(
                    onClick = { doGoogleSignIn() },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(26.dp),
                    color = Color(0xFF111827),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("welcome_google_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        GoogleLogoIcon(modifier = Modifier.size(28.dp))

                        Text(
                            text = if (selectedLanguage == "తెలుగు") "గూగుల్ తో కొనసాగించండి" else "Continue with Google",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.5.sp
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Secondary Authentication Button: Continue with Email
                Surface(
                    onClick = onContinueWithEmail,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(26.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("welcome_email_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Mail,
                            contentDescription = "Email",
                            tint = Color(0xFF374151),
                            modifier = Modifier.size(20.dp)
                        )

                        Text(
                            text = if (selectedLanguage == "తెలుగు") "ఈమెయిల్ తో కొనసాగించండి" else "Continue with Email",
                            color = Color(0xFF111827),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.5.sp
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFF111827),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Supporting Text
                Text(
                    text = if (selectedLanguage == "తెలుగు") {
                        "అద్దెదారులను నిర్వహించండి • అద్దె వసూలు • మీ PG ట్రాక్ చేయండి"
                    } else {
                        "Manage tenants • Collect rent • Track your PG"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF6B7280),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Pill language selector at top-right [ EN | తెలుగు ]
 */
@Composable
private fun LanguageSelectorPill(
    selectedLanguage: String,
    onLanguageSelect: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF3F0EA),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8E3D9)),
        modifier = Modifier.height(38.dp)
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LanguageItem(
                text = "EN",
                isSelected = selectedLanguage == "EN",
                onClick = { onLanguageSelect("EN") }
            )
            Spacer(modifier = Modifier.width(2.dp))
            LanguageItem(
                text = "తెలుగు",
                isSelected = selectedLanguage == "తెలుగు",
                onClick = { onLanguageSelect("తెలుగు") }
            )
        }
    }
}

@Composable
private fun LanguageItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF0F172A) else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "langBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color(0xFF334155),
        animationSpec = tween(durationMillis = 200),
        label = "langText"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp,
                color = textColor
            )
        )
    }
}

/**
 * PG Manager Icon: House + Multi-story Building with windows
 */
@Composable
private fun PgLogoGraphic(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val blueLight = Color(0xFF60A5FA)
        val bluePrimary = Color(0xFF2563EB)
        val blueDark = Color(0xFF1D4ED8)

        // Left building / house with pitched roof
        val housePath = Path().apply {
            moveTo(w * 0.44f, h * 0.18f)
            lineTo(w * 0.12f, h * 0.44f)
            lineTo(w * 0.12f, h * 0.88f)
            lineTo(w * 0.28f, h * 0.88f)
            lineTo(w * 0.28f, h * 0.58f)
            lineTo(w * 0.44f, h * 0.58f)
            lineTo(w * 0.44f, h * 0.88f)
            lineTo(w * 0.48f, h * 0.88f)
            lineTo(w * 0.48f, h * 0.38f)
            close()
        }

        drawPath(
            path = housePath,
            brush = Brush.verticalGradient(
                colors = listOf(blueLight, bluePrimary),
                startY = 0f,
                endY = h
            )
        )

        // Right multi-story high rise building
        val highRisePath = Path().apply {
            moveTo(w * 0.54f, h * 0.14f)
            lineTo(w * 0.88f, h * 0.14f)
            lineTo(w * 0.88f, h * 0.88f)
            lineTo(w * 0.54f, h * 0.88f)
            close()
        }

        drawPath(
            path = highRisePath,
            brush = Brush.verticalGradient(
                colors = listOf(bluePrimary, blueDark),
                startY = 0f,
                endY = h
            )
        )

        // Windows in right high-rise building
        val windowColor = Color.White.copy(alpha = 0.95f)
        val winW = w * 0.09f
        val winH = h * 0.12f

        // Top window
        drawRoundRect(
            color = windowColor,
            topLeft = Offset(w * 0.65f, h * 0.24f),
            size = Size(winW, winH),
            cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
        )

        // Middle window
        drawRoundRect(
            color = windowColor,
            topLeft = Offset(w * 0.65f, h * 0.44f),
            size = Size(winW, winH),
            cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
        )

        // Bottom window
        drawRoundRect(
            color = windowColor,
            topLeft = Offset(w * 0.65f, h * 0.64f),
            size = Size(winW, winH),
            cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
        )
    }
}

/**
 * Clean 4-color Google 'G' icon badge
 */
@Composable
private fun GoogleLogoIcon(modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = Color.White,
        modifier = modifier
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = size.width * 0.42f
            val strokeW = size.width * 0.20f

            // Red arc (Top)
            drawArc(
                color = Color(0xFFEA4335),
                startAngle = 195f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeW, cap = StrokeCap.Butt)
            )

            // Yellow arc (Left)
            drawArc(
                color = Color(0xFFFBBC05),
                startAngle = 135f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeW, cap = StrokeCap.Butt)
            )

            // Green arc (Bottom)
            drawArc(
                color = Color(0xFF34A853),
                startAngle = 45f,
                sweepAngle = 95f,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeW, cap = StrokeCap.Butt)
            )

            // Blue arc & bar (Right / Center)
            drawArc(
                color = Color(0xFF4285F4),
                startAngle = 315f,
                sweepAngle = 95f,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeW, cap = StrokeCap.Butt)
            )

            // Blue center horizontal bar
            drawLine(
                color = Color(0xFF4285F4),
                start = Offset(cx - radius * 0.15f, cy),
                end = Offset(cx + radius + strokeW * 0.15f, cy),
                strokeWidth = strokeW,
                cap = StrokeCap.Square
            )
        }
    }
}

/**
 * Stylized PG Architectural Building Illustration matching the design aesthetics
 */
@Composable
private fun PgBuildingHeroGraphic(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFF2E6),
                        Color(0xFFFFE8D6),
                        Color(0xFFF3ECE4),
                        Color(0xFFF9F8F6)
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Soft evening sky glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFD4B2).copy(alpha = 0.7f), Color.Transparent),
                    center = Offset(w * 0.25f, h * 0.35f),
                    radius = w * 0.55f
                )
            )

            // Distant background architecture & trees
            drawRect(
                color = Color(0xFFE2D6CA),
                topLeft = Offset(w * 0.05f, h * 0.62f),
                size = Size(w * 0.32f, h * 0.25f)
            )
            drawCircle(
                color = Color(0xFF7C9070).copy(alpha = 0.5f),
                center = Offset(w * 0.18f, h * 0.65f),
                radius = w * 0.12f
            )
            drawCircle(
                color = Color(0xFF5B7050).copy(alpha = 0.6f),
                center = Offset(w * 0.30f, h * 0.60f),
                radius = w * 0.14f
            )

            // Modern Main Building Structure (Right 65% of screen)
            val bLeft = w * 0.44f
            val bTop = h * 0.08f
            val bWidth = w * 0.56f
            val bHeight = h * 0.92f

            // Building base wall (Warm concrete off-white)
            drawRect(
                color = Color(0xFFEDE7DF),
                topLeft = Offset(bLeft, bTop),
                size = Size(bWidth, bHeight)
            )

            // Wooden Louver / Vertical Slats section (Left side of building)
            val slatsLeft = bLeft + w * 0.03f
            val slatsWidth = w * 0.12f
            val slatsHeight = h * 0.46f
            val slatsTop = bTop + h * 0.06f

            drawRect(
                color = Color(0xFF8B5A2B),
                topLeft = Offset(slatsLeft, slatsTop),
                size = Size(slatsWidth, slatsHeight)
            )
            // Vertical slatted lines
            for (i in 0..6) {
                val lineX = slatsLeft + (slatsWidth / 6f) * i
                drawLine(
                    color = Color(0xFF5C3A1E),
                    start = Offset(lineX, slatsTop),
                    end = Offset(lineX, slatsTop + slatsHeight),
                    strokeWidth = 2f
                )
            }

            // Top Balcony & Warm Lit Room
            val topBalcLeft = bLeft + w * 0.17f
            val topBalcTop = bTop + h * 0.06f
            val topBalcWidth = w * 0.36f
            val topBalcHeight = h * 0.22f

            // Warm interior lighting glow
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFEEAA), Color(0xFFFFCC66)),
                    center = Offset(topBalcLeft + topBalcWidth * 0.6f, topBalcTop + topBalcHeight * 0.4f),
                    radius = topBalcWidth * 0.7f
                ),
                topLeft = Offset(topBalcLeft, topBalcTop),
                size = Size(topBalcWidth, topBalcHeight)
            )

            // Balcony black railing
            drawRect(
                color = Color(0xFF1F2937),
                topLeft = Offset(topBalcLeft, topBalcTop + topBalcHeight * 0.65f),
                size = Size(topBalcWidth, topBalcHeight * 0.35f),
                style = Stroke(width = 3f)
            )
            for (i in 0..8) {
                val rx = topBalcLeft + (topBalcWidth / 8f) * i
                drawLine(
                    color = Color(0xFF1F2937),
                    start = Offset(rx, topBalcTop + topBalcHeight * 0.65f),
                    end = Offset(rx, topBalcTop + topBalcHeight),
                    strokeWidth = 2f
                )
            }

            // Middle Balcony & Warm Lit Room
            val midBalcTop = topBalcTop + topBalcHeight + h * 0.04f
            val midBalcHeight = h * 0.22f

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFEEAA), Color(0xFFFFCC66)),
                    center = Offset(topBalcLeft + topBalcWidth * 0.6f, midBalcTop + midBalcHeight * 0.4f),
                    radius = topBalcWidth * 0.7f
                ),
                topLeft = Offset(topBalcLeft, midBalcTop),
                size = Size(topBalcWidth, midBalcHeight)
            )

            // Middle Balcony Railing
            drawRect(
                color = Color(0xFF1F2937),
                topLeft = Offset(topBalcLeft, midBalcTop + midBalcHeight * 0.65f),
                size = Size(topBalcWidth, midBalcHeight * 0.35f),
                style = Stroke(width = 3f)
            )
            for (i in 0..8) {
                val rx = topBalcLeft + (topBalcWidth / 8f) * i
                drawLine(
                    color = Color(0xFF1F2937),
                    start = Offset(rx, midBalcTop + midBalcHeight * 0.65f),
                    end = Offset(rx, midBalcTop + midBalcHeight),
                    strokeWidth = 2f
                )
            }

            // Foreground Entrance Wall with illuminated "PG" Sign
            val wallTop = h * 0.60f
            val wallLeft = w * 0.16f
            val wallWidth = w * 0.84f
            val wallHeight = h * 0.40f

            drawRect(
                color = Color(0xFFF3ECE4),
                topLeft = Offset(wallLeft, wallTop),
                size = Size(wallWidth, wallHeight)
            )

            // Entrance Black Gate
            val gateLeft = bLeft - w * 0.05f
            val gateTop = wallTop - h * 0.04f
            val gateWidth = w * 0.16f
            val gateHeight = wallHeight + h * 0.04f

            drawRect(
                color = Color(0xFF111827),
                topLeft = Offset(gateLeft, gateTop),
                size = Size(gateWidth, gateHeight)
            )
            for (i in 0..4) {
                val gx = gateLeft + (gateWidth / 4f) * i
                drawLine(
                    color = Color(0xFF374151),
                    start = Offset(gx, gateTop),
                    end = Offset(gx, gateTop + gateHeight),
                    strokeWidth = 2f
                )
            }

            // Warm Backlit "PG" Wall letters
            val pgSignX = w * 0.72f
            val pgSignY = wallTop + h * 0.07f

            // Golden lighting glow behind PG sign
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFDF9E), Color(0xFFFFBE53).copy(alpha = 0.8f), Color.Transparent),
                    center = Offset(pgSignX + w * 0.08f, pgSignY + h * 0.05f),
                    radius = w * 0.16f
                )
            )

            // Warm Wall Sconce Lamp
            drawRoundRect(
                color = Color(0xFF1F2937),
                topLeft = Offset(bLeft + w * 0.02f, wallTop + h * 0.06f),
                size = Size(w * 0.025f, h * 0.04f),
                cornerRadius = CornerRadius(2f, 2f)
            )

            // Lush greenery and shrub planters in front
            val shrubY = wallTop + h * 0.22f
            drawCircle(color = Color(0xFF2D5A27), center = Offset(w * 0.20f, shrubY), radius = w * 0.08f)
            drawCircle(color = Color(0xFF3F7A34), center = Offset(w * 0.28f, shrubY - h * 0.02f), radius = w * 0.09f)
            drawCircle(color = Color(0xFF234B20), center = Offset(w * 0.36f, shrubY), radius = w * 0.08f)
            drawCircle(color = Color(0xFF4C8C3E), center = Offset(w * 0.44f, shrubY - h * 0.01f), radius = w * 0.07f)
            drawCircle(color = Color(0xFF356B2E), center = Offset(w * 0.88f, shrubY + h * 0.04f), radius = w * 0.12f)

            // Warm landscape spotlight beams at base
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFEEBB).copy(alpha = 0.9f), Color.Transparent),
                    center = Offset(w * 0.32f, shrubY + h * 0.04f),
                    radius = w * 0.12f
                )
            )

            // Bottom Gradient Fade (Smooth transition into page background)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFFF9F8F6).copy(alpha = 0.4f),
                        Color(0xFFF9F8F6).copy(alpha = 0.95f),
                        Color(0xFFF9F8F6)
                    ),
                    startY = h * 0.65f,
                    endY = h
                ),
                topLeft = Offset(0f, h * 0.65f),
                size = Size(w, h * 0.35f)
            )
        }

        // Stylized "Manage Better" Badge on Left
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp, top = 20.dp)
        ) {
            Column {
                Text(
                    text = "Manage\nBetter",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color(0xFF78716C),
                        fontWeight = FontWeight.Light,
                        fontStyle = FontStyle.Italic,
                        fontSize = 20.sp,
                        lineHeight = 22.sp,
                        fontFamily = FontFamily.Cursive
                    )
                )
                // Decorative underline swoosh
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(42.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color(0xFFBCAAA4))
                )
            }
        }

        // Backlit "PG" text in entrance wall
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 46.dp, bottom = 44.dp)
        ) {
            Text(
                text = "PG",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = Color(0xFF1F2937),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                    letterSpacing = 2.sp
                )
            )
        }
    }
}

