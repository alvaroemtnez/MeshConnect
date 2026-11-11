package io.alvaroedo.meshconnect.platform

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.alvaroedo.meshconnect.R
import io.alvaroedo.meshconnect.storage.Preferences
import io.alvaroedo.meshconnect.ui.theme.MeshCoreTheme
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scans one QR code and returns its text: CameraX + ZXing core, everything on the
 * phone, no Google services. Skypost's scanner: a rounded frame over the preview and
 * the prompt in a bubble below it. Pinned to portrait in the manifest.
 *
 * Launch it through [ScanQr], the one way into it; a test holds every QR button to that.
 */
class QrScanActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prompt = intent.getStringExtra(EXTRA_PROMPT).orEmpty()
        val theme = Preferences(this).theme
        setContent {
            MeshCoreTheme(
                darkTheme = when (theme) {
                    "dark" -> true
                    "light" -> false
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                },
            ) {
                ScanScreen(
                    prompt = prompt,
                    onResult = { text ->
                        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_RESULT, text))
                        finish()
                    },
                    onBack = { finish() },
                )
            }
        }
    }

    internal companion object {
        const val EXTRA_PROMPT = "io.alvaroedo.meshconnect.SCAN_PROMPT"
        const val EXTRA_RESULT = "io.alvaroedo.meshconnect.SCAN_RESULT"
    }
}

/** Launch with the prompt to show; the result is the code's text, or null if cancelled. */
class ScanQr : ActivityResultContract<String, String?>() {
    override fun createIntent(context: Context, input: String): Intent =
        Intent(context, QrScanActivity::class.java).putExtra(QrScanActivity.EXTRA_PROMPT, input)

    override fun parseResult(resultCode: Int, intent: Intent?): String? =
        if (resultCode == Activity.RESULT_OK) intent?.getStringExtra(QrScanActivity.EXTRA_RESULT) else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanScreen(prompt: String, onResult: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            if (granted) {
                Camera(onResult)
                Box(
                    Modifier
                        .size(260.dp)
                        .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp)),
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                ) {
                    Text(
                        prompt.ifBlank { stringResource(R.string.scan_hint) },
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Column(
                    Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stringResource(R.string.scan_permission), textAlign = TextAlign.Center)
                    Button(onClick = { ask.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.scan_allow_camera))
                    }
                }
            }
        }
    }
}

@Composable
private fun Camera(onResult: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val deliver by rememberUpdatedState(onResult)
    var request by remember { mutableStateOf<SurfaceRequest?>(null) }
    LaunchedEffect(owner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().apply { setSurfaceProvider { request = it } }
        val done = AtomicBoolean(false)
        val worker = Executors.newSingleThreadExecutor()
        val decoder = QrFrameDecoder()
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                setAnalyzer(worker) { image ->
                    val text = image.use { decode(decoder, it) }
                    if (text != null && done.compareAndSet(false, true)) {
                        ContextCompat.getMainExecutor(context).execute { deliver(text) }
                    }
                }
            }
        try {
            provider.unbindAll()
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            awaitCancellation()
        } finally {
            provider.unbindAll()
            worker.shutdown()
        }
    }
    request?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize()) }
}

private fun decode(decoder: QrFrameDecoder, image: ImageProxy): String? {
    val plane = image.planes[0]
    val buf = plane.buffer
    val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
    return decoder.decode(bytes, plane.rowStride, image.width, image.height)
}
