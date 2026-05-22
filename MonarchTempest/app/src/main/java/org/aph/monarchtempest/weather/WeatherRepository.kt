/*
 * Copyright (c) 2026 American Printing House for the Blind
 * Use of this source code is governed by an MIT-style license that can be found in the LICENSE file.
 */

package org.aph.monarchtempest.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.URL

/**
 * Holds the parsed current weather data returned from the Open-Meteo API.
 *
 * @property temperatureF  Current temperature in Fahrenheit.
 * @property windSpeedMph  Current wind speed in mph.
 * @property windDirection Wind direction in degrees (0–360).
 * @property humidity      Relative humidity percentage.
 * @property weatherCode   WMO weather interpretation code.
 * @property isDay         True when it is currently daytime at the location.
 */
data class CurrentWeather(
    val temperatureF: Double,
    val windSpeedMph: Double,
    val windDirection: Int,
    val humidity: Int,
    val weatherCode: Int,
    val isDay: Boolean
) {
    /** Human-readable description derived from the WMO weather code. */
    val description: String get() = wmoDescription(weatherCode)

    /** Cardinal direction label derived from wind direction degrees. */
    val windDirectionLabel: String get() = degreesToCardinal(windDirection)

    /**
     * Formats all weather fields into a single readable string suitable
     * for display in a [org.aph.monarchtempest.monarch_utils.BrlScrollView].
     */
    fun toDisplayString(): String {
        val dayNight = if (isDay) "Day" else "Night"
        return "Current Conditions ($dayNight)\n" +
                "Temperature: ${temperatureF.toInt()} F\n" +
                "Conditions: $description\n" +
                "Wind: ${windSpeedMph.toInt()} mph $windDirectionLabel\n" +
                "Humidity: $humidity percent"
    }

    companion object {
        private fun wmoDescription(code: Int): String = when (code) {
            0            -> "Sunny"
            1            -> "Mainly clear sky"
            2            -> "Partly cloudy"
            3            -> "Overcast"
            45, 48       -> "Foggy"
            51, 53, 55   -> "Drizzle"
            61, 63, 65   -> "Rain"
            71, 73, 75   -> "Snow"
            77           -> "Snow grains"
            80, 81, 82   -> "Rain showers"
            85, 86       -> "Snow showers"
            95           -> "Thunderstorm"
            96, 99       -> "Thunderstorm with hail"
            else         -> "Unknown"
        }

        private fun degreesToCardinal(degrees: Int): String {
            val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
            val index = ((degrees + 22) / 45) % 8
            return directions[index]
        }
    }
}

/**
 * Fetches current weather from the Open-Meteo API for the device's GPS location,
 * falling back to San Francisco if location permission is not granted.
 *
 * All network calls are blocking and must be called from a background thread / coroutine.
 */
object WeatherRepository {

    // SF as default fallback location
    private const val DEFAULT_LAT = 37.7823
    private const val DEFAULT_LON = 122.3912

    /**
     * Returns the device's last known GPS location, or null if unavailable / permission denied.
     */
    fun getDeviceLocation(context: Context): Location? {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetches the current weather for the given coordinates from Open-Meteo.
     * Uses [DEFAULT_LAT]/[DEFAULT_LON] when [location] is null.
     *
     * @param location The device location, or null to use the default.
     * @return [CurrentWeather] on success, or null on any network / parse error.
     */
    fun fetchCurrentWeather(location: Location?): CurrentWeather? {
        val lat = location?.latitude ?: DEFAULT_LAT
        val lon = location?.longitude ?: DEFAULT_LON

        // Request current weather variables:
        //   temperature_2m        – air temp at 2 m (°F via temperature_unit=Fahrenheit)
        //   wind_speed_10m        – wind speed at 10 m (mph via wind_speed_unit=mph)
        //   wind_direction_10m    – wind direction in degrees
        //   relative_humidity_2m  – humidity %
        //   weather_code          – WMO interpretation code
        //   is_day                – 1 = day, 0 = night
        val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat" +
                "&longitude=$lon" +
                "&current=temperature_2m,wind_speed_10m,wind_direction_10m," +
                "relative_humidity_2m,weather_code,is_day" +
                "&temperature_unit=fahrenheit" +
                "&wind_speed_unit=mph" +
                "&forecast_days=1"

        return try {
            val raw = URL(url).readText()
            parseResponse(raw)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseResponse(json: String): CurrentWeather? {
        return try {
            val root    = JSONObject(json)
            val current = root.getJSONObject("current")

            CurrentWeather(
                temperatureF  = current.getDouble("temperature_2m"),
                windSpeedMph  = current.getDouble("wind_speed_10m"),
                windDirection = current.getInt("wind_direction_10m"),
                humidity      = current.getInt("relative_humidity_2m"),
                weatherCode   = current.getInt("weather_code"),
                isDay         = current.getInt("is_day") == 1
            )
        } catch (e: Exception) {
            null
        }
    }
}