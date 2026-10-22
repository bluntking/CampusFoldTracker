package com.campusfold.tracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.campusfold.tracker.ui.CampusFoldApp
import com.campusfold.tracker.ui.TransactionViewModel
import com.campusfold.tracker.ui.theme.CampusFoldTrackerTheme
import com.campusfold.tracker.util.NotificationHelper

class MainActivity : ComponentActivity() {

    private val viewModel: TransactionViewModel by viewModels()

    private val hasSmsPermissionState = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsGranted = permissions[Manifest.permission.READ_SMS] == true ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        hasSmsPermissionState.value = smsGranted
        // Do not auto-scan inbox on permission grant to respect fresh start.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NotificationHelper.createNotificationChannel(this)
        updatePermissionStatus()

        setContent {
            CampusFoldTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CampusFoldApp(
                        viewModel = viewModel,
                        hasSmsPermission = hasSmsPermissionState.value,
                        onRequestPermissions = { requestRequiredPermissions() }
                    )
                }
            }
        }

        // Auto-request if not yet granted
        if (!hasSmsPermissionState.value) {
            requestRequiredPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        viewModel.checkAndAutoSyncGmail()
    }

    private fun updatePermissionStatus() {
        val readSms = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        val receiveSms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        hasSmsPermissionState.value = readSms && receiveSms
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }
}
