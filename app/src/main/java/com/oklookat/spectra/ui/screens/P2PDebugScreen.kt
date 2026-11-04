package com.oklookat.spectra.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oklookat.spectra.R
import com.oklookat.spectra.ui.components.AppButton
import com.oklookat.spectra.ui.components.AppTextButton
import com.oklookat.spectra.ui.components.SettingsSectionTitle
import com.oklookat.spectra.util.P2PServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun P2PDebugScreen(
    onBack: () -> Unit,
    isTv: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var targetIp by remember { mutableStateOf("192.168.2.166") }
    var targetPort by remember { mutableStateOf("18080") }
    var outputLog by remember { mutableStateOf("Ready for diagnostics. Select a test above.\n") }
    var isRunningTest by remember { mutableStateOf(false) }

    var testServer by remember { mutableStateOf<P2PServer?>(null) }
    var isServerRunning by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf<String?>(null) }

    fun appendLog(msg: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        outputLog = "[$timestamp] $msg\n$outputLog"
    }

    DisposableEffect(Unit) {
        onDispose {
            testServer?.stop()
            testServer = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.p2p_debug)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Local Server Control Card
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Local Test HTTP Server",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    if (isServerRunning && serverUrl != null) {
                        Text("Server Running at: $serverUrl", fontWeight = FontWeight.Medium)
                        AppButton(
                            onClick = {
                                testServer?.stop()
                                testServer = null
                                isServerRunning = false
                                serverUrl = null
                                appendLog("Local Test Server stopped.")
                            },
                            isTv = isTv
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.stop_test_server))
                        }
                    } else {
                        Text("Start a local server on this phone to test incoming connections.")
                        AppButton(
                            onClick = {
                                try {
                                    val server = P2PServer(context = context, preferredPort = 18080)
                                    server.start()
                                    testServer = server
                                    isServerRunning = true
                                    serverUrl = server.getShareUrl()
                                    appendLog("Local Test Server started at: ${server.getShareUrl()}")
                                } catch (e: Exception) {
                                    appendLog("Failed to start test server: ${e.message}")
                                }
                            },
                            isTv = isTv
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.start_test_server))
                        }
                    }
                }
            }

            // Target Settings Card
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Target Device Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = targetIp,
                            onValueChange = { targetIp = it },
                            label = { Text(stringResource(R.string.target_ip)) },
                            modifier = Modifier.weight(1.8f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = targetPort,
                            onValueChange = { targetPort = it },
                            label = { Text(stringResource(R.string.target_port)) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }

                    // Test Action Buttons
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isRunningTest = true
                                    appendLog("Starting TCP Socket Test to $targetIp:$targetPort...")
                                    withContext(Dispatchers.IO) {
                                        try {
                                            val portInt = targetPort.toIntOrNull() ?: 18080
                                            val startTime = System.currentTimeMillis()
                                            val socket = Socket()
                                            socket.connect(InetSocketAddress(targetIp, portInt), 3000)
                                            val elapsed = System.currentTimeMillis() - startTime
                                            socket.close()
                                            appendLog("SUCCESS: TCP Socket connected to $targetIp:$portInt in $elapsed ms!")
                                        } catch (e: Exception) {
                                            appendLog("FAILED: Raw TCP Socket error: ${e.javaClass.simpleName} - ${e.message}")
                                        }
                                    }
                                    isRunningTest = false
                                }
                            },
                            enabled = !isRunningTest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.BugReport, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.test_tcp_socket))
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    isRunningTest = true
                                    appendLog("Running ICMP Ping to $targetIp...")
                                    withContext(Dispatchers.IO) {
                                        try {
                                            val process = Runtime.getRuntime().exec("ping -c 3 $targetIp")
                                            val reader = BufferedReader(InputStreamReader(process.inputStream))
                                            val output = StringBuilder()
                                            var line: String?
                                            while (reader.readLine().also { line = it } != null) {
                                                output.append(line).append("\n")
                                            }
                                            process.waitFor()
                                            appendLog("Ping Result:\n$output")
                                        } catch (e: Exception) {
                                            appendLog("Ping failed: ${e.message}")
                                        }
                                    }
                                    isRunningTest = false
                                }
                            },
                            enabled = !isRunningTest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.test_ping))
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    isRunningTest = true
                                    appendLog("Testing HTTP GET to http://$targetIp:$targetPort/share...")
                                    withContext(Dispatchers.IO) {
                                        try {
                                            val portInt = targetPort.toIntOrNull() ?: 18080
                                            val client = OkHttpClient.Builder()
                                                .connectTimeout(4, TimeUnit.SECONDS)
                                                .readTimeout(4, TimeUnit.SECONDS)
                                                .build()

                                            val req = Request.Builder()
                                                .url("http://$targetIp:$portInt/share")
                                                .get()
                                                .build()

                                            client.newCall(req).execute().use { resp ->
                                                appendLog("HTTP Response: Code ${resp.code} ${resp.message}")
                                                val body = resp.body?.string() ?: ""
                                                appendLog("HTTP Body (${body.length} chars): ${body.take(150)}")
                                            }
                                        } catch (e: Exception) {
                                            appendLog("HTTP GET Error: ${e.javaClass.simpleName} - ${e.message}")
                                        }
                                    }
                                    isRunningTest = false
                                }
                            },
                            enabled = !isRunningTest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.test_http_get))
                        }
                    }
                }
            }

            // Results Section
            SettingsSectionTitle(stringResource(R.string.diagnostic_results))
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Log Output", fontWeight = FontWeight.Bold)
                        AppTextButton(
                            onClick = { outputLog = "Cleared.\n" },
                            isTv = isTv
                        ) {
                            Text("Clear")
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 350.dp)
                            .padding(top = 8.dp)
                    ) {
                        Text(
                            text = outputLog,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
