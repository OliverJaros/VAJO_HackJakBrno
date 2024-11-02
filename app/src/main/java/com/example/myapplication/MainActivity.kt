package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
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
import java.util.concurrent.ExecutorService
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {

    private lateinit var recordButton: ImageButton
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private lateinit var lineChart: LineChart
    private lateinit var pcgChart: LineChart
    private val chartEntries = ArrayList<Entry>()
    private val pcgEntries = ArrayList<Entry>()
    private var timeIndex = 0f
    private var pcgTimeIndex = 0f
    private var isRecording = false
    private var isCameraRunning = false
    private lateinit var cameraProvider: ProcessCameraProvider


    private var preview: Preview? = null

    private val CAMERA_PERMISSION_CODE = 100
    private val AUDIO_PERMISSION_CODE = 101
    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private lateinit var audioRecord: AudioRecord
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
    }

    private fun setupChart(chart: LineChart) {
        // Nastavte základné vlastnosti pre LineChart
        lineChart.axisRight.isEnabled = false
        lineChart.axisLeft.axisMinimum = 0f
        lineChart.axisLeft.axisMaximum = 255f
        lineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        lineChart.xAxis.setDrawGridLines(false)
        lineChart.description.isEnabled = false
        lineChart.legend.isEnabled = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        cameraExecutor = Executors.newSingleThreadExecutor()
        // Inicializujte PreviewView a LineChart
        recordButton = findViewById(R.id.recordButton)
        previewView = findViewById(R.id.previewView)
        lineChart = findViewById(R.id.lineChart)
        pcgChart = findViewById(R.id.pcgChart)

        setupChart(lineChart)
        setupChart(pcgChart)



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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Ak povolenie nie je udelené, požiadajte o povolenie pre kameru
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_CODE)
        }

        recordButton.setOnClickListener {
            if (isCameraRunning) {
                recordButton.setImageResource(R.drawable.ic_play)
                stopCamera()
            } else {
                recordButton.setImageResource(R.drawable.ic_stop)
                startCamera(previewView)
                //startAudioRecording()
            }
            isCameraRunning = !isCameraRunning
        }

    }


    // Metóda pre spracovanie výsledkov žiadosti o povolenie
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                //startCamera(previewView) // Spustí kameru po udelení povolenia
            //} else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
            }
        }
        if (requestCode == AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                //startCamera(previewView) // Spustí kameru po udelení povolenia
                //} else {
                Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun startCamera(previewView: PreviewView) {
        Toast.makeText(this, "Camera start", Toast.LENGTH_SHORT).show()

        Log.d("camera", "Camera running: $isCameraRunning")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            cameraProvider = cameraProviderFuture.get()

            bindCamera(previewView)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera(previewView: PreviewView) {
        preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

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
                        //Log.d("PPG", "Average green brightness (whole image): $avgGreenBrightness")
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
            val cameraControl = camera?.cameraControl
            cameraControl?.enableTorch(true)

        } catch (exc: Exception) {
            Log.e("CameraXApp", "Nepodarilo sa zapnúť kameru.", exc)
        }

        Toast.makeText(this, "Camera stopped", Toast.LENGTH_SHORT).show()
    }

    private fun stopCamera() {
        cameraProvider.unbindAll() // Unbind all use cases to stop the camera
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
        if (chartEntries.size > 90) {
            chartEntries.removeAt(0)  // Odstráni najstaršiu vzorku
        }

        // Získajte posledných 40 vzoriek pre dynamické nastavenie osi y
        val recentEntries = chartEntries.takeLast(90)

        // Nájdite maximálnu a minimálnu hodnotu medzi poslednými 40 vzorkami
        val maxBrightness = recentEntries.maxOfOrNull { it.y } ?: 255f
        val minBrightness = recentEntries.minOfOrNull { it.y } ?: 0f

        // Nastavte novú maximálnu a minimálnu hodnotu pre os y
        lineChart.axisLeft.axisMaximum = maxBrightness   // Pridajte trochu priestoru nad max hodnotou
        lineChart.axisLeft.axisMinimum = minBrightness   // Pridajte trochu priestoru pod min hodnotou

        // Nastavte údaje do grafu
        val lineDataSet = LineDataSet(chartEntries, "Brightness")
        lineDataSet.setDrawValues(false)
        lineDataSet.setDrawCircles(false)

        val lineData = LineData(lineDataSet)
        lineChart.data = lineData
        lineChart.notifyDataSetChanged()
        lineChart.invalidate()
    }

    private fun startAudioRecording() {
        val sampleRate = 10000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("PCG", "AudioRecord initialization failed.")
            return
        }

        audioRecord.startRecording()
        val audioBuffer = ShortArray(bufferSize)

        // Aggregate amplitudes for batch updating the chart
        val amplitudeBatch = mutableListOf<Float>()
        val batchSize = 50  // Adjust this batch size as needed

        Thread {
            while (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val readSize = audioRecord.read(audioBuffer, 0, bufferSize)
                if (readSize > 0) {
                    for (i in 0 until readSize) {
                        val amplitude = audioBuffer[i].toFloat()
                        amplitudeBatch.add(amplitude)

                        // When batch size is reached, send to the chart
                        if (amplitudeBatch.size >= batchSize) {
                            val avgAmplitude = amplitudeBatch.average().toFloat()  // Average of the batch
                            amplitudeBatch.clear()  // Clear batch after sending

                            // Update the chart on the main thread with batched data
                            runOnUiThread { updatePcgChart(avgAmplitude) }
                        }
                    }
                }
            }
        }.start()
    }
    private fun updatePcgChart(amplitude: Float) {
        pcgEntries.add(Entry(pcgTimeIndex, -amplitude))
        pcgTimeIndex += 1f

        if (pcgEntries.size > 3000) {
            pcgEntries.removeAt(0)
        }

        val lineDataSet = LineDataSet(pcgEntries, "Amplitude")
        lineDataSet.setDrawValues(false)
        lineDataSet.setDrawCircles(false)

        val lineData = LineData(lineDataSet)
        pcgChart.data = lineData

        // Set Y-axis range from 0 to 200
        pcgChart.axisLeft.axisMinimum = -800f
        pcgChart.axisLeft.axisMaximum = 800f

        pcgChart.notifyDataSetChanged()
        pcgChart.invalidate()
    }
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}