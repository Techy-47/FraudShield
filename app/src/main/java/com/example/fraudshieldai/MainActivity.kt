package com.example.fraudshieldai

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
//import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Textsms
import androidx.compose.material3.Icon

class MainActivity : ComponentActivity() {

    private val SMS_PERMISSION_CODE = 100
    private val NOTIFICATION_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        requestSmsPermission()
        requestNotificationPermission()
        NotificationHelper.createNotificationChannel(this)
        SmsAnalysisState.initialize(this)

        setContent {
            FraudShieldApp()
        }
    }

    override fun onResume() {
        super.onResume()
        requestNotificationPermission()
    }

    private fun requestSmsPermission() {
        val permissions = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                SMS_PERMISSION_CODE
            )
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
    }
}

data class ProtectionState(
    val notificationAccessEnabled: Boolean = false,
    val overlayEnabled: Boolean = false
) {
    val isFullyEnabled: Boolean
        get() = notificationAccessEnabled && overlayEnabled
}

private fun getProtectionState(context: Context): ProtectionState {
    return ProtectionState(
        notificationAccessEnabled = isNotificationAccessEnabled(context),
        overlayEnabled = Settings.canDrawOverlays(context)
    )
}

private fun isNotificationAccessEnabled(context: Context): Boolean {
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false

    val expected = ComponentName(
        context,
        NotificationCaptureService::class.java
    ).flattenToString()

    return enabledListeners.contains(expected)
}

    @Composable
    fun FraudShieldApp() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val uiState by SmsAnalysisState.uiState.collectAsState()

        var protectionState by remember { mutableStateOf(getProtectionState(context)) }
        var selectedTab by remember { mutableStateOf("Home") }

        fun refreshProtectionState() {
            protectionState = getProtectionState(context)
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshProtectionState()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        val riskColor = when (uiState.riskLevel) {
            "HIGH" -> Color(0xFFD32F2F)
            "MEDIUM" -> Color(0xFFF57C00)
            "LOW" -> Color(0xFFFBC02D)
            else -> Color(0xFF388E3C)
        }

        if (!protectionState.isFullyEnabled) {
            SetupRequiredScreen(
                protectionState = protectionState,
                onEnableNotificationAccess = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                onEnableOverlay = {
                    OverlayAlertService.openOverlayPermission(context)
                },
                onRefresh = {
                    refreshProtectionState()
                }
            )
            return
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F7FB))
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                HeaderCard()

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .padding(bottom = 100.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (selectedTab) {
                        "Home" -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(20.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Latest Scan",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Sender: ${uiState.sender}",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = uiState.message)
                                }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(20.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Overall Verdict",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                color = riskColor.copy(alpha = 0.14f),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .padding(16.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = uiState.category,
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = riskColor
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Text(
                                                text = "Scanned at: ${uiState.scannedAt}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(20.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Fraud Analysis",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(riskColor, RoundedCornerShape(14.dp))
                                            .padding(14.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = "Risk Level: ${uiState.riskLevel}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Fraud Score: ${uiState.fraudScore}",
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Category: ${uiState.category}",
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "ML Score: ${uiState.mlScore} | Links: ${uiState.linkCount}",
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(20.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Detection Reasons",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    if (uiState.reasons.isEmpty()) {
                                        Text(text = "No suspicious indicators detected yet.")
                                    } else {
                                        uiState.reasons.forEach { reason ->
                                            Text(
                                                text = "• $reason",
                                                modifier = Modifier.padding(vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        "Scan" -> {
                            SimpleTabCard(
                                title = "Scan",
                                description = "This section can be used for manual scan action and quick security checks."
                            )
                        }

                        "Text" -> {
                            SimpleTabCard(
                                title = "Text",
                                description = "This section can be used to paste suspicious text or messages for fraud analysis."
                            )
                        }

                        "History" -> {
                            HistorySection(history = uiState.history)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            BottomNavCard(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
            )
        }
    }

@Composable
fun SetupRequiredScreen(
    protectionState: ProtectionState,
    onEnableNotificationAccess: () -> Unit,
    onEnableOverlay: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FB))
    ) {
        HeaderCard()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Enable Protection",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF081225)
                    )

                    Text(
                        text = "FraudShield needs 2 quick permissions to detect scam messages in real time and show instant warning alerts.",
                        color = Color.DarkGray,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    PermissionRow(
                        title = "Notification Access",
                        subtitle = "Scan RCS, WhatsApp, and Gmail notifications",
                        enabled = protectionState.notificationAccessEnabled
                    )

                    PermissionRow(
                        title = "Floating Fraud Alert",
                        subtitle = "Show red warning overlay over other apps",
                        enabled = protectionState.overlayEnabled
                    )

                    if (!protectionState.notificationAccessEnabled) {
                        Button(
                            onClick = onEnableNotificationAccess,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1D4ED8)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Enable Notification Access")
                        }
                    }

                    if (!protectionState.overlayEnabled) {
                        Button(
                            onClick = onEnableOverlay,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD32F2F)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Enable Floating Alert")
                        }
                    }

                    Button(
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF081225)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("I Have Enabled Them")
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    subtitle: String,
    enabled: Boolean
) {
    val dotColor = if (enabled) Color(0xFF22C55E) else Color(0xFFD32F2F)
    val statusText = if (enabled) "Enabled" else "Not enabled"

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(10.dp)
                .background(dotColor, CircleShape)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF081225)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = Color.DarkGray,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = statusText,
            color = dotColor,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun ProtectionStatusBanner(
    protectionState: ProtectionState,
    onClick: () -> Unit
) {
    val missingText = buildString {
        if (!protectionState.notificationAccessEnabled) {
            append("Notification access")
        }
        if (!protectionState.overlayEnabled) {
            if (isNotEmpty()) append(" + ")
            append("overlay")
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E5)),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Protection setup incomplete",
                color = Color(0xFFB45309),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Missing: $missingText",
                color = Color(0xFF78350F),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun HeaderCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF081225),
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
            )
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 18.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFF1D4ED8), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🛡",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Fraud Shield",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFF22C55E), CircleShape)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Active Protection",
                            color = Color(0xFF86EFAC),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

//            Spacer(modifier = Modifier.height(16.dp))
//
////            Box(
////                modifier = Modifier
////                    .fillMaxWidth()
////                    .background(
////                        color = Color.White.copy(alpha = 0.08f),
////                        shape = RoundedCornerShape(16.dp)
////                    )
////                    .padding(horizontal = 14.dp, vertical = 14.dp)
////            ) {
////                Text(
////                    text = "AI-powered scam detection for SMS, RCS, WhatsApp and Gmail",
////                    color = Color.White.copy(alpha = 0.88f),
////                    style = MaterialTheme.typography.bodyMedium
////                )
////            }
        }
    }
}

@Composable
fun HistorySection(history: List<SmsHistoryItem>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Scan History",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (history.isEmpty()) {
                Text(text = "No scan history yet.")
            } else {
                history.forEach { item ->
                    HistoryItemCard(item)
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(item: SmsHistoryItem) {
    val riskColor = when (item.riskLevel) {
        "HIGH" -> Color(0xFFD32F2F)
        "MEDIUM" -> Color(0xFFF57C00)
        "LOW" -> Color(0xFFFBC02D)
        else -> Color(0xFF388E3C)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = riskColor.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = item.category,
                color = riskColor,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "Sender: ${item.sender}")
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Risk: ${item.riskLevel} | Score: ${item.fraudScore}")
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "ML Score: ${item.mlScore} | Links: ${item.linkCount}")
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Time: ${item.scannedAt}")
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = item.message.take(100) + if (item.message.length > 100) "..." else "")
        }
    }
}


@Composable
fun BottomNavCard(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(25.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NavIconButton(
                selected = selectedTab == "Home",
                onClick = { onTabSelected("Home") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = "Home"
                )
            }

            NavIconButton(
                selected = selectedTab == "Text",
                onClick = { onTabSelected("Text") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Textsms,
                    contentDescription = "Text"
                )
            }


            NavIconButton(
                selected = selectedTab == "Scan",
                onClick = { onTabSelected("Scan") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = "Scan"
                )
            }

            NavIconButton(
                selected = selectedTab == "History",
                onClick = { onTabSelected("History") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "History"
                )
            }
        }
    }
}

@Composable
fun NavIconButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val containerColor = if (selected) Color.Black else Color.White
    val contentColor = if (selected) Color.White else Color(0xFF080F1A)

    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        content()
    }
}

    @Composable
    fun SimpleTabCard(
        title: String,
        description: String
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    color = Color.DarkGray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }