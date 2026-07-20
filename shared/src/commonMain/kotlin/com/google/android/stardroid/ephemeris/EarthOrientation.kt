package com.google.android.stardroid.ephemeris

/**
 * Earth Orientation Parameters from IERS.
 * 
 * These parameters describe irregularities in Earth's rotation:
 * - Polar Motion: Wobble of Earth's rotation axis (~10m at poles)
 * - UT1-UTC: Difference between uniform time and Earth rotation time
 */
data class EarthOrientationParameters(
    val mjd: Double,              // Modified Julian Date
    val polarMotionX: Double,     // arcseconds (positive toward Greenwich)
    val polarMotionY: Double,     // arcseconds (positive toward 90°W)
    val ut1MinusUtc: Double,      // seconds (UT1 - UTC)
    val isPrediction: Boolean     // true if predicted, false if observed
) {
    companion object {
        /**
         * Parse a CSV line from eop.dat file.
         * Format: mjd,pmX,pmY,ut1-utc,isPrediction
         */
        fun fromCSV(line: String): EarthOrientationParameters? {
            return try {
                val parts = line.split(",")
                if (parts.size != 5) return null
                
                EarthOrientationParameters(
                    mjd = parts[0].toDouble(),
                    polarMotionX = parts[1].toDouble(),
                    polarMotionY = parts[2].toDouble(),
                    ut1MinusUtc = parts[3].toDouble(),
                    isPrediction = parts[4].toBoolean()
                )
            } catch (e: Exception) {
                null
            }
        }
        
        /**
         * Convert to CSV format for storage.
         */
        fun toCSV(eop: EarthOrientationParameters): String {
            return "${eop.mjd},${eop.polarMotionX},${eop.polarMotionY},${eop.ut1MinusUtc},${eop.isPrediction}"
        }
    }
}

/**
 * Global EOP data cache.
 */
object EOPData {
    private var eopList: List<EarthOrientationParameters> = emptyList()
    
    fun load(data: List<EarthOrientationParameters>) {
        eopList = data.sortedBy { it.mjd }
    }
    
    /**
     * Get EOP for a specific Modified Julian Date.
     * Returns the closest available data point.
     */
    fun getEOPForDate(mjd: Double): EarthOrientationParameters? {
        if (eopList.isEmpty()) return null
        return eopList.minByOrNull { kotlin.math.abs(it.mjd - mjd) }
    }
    
    /**
     * Get current EOP (for today).
     */
    fun getCurrentEOP(): EarthOrientationParameters? {
        val nowMJD = System.currentTimeMillis() / 86400000.0 + 40587.0
        return getEOPForDate(nowMJD)
    }
}
