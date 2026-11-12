package io.alvaroedo.meshconnect.widget

/**
 * What the widget says about the link to the radio. Pure, so the rules are tested without a
 * launcher; MeshWidget draws it.
 *
 * 1.2 (reported 2026-11-12: "you can't really tell if the companion is connected"). Until
 * then the link was an 8 dp dot whose colour alone told the states apart, and with Material
 * You's palette the connected and offline colours can sit close; the battery and signal
 * stayed on screen with no link at all, which read as a live radio. Now:
 *
 * - The dot is an icon whose **shape** differs: a link, a sync arrow, a broken link.
 * - The battery and signal are the radio's readings, so they are shown only while it is
 *   connected. Otherwise that line says the state in words.
 */
object WidgetStatus {

    enum class Link {
        /** Connected over Bluetooth or USB. */
        Connected,

        /** Connected over TCP, which is unencrypted (flagged as everywhere else in the app). */
        ConnectedUnencrypted,
        Connecting,

        /** The app is running, with no radio connected. */
        NotConnected,

        /** Nobody has updated the snapshot lately: the app isn't running (WidgetSnapshot.isStale). */
        NotRunning,
    }

    fun of(s: WidgetSnapshot, nowMs: Long): Link = when {
        // Checked first: a stale "Connected" is history, not a link.
        s.isStale(nowMs) -> Link.NotRunning
        s.status == WidgetSnapshot.Status.Connected && s.plaintext -> Link.ConnectedUnencrypted
        s.status == WidgetSnapshot.Status.Connected -> Link.Connected
        s.status == WidgetSnapshot.Status.Connecting -> Link.Connecting
        else -> Link.NotConnected
    }

    /** Battery and signal only while connected; otherwise the state in words takes their line. */
    fun showsGauges(link: Link): Boolean = link == Link.Connected || link == Link.ConnectedUnencrypted
}
