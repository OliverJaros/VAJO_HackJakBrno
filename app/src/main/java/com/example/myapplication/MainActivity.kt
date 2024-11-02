package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet


class MainActivity : AppCompatActivity() {

    private lateinit var recordButton: ImageButton
    private lateinit var previewView: PreviewView
    private lateinit var lineChart: LineChart
    private val chartEntries = ArrayList<Entry>()
    private var timeIndex = 0f
    private var isRecording = false
    private var isCameraRunning = false
    private val cameraProvider: ProcessCameraProvider? = null

    private val CAMERA_PERMISSION_CODE = 100 // Konštanta pre identifikáciu žiadosti o povolenie kamery

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Inicializujte PreviewView a LineChart
        recordButton = findViewById(R.id.recordButton)
        previewView = findViewById(R.id.previewView)
        lineChart = findViewById(R.id.lineChart)

        setupChart()


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        recordButton = findViewById(R.id.recordButton)
        // Skontrolujte, či má aplikácia povolenie pre kameru
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // Ak povolenie nie je udelené, požiadajte o povolenie pre kameru
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        }
        recordButton.setOnClickListener {
            if (isRecording) {
                stopCamera()
            } else {
                startCamera()
            }
            isRecording = !isRecording
        }

    }


    // Metóda pre spracovanie výsledkov žiadosti o povolenie
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera() // Spustí kameru po udelení povolenia
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupChart() {
        // Nastavte základné vlastnosti pre LineChart
        lineChart.axisRight.isEnabled = false
        lineChart.axisLeft.axisMinimum = 0f
        lineChart.axisLeft.axisMaximum = 255f
        lineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        lineChart.xAxis.setDrawGridLines(false)
        lineChart.description.isEnabled = false
        lineChart.legend.isEnabled = false
    }

    private fun startCamera() {
        if (isCameraRunning) return;
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(ContextCompat.getMainExecutor(this), { image ->
                        if (image.format == ImageFormat.YUV_420_888) {
                            val yPlane = image.planes[0].buffer
                            val uPlane = image.planes[1].buffer
                            val vPlane = image.planes[2].buffer

                            val width = image.width
                            val height = image.height

                            var totalGreenBrightness = 0f
                            var pixelCount = 0

                            // Prechádzame cez každý pixel na obrázku
                            for (y in height / 4 until height * 3 / 4) {
                                for (x in width / 4 until width * 3 / 4) {
                                    val yIndex = y * width + x
                                    val uIndex = (y / 2) * (width / 2) + (x / 2)
                                    val vIndex = uIndex

                                    val green = extractGreenFromYUV(
                                        yPlane.get(yIndex),
                                        uPlane.get(uIndex),
                                        vPlane.get(vIndex)
                                    )
                                    if (y > height/4) {
                                        if (y < height*3/4) {
                                            if (x > width/4) {
                                                if (x<width*3/4) {
                                                    totalGreenBrightness += green
                                                    pixelCount++
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            val avgGreenBrightness = totalGreenBrightness / pixelCount
                            Log.d("PPG", "Average green brightness (whole image): $avgGreenBrightness")
                            updateChart(avgGreenBrightness)
                        }

                        image.close()
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )

                camera.cameraControl.enableTorch(true)

            } catch (exc: Exception) {
                Log.e("CameraXApp", "Nepodarilo sa zapnúť kameru.", exc)
            }

            isCameraRunning = true
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll() // Unbind all use cases to stop the camera
        isCameraRunning = false
        Toast.makeText(this, "Camera stopped", Toast.LENGTH_SHORT).show()
    }

    private fun extractGreenFromYUV(y: Byte, u: Byte, v: Byte): Int {
        // YUV to RGB transformácia
        val yVal = y.toInt() and 0xFF
        val uVal = u.toInt() and 0xFF - 128
        val vVal = v.toInt() and 0xFF - 128

        val r = (yVal + 1.370705 * vVal).toInt()
        val g = (yVal - 0.337633 * uVal - 0.698001 * vVal).toInt()
        val b = (yVal + 1.732446 * uVal).toInt()

        // Orezanie hodnôt na rozsah 0-255
        return g.coerceIn(0, 255)
    }

    private fun updateChart(avgBrightness: Float) {
        // Pridajte nový záznam s priemerným jasom do grafu
        chartEntries.add(Entry(timeIndex, avgBrightness))
        timeIndex += 1f

        // Obmedzte počet vzoriek na posledných 40
        if (chartEntries.size > 80) {
            chartEntries.removeAt(0)  // Odstráni najstaršiu vzorku
        }

        // Získajte posledných 40 vzoriek pre dynamické nastavenie osi y
        val recentEntries = chartEntries.takeLast(80)

        // Nájdite maximálnu a minimálnu hodnotu medzi poslednými 40 vzorkami
        val maxBrightness = recentEntries.maxOfOrNull { it.y } ?: 255f
        val minBrightness = recentEntries.minOfOrNull { it.y } ?: 0f

        // Nastavte novú maximálnu a minimálnu hodnotu pre os y
        lineChart.axisLeft.axisMaximum = maxBrightness + 1  // Pridajte trochu priestoru nad max hodnotou
        lineChart.axisLeft.axisMinimum = minBrightness - 1  // Pridajte trochu priestoru pod min hodnotou

        // Nastavte údaje do grafu
        val lineDataSet = LineDataSet(chartEntries, "Brightness")
        lineDataSet.setDrawValues(false)
        lineDataSet.setDrawCircles(false)

        val lineData = LineData(lineDataSet)
        lineChart.data = lineData
        lineChart.notifyDataSetChanged()
        lineChart.invalidate()
    }
}