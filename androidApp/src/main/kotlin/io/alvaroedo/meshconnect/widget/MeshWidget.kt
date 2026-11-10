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
 * The home-screen widget: this radio at a glance. One Glance widget behind
 * two picker entries — the 2×2 one and a 2×1 "compact" one — that chooses
 * its layout from its size: one row (status and gauges only), the 2×2 card,
 * wider adds the latest message, and tallest adds the nearby repeaters.
 * Colours are the launcher's Material You palette (GlanceTheme).
 */
class MeshWidget : GlanceAppWidget() {

    // The real size, not the nearest of a few breakpoints: see WidgetLayout.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetState.current(context)
        provideContent {
            GlanceTheme { Content(snapshot) }
        }
    }

    companion object {
        /** Both picker entries, so a redraw reaches every placed widget. */
        suspend fun updateEverywhere(context: Context) = MeshWidget().updateAll(context)
    }
}

class MeshWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MeshWidget()
}

/**
 * The 2×1 picker entry. Same widget (Glance maps both receivers to
 * [MeshWidget], so one updateAll redraws both); only the provider info,
 * with its one-row default, differs.
 */
class MeshWidgetCompactReceiver : GlanceAppWidgetReceiver() {
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
            runCatching { MeshWidget.updateEverywhere(app) }
        }
    }
}

@Composable
private fun Content(s: WidgetSnapshot) {
    val context = LocalContext.current
    val size = LocalSize.current
    val now = System.currentTimeMillis()
    val stale = s.isStale(now)
    val plan = WidgetLayout.plan(size.width.value, size.height.value)
    val wide = plan.wide
    val muted = GlanceTheme.colors.onSurfaceVariant
    val ink = if (stale) muted else GlanceTheme.colors.onSurface

    if (plan.row) {
        RowContent(s, stale, wide, ink, roomy = plan.roomyRow, large = plan.largeRow)
        return
    }

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
                // The launcher mark itself (white on the theme's primary):
                // a tinted evenOdd vector drew nothing in RemoteViews.
                Mark()
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
                    Gauges(s, ink, plan.snrLine)
                    Spacer(GlanceModifier.height(6.dp))
                    Unread(s, ink)
                }
                if (wide) {
                    Spacer(GlanceModifier.width(12.dp))
                    Column(GlanceModifier.defaultWeight()) { Latest(s, ink, muted, now, plan.messageLines) }
                }
            }
            // Narrow: the latest message goes underneath, as many lines as fit.
            if (!wide && plan.messageLines > 0) {
                Spacer(GlanceModifier.height(10.dp))
                Latest(s, ink, muted, now, plan.messageLines)
            }
            // Whatever height is left: the repeaters this radio hears directly.
            if (plan.nearbyRows > 0) {
                Spacer(GlanceModifier.height(12.dp))
                Nearby(s, ink, muted, stale, plan.nearbyRows)
            }
        }
    }
}

@Composable
private fun StatusLine(s: WidgetSnapshot, stale: Boolean) {
    val context = LocalContext.current
    val c = GlanceTheme.colors
    val dot = statusDot(s, stale)
    val text = when {
        stale -> context.getString(R.string.widget_not_running)
        s.status == WidgetSnapshot.Status.Connected && s.plaintext -> context.getString(R.string.status_tcp_plain)
        s.status == WidgetSnapshot.Status.Connected -> context.getString(R.string.status_connected)
        s.status == WidgetSnapshot.Status.Connecting -> context.getString(R.string.status_connecting)
        else -> context.getString(R.string.status_offline)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(dot)) {}
        Spacer(GlanceModifier.width(6.dp))
        Text(text, maxLines = 1, style = TextStyle(color = c.onSurfaceVariant, fontSize = 12.sp))
    }
}

@Composable
private fun Gauges(s: WidgetSnapshot, ink: ColorProvider, snrLine: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BatteryGauge(s, ink, 18.dp, 13)
        Spacer(GlanceModifier.width(10.dp))
        SignalGauge(s.signalBars, ink, 18.dp)
        s.repeaterHash?.let {
            Text(" " + it.uppercase(), maxLines = 1, style = TextStyle(color = ink, fontSize = 13.sp))
        }
    }
    if (snrLine && s.repeaterSnr != null) {
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
private fun Latest(s: WidgetSnapshot, ink: ColorProvider, muted: ColorProvider, now: Long, lines: Int) {
    val context = LocalContext.current
    if (s.lastFrom == null) {
        Text(context.getString(R.string.widget_no_messages), style = TextStyle(color = muted, fontSize = 12.sp))
        return
    }
    Text(s.lastFrom, maxLines = 1, style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = FontWeight.Medium))
    Text(
        // Only while the app is running; never stored (WidgetSnapshot).
        s.lastText ?: context.getString(R.string.widget_open_to_read),
        maxLines = lines,
        style = TextStyle(color = muted, fontSize = 12.sp),
    )
    if (s.lastAtMs > 0) {
        Text(
            io.alvaroedo.meshconnect.ui.model.Ago.of(context, s.lastAtMs, now),
            style = TextStyle(color = muted, fontSize = 11.sp),
        )
    }
}

/** The launcher mark on the theme's primary, as in the card's header. */
@Composable
private fun Mark() {
    Box(
        GlanceModifier.size(26.dp).cornerRadius(13.dp).background(GlanceTheme.colors.primary),
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(R.drawable.ic_launcher_foreground), null, GlanceModifier.size(30.dp))
    }
}

/**
 * 2×1 / 4×1: name over a status dot and the gauges, no message text.
 *
 * At 2×1 the app mark is left out: it cost a third of the width, and that width is what
 * the heard repeater's hash and its SNR need (reported 2026-11-10: the hash showed at 3×1
 * and vanished at 2×1). The unread count rides beside the name, as icon and number like
 * the card's, where it cannot be pushed off the edge by the gauges.
 */
@Composable
private fun RowContent(
    s: WidgetSnapshot,
    stale: Boolean,
    wide: Boolean,
    ink: ColorProvider,
    roomy: Boolean = false,
    large: Boolean = false,
) {
    // A tall row (a 2×1 on a launcher with big cells) a size up throughout, so it fills
    // its cell instead of sitting small in the middle of it.
    val nameSp = if (large) 17 else 14
    val textSp = if (large) 14 else 12
    val icon = if (large) 20.dp else 16.dp
    // Narrow but tall enough: the repeater gets its own line, where its hash and SNR
    // both fit. A 2×1 is about four items wide, and the gauge line already holds three.
    val ownLine = !wide && roomy
    val context = LocalContext.current
    val c = GlanceTheme.colors
    Box(
        GlanceModifier.fillMaxSize()
            .background(c.widgetBackground)
            .cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (wide) {
                Mark()
                Spacer(GlanceModifier.width(8.dp))
            }
            Column(GlanceModifier.defaultWeight()) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.nodeName ?: context.getString(R.string.app_name),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = ink, fontSize = nameSp.sp, fontWeight = FontWeight.Medium),
                    )
                    // Beside the name at every width: at the end of the gauge line a
                    // 4×1 pushed the number off the edge and showed a bare icon.
                    run {
                        Spacer(GlanceModifier.width(4.dp))
                        Image(
                            ImageProvider(R.drawable.ic_mark_unread), null, GlanceModifier.size(icon - 2.dp),
                            colorFilter = ColorFilter.tint(if (s.unread > 0) c.primary else ink),
                        )
                        Text(
                            " " + if (s.unread > 99) "99+" else s.unread.toString(),
                            maxLines = 1,
                            style = TextStyle(
                                color = if (s.unread > 0) c.primary else ink,
                                fontSize = textSp.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
                if (large) Spacer(GlanceModifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(statusDot(s, stale))) {}
                    Spacer(GlanceModifier.width(6.dp))
                    BatteryGauge(s, ink, icon, textSp)
                    Spacer(GlanceModifier.width(6.dp))
                    SignalGauge(s.signalBars, ink, icon)
                    if (!ownLine) RepeaterReading(s, ink, textSp = textSp)
                }
                if (ownLine && s.repeaterHash != null) {
                    if (large) Spacer(GlanceModifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) { RepeaterReading(s, ink, lead = "", textSp = textSp) }
                }
            }
        }
    }
}

/** The repeater heard best: its hash, then its SNR in a lighter ink. */
@Composable
private fun RepeaterReading(s: WidgetSnapshot, ink: ColorProvider, lead: String = " ", textSp: Int = 12) {
    val hash = s.repeaterHash ?: return
    Text(lead + hash.uppercase(), maxLines = 1, style = TextStyle(color = ink, fontSize = textSp.sp))
    s.repeaterSnr?.let {
        Text(
            String.format(java.util.Locale.ROOT, " %.1f dB", it),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = (textSp - 1).sp),
        )
    }
}

@Composable
private fun statusDot(s: WidgetSnapshot, stale: Boolean): ColorProvider {
    val c = GlanceTheme.colors
    return when {
        stale -> c.outline
        s.status == WidgetSnapshot.Status.Connected && s.plaintext -> c.error
        s.status == WidgetSnapshot.Status.Connected -> c.primary
        s.status == WidgetSnapshot.Status.Connecting -> c.tertiary
        else -> c.outline
    }
}

/** Up to four repeaters heard directly, strongest first, named only when unambiguous. */
@Composable
private fun Nearby(s: WidgetSnapshot, ink: ColorProvider, muted: ColorProvider, stale: Boolean, maxRows: Int) {
    val context = LocalContext.current
    Column(
        GlanceModifier.fillMaxWidth()
            .background(GlanceTheme.colors.secondaryContainer)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            context.getString(R.string.widget_nearby),
            style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
        // A stale snapshot's repeaters are history, not "nearby".
        val list = if (stale) emptyList() else s.nearby.take(maxRows)
        if (list.isEmpty()) {
            Text(context.getString(R.string.widget_nearby_none), style = TextStyle(color = muted, fontSize = 12.sp))
        }
        for (r in list) {
            Row(GlanceModifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SignalGauge(r.bars, ink, 16.dp)
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    r.name ?: r.hash.uppercase(),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = ink, fontSize = 13.sp),
                )
                Text(
                    String.format(java.util.Locale.ROOT, "%.0f dB", r.snr),
                    maxLines = 1,
                    style = TextStyle(color = muted, fontSize = 12.sp),
                )
            }
        }
    }
}

@Composable
private fun BatteryGauge(s: WidgetSnapshot, ink: ColorProvider, icon: androidx.compose.ui.unit.Dp, textSp: Int) {
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
        null, GlanceModifier.size(icon),
        colorFilter = ColorFilter.tint(if (bat != null && bat <= 15) GlanceTheme.colors.error else ink),
    )
    Text(bat?.let { "$it%" } ?: "–", maxLines = 1, style = TextStyle(color = ink, fontSize = textSp.sp))
}

@Composable
private fun SignalGauge(bars: Int?, ink: ColorProvider, icon: androidx.compose.ui.unit.Dp) {
    Image(
        ImageProvider(
            when (bars) {
                null, 0 -> R.drawable.ic_signal_0
                1 -> R.drawable.ic_signal_1
                2, 3 -> R.drawable.ic_signal_2
                else -> R.drawable.ic_signal_4
            },
        ),
        null, GlanceModifier.size(icon),
        colorFilter = ColorFilter.tint(ink),
    )
}
