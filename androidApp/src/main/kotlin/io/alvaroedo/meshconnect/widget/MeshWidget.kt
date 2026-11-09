package io.alvaroedo.meshconnect.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.alvaroedo.meshconnect.MainActivity
import io.alvaroedo.meshconnect.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The home-screen widget: this radio at a glance. Resizable from 2×2;
 * wider adds the latest message, taller adds the signal reading in full.
 * Colours are the launcher's Material You palette (GlanceTheme).
 */
class MeshWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, NARROW_TALL, WIDE, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetState.current(context)
        provideContent {
            GlanceTheme { Content(snapshot) }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val NARROW_TALL = DpSize(110.dp, 180.dp)
        val WIDE = DpSize(250.dp, 110.dp)
        val TALL = DpSize(250.dp, 180.dp)
    }
}

class MeshWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MeshWidget()
}

/**
 * The live snapshot (with the latest message text, kept in memory only)
 * and the debounced redraw the service calls on every change.
 */
object WidgetState {
    @Volatile private var live: WidgetSnapshot? = null
    private var pending: Job? = null
    private const val HEARTBEAT_REDRAW_MS = 3L * 60 * 1000
    private val scope = CoroutineScope(Dispatchers.Default)

    private fun prefs(context: Context) =
        context.getSharedPreferences("widget", Context.MODE_PRIVATE)

    fun current(context: Context): WidgetSnapshot = live ?: WidgetSnapshot.load(prefs(context))

    fun publish(context: Context, snapshot: WidgetSnapshot) {
        val previous = live
        val same = previous != null && snapshot.copy(updatedAtMs = 0) == previous.copy(updatedAtMs = 0)
        // The heartbeat moves updatedAtMs; redraw for it only now and then.
        if (same && snapshot.updatedAtMs - previous!!.updatedAtMs < HEARTBEAT_REDRAW_MS) return
        live = snapshot
        snapshot.save(prefs(context))
        val app = context.applicationContext
        pending?.cancel()
        pending = scope.launch {
            delay(1500)
            runCatching { MeshWidget().updateAll(app) }
        }
    }
}

@Composable
private fun Content(s: WidgetSnapshot) {
    val context = LocalContext.current
    val size = LocalSize.current
    val now = System.currentTimeMillis()
    val stale = s.isStale(now)
    val wide = size.width >= MeshWidget.WIDE.width
    val tall = size.height >= MeshWidget.TALL.height
    val muted = GlanceTheme.colors.onSurfaceVariant
    val ink = if (stale) muted else GlanceTheme.colors.onSurface

    Box(
        GlanceModifier.fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(14.dp),
    ) {
        Column(GlanceModifier.fillMaxSize()) {
            // Mark + node name
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    GlanceModifier.size(26.dp).cornerRadius(13.dp).background(GlanceTheme.colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    // The launcher mark itself (white on the theme's primary):
                    // a tinted evenOdd vector drew nothing in RemoteViews.
                    Image(
                        ImageProvider(R.drawable.ic_launcher_foreground), null,
                        GlanceModifier.size(30.dp),
                    )
                }
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    s.nodeName ?: context.getString(R.string.app_name),
                    maxLines = 1,
                    style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            StatusLine(s, stale)
            Spacer(GlanceModifier.height(8.dp))

            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(GlanceModifier.defaultWeight()) {
                    Gauges(s, ink, tall)
                    Spacer(GlanceModifier.height(6.dp))
                    Unread(s, ink)
                }
                if (wide) {
                    Spacer(GlanceModifier.width(12.dp))
                    Column(GlanceModifier.defaultWeight()) { Latest(s, ink, muted, now) }
                }
            }
            // Narrow but tall: the latest message goes underneath.
            if (!wide && size.height >= 170.dp) {
                Spacer(GlanceModifier.height(10.dp))
                Latest(s, ink, muted, now)
            }
        }
    }
}

@Composable
private fun StatusLine(s: WidgetSnapshot, stale: Boolean) {
    val context = LocalContext.current
    val c = GlanceTheme.colors
    val (dot, text) = when {
        stale -> c.outline to context.getString(R.string.widget_not_running)
        s.status == WidgetSnapshot.Status.Connected && s.plaintext -> c.error to context.getString(R.string.status_tcp_plain)
        s.status == WidgetSnapshot.Status.Connected -> c.primary to context.getString(R.string.status_connected)
        s.status == WidgetSnapshot.Status.Connecting -> c.tertiary to context.getString(R.string.status_connecting)
        else -> c.outline to context.getString(R.string.status_offline)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(dot)) {}
        Spacer(GlanceModifier.width(6.dp))
        Text(text, maxLines = 1, style = TextStyle(color = c.onSurfaceVariant, fontSize = 12.sp))
    }
}

@Composable
private fun Gauges(s: WidgetSnapshot, ink: ColorProvider, tall: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val bat = s.batteryPercent
        Image(
            ImageProvider(
                when {
                    bat == null -> R.drawable.ic_battery_alert
                    bat >= 85 -> R.drawable.ic_battery_full
                    bat >= 55 -> R.drawable.ic_battery_5
                    bat >= 25 -> R.drawable.ic_battery_3
                    else -> R.drawable.ic_battery_1
                },
            ),
            null, GlanceModifier.size(18.dp),
            colorFilter = ColorFilter.tint(if (bat != null && bat <= 15) GlanceTheme.colors.error else ink),
        )
        Text(bat?.let { "$it%" } ?: "–", style = TextStyle(color = ink, fontSize = 13.sp))
        Spacer(GlanceModifier.width(10.dp))
        Image(
            ImageProvider(
                when (s.signalBars) {
                    null, 0 -> R.drawable.ic_signal_0
                    1 -> R.drawable.ic_signal_1
                    2, 3 -> R.drawable.ic_signal_2
                    else -> R.drawable.ic_signal_4
                },
            ),
            null, GlanceModifier.size(18.dp),
            colorFilter = ColorFilter.tint(ink),
        )
        s.repeaterHash?.let {
            Text(" " + it.uppercase(), maxLines = 1, style = TextStyle(color = ink, fontSize = 13.sp))
        }
    }
    if (tall && s.repeaterSnr != null) {
        Text(
            String.format(java.util.Locale.ROOT, "SNR %.1f dB", s.repeaterSnr),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
    }
}

@Composable
private fun Unread(s: WidgetSnapshot, ink: ColorProvider) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            ImageProvider(R.drawable.ic_mark_unread), null, GlanceModifier.size(18.dp),
            colorFilter = ColorFilter.tint(if (s.unread > 0) GlanceTheme.colors.primary else ink),
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(
            context.resources.getQuantityString(R.plurals.widget_unread, s.unread, s.unread),
            maxLines = 1,
            style = TextStyle(color = ink, fontSize = 13.sp),
        )
    }
}

@Composable
private fun Latest(s: WidgetSnapshot, ink: ColorProvider, muted: ColorProvider, now: Long) {
    val context = LocalContext.current
    if (s.lastFrom == null) {
        Text(context.getString(R.string.widget_no_messages), style = TextStyle(color = muted, fontSize = 12.sp))
        return
    }
    Text(s.lastFrom, maxLines = 1, style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = FontWeight.Medium))
    Text(
        // Only while the app is running; never stored (WidgetSnapshot).
        s.lastText ?: context.getString(R.string.widget_open_to_read),
        maxLines = 3,
        style = TextStyle(color = muted, fontSize = 12.sp),
    )
    if (s.lastAtMs > 0) {
        Text(
            io.alvaroedo.meshconnect.ui.model.Ago.of(context, s.lastAtMs, now),
            style = TextStyle(color = muted, fontSize = 11.sp),
        )
    }
}
