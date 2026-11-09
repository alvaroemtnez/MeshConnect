package io.alvaroedo.meshconnect.ui.map

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.alvaroedo.meshconnect.R
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** A point drawn with a bitmap: a node pin (anchored at its tip) or a label chip (centred). */
data class MapMarker(
    val id: String,
    val lat: Double,
    val lon: Double,
    /** Key for [bitmap]; markers that look alike share one image. */
    val imageKey: String,
    val bitmap: () -> Bitmap,
    /** Where the point is, as a fraction of the bitmap's height (a pin's tip). */
    val anchorV: Float = 0.5f,
    /** Chips: drawn on a layer above every pin. */
    val centred: Boolean = false,
    val tappable: Boolean = true,
)

enum class LineStyle { Solid, Dotted, Dashed }

data class MapLine(
    val points: List<Pair<Double, Double>>,
    val argb: Int,
    val widthDp: Float = 4f,
    val style: LineStyle = LineStyle.Solid,
)

/** One camera instruction; a new [token] makes it run again. */
data class CameraRequest(
    val token: Int,
    val fit: List<Pair<Double, Double>> = emptyList(),
    val centre: Pair<Double, Double>? = null,
    val zoom: Double? = null,
    val animate: Boolean = true,
)

/**
 * The app's map: MapLibre with OpenFreeMap vector tiles in the theme's
 * light or dark style, or a plain background when tiles are off (nothing
 * leaves the phone then). Markers are bitmaps (NodeMarkers, PathPins), so
 * the map needs no glyphs or sprites of its own.
 *
 * With [controls] it carries the round buttons on the right: zoom in and
 * out, centre on this radio, and fit the whole network.
 */
@Composable
fun MeshMap(
    modifier: Modifier = Modifier,
    markers: List<MapMarker> = emptyList(),
    lines: List<MapLine> = emptyList(),
    tilesEnabled: Boolean,
    initial: CameraPosition? = null,
    camera: CameraRequest? = null,
    controls: Boolean = false,
    selfPosition: Pair<Double, Double>? = null,
    fitAll: List<Pair<Double, Double>> = emptyList(),
    onMarkerTap: (String) -> Unit = {},
    onCameraIdle: (CameraPosition) -> Unit = {},
    gesturesEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val background = MaterialTheme.colorScheme.surfaceContainerLow.toArgb()
    val styleUri = MapStyles.uri(tilesEnabled, dark, background)

    val mapView = remember {
        MapStyles.init(context)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val tap by rememberUpdatedState(onMarkerTap)
    val idle by rememberUpdatedState(onCameraIdle)

    // MapView needs the host's lifecycle forwarded to it.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(Unit) {
        mapView.getMapAsync { m ->
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.isCompassEnabled = true
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            // OpenStreetMap's licence requires attribution. MapLibre's own (i) button sits
            // where its logo would be, so with the logo off it floats oddly; the map draws
            // the credit itself instead (see the label below).
            m.uiSettings.isAttributionEnabled = false
            initial?.let { m.cameraPosition = it }
            m.addOnMapClickListener { latLng ->
                val pt = m.projection.toScreenLocation(latLng)
                val hit = m.queryRenderedFeatures(pt, LAYER_PINS)
                    .firstOrNull { it.getBooleanProperty("tap") == true }
                    ?.getStringProperty("id")
                if (hit != null) tap(hit)
                hit != null
            }
            m.addOnCameraIdleListener { idle(m.cameraPosition) }
            map = m
        }
    }

    // (Re)load the style when the theme or the tiles switch changes.
    LaunchedEffect(map, styleUri) {
        val m = map ?: return@LaunchedEffect
        style = null
        MapLibre.setConnected(tilesEnabled)
        val builder = if (styleUri.startsWith("{")) Style.Builder().fromJson(styleUri) else Style.Builder().fromUri(styleUri)
        m.setStyle(builder) { s -> addLayers(s); style = s }
    }

    LaunchedEffect(map, gesturesEnabled) {
        map?.uiSettings?.setAllGesturesEnabled(gesturesEnabled)
    }

    // Data: re-sent whenever the markers or lines change.
    LaunchedEffect(style, markers, lines) {
        val s = style ?: return@LaunchedEffect
        for (mk in markers) {
            if (s.getImage(mk.imageKey) == null) s.addImage(mk.imageKey, centreOn(mk.bitmap(), mk.anchorV))
        }
        s.getSourceAs<GeoJsonSource>(SOURCE_PINS)?.setGeoJson(
            FeatureCollection.fromFeatures(
                markers.map { mk ->
                    Feature.fromGeometry(Point.fromLngLat(mk.lon, mk.lat)).apply {
                        addStringProperty("id", mk.id)
                        addStringProperty("icon", mk.imageKey)
                        addBooleanProperty("tap", mk.tappable)
                        addBooleanProperty("centred", mk.centred)
                    }
                },
            ),
        )
        s.getSourceAs<GeoJsonSource>(SOURCE_LINES)?.setGeoJson(
            FeatureCollection.fromFeatures(
                lines.filter { it.points.size >= 2 }.map { ln ->
                    Feature.fromGeometry(LineString.fromLngLats(ln.points.map { Point.fromLngLat(it.second, it.first) })).apply {
                        addStringProperty("color", String.format("#%06X", ln.argb and 0xFFFFFF))
                        addNumberProperty("opacity", ((ln.argb ushr 24) and 0xFF) / 255.0)
                        addNumberProperty("width", ln.widthDp.toDouble())
                        addStringProperty("style", ln.style.name)
                    }
                },
            ),
        )
    }

    LaunchedEffect(map, camera) {
        val m = map ?: return@LaunchedEffect
        val req = camera ?: return@LaunchedEffect
        moveCamera(m, req, context)
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.matchParentSize())
        if (tilesEnabled) {
            Text(
                stringResource(R.string.map_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (controls) {
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (selfPosition != null) {
                    MapButton(R.drawable.ic_my_location, stringResource(R.string.map_my_node)) {
                        map?.let { moveCamera(it, CameraRequest(0, centre = selfPosition, zoom = 13.0), context) }
                    }
                }
                if (fitAll.isNotEmpty()) {
                    MapButton(R.drawable.ic_zoom_out_map, stringResource(R.string.map_fit_all)) {
                        map?.let { moveCamera(it, CameraRequest(0, fit = fitAll), context) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                MapButton(R.drawable.ic_add, stringResource(R.string.map_zoom_in)) {
                    map?.animateCamera(CameraUpdateFactory.zoomIn())
                }
                MapButton(R.drawable.ic_remove, stringResource(R.string.map_zoom_out)) {
                    map?.animateCamera(CameraUpdateFactory.zoomOut())
                }
            }
        }
    }
}

@Composable
private fun MapButton(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    SmallFloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(44.dp),
    ) {
        Icon(painterResource(icon), label)
    }
}

private fun moveCamera(m: MapLibreMap, req: CameraRequest, context: Context) {
    val density = context.resources.displayMetrics.density
    val update = when {
        req.fit.size >= 2 -> {
            val bounds = LatLngBounds.Builder().includes(req.fit.map { LatLng(it.first, it.second) }).build()
            // Degenerate bounds (every point the same) would zoom to the max.
            if (bounds.latitudeSpan < 1e-6 && bounds.longitudeSpan < 1e-6) {
                CameraUpdateFactory.newLatLngZoom(LatLng(req.fit[0].first, req.fit[0].second), 13.0)
            } else {
                CameraUpdateFactory.newLatLngBounds(bounds, (56 * density).toInt())
            }
        }
        req.fit.size == 1 -> CameraUpdateFactory.newLatLngZoom(LatLng(req.fit[0].first, req.fit[0].second), req.zoom ?: 13.0)
        req.centre != null -> CameraUpdateFactory.newLatLngZoom(
            LatLng(req.centre.first, req.centre.second),
            req.zoom ?: m.cameraPosition.zoom,
        )
        else -> return
    }
    runCatching { if (req.animate) m.animateCamera(update, 450) else m.moveCamera(update) }
    // Never past the tiles' last level, where there is nothing to draw.
    if (m.cameraPosition.zoom > MAX_ZOOM) m.moveCamera(CameraUpdateFactory.zoomTo(MAX_ZOOM))
}

/**
 * Pad [b] so the anchor point (a pin's tip, with its name label under it)
 * is the bitmap's centre: symbol anchors are per layer, not per image.
 */
private fun centreOn(b: Bitmap, anchorV: Float): Bitmap {
    val anchorY = (anchorV * b.height).toInt()
    val below = b.height - anchorY
    if (anchorY == below) return b
    val half = maxOf(anchorY, below)
    val out = Bitmap.createBitmap(b.width, half * 2, Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(out).drawBitmap(b, 0f, (half - anchorY).toFloat(), null)
    return out
}

private const val SOURCE_PINS = "mc-pins"
private const val SOURCE_LINES = "mc-lines"
private const val LAYER_PINS = "mc-pins-layer"
private const val LAYER_CHIPS = "mc-chips-layer"
private const val MAX_ZOOM = 17.5

private fun addLayers(s: Style) {
    s.addSource(GeoJsonSource(SOURCE_LINES))
    s.addSource(GeoJsonSource(SOURCE_PINS))
    for (ls in LineStyle.entries) {
        val layer = LineLayer("mc-lines-${ls.name}", SOURCE_LINES).withProperties(
            PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.lineOpacity(Expression.get("opacity")),
            PropertyFactory.lineWidth(Expression.get("width")),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ).withFilter(Expression.eq(Expression.get("style"), ls.name))
        when (ls) {
            LineStyle.Dotted -> layer.setProperties(PropertyFactory.lineDasharray(arrayOf(0.5f, 2.5f)))
            LineStyle.Dashed -> layer.setProperties(PropertyFactory.lineDasharray(arrayOf(3f, 2f)))
            LineStyle.Solid -> Unit
        }
        s.addLayer(layer)
    }
    // Pins anchored at their tip; chips centred, above the pins so a
    // reading is never hidden under a neighbouring node's label.
    s.addLayer(
        SymbolLayer(LAYER_PINS, SOURCE_PINS).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ).withFilter(Expression.eq(Expression.get("centred"), false)),
    )
    s.addLayer(
        SymbolLayer(LAYER_CHIPS, SOURCE_PINS).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ).withFilter(Expression.eq(Expression.get("centred"), true)),
    )
}

/**
 * Where the map's look comes from. OpenFreeMap (openfreemap.org): free
 * OpenStreetMap vector tiles, no key, one host for style, tiles, glyphs
 * and sprites. With tiles off, a one-colour style that loads nothing.
 */
object MapStyles {
    const val LIGHT = "https://tiles.openfreemap.org/styles/liberty"
    const val DARK = "https://tiles.openfreemap.org/styles/dark"

    @Volatile private var initialised = false

    fun init(context: Context) {
        if (!initialised) {
            MapLibre.getInstance(context.applicationContext)
            initialised = true
        }
    }

    fun uri(tilesEnabled: Boolean, dark: Boolean, backgroundArgb: Int): String =
        if (!tilesEnabled) {
            """{"version":8,"sources":{},"layers":[{"id":"bg","type":"background","paint":{"background-color":"${String.format("#%06X", backgroundArgb and 0xFFFFFF)}"}}]}"""
        } else if (dark) {
            DARK
        } else {
            LIGHT
        }

    /** Drop every cached tile, style and glyph. */
    fun clearCache(context: Context, done: () -> Unit) {
        init(context)
        OfflineManager.getInstance(context).clearAmbientCache(object : OfflineManager.FileSourceCallback {
            override fun onSuccess() = done()
            override fun onError(message: String) = done()
        })
    }
}
