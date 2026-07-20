package com.google.android.stardroid.ephemeris

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Imports Earth Orientation Parameters from IERS Finals2000A format.
 * 
 * Data source: https://maia.usno.navy.mil/ser7/finals2000A.all
 * Format documentation: https://maia.usno.navy.mil/ser7/readme.finals2000A
 */
class EOPImporter {
    
    data class ImportResult(val eop: EarthOrientationParameters, val originalLine: String)
    
    companion object {
        private const val TAG = "EOPImporter"
        
        /**
         * Download EOP data from IERS.
         */
        fun download(urlString: String): List<String> {
            val lines = ArrayList<String>()
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 60000  // 60 seconds for connection
            connection.readTimeout = 120000     // 120 seconds for reading (large file)
            connection.setRequestProperty("User-Agent", "Stardroid/1.0")
            
            try {
                connection.connect()
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("HTTP error code: $responseCode")
                }
                
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                var line: String? = reader.readLine()
                while (line != null) {
                    lines.add(line)
                    line = reader.readLine()
                }
                reader.close()
                
                Log.d(TAG, "Successfully downloaded ${lines.size} lines")
            } finally {
                connection.disconnect()
            }
            return lines
        }
        
        /**
         * Parse IERS Finals2000A format.
         * 
         * Fixed-width columns:
         * 1-2:   Year (last 2 digits)
         * 3-4:   Month
         * 5-6:   Day
         * 8-15:  Modified Julian Date
         * 17:    I (observed) or P (predicted)
         * 19-27: Polar Motion X (arcseconds)
         * 38-46: Polar Motion Y (arcseconds)
         * 59-68: UT1-UTC (seconds)
         */
        fun parse(lines: List<String>): List<ImportResult> {
            val results = ArrayList<ImportResult>()
            
            for (line in lines) {
                try {
                    if (line.length < 68) continue
                    
                    // Skip header lines
                    if (line.startsWith(" ") || line.contains("MJD")) continue
                    
                    val mjd = line.substring(7, 15).trim().toDoubleOrNull() ?: continue
                    val flag = line.getOrNull(16) ?: continue
                    
                    val pmX = line.substring(18, 27).trim().toDoubleOrNull() ?: continue
                    val pmY = line.substring(37, 46).trim().toDoubleOrNull() ?: continue
                    val ut1Utc = line.substring(58, 68).trim().toDoubleOrNull() ?: continue
                    
                    val eop = EarthOrientationParameters(
                        mjd = mjd,
                        polarMotionX = pmX,
                        polarMotionY = pmY,
                        ut1MinusUtc = ut1Utc,
                        isPrediction = (flag == 'P')
                    )
                    
                    results.add(ImportResult(eop, line))
                    
                } catch (ex: Exception) {
                    Log.w(TAG, "Skipping invalid line: ${line.take(50)}", ex)
                }
            }
            
            return results
        }
    }
}
