/*
 * Copyright (c) 2026 American Printing House for the Blind
 * Use of this source code is governed by an MIT-style license that can be found in the LICENSE file.
 */

package org.aph.monarchtempest.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.humanware.keysoftsdk.selfbrailling.SelfBraillingManager
import com.humanware.keysoftsdk.selfbrailling.aidl.DotsMatrix
import com.humanware.keysoftsdk.selfbrailling.widget.SelfBraillingWidget
import com.humanware.keysoftsdk.translator.Translator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.aph.monarchtempest.monarch_utils.BrailleUtil
import org.aph.monarchtempest.monarch_utils.CustomGraphicsFunc
import org.aph.monarchtempest.monarch_utils.ImageUtil
import org.aph.monarchtempest.weather.WeatherRepository
import org.aph.monarchtempest.R as TempestAppR

/**
 * Displays the live current weather condition as a large centered tactile graphic icon
 * on the Monarch pin array, with a text label positioned directly beneath it.
 */
class CurrentWeatherGraphicActivity : AppCompatActivity() {

    // ── SelfBraillingWidget Setup ──────────────────────────────────────────
    private lateinit var manager: SelfBraillingManager
    private lateinit var widget: SelfBraillingWidget
    private val mutableLiveDots    = MutableLiveData<Array<ByteArray>>()
    private val mutableViewedImage = MutableLiveData<Array<ByteArray>>()
    private lateinit var screenDimensions: Size
    private lateinit var brailleScreen: DotsMatrix

    private lateinit var translator: Translator
    private var areServicesBound = false

    // ── Location Permission Launcher ───────────────────────────────────────
    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            loadWeatherData()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        areServicesBound = true

        // Initialize hardware surface wrappers
        manager = SelfBraillingManager(this).apply { bindService() }
        widget  = SelfBraillingWidget(this).also { setContentView(it) }
        supportActionBar?.hide()

        mutableViewedImage.observe(this) { widget.refresh(it) }
        mutableLiveDots.observe(this)    { manager.displayDots(it) }

        screenDimensions = Size(manager.brailleDisplayDotsSizeX, manager.brailleDisplayDotsSizeY)
        brailleScreen    = DotsMatrix(screenDimensions.height, screenDimensions.width)

        translator = Translator(this).apply { bindService() }

        widget.onSelfBraillingWidgetListener =
            object : SelfBraillingWidget.OnSelfBraillingWidgetListener {
                override fun onFocused() = refreshScreen()
                override fun onDoubleTapAtBraillePosition(pointX: Int, pointY: Int) { /* no-op */ }
            }

        lifecycleScope.launch {
            delay(1000) // Mandatory warm-up sequence for the core service binding loop
            showLoadingScreen()
            checkLocationPermissionThenLoad()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!areServicesBound) {
            areServicesBound = true
            manager.bindService()
            translator.bindService()
            refreshScreen()
        }
    }

    override fun onStop() {
        super.onStop()
        if (areServicesBound) {
            areServicesBound = false
            manager.unbindService()
            translator.unbindService()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (areServicesBound) {
            areServicesBound = false
            manager.unbindService()
            translator.unbindService()
        }
    }

    private fun checkLocationPermissionThenLoad() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            loadWeatherData()
        } else {
            requestLocationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun loadWeatherData() {
        lifecycleScope.launch {
            val weatherData = withContext(Dispatchers.IO) {
                val location = WeatherRepository.getDeviceLocation(this@CurrentWeatherGraphicActivity)
                WeatherRepository.fetchCurrentWeather(location)
            }

            clearScreen()
            if (weatherData != null) {
                // Determine resource id matching the WMO code response
                val iconResId = getWeatherIconResource(weatherData.weatherCode)
                val descriptionText = weatherData.description

                renderTactileWeatherDisplay(iconResId, descriptionText)
            } else {
                renderTactileErrorDisplay()
            }
        }
    }

    /**
     * Maps the specific WMO weather code to a precise tactile icon variation,
     * dynamically adjusting the number of rendered raindrops based on precipitation intensity.
     */
    private fun getWeatherIconResource(code: Int): Int {
        return when (code) {
            // Sunny / Clear Sky
            0, 1 -> TempestAppR.raw.sun

//            // Cloudy / Overcast
//            2, 3 -> TempestAppR.raw.cloud

            // Foggy
            45, 48 -> TempestAppR.raw.fog

//            // Light Rain / Drizzle (Fewer drops)
//            51, 53, 61, 80 -> TempestAppR.raw.rain_light
//
//            // Moderate Rain (Standard drop pattern)
//            55, 63, 81 -> TempestAppR.raw.rain_moderate
//
//            // Heavy Rain / Showers (Dense drop pattern across pins)
//            65, 82 -> TempestAppR.raw.rain_heavy
//
//            // Thunder / Lightning Storm
//            95, 96, 99 -> TempestAppR.raw.lightning
//
//            // Light Snow / Grains / Light Showers
//            71, 77, 85 -> TempestAppR.raw.snow_light
//
//            // Heavy Snow / Fall / Heavy Showers
//            73, 75, 86 -> TempestAppR.raw.snow_heavy

            else -> TempestAppR.raw.sun
        }
    }

    /**
     * Generates a structural rendering block composed of a centered weather pattern graphic
     * balanced alongside dynamic Braille sentences.
     */
    private fun renderTactileWeatherDisplay(imageResId: Int, description: String) {
        val imageMatrix = ImageUtil.onGetImageAsDotsMatrix(imageResId, this)

        if (imageMatrix != null) {
            // Center graphic alignment tracking calculations
            val imgXOffset = CustomGraphicsFunc.calculateImageCenterOffset(imageMatrix.numberOfColumns, screenDimensions.width)
            // Leave space at the bottom for text lines by shifting the icon up slightly
            val imgYOffset = 2

            brailleScreen.include(imageMatrix, imgXOffset, imgYOffset)

            // Convert string summary array structure to Braille font definitions
            val brailleTranslation = BrailleUtil.translate(description, translator)
            val textGraphic = CustomGraphicsFunc.createBrailleSentenceGraphic(brailleTranslation)

            val textXOffset = CustomGraphicsFunc.calculateImageCenterOffset(textGraphic.numberOfColumns, screenDimensions.width)
            // Position summary blocks directly below the active image asset
            val textYOffset = imageMatrix.numberOfRows + imgYOffset + 3

            // Prevent array clipping bounds exceptions safely
            if (textYOffset + textGraphic.numberOfRows <= screenDimensions.height) {
                brailleScreen.include(textGraphic, textXOffset, textYOffset)
            }
        } else {
            // Fall back to text rendering if raw file mapping fails
            val genericText = BrailleUtil.translate(description, translator)
            val textGraphic = CustomGraphicsFunc.createBrailleSentenceGraphic(genericText)
            val textXOffset = CustomGraphicsFunc.calculateImageCenterOffset(textGraphic.numberOfColumns, screenDimensions.width)
            brailleScreen.include(textGraphic, textXOffset, (screenDimensions.height - textGraphic.numberOfRows) / 2)
        }
        refreshScreen()
    }

    private fun showLoadingScreen() {
        clearScreen()
        val loadingText = BrailleUtil.translate("Loading graphic...", translator)
        val textGraphic = CustomGraphicsFunc.createBrailleSentenceGraphic(loadingText)
        val xOffset = CustomGraphicsFunc.calculateImageCenterOffset(textGraphic.numberOfColumns, screenDimensions.width)
        val yOffset = (screenDimensions.height - textGraphic.numberOfRows) / 2
        brailleScreen.include(textGraphic, xOffset, yOffset)
        refreshScreen()
    }

    private fun renderTactileErrorDisplay() {
        val errorText = BrailleUtil.translate("Error fetching data.", translator)
        val textGraphic = CustomGraphicsFunc.createBrailleSentenceGraphic(errorText)
        val xOffset = CustomGraphicsFunc.calculateImageCenterOffset(textGraphic.numberOfColumns, screenDimensions.width)
        val yOffset = (screenDimensions.height - textGraphic.numberOfRows) / 2
        brailleScreen.include(textGraphic, xOffset, yOffset)
        refreshScreen()
    }

    private fun refreshScreen() {
        mutableViewedImage.value = brailleScreen.matrix
        mutableLiveDots.value    = brailleScreen.matrix
    }

    private fun clearScreen() {
        brailleScreen.include(
            DotsMatrix(Array(screenDimensions.height) { ByteArray(screenDimensions.width) { 0 } }),
            0, 0
        )
    }
}