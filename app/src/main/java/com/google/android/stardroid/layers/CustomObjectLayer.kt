// Copyright 2023 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.android.stardroid.layers

import android.content.SharedPreferences
import android.content.res.Resources
import org.cosmosmataro.skymap.R
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.renderables.*
import com.google.android.stardroid.renderer.RendererObjectManager.UpdateType
import java.util.ArrayList
import java.util.EnumSet
import android.content.res.AssetManager
import org.json.JSONArray
import com.google.android.stardroid.math.getGeocentricCoords

/**
 * A [Layer] to show a custom set of objects.
 *
 * @author Your Name
 */
class CustomObjectLayer(private val model: AstronomerModel, private val _resources: Resources, preferences:
    SharedPreferences) :
  AbstractRenderablesLayer(_resources, true, preferences) {
  private val customObjects: MutableList<CustomObject> = ArrayList()

  /**
   * Represents a custom object.
   */
  private data class CustomObject(
    val name: String,
    val ra: Float,
    val dec: Float,
    val color: Long,
    val size: Float,
    val icon: String,
    val catalogName: String?,
    val baptismDate: String?,
    val comments: String?
  )

  private fun initializeCustomObjects() {
    val inputStream = _resources.assets.open("custom_objects.json")
    val jsonString = inputStream.bufferedReader().use { it.readText() }
    val jsonArray = org.json.JSONArray(jsonString)

    for (i in 0 until jsonArray.length()) {
      val jsonObject = jsonArray.getJSONObject(i)
      val name = jsonObject.getString("name")
      val ra = jsonObject.getDouble("ra").toFloat()
      val dec = jsonObject.getDouble("dec").toFloat()
      val colorString = jsonObject.getString("color")
      val color = if (colorString.startsWith("0x")) colorString.substring(2).toLong(16) else colorString.toLong(16)
      val size = jsonObject.getDouble("size").toFloat()
      val icon = jsonObject.getString("icon")
      val catalogName = jsonObject.optString("catalogName")
      val baptismDate = jsonObject.optString("baptismDate")
      val comments = jsonObject.optString("comments")

      customObjects.add(CustomObject(name, ra, dec, color, size, icon, catalogName, baptismDate, comments))
    }
  }

  override fun initializeAstroSources(sources: ArrayList<AstronomicalRenderable>) {
    for (customObject in customObjects) {
      sources.add(CustomObjectRenderable(model, customObject, _resources))
    }
  }

  override val layerDepthOrder = 80
  override val preferenceId = "source_provider.7"
  override val layerName = _resources.getString(R.string.show_custom_objects_pref)
  // TODO: Add a new string resource for this.
  override val layerNameId = R.string.show_custom_objects_pref

  private class CustomObjectRenderable(
    private val model: AstronomerModel,
    private val customObject: CustomObject,
    private val resources: Resources
  ) : AbstractAstronomicalRenderable() {
    override val labels: MutableList<TextPrimitive> = ArrayList()
    override val images: MutableList<ImagePrimitive> = ArrayList()
    private val theImage: ImagePrimitive
    private val label: TextPrimitive
    private val name = customObject.name
    override val names: MutableList<String> = ArrayList()
    override val searchLocation: Vector3
      get() = getGeocentricCoords(customObject.ra, customObject.dec)

    override fun initialize(): Renderable {
      theImage.setImageId(resources.getIdentifier("star_on", "drawable", resources.getResourcePackageName(R.drawable.gift_on)))
      label.text = name
      return this
    }

    override fun update(): EnumSet<UpdateType> {
      return EnumSet.noneOf(UpdateType::class.java)
    }

    companion object {
      private val UP = Vector3(0.0f, 1.0f, 0.0f)
    }

    init {
      names.add(name)
      theImage = ImagePrimitive(
        getGeocentricCoords(customObject.ra, customObject.dec),
        resources,
        resources.getIdentifier("star_on", "drawable", resources.getResourcePackageName(R.drawable.gift_on)),
        UP,
        0.03f
      )
      theImage.requiresBlending = true // Enable alpha blending
      images.add(theImage)
      var fullLabelText = name
      customObject.catalogName?.let { fullLabelText += "\nCat: $it" }
      customObject.baptismDate?.let { fullLabelText += "\nDate: $it" }
      customObject.comments?.let { fullLabelText += "\nComments: $it" }

      label = TextPrimitive(
        getGeocentricCoords(customObject.ra, customObject.dec),
        fullLabelText,
        customObject.color.toInt()
      )
      labels.add(label)
    }
  }

  init {
    initializeCustomObjects()
  }
}