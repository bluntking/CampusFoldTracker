package com.campusfold.tracker.ui.screens.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusfold.tracker.ai.AiModelCatalog
import com.campusfold.tracker.ai.AiModelStatus
import com.campusfold.tracker.ai.LocalAiManager
import com.campusfold.tracker.ui.TransactionViewModel
import com.campusfold.tracker.ui.components.SuggestedDuplicateCard
import com.campusfold.tracker.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: TransactionViewModel,
    onOnboardingComplete: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentStep by remember { mutableStateOf(OnboardingStep.WELCOME) }
    var userNameInput by remember { mutableStateOf("") }

    // Permissions State
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasSmsPermission = perms[Manifest.permission.READ_SMS] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = perms[Manifest.permission.POST_NOTIFICATIONS] == true ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
    }

    // AI Model State
    val systemRecommendedModel = remember { AiModelCatalog.recommendModel(context) ?: AiModelCatalog.QWEN_1_5B }
    var selectedAiModel by remember { mutableStateOf(systemRecommendedModel) }
    val availableModels = remember { listOf(AiModelCatalog.QWEN_1_5B, AiModelCatalog.QWEN_0_5B) }
    val aiStatus by LocalAiManager.modelStatus.collectAsStateWithLifecycle()
    var isTestingAi by remember { mutableStateOf(false) }
    var aiTestResult by remember { mutableStateOf<Boolean?>(null) }
    var aiTestError by remember { mutableStateOf<String?>(null) }

    // Storage Check
    val freeStorageGb = remember {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
            bytesAvailable / (1024.0 * 1024.0 * 1024.0)
        } catch (_: Exception) {
            4.0
        }
    }

    // Gmail State
    var gmailEmail by remember { mutableStateOf("") }
    var gmailAppPassword by remember { mutableStateOf("") }
    var isVerifyingGmail by remember { mutableStateOf(false) }
    var gmailErrorMessage by remember { mutableStateOf<String?>(null) }
    var gmailSuccess by remember { mutableStateOf(false) }

    // Scanning State
    var selectedScanMonths by remember { mutableIntStateOf(3) }
    var isScanningInbox by remember { mutableStateOf(false) }
    var scanProgressPercent by remember { mutableIntStateOf(0) }
    var scanStatusText by remember { mutableStateOf("Ready to scan") }

    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val vendorMappings by viewModel.vendorMappings.collectAsStateWithLifecycle()
    val suggestedDuplicates by viewModel.suggestedDuplicates.collectAsStateWithLifecycle()
    val smsWorkInfos by viewModel.smsWorkFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(isScanningInbox, smsWorkInfos) {
        if (!isScanningInbox) return@LaunchedEffect
        val info = smsWorkInfos.firstOrNull() ?: return@LaunchedEffect
        when (info.state) {
            androidx.work.WorkInfo.State.RUNNING -> {
                val read = info.progress.getInt(com.campusfold.tracker.worker.SmsScanWorker.PROGRESS_READ, 0)
                val added = info.progress.getInt(com.campusfold.tracker.worker.SmsScanWorker.PROGRESS_ADDED, 0)
                scanProgressPercent = 50
                scanStatusText = if (read > 0) "Scanning SMS... $read read, $added txns found" else "Scanning SMS inbox..."
            }
            androidx.work.WorkInfo.State.SUCCEEDED -> {
                val read = info.outputData.getInt(com.campusfold.tracker.worker.SmsScanWorker.PROGRESS_READ, 0)
                val added = info.outputData.getInt(com.campusfold.tracker.worker.SmsScanWorker.PROGRESS_ADDED, 0)
                scanProgressPercent = 100
                scanStatusText = "Scan Complete! $added transactions found ($read scanned)"
                kotlinx.coroutines.delay(600)
                isScanningInbox = false
                currentStep = OnboardingStep.DISCOVERY_REVIEW
            }
            androidx.work.WorkInfo.State.FAILED, androidx.work.WorkInfo.State.CANCELLED -> {
                scanProgressPercent = 100
                scanStatusText = "Scan finished"
                kotlinx.coroutines.delay(400)
                isScanningInbox = false
                currentStep = OnboardingStep.DISCOVERY_REVIEW
            }
            else -> {}
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (currentStep != OnboardingStep.WELCOME && currentStep != OnboardingStep.COMPLETED) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Step ${currentStep.ordinal} of ${OnboardingStep.values().size - 2}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                val prevIndex = currentStep.ordinal - 1
                                if (prevIndex >= 0) {
                                    currentStep = OnboardingStep.values()[prevIndex]
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + slideInHorizontally { width -> width / 2 }) togetherWith
                            (fadeOut(animationSpec = tween(180)) + slideOutHorizontally { width -> -width / 2 })
                },
                label = "OnboardingStepAnim"
            ) { step ->
                when (step) {
                    // ─────────────────────────────────────────────────────────────
                    // Step (a): WELCOME & DATA PRIVACY
                    // ─────────────────────────────────────────────────────────────
                    OnboardingStep.WELCOME -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 24.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(top = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Text(
                                    text = "Welcome to CampusFold",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "100% Private Campus Expense & Balance Tracker",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // Privacy Guarantees Card
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(16.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        PrivacyBullet(
                                            icon = Icons.Default.Lock,
                                            title = "Your data stays on your phone",
                                            desc = "No external analytics, no user tracking, and no proprietary servers."
                                        )
                                        PrivacyBullet(
                                            icon = Icons.Default.CloudSync,
                                            title = "Direct, secure connections only",
                                            desc = "Data only leaves your phone for optional direct Gmail SSL sync and downloading AI model weights directly from HuggingFace."
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                OutlinedTextField(
                                    value = userNameInput,
                                    onValueChange = { userNameInput = it },
                                    label = { Text("What should we call you? (Optional)") },
                                    placeholder = { Text("e.g. Alex") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Button(
                                onClick = {
                                    if (userNameInput.isNotBlank()) {
                                        viewModel.setUserName(userNameInput.trim())
                                    }
                                    currentStep = OnboardingStep.PERMISSIONS
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Text(
                                    text = "Get Started",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                            }
                        }
                    }

                    // ─────────────────────────────────────────────────────────────
                    // Step (b): PERMISSIONS (SMS & Notifications with 1-line reasons)
                    // ─────────────────────────────────────────────────────────────
                    OnboardingStep.PERMISSIONS -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 24.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = "Permissions",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "CampusFold needs local access to your messages to automatically log bank transactions.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // SMS Permission Card
                                PermissionCard(
                                    icon = Icons.Default.Sms,
                                    title = "SMS Access",
                                    reason = "Reads incoming transaction SMS to track your expenses and account balances automatically.",
                                    isGranted = hasSmsPermission
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Notifications Permission Card
                                PermissionCard(
                                    icon = Icons.Default.Notifications,
                                    title = "Notification Alerts",
                                    reason = "Alerts you before you exceed monthly category budgets and notifies you of friend settlements.",
                                    isGranted = hasNotificationPermission
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (!hasSmsPermission) {
                                    Button(
                                        onClick = {
                                            val permissionsToRequest = mutableListOf(
                                                Manifest.permission.READ_SMS,
                                                Manifest.permission.RECEIVE_SMS
                                            )
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                            permissionLauncher.launch(permissionsToRequest.toTypedArray())
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                    ) {
                                        Text(
                                            "Grant Permissions",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = { currentStep = OnboardingStep.AI_OFFER },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Text(if (hasSmsPermission) "Continue" else "Skip Permissions for Now")
                                }
                            }
                        }
                    }

                    // ─────────────────────────────────────────────────────────────
                    // Step (c): OFFER AI MODEL DOWNLOAD
                    // ─────────────────────────────────────────────────────────────
                    OnboardingStep.AI_OFFER -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 24.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = "Local AI Engine",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Optional on-device AI for categorizing messy UPI transactions and answering campus finance questions.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                // Model Spec Cards (Choice of Recommended vs Light)
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    availableModels.forEach { model ->
                                        val isSelected = selectedAiModel.id == model.id
                                        val isRecommended = model.id == systemRecommendedModel.id
                                        val sizeStr = if (model.id == AiModelCatalog.QWEN_0_5B.id) "~547 MB" else "~1.5 GB"
                                        val ramReq = if (model.id == AiModelCatalog.QWEN_0_5B.id) "Requires 3 GB RAM" else "Requires 6 GB RAM"

                                        Surface(
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(16.dp),
                                            border = androidx.compose.foundation.BorderStroke(
                                                if (isSelected) 2.dp else 1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedAiModel = model }
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        RadioButton(
                                                            selected = isSelected,
                                                            onClick = { selectedAiModel = model },
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Text(
                                                            text = model.displayName,
                                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                    if (isRecommended) {
                                                        Surface(
                                                            color = MaterialTheme.colorScheme.primaryContainer,
                                                            shape = RoundedCornerShape(8.dp)
                                                        ) {
                                                            Text(
                                                                text = "Recommended",
                                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = model.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )

                                                Spacer(modifier = Modifier.height(10.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Size: $sizeStr", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                                    Text(ramReq, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text("%.1f GB free".format(freeStorageGb), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        }
                                    }
                                }

                                // Download Progress (if actively downloading)
                                if (aiStatus is AiModelStatus.Loading) {
                                    val dl = aiStatus as AiModelStatus.Loading
                                    Spacer(modifier = Modifier.height(16.dp))
                                    LinearProgressIndicator(
                                        progress = (dl.progressPercent / 100f).coerceIn(0f, 1f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = dl.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${dl.progressPercent}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (aiStatus is AiModelStatus.Ready) {
                                    Button(
                                        onClick = { currentStep = OnboardingStep.AI_TEST },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                    ) {
                                        Text("Model Ready • Run Test", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                LocalAiManager.downloadModel(context, selectedAiModel)
                                            }
                                        },
                                        enabled = aiStatus !is AiModelStatus.Loading,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        val btnSize = if (selectedAiModel.id == AiModelCatalog.QWEN_0_5B.id) "~547 MB" else "~1.5 GB"
                                        Text("Download ${selectedAiModel.displayName.substringBefore(" ")} ($btnSize)", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                    }
                                }

                                OutlinedButton(
                                    onClick = { currentStep = OnboardingStep.GMAIL_SETUP },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Text("Skip, use without AI")
                                }
                            }
                        }
                    }

                    // ─────────────────────────────────────────────────────────────
                    // Step (d): TEST MODEL SAFELY (CrashLoopGuard & Graceful Bypass)
                    // ─────────────────────────────────────────────────────────────
                    OnboardingStep.AI_TEST -> {
                        LaunchedEffect(Unit) {
                            isTestingAi = true
                            try {
                                val testOk = LocalAiManager.runInferenceTest(context)
                                aiTestResult = testOk
                                if (!testOk) {
                                    aiTestError = "Inference test timed out or memory limit reached."
                                }
                            } catch (e: Exception) {
                                aiTestResult = false
                                aiTestError = e.message ?: "Failed to initialize native engine."
                            } finally {
                                isTestingAi = false
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 24.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(top = 40.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (isTestingAi) {
                                    CircularProgressIndicator(modifier = Modifier.size(56.dp))
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text(
                                        text = "Verifying Engine...",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "CrashLoopGuard is testing model memory safely in an isolated sandbox.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                } else if (aiTestResult == true) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(NeonEmerald.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(32.dp))
                                    }
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = "AI Engine Ready!",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "The model loaded cleanly and passed the inference test.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(NeonAmber.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = NeonAmber, modifier = Modifier.size(32.dp))
                                    }
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = "Proceeding without AI",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Your device had limited free RAM for local model loading. CampusFold will seamlessly use high-speed regex & rule engines instead.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            Button(
                                onClick = { currentStep = OnboardingStep.GMAIL_SETUP },
                                enabled = !isTestingAi,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Text("Continue", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }

                    // ─────────────────────────────────────────────────────────────
                    // Step (e): OPTIONAL GMAIL CONNECTION (with App Password Note)
                    // ─────────────────────────────────────────────────────────────
                    OnboardingStep.GMAIL_SETUP -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 24.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = "Connect Gmail (Optional)",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Sync e-statements, card bills, and net-banking alerts directly over IMAP SSL.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // App Password Instruction Box
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(14.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Key,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "How to create a Google App Password",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "1. Go to myaccount.google.com > Security\n2. Turn on 2-Step Verification\n3. Under 'App Passwords', create a 16-character password for CampusFold.",
                                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = gmailEmail,
                                    onValueChange = { gmailEmail = it },
                                    label = { Text("Gmail Address") },
                                    placeholder = { Text("example@gmail.com") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = gmailAppPassword,
                                    onValueChange = { gmailAppPassword = it },
                                    label = { Text("16-character App Password") },
                                    placeholder = { Text("xxxx xxxx xxxx xxxx") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (gmailErrorMessage != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = gmailErrorMessage!!,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (gmailEmail.isNotBlank() && gmailAppPassword.isNotBlank()) {
                                    Button(
                                        onClick = {
                                            isVerifyingGmail = true
                                            coroutineScope.launch {
                                                viewModel.saveGmailCredentials(gmailEmail.trim(), gmailAppPassword.trim())
                                                delay(600)
                                                isVerifyingGmail = false
                                                currentStep = OnboardingStep.SCAN_MESSAGES
                                            }
                                        },
                                        enabled = !isVerifyingGmail,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                    ) {
                                        Text("Save & Continue", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                    }
                                }

                                OutlinedButton(
                                    onClick = { currentStep = OnboardingStep.SCAN_MESSAGES },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Text("Skip Gmail Setup")
                                }
                            }
                        }
                    }

                    // ─────────────────────────────────────────────────────────────
                    // Step (f): "SCAN MY MESSAGES" (1, 3, or 6 months with progress)
                    // ─────────────────────────────────────────────────────────────
                    OnboardingStep.SCAN_MESSAGES -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 24.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = "Scan Past Messages",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Choose how far back CampusFold should scan to detect your bank accounts, debit cards, and expenses.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // 3 Range Options
                                ScanRangeTile(
                                    title = "Past 1 Month",
                                    subtitle = "Quick scan for current month expenses",
                                    isSelected = selectedScanMonths == 1,
                                    onSelect = { selectedScanMonths = 1 }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                ScanRangeTile(
                                    title = "Past 3 Months (Recommended)",
                                    subtitle = "Captures full quarterly statements and recurring vendors",
                                    isSelected = selectedScanMonths == 3,
                                    onSelect = { selectedScanMonths = 3 }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                ScanRangeTile(
                                    title = "Past 6 Months",
                                    subtitle = "Deep history scan for all campus transactions",
                                    isSelected = selectedScanMonths == 6,
                                    onSelect = { selectedScanMonths = 6 }
                                )

                                // Live Scan Progress Card
                                if (isScanningInbox) {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = scanStatusText,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "$scanProgressPercent%",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            LinearProgressIndicator(
                                                progress = scanProgressPercent / 100f,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                            )
                                        }
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        isScanningInbox = true
                                        scanProgressPercent = 10
                                        scanStatusText = "Scanning SMS inbox..."
                                        viewModel.scanSmsMessages(selectedScanMonths)
                                    },
                                    enabled = !isScanningInbox,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (isScanningInbox) "Scanning Inbox..." else "Scan My Messages", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                }

                                OutlinedButton(
                                    onClick = { currentStep = OnboardingStep.DISCOVERY_REVIEW },
                                    enabled = !isScanningInbox,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Text("Skip Scan")
                                }
                            }
                        }
                    }

                    // ─────────────────────────────────────────────────────────────
                    // Step (g): DISCOVERY REVIEW SCREEN
                    // ─────────────────────────────────────────────────────────────
                    OnboardingStep.DISCOVERY_REVIEW -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 24.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(top = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                item {
                                    Text(
                                        text = "Setup Summary",
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Here's what CampusFold discovered from your messages.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Metrics Summary Row
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        DiscoveryStatCard(
                                            count = accounts.size.toString(),
                                            label = "Accounts",
                                            modifier = Modifier.weight(1f)
                                        )
                                        DiscoveryStatCard(
                                            count = allTransactions.size.toString(),
                                            label = "Transactions",
                                            modifier = Modifier.weight(1f)
                                        )
                                        DiscoveryStatCard(
                                            count = vendorMappings.size.toString(),
                                            label = "Vendors",
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                // Discovered Accounts List
                                if (accounts.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "DISCOVERED ACCOUNTS",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    items(accounts, key = { it.id }) { acc ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.AccountBalance,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Column {
                                                        Text(
                                                            text = acc.displayName.ifEmpty { acc.bankName },
                                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                                        )
                                                        if (acc.last4.isNotBlank()) {
                                                            Text(
                                                                text = "A/c ••${acc.last4}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }

                                                Text(
                                                    text = if (acc.balancePaise > 0L) "₹${acc.balancePaise / 100}" else "Detected",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }

                                // Suggested Duplicates (if any found during scan)
                                if (suggestedDuplicates.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "POSSIBLE DUPLICATES TO MERGE",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    items(suggestedDuplicates, key = { "${it.accountA.id}_${it.accountB.id}" }) { pair ->
                                        SuggestedDuplicateCard(
                                            pair = pair,
                                            onMerge = { source, target ->
                                                viewModel.mergeAccounts(target.id, source.id)
                                            },
                                            onNotSame = { a, b -> viewModel.markPairNotSame(a, b) },
                                            onDismiss = { viewModel.dismissSuggestedDuplicate(pair) }
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    viewModel.setCompletedOnboarding(true)
                                    onOnboardingComplete()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Text(
                                    "Finish Setup & Go to Home",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    OnboardingStep.COMPLETED -> {
                        // Handled by callback
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyBullet(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    reason: String,
    isGranted: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isGranted) NeonEmerald.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isGranted) NeonEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isGranted) NeonEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isGranted) {
                Surface(
                    color = NeonEmerald.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Granted",
                        color = NeonEmerald,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanRangeTile(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )
        }
    }
}

@Composable
private fun DiscoveryStatCard(
    count: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
