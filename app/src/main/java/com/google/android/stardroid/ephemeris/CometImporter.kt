package com.google.android.stardroid.ephemeris

import android.util.Log
import com.google.android.stardroid.space.Comet
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.TimeZone
import java.util.ArrayList

/**
 * Imports comets from the IAU Minor Planet Center "One-line" format.
 */
class CometImporter {

    // Helper class to hold parsed comet and its original source line
    data class ImportResult(val comet: Comet, val originalLine: String)

    companion object {
        private const val TAG = "CometImporter"
        // MPC format: https://www.minorplanetcenter.net/iau/Ephemerides/Comets/Soft00Cmt.txt
        // Cols:
        // 92-95: Magnitude H (Absolute)
        // 97-100: Slope parameter n (Slope)
        
        fun parse(lines: List<String>): List<ImportResult> {
            val results = ArrayList<ImportResult>()
            for (line in lines) {
                try {
                    if (line.length < 100) continue // Skip short lines

                    val perihelionYear = line.substring(14, 18).toInt()
                    val perihelionMonth = line.substring(19, 21).toInt()
                    val perihelionDayVal = line.substring(22, 29).toDouble()
                    
                    val q = line.substring(30, 40).trim().toDouble()
                    val e = line.substring(40, 50).trim().toDouble()
                    
                    val w = line.substring(50, 60).trim().toDouble()
                    val node = line.substring(60, 70).trim().toDouble()
                    val i = line.substring(70, 80).trim().toDouble()

                    // Parse Magnitude Parameters
                    val hStr = line.substring(91, 95).trim()
                    val h = if (hStr.isNotEmpty()) hStr.toDouble() else 10.0
                    
                    val nStr = line.substring(96, 100).trim()
                    // If n is empty, default for comets is often 4.0
                    val n = if (nStr.isNotEmpty()) nStr.toDouble() else 4.0
                    
                    val namePart = line.substring(102).trim()
                    val cleanName = namePart.split("MPC")[0].split("MPEC")[0].trim()

                    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                    val dayInt = perihelionDayVal.toInt()
                    val dayFrac = perihelionDayVal - dayInt
                    
                    cal.set(perihelionYear, perihelionMonth - 1, dayInt, 0, 0, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    
                    val msToAdd = (dayFrac * 24.0 * 3600.0 * 1000.0).toLong()
                    cal.add(Calendar.MILLISECOND, msToAdd.toInt())
                    
                    val tp = cal.timeInMillis
                    
                    val comet = Comet(
                        cleanName,
                        q,
                        e,
                        i,
                        node,
                        w,
                        tp,
                        h,
                        n
                    )
                    
                    results.add(ImportResult(comet, line))

                } catch (ex: Exception) {
                    Log.w(TAG, "Skipping invalid line: $line", ex)
                }
            }
            return results
        }
        
        fun download(urlString: String): List<String> {
            val lines = ArrayList<String>()
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            
            try {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                var line: String? = reader.readLine()
                while (line != null) {
                    lines.add(line)
                    line = reader.readLine()
                }
                reader.close()
            } finally {
                connection.disconnect()
            }
            return lines
        }
    }
}
