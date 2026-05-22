/*
 * Copyright (c) 2026 American Printing House for the Blind
 * Use of this source code is governed by an MIT-style license that can be found in the LICENSE file.
 */

package org.aph.monarchtempest.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.view.KeyEvent
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
import org.aph.monarchtempest.monarch_utils.BrlScrollView
import org.aph.monarchtempest.monarch_utils.BrailleUtil
import org.aph.monarchtempest.monarch_utils.CustomGraphicsFunc
import org.aph.monarchtempest.weather.WeatherRepository

/**
 * Displays current weather conditions in braille using the [SelfBraillingWidget].
 *
 * On launch the activity:
 *  1. Renders a "Loading…" message while the network request is in-flight.
 *  2. Fetches the device's GPS location (falls back to Puerto Rico if
 *     location permission is not granted or is unavailable).
 *  3. Calls the Open-Meteo API for current conditions.
 *  4. Renders the result inside a full-screen [BrlScrollView] so the user
 *     can pan up/down if the text wraps across multiple braille lines.
 *
 * Key bindings (on top of the Monarch system defaults):
 *  - Panning Up / Page Up   → scroll to previous page
 *  - Panning Down / Page Down → scroll to next page
 *  - Zoom In (+)            → increase line spacing
 *  - Zoom Out (-)           → decrease line spacing
 *  - Home                   → jump to first page
 *  - End                    → jump to last page
 */
class CurrentWeatherActivity : AppCompatActivity() {

    // ── SelfBraillingWidget setup ──────────────────────────────────────────

    private lateinit var manager: SelfBraillingManager
    private lateinit var widget: SelfBraillingWidget
    private val mutableLiveDots    = MutableLiveData<Array<ByteArray>>()
    private val mutableViewedImage = MutableLiveData<Array<ByteArray>>()
    private lateinit var screenDimensions: Size
    private lateinit var brailleScreen: DotsMatrix

    // ── Translator & scroll view ───────────────────────────────────────────

    private lateinit var translator: Translator
    private var scrollView: BrlScrollView? = null
    private var areServicesBound = false

    // ── Location permission launcher ───────────────────────────────────────

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Whether the user grants or denies, proceed — WeatherRepository
            // will fall back to the default location if permission is denied.
            loadWeather()
        }

    // ──────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        areServicesBound = true

        // Set up SelfBraillingWidget
        manager = SelfBraillingManager(this).apply { bindService() }
        widget  = SelfBraillingWidget(this).also { setContentView(it) }
        supportActionBar?.hide()

        mutableViewedImage.observe(this) { widget.refresh(it) }
        mutableLiveDots.observe(this)    { manager.displayDots(it) }

        screenDimensions = Size(manager.brailleDisplayDotsSizeX, manager.brailleDisplayDotsSizeY)
        brailleScreen    = DotsMatrix(screenDimensions.height, screenDimensions.width)

        // Translator must be delayed at least 1 second before use in onCreate.
        translator = Translator(this).apply { bindService() }

        widget.onSelfBraillingWidgetListener =
            object : SelfBraillingWidget.OnSelfBraillingWidgetListener {
                override fun onFocused() = refreshScreen()
                override fun onDoubleTapAtBraillePosition(pointX: Int, pointY: Int) { /* no-op */ }
            }

        // Show a "Loading" message while we wait for the translator to
        // initialise and the network request to complete.
        lifecycleScope.launch {
            delay(1000) // mandatory translator warm-up (see Known Issues)
            showLoadingMessage()
            checkLocationPermissionThenLoad()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!areServicesBound) {
            areServicesBound = true
            manager.bindService()
            translator.bindService()
            // Re-draw whatever the scroll view last showed
            scrollView?.getPage(brailleScreen)
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

    // ──────────────────────────────────────────────────────────────────────
    // Key handling — panning / scrolling / zoom
    // ──────────────────────────────────────────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val scrollHandled = when (keyCode) {
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_DPAD_UP    -> handleScroll(KeyEvent.KEYCODE_PAGE_UP)

            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_DPAD_DOWN  -> handleScroll(KeyEvent.KEYCODE_PAGE_DOWN)

            KeyEvent.KEYCODE_MOVE_HOME  -> handleScroll(KeyEvent.KEYCODE_MOVE_HOME)
            KeyEvent.KEYCODE_MOVE_END   -> handleScroll(KeyEvent.KEYCODE_MOVE_END)
            KeyEvent.KEYCODE_ZOOM_IN    -> handleScroll(KeyEvent.KEYCODE_ZOOM_IN)
            KeyEvent.KEYCODE_ZOOM_OUT   -> handleScroll(KeyEvent.KEYCODE_ZOOM_OUT)

            else -> false
        }

        if (scrollHandled) refreshScreen()
        return super.onKeyDown(keyCode, event)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Permission → location → network
    // ──────────────────────────────────────────────────────────────────────

    private fun checkLocationPermissionThenLoad() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            loadWeather()
        } else {
            requestLocationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    /**
     * Performs the GPS lookup and Open-Meteo network call on an IO thread,
     * then updates the braille display on the main thread.
     */
    private fun loadWeather() {
        lifecycleScope.launch {
            val weather = withContext(Dispatchers.IO) {
                val location = WeatherRepository.getDeviceLocation(this@CurrentWeatherActivity)
                WeatherRepository.fetchCurrentWeather(location)
            }

            if (weather != null) {
                showWeather(weather.toDisplayString())
            } else {
                showWeather("Could not load weather. Please check your connection and try again.")
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Braille rendering helpers
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Renders a "Loading" message centred on the screen while the fetch is in-flight.
     */
    private fun showLoadingMessage() {
        clearScreen()
        val brailleText = BrailleUtil.translate("Loading weather...", translator)
        val graphic     = CustomGraphicsFunc.createBrailleSentenceGraphic(brailleText)
        val xOffset     = CustomGraphicsFunc.calculateImageCenterOffset(
            graphic.numberOfColumns, screenDimensions.width
        )
        val yOffset     = (screenDimensions.height - graphic.numberOfRows) / 2
        brailleScreen.include(graphic, xOffset, yOffset)
        refreshScreen()
    }

    /**
     * Renders [text] via a [BrlScrollView] so the user can page through content
     * that is longer than the physical display height.
     */
    private fun showWeather(text: String) {
        clearScreen()
        val sv = BrlScrollView(
            initialContent   = text,
            screenDimensions = screenDimensions,
            translate        = { input -> BrailleUtil.translate(input, translator) }
        )
        sv.getPage(brailleScreen)
        scrollView = sv
        refreshScreen()
    }

    /**
     * Handles panning / zooming key codes and delegates to the [BrlScrollView].
     *
     * @return true if the scroll view consumed the event and the screen
     *         should be redrawn.
     */
    private fun handleScroll(keyCode: Int): Boolean {
        val sv = scrollView ?: return false
        val moved = when (keyCode) {
            KeyEvent.KEYCODE_PAGE_UP    -> sv.prevPage()
            KeyEvent.KEYCODE_PAGE_DOWN  -> sv.nextPage()
            KeyEvent.KEYCODE_MOVE_HOME  -> sv.firstPage()
            KeyEvent.KEYCODE_MOVE_END   -> sv.lastPage()
            KeyEvent.KEYCODE_ZOOM_IN    -> sv.zoomIn()
            KeyEvent.KEYCODE_ZOOM_OUT   -> sv.zoomOut()
            else                        -> false
        }
        if (moved) sv.getPage(brailleScreen)
        return moved
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