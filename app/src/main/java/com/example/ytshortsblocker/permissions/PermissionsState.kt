package com.example.ytshortsblocker.permissions

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

data class PermissionsState(
    val accessibility: Boolean,
    val overlay: Boolean,
) {
    val allGranted: Boolean get() = accessibility && overlay

    companion object {
        fun read(context: Context) = PermissionsState(
            accessibility = AppPermissions.isAccessibilityServiceEnabled(context),
            overlay = AppPermissions.canDrawOverlays(context),
        )
    }
}

class PermissionsChecker(
    val state: PermissionsState,
    val refresh: () -> Unit,
)

@Composable
fun rememberPermissionsState(): PermissionsChecker {
    val context = LocalContext.current
    var state by remember { mutableStateOf(PermissionsState.read(context)) }
    val refresh = { state = PermissionsState.read(context) }

    val lifecycle = context.findActivity()?.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state = PermissionsState.read(context)
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    return PermissionsChecker(state, refresh)
}
