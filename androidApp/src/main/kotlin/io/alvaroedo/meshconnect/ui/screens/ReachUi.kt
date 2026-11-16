package io.alvaroedo.meshconnect.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.alvaroedo.meshconnect.R
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.alvaroedo.meshconnect.ui.model.ReachText
import io.github.thatsfguy.meshcore.presentation.Reachability
import io.github.thatsfguy.meshcore.protocol.Codes
import kotlinx.coroutines.launch

/** Two or three words for a list row; null when there is nothing to say. */
@Composable
internal fun reachShort(state: Reachability.State?): String? = when (state) {
    Reachability.State.TwoWay -> stringResource(R.string.reach_two_way)
    Reachability.State.TwoWayBefore -> stringResource(R.string.reach_two_way_before)
    Reachability.State.TheyHeardYou -> stringResource(R.string.reach_they_heard)
    Reachability.State.HeardOnly -> stringResource(R.string.reach_heard_only)
    Reachability.State.NoAnswer -> stringResource(R.string.reach_no_answer)
    Reachability.State.Unknown, null -> null
}

/** The full sentence for a node's sheet; null when nothing is known either way. */
@Composable
internal fun reachSentence(info: ReachText.Info, nowSeconds: Long): String? =
    when (val line = ReachText.line(info, nowSeconds)) {
        is ReachText.Line.Confirmed -> stringResource(R.string.reach_line_confirmed, relativeAge(line.at))
        ReachText.Line.RepeatsYou -> stringResource(R.string.reach_line_repeats)
        is ReachText.Line.ConfirmedBefore -> stringResource(R.string.reach_line_before, relativeAge(line.at))
        ReachText.Line.TheyHeardYou -> stringResource(R.string.reach_line_they_heard)
        ReachText.Line.NotReached -> stringResource(R.string.reach_line_not_reached)
        ReachText.Line.NoSignOfHearing -> stringResource(R.string.reach_line_no_sign)
        ReachText.Line.NoAnswer -> stringResource(R.string.reach_line_no_answer)
        ReachText.Line.None -> null
    }

/**
 * "Can I talk to them?" on a node's sheet (upstream 0.11.0, GUIDED-UX question 1): the
 * verdict from what the app already knows, which costs no airtime, and for people and
 * sensors a Check that asks them for the route both ways (path discovery), on a tap only.
 */
@Composable
internal fun ReachSection(vm: MeshCoreViewModel, keyHex: String, type: Int, allowCheck: Boolean = true) {
    val info = vm.reachability.collectAsState().value[keyHex]
    val sentence = info?.let { reachSentence(it, vm.nowSeconds()) }
    // Repeaters and rooms have Ping, which says the same and more; this is for the
    // nodes that don't forward a trace.
    // The radio looks the node up in its contacts to send the request, so a node heard but
    // not added cannot be checked ([allowCheck] false).
    val canCheck = allowCheck && (type == Codes.ADV_TYPE_CHAT || type == Codes.ADV_TYPE_SENSOR)
    if (sentence == null && !canCheck) return
    Spacer(Modifier.height(8.dp))
    InfoTile(
        stringResource(R.string.reach_label),
        sentence ?: stringResource(R.string.reach_nothing_known),
        Modifier.fillMaxWidth(),
    )
    if (!canCheck) return
    val scope = rememberCoroutineScope()
    var checking by remember(keyHex) { mutableStateOf(false) }
    var result by remember(keyHex) { mutableStateOf<MeshCoreViewModel.ReachCheck?>(null) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        FilledTonalButton(
            onClick = {
                checking = true
                result = null
                scope.launch {
                    result = vm.checkReachability(keyHex, type)
                    checking = false
                }
            },
            enabled = !checking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (checking) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(painterResource(R.drawable.ic_route), null, Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (checking) R.string.reach_checking else R.string.reach_check))
        }
        result?.let {
            Text(
                checkText(it),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun checkText(r: MeshCoreViewModel.ReachCheck): String = when (r) {
    MeshCoreViewModel.ReachCheck.NotConnected -> stringResource(R.string.link_not_connected)
    is MeshCoreViewModel.ReachCheck.NoAnswer ->
        stringResource(if (r.asksPermission) R.string.reach_check_no_answer_permission else R.string.reach_check_no_answer)
    is MeshCoreViewModel.ReachCheck.TwoWay -> {
        val direct = stringResource(R.string.reach_route_direct)
        val out = if (r.out.isEmpty()) direct else stringResource(R.string.reach_route_through, r.out.joinToString(", "))
        val back = if (r.back.isEmpty()) direct else stringResource(R.string.reach_route_through, r.back.joinToString(", "))
        stringResource(R.string.reach_check_two_way, out, back)
    }
}
