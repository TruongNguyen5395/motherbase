package dev.hermeskotlin.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.calendar.ApplyProposalOutcome
import dev.hermeskotlin.core.calendar.ApplyProposalRequest
import dev.hermeskotlin.core.calendar.CalendarApi
import dev.hermeskotlin.core.calendar.ProposalOperationConflicts
import dev.hermeskotlin.core.calendar.ScheduleProposal
import dev.hermeskotlin.core.gateway.GatewayUrl
import dev.hermeskotlin.core.network.ApiResult
import dev.hermeskotlin.core.rpc.GatewayEvent
import dev.hermeskotlin.designsystem.bodySmall
import dev.hermeskotlin.designsystem.caption
import dev.hermeskotlin.designsystem.colors
import dev.hermeskotlin.designsystem.components.Button
import dev.hermeskotlin.designsystem.components.ButtonSize
import dev.hermeskotlin.designsystem.components.ButtonVariant
import dev.hermeskotlin.designsystem.components.Dialog
import dev.hermeskotlin.designsystem.surface2
import dev.hermeskotlin.designsystem.text as textColor
import dev.hermeskotlin.designsystem.typography
import dev.hermeskotlin.designsystem.warning
import dev.hermeskotlin.ui.resources.Res
import dev.hermeskotlin.ui.resources.cal_proposal_conflict_message
import dev.hermeskotlin.ui.resources.cal_proposal_conflict_title
import dev.hermeskotlin.ui.resources.cal_proposal_force_apply
import dev.hermeskotlin.ui.resources.common_cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.stringResource

/** Conflict state held when applying a staged proposal hits newly created busy events (HTTP 409). */
data class PendingProposalConflict(
    val proposalId: String,
    val selectedOpIds: List<String>,
    val conflicts: List<ProposalOperationConflicts>,
)

internal fun <T> ApiResult<T>.errorTextOrNull(): String? = when (this) {
    is ApiResult.Success -> null
    is ApiResult.SessionExpired -> "Session expired. Please sign in again."
    is ApiResult.InvalidCredentials -> "Invalid credentials."
    is ApiResult.RateLimited -> "Rate limited. Try again shortly."
    is ApiResult.Unavailable -> message
    is ApiResult.Failed -> message
}

/**
 * Coordinates staged schedule proposal lifecycle actions (`apply`, `discard`, `revert`) and live
 * `plugin.motherbase-calendar.calendar.proposal_updated` status updates so Chat, Dispatch, and Calendar
 * share identical confirmation and conflict handling without duplicating coroutine logic.
 */
class ScheduleProposalCoordinator(
    private val calendarApi: CalendarApi?,
    private val scope: CoroutineScope,
    private val onError: (String) -> Unit = {},
    private val onSessionExpired: () -> Unit = {},
    private val onProposalChanged: (suspend () -> Unit)? = null,
) {
    private val _statuses = MutableStateFlow<Map<String, String>>(emptyMap())
    val statuses: StateFlow<Map<String, String>> = _statuses.asStateFlow()

    private val _busyProposalIds = MutableStateFlow<Set<String>>(emptySet())
    val busyProposalIds: StateFlow<Set<String>> = _busyProposalIds.asStateFlow()

    private val _pendingConflict = MutableStateFlow<PendingProposalConflict?>(null)
    val pendingConflict: StateFlow<PendingProposalConflict?> = _pendingConflict.asStateFlow()

    fun seedFromProposals(proposals: List<ScheduleProposal>) {
        if (proposals.isEmpty()) return
        _statuses.update { cur ->
            val next = cur.toMutableMap()
            proposals.forEach { p -> next[p.id] = p.status }
            next
        }
    }

    fun clear() {
        _statuses.value = emptyMap()
        _busyProposalIds.value = emptySet()
        _pendingConflict.value = null
    }

    fun observeGatewayEvent(event: GatewayEvent) {
        if (event.type != EVENT_PROPOSAL_UPDATED) return
        val payload = event.payload as? JsonObject ?: return
        val id = payload["proposal_id"]?.jsonPrimitive?.contentOrNull ?: return
        val status = payload["status"]?.jsonPrimitive?.contentOrNull ?: return
        _statuses.update { it + (id to status) }
    }

    fun dismissConflict() {
        _pendingConflict.value = null
    }

    fun confirmForceApply(gatewayUrl: GatewayUrl) {
        val pending = _pendingConflict.value ?: return
        _pendingConflict.value = null
        apply(gatewayUrl, pending.proposalId, pending.selectedOpIds, force = true)
    }

    fun apply(
        gatewayUrl: GatewayUrl,
        proposalId: String,
        selectedOpIds: List<String>,
        force: Boolean = false,
    ) {
        val api = calendarApi ?: return
        runMutation(proposalId) {
            when (val res = api.applyProposal(gatewayUrl, proposalId, ApplyProposalRequest(selectedOpIds = selectedOpIds, force = force))) {
                is ApiResult.Success -> when (val outcome = res.value) {
                    is ApplyProposalOutcome.Applied -> {
                        _statuses.update { it + (proposalId to outcome.proposal.status) }
                        onProposalChanged?.invoke()
                    }
                    is ApplyProposalOutcome.Conflict -> {
                        if (!force) {
                            _pendingConflict.value = PendingProposalConflict(
                                proposalId = proposalId,
                                selectedOpIds = selectedOpIds,
                                conflicts = outcome.conflicts,
                            )
                        }
                    }
                }
                is ApiResult.SessionExpired -> onSessionExpired()
                else -> res.errorTextOrNull()?.let(onError)
            }
        }
    }

    fun discard(gatewayUrl: GatewayUrl, proposalId: String) {
        val api = calendarApi ?: return
        runMutation(proposalId) {
            when (val res = api.discardProposal(gatewayUrl, proposalId)) {
                is ApiResult.Success -> {
                    _statuses.update { it + (proposalId to res.value.status) }
                    onProposalChanged?.invoke()
                }
                is ApiResult.SessionExpired -> onSessionExpired()
                else -> res.errorTextOrNull()?.let(onError)
            }
        }
    }

    fun revert(gatewayUrl: GatewayUrl, proposalId: String, force: Boolean = true) {
        val api = calendarApi ?: return
        runMutation(proposalId) {
            when (val res = api.revertProposal(gatewayUrl, proposalId, force = force)) {
                is ApiResult.Success -> {
                    _statuses.update { it + (proposalId to res.value.status) }
                    onProposalChanged?.invoke()
                }
                is ApiResult.SessionExpired -> onSessionExpired()
                else -> res.errorTextOrNull()?.let(onError)
            }
        }
    }

    private fun runMutation(proposalId: String, block: suspend () -> Unit) {
        scope.launch {
            _busyProposalIds.update { it + proposalId }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.message ?: "Proposal action failed.")
            } finally {
                _busyProposalIds.update { it - proposalId }
            }
        }
    }

    companion object {
        const val EVENT_PROPOSAL_UPDATED = "plugin.motherbase-calendar.calendar.proposal_updated"
        const val EVENT_PROPOSAL_STAGED = "plugin.motherbase-calendar.calendar.proposal_staged"
        const val EVENT_SYNCED = "plugin.motherbase-calendar.calendar.synced"
        const val EVENT_CHANGED = "plugin.motherbase-calendar.calendar.changed"
    }
}

@Composable
internal fun ScheduleProposalConflictDialog(
    conflict: PendingProposalConflict?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onForceApply: () -> Unit,
) {
    Dialog(
        visible = conflict != null,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.cal_proposal_conflict_title),
        message = stringResource(Res.string.cal_proposal_conflict_message),
        actions = {
            Button(
                text = stringResource(Res.string.common_cancel),
                onClick = onDismiss,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Small,
                enabled = !busy,
            )
            Button(
                text = stringResource(Res.string.cal_proposal_force_apply),
                onClick = onForceApply,
                variant = ButtonVariant.Primary,
                size = ButtonSize.Small,
                enabled = !busy,
            )
        },
    ) {
        if (conflict != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Theme[colors][surface2], RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                conflict.conflicts.forEach { item ->
                    Text(
                        item.title,
                        style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.SemiBold),
                        color = Theme[colors][textColor],
                    )
                    item.conflicts.forEach { c ->
                        val window = listOfNotNull(c.startAt?.take(16)?.replace('T', ' '), c.endAt?.drop(11)?.take(5)).joinToString(" – ")
                        val label = listOfNotNull(c.title.ifBlank { null }, window.ifBlank { null }, c.calendarName).joinToString(" · ")
                        Text(
                            "• $label",
                            style = Theme[typography][caption],
                            color = Theme[colors][warning],
                        )
                    }
                }
            }
        }
    }
}
