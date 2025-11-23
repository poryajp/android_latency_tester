package com.example.latencytester

import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var resultText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var analyzeButton: Button
    private lateinit var urlInput: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resultText = findViewById(R.id.resultText)
        progressBar = findViewById(R.id.progressBar)
        analyzeButton = findViewById(R.id.analyzeButton)
        urlInput = findViewById(R.id.urlInput)

        analyzeButton.setOnClickListener {
            val url = urlInput.text.toString()
            if (url.isNotEmpty()) {
                performLatencyTest(url)
            }
        }
    }

    private fun performLatencyTest(url: String) {
        progressBar.visibility = ProgressBar.VISIBLE
        resultText.text = ""
        analyzeButton.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            val metrics = LatencyMetrics()
            
            val client = OkHttpClient.Builder()
                .eventListener(object : EventListener() {
                    var dnsStart = 0L
                    var connectStart = 0L
                    var secureConnectStart = 0L
                    var requestStart = 0L
                    var responseStart = 0L

                    override fun dnsStart(call: Call, domainName: String) {
                        dnsStart = System.nanoTime()
                    }

                    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
                        metrics.dnsLookup = (System.nanoTime() - dnsStart) / 1_000_000_000.0
                    }

                    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
                        connectStart = System.nanoTime()
                    }

                    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: okhttp3.Protocol?) {
                        metrics.tcpConnect = (System.nanoTime() - connectStart) / 1_000_000_000.0
                    }

                    override fun secureConnectStart(call: Call) {
                        secureConnectStart = System.nanoTime()
                    }

                    override fun secureConnectEnd(call: Call, handshake: okhttp3.Handshake?) {
                        metrics.tlsHandshake = (System.nanoTime() - secureConnectStart) / 1_000_000_000.0
                    }
                    
                    override fun requestHeadersStart(call: Call) {
                        requestStart = System.nanoTime()
                    }
                    
                    override fun responseHeadersEnd(call: Call, response: okhttp3.Response) {
                         metrics.ttfb = (System.nanoTime() - requestStart) / 1_000_000_000.0
                    }
                })
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .build()

            try {
                val startTotal = System.nanoTime()
                client.newCall(request).execute().use { response ->
                    val body = response.body
                    val bytes = body?.bytes()?.size ?: 0
                    val endTotal = System.nanoTime()
                    
                    metrics.totalTime = (endTotal - startTotal) / 1_000_000_000.0
                    if (metrics.totalTime > 0) {
                        metrics.downloadSpeed = bytes / metrics.totalTime
                    }
                }

                withContext(Dispatchers.Main) {
                    displayResults(metrics)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    resultText.text = "Error: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = ProgressBar.GONE
                    analyzeButton.isEnabled = true
                }
            }
        }
    }

    private fun displayResults(metrics: LatencyMetrics) {
        val sb = StringBuilder()
        sb.append(String.format("DNS Lookup:        %.6f s\n", metrics.dnsLookup))
        sb.append(String.format("TCP Connect:       %.6f s\n", metrics.tcpConnect))
        sb.append(String.format("TLS Handshake:     %.6f s\n", metrics.tlsHandshake))
        sb.append(String.format("TTFB:              %.6f s\n", metrics.ttfb))
        sb.append(String.format("Total Time:        %.6f s\n", metrics.totalTime))
        sb.append(String.format("Download Speed:    %.0f bytes/sec\n", metrics.downloadSpeed))
        
        resultText.text = sb.toString()
    }

    class LatencyMetrics {
        var dnsLookup = 0.0
        var tcpConnect = 0.0
        var tlsHandshake = 0.0
        var ttfb = 0.0
        var totalTime = 0.0
        var downloadSpeed = 0.0
    }
}
