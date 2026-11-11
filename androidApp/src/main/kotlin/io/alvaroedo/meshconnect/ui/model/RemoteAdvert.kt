package io.alvaroedo.meshconnect.ui.model

/**
 * Asking a repeater to advertise itself, and reading what it actually did.
 *
 * Firmware (meshcore-dev/MeshCore main, src/helpers/CommonCLI.cpp:202-209):
 * `advert.zerohop` sends a zero-hop advert and replies "OK - zerohop advert sent";
 * `advert` floods and replies "OK - Advert sent". A firmware older than `advert.zerohop`
 * has only the second handler, and it matches on the six-byte prefix "advert" — so asking
 * an old node for a nearby advert sends a FLOOD one, and says "OK - Advert sent". The
 * reply is the only way to tell, so it decides what the app reports.
 */
object RemoteAdvert {
    const val NEARBY = "advert.zerohop"
    const val MESH = "advert"

    enum class Outcome { Nearby, Mesh, MeshInsteadOfNearby, NoAnswer, Refused }

    fun command(nearby: Boolean): String = if (nearby) NEARBY else MESH

    fun outcome(askedNearby: Boolean, reply: String?): Outcome {
        val r = reply?.trim()?.removePrefix(">")?.trim()?.lowercase() ?: return Outcome.NoAnswer
        if (r.isEmpty()) return Outcome.NoAnswer
        return when {
            r.contains("zerohop advert sent") -> Outcome.Nearby
            r.contains("advert sent") -> if (askedNearby) Outcome.MeshInsteadOfNearby else Outcome.Mesh
            // Accepted in words this table doesn't know: take it at its word.
            r.startsWith("ok") -> if (askedNearby) Outcome.Nearby else Outcome.Mesh
            else -> Outcome.Refused
        }
    }
}
