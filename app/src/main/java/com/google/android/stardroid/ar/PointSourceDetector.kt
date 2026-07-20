package com.google.android.stardroid.ar

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.util.LinkedList

import android.util.Log

/**
 * An analyzer that detects bright point sources in the image.
 */
class PointSourceDetector(private val onPointsDetected: (List<Pair<Float, Float>>, Int, Int) -> Unit) : ImageAnalysis.Analyzer {

    private val brightnessThreshold = 40 // Lowered to 40 to detect fainter stars (was 80)
    private val clusterDistance = 50.0 // Squared distance to merge points
    private val maxDetections = 50 // Increased to 50 to allow PointSourceComparisonLayer to filter noise vs stars

    data class WeightedPoint(var x: Float, var y: Float, var totalBrightness: Long, var count: Int)

    override fun analyze(image: ImageProxy) {
        val buffer = image.planes[0].buffer // Y channel (Luminance)
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        
        val width = image.width
        val height = image.height
        val stride = 4 
        
        // List of candidate clusters
        val clusters = mutableListOf<WeightedPoint>()

        for (y in 0 until height step stride) {
            for (x in 0 until width step stride) {
                // Y data is unsigned byte, so in Java/Kotlin we need to convert to 0-255 int
                val pixelValue = data[y * width + x].toInt() and 0xFF
                if (pixelValue > brightnessThreshold) {
                    // Greedy clustering
                    var foundCluster = false
                    for (cluster in clusters) {
                        val cx = cluster.x / cluster.count
                        val cy = cluster.y / cluster.count
                        val distSq = (cx - x) * (cx - x) + (cy - y) * (cy - y)
                        
                        if (distSq < clusterDistance) {
                            cluster.x += x
                            cluster.y += y
                            cluster.totalBrightness += pixelValue
                            cluster.count++
                            foundCluster = true
                            break
                        }
                    }
                    
                    if (!foundCluster) {
                        clusters.add(WeightedPoint(x.toFloat(), y.toFloat(), pixelValue.toLong(), 1))
                    }
                }
            }
        }

        // Finalize centroids
        val sortedCentroids = clusters.map { 
            Pair(it.x / it.count, it.y / it.count) to it.totalBrightness 
        }.sortedByDescending { it.second } // Sort by brightness
         .take(maxDetections)
         .map { it.first }

        // Convert to normalized coordinates (0..1)
        val rotationDegrees = image.imageInfo.rotationDegrees
        Log.d("PointSourceDetector", "Found ${clusters.size} clusters. Keeping top ${sortedCentroids.size}. Rotation: $rotationDegrees")

        val normalizedPoints = sortedCentroids.map { (cx, cy) ->
            // Assume the View handles the aspect ratio match (ScaleType.FILL or CENTER_CROP)
            // We just need to rotate the normalize coordinates
            
            val normX = cx / width
            val normY = cy / height
            
            when (rotationDegrees) {
                0 -> Pair(normX, normY)
                90 -> Pair(1f - normY, normX) // CORRECTED: (1-y, x) for 90 CW
                180 -> Pair(1f - normX, 1f - normY)
                270 -> Pair(normY, 1f - normX)
                else -> Pair(normX, normY)
            }
        }

        // Pass rotated dimensions? No, pass raw buffer dimensions and Rotation, let Overlay handle it?
        // Simpler: Just pass the normalized points and the "Aspect Ratio" of the coordinates we generated.
        // If we rotated 90, the "Virtual Image" is Height x Width.
        val outputWidth = if (rotationDegrees == 90 || rotationDegrees == 270) height else width
        val outputHeight = if (rotationDegrees == 90 || rotationDegrees == 270) width else height

        onPointsDetected(normalizedPoints, outputWidth, outputHeight)
        
        image.close()
    }
}
