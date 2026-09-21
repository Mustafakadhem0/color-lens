package com.mustafakadhem.shnolona

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.mustafakadhem.shnolona.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService

    @Volatile
    private var latestColor: Int = Color.WHITE

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "لازم تسمح للكاميرا حتى نعرف اللون",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.detectButton.setOnClickListener {
            showColor(latestColor)
        }
    }

    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(
                    ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                )
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { image ->

                try {
                    val bitmap = image.toBitmap()

                    val centerX = bitmap.width / 2
                    val centerY = bitmap.height / 2

                    // ناخذ معدل منطقة صغيرة بدل بكسل واحد
                    // حتى تكون قراءة اللون أكثر ثباتاً.
                    latestColor = getAverageColor(
                        bitmap,
                        centerX,
                        centerY
                    )

                } catch (_: Exception) {
                } finally {
                    image.close()
                }
            }

            val cameraSelector =
                CameraSelector.DEFAULT_BACK_CAMERA

            try {

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

            } catch (e: Exception) {

                Toast.makeText(
                    this,
                    "صار خطأ بتشغيل الكاميرا",
                    Toast.LENGTH_LONG
                ).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun getAverageColor(
        bitmap: Bitmap,
        centerX: Int,
        centerY: Int
    ): Int {

        var redTotal = 0L
        var greenTotal = 0L
        var blueTotal = 0L
        var count = 0

        val radius = 5

        for (x in centerX - radius..centerX + radius) {
            for (y in centerY - radius..centerY + radius) {

                if (
                    x >= 0 &&
                    y >= 0 &&
                    x < bitmap.width &&
                    y < bitmap.height
                ) {

                    val pixel = bitmap.getPixel(x, y)

                    redTotal += Color.red(pixel)
                    greenTotal += Color.green(pixel)
                    blueTotal += Color.blue(pixel)

                    count++
                }
            }
        }

        if (count == 0) {
            return Color.WHITE
        }

        return Color.rgb(
            (redTotal / count).toInt(),
            (greenTotal / count).toInt(),
            (blueTotal / count).toInt()
        )
    }

    private fun showColor(color: Int) {

        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)

        val hex = String.format(
            "#%02X%02X%02X",
            red,
            green,
            blue
        )

        val colorName = getColorName(
            red,
            green,
            blue
        )

        runOnUiThread {

            binding.colorName.text = colorName
            binding.hexText.text = "HEX: $hex"
            binding.rgbText.text =
                "RGB: $red, $green, $blue"

            binding.colorPreview.setBackgroundColor(color)
        }
    }

    private fun getColorName(
        red: Int,
        green: Int,
        blue: Int
    ): String {

        val colors = listOf(
            NamedColor("أسود", 0, 0, 0),
            NamedColor("أبيض", 255, 255, 255),
            NamedColor("رمادي", 128, 128, 128),
            NamedColor("رصاصي", 90, 90, 90),

            NamedColor("أحمر", 255, 0, 0),
            NamedColor("خمري", 128, 0, 32),
            NamedColor("عنابي", 128, 0, 0),
            NamedColor("وردي", 255, 192, 203),

            NamedColor("برتقالي", 255, 165, 0),
            NamedColor("أصفر", 255, 255, 0),
            NamedColor("ذهبي", 212, 175, 55),

            NamedColor("أخضر", 0, 128, 0),
            NamedColor("أخضر فاتح", 144, 238, 144),
            NamedColor("زيتي", 128, 128, 0),

            NamedColor("أزرق", 0, 0, 255),
            NamedColor("سماوي", 135, 206, 235),
            NamedColor("كحلي", 0, 0, 128),
            NamedColor("تركوازي", 64, 224, 208),

            NamedColor("بنفسجي", 128, 0, 128),
            NamedColor("موف", 186, 85, 211),

            NamedColor("بني", 150, 75, 0),
            NamedColor("بيج", 245, 245, 220),
            NamedColor("سكري", 255, 253, 208)
        )

        return colors.minByOrNull { namedColor ->

            colorDistance(
                red,
                green,
                blue,
                namedColor.red,
                namedColor.green,
                namedColor.blue
            )

        }?.name ?: "لون غير معروف"
    }

    private fun colorDistance(
        r1: Int,
        g1: Int,
        b1: Int,
        r2: Int,
        g2: Int,
        b2: Int
    ): Double {

        return sqrt(
            (r1 - r2).toDouble().pow(2) +
            (g1 - g2).toDouble().pow(2) +
            (b1 - b2).toDouble().pow(2)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    data class NamedColor(
        val name: String,
        val red: Int,
        val green: Int,
        val blue: Int
    )
}