package app.cineroll.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.cineroll.platform.CameraXCapturer

@Composable
fun CineRollApp(viewModel: ShootViewModel) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    val askForCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasCameraPermission(context) }
    LaunchedEffect(Unit) {
        if (!granted) askForCamera.launch(Manifest.permission.CAMERA)
    }

    val roll by viewModel.shutter.roll.collectAsState()
    when {
        !granted -> PermissionScreen(
            onAllow = { askForCamera.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
        )
        roll.isFinished -> FinishedRollScreen(onLoadNewRoll = viewModel.shutter::loadNewRoll)
        else -> ViewfinderScreen(viewModel)
    }
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@Composable
private fun PermissionScreen(onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Cineroll needs the camera to take Exposures.")
        Button(onClick = onAllow, modifier = Modifier.padding(top = 24.dp)) { Text("Allow camera") }
        Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 8.dp)) { Text("Open settings") }
    }
}

@Composable
private fun FinishedRollScreen(onLoadNewRoll: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("This Roll is finished.")
        Button(onClick = onLoadNewRoll, modifier = Modifier.padding(top = 24.dp)) { Text("Load new Roll") }
    }
}

@Composable
private fun ViewfinderScreen(viewModel: ShootViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val roll by viewModel.shutter.roll.collectAsState()
    val busy by viewModel.shutter.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).also { bindCamera(ctx, lifecycleOwner, it, viewModel.capturer) }
                },
            )
            Text(
                text = "${roll.left}/${roll.total}",
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp).testTag("counter"),
            )
            Button(
                onClick = viewModel::shoot,
                enabled = !busy,
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp).testTag("shutter"),
            ) { Text("Shoot") }
        }
    }
}

private fun bindCamera(
    context: Context,
    owner: LifecycleOwner,
    previewView: PreviewView,
    capturer: CameraXCapturer,
) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener({
        val provider = providerFuture.get()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        provider.unbindAll()
        provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        capturer.bind(imageCapture)
    }, ContextCompat.getMainExecutor(context))
}
