package dev.hermeskotlin.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.calendar.ApplyProposalOutcome
import dev.hermeskotlin.core.calendar.ApplyProposalRequest
import dev.hermeskotlin.core.calendar.CalendarApi
import dev.hermeskotlin.core.calendar.ProposalOperationConflicts
import dev.hermeskotlin.core.calendar.ProposalOperationPatch
import dev.hermeskotlin.core.calendar.ScheduleOperation
import dev.hermeskotlin.core.calendar.ScheduleProposal
import dev.hermeskotlin.core.calendar.UpdateProposalRequest
import dev.hermeskotlin.core.calendar.calendarDateFromIso
import dev.hermeskotlin.core.calendar.calendarToUtcIso
import dev.hermeskotlin.core.calendar.calendarToday
import dev.hermeskotlin.core.calendar.calendarZonedFromIso
import dev.hermeskotlin.core.calendar.formatClock24
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
import dev.hermeskotlin.designsystem.components.Switch
import dev.hermeskotlin.designsystem.components.TextField
import dev.hermeskotlin.designsystem.components.handCursor
import dev.hermeskotlin.designsystem.surface2
import dev.hermeskotlin.designsystem.text as textColor
import dev.hermeskotlin.designsystem.typography
import dev.hermeskotlin.designsystem.warning
import dev.hermeskotlin.ui.resources.Res
import dev.hermeskotlin.ui.resources.cal_event_all_day
import dev.hermeskotlin.ui.resources.cal_event_date
import dev.hermeskotlin.ui.resources.cal_event_end
import dev.hermeskotlin.ui.resources.cal_event_start
import dev.hermeskotlin.ui.resources.cal_proposal_conflict_message
import dev.hermeskotlin.ui.resources.cal_proposal_conflict_title
import dev.hermeskotlin.ui.resources.cal_proposal_edit_item
import dev.hermeskotlin.ui.resources.cal_proposal_force_apply
import dev.hermeskotlin.ui.resources.cal_proposal_item_title
import dev.hermeskotlin.ui.resources.cal_proposal_remove_item
import dev.hermeskotlin.ui.resources.cal_proposal_save_item
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

    private val _updatedProposals = MutableStateFlow<Map<String, ScheduleProposal>>(emptyMap())
    val updatedProposals: StateFlow<Map<String, ScheduleProposal>> = _updatedProposals.asStateFlow()

    private val _deletedProposalIds = MutableStateFlow<Set<String>>(emptySet())
    val deletedProposalIds: StateFlow<Set<String>> = _deletedProposalIds.asStateFlow()

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
        _updatedProposals.value = emptyMap()
        _deletedProposalIds.value = emptySet()
        _busyProposalIds.value = emptySet()
        _pendingConflict.value = null
    }

    fun observeGatewayEvent(event: GatewayEvent) {
        val payload = event.payload as? JsonObject ?: return
        val id = payload["proposal_id"]?.jsonPrimitive?.contentOrNull ?: return
        when (event.type) {
            EVENT_PROPOSAL_UPDATED -> {
                val status = payload["status"]?.jsonPrimitive?.contentOrNull ?: return
                _statuses.update { it + (id to status) }
                _updatedProposals.update { map ->
                    val existing = map[id] ?: return@update map
                    map + (id to existing.copy(status = status))
                }
            }
            EVENT_PROPOSAL_DELETED -> {
                _deletedProposalIds.update { it + id }
                _updatedProposals.update { it - id }
            }
        }
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
                        _updatedProposals.update { it + (proposalId to outcome.proposal) }
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
                    _updatedProposals.update { it + (proposalId to res.value) }
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
                    _updatedProposals.update { it + (proposalId to res.value) }
                    _statuses.update { it + (proposalId to res.value.status) }
                    onProposalChanged?.invoke()
                }
                is ApiResult.SessionExpired -> onSessionExpired()
                else -> res.errorTextOrNull()?.let(onError)
            }
        }
    }

    fun update(gatewayUrl: GatewayUrl, proposalId: String, request: UpdateProposalRequest) {
        val api = calendarApi ?: return
        runMutation(proposalId) {
            when (val res = api.updateProposal(gatewayUrl, proposalId, request)) {
                is ApiResult.Success -> {
                    _deletedProposalIds.update { it - proposalId }
                    _updatedProposals.update { it + (proposalId to res.value) }
                    _statuses.update { it + (proposalId to res.value.status) }
                    onProposalChanged?.invoke()
                }
                is ApiResult.SessionExpired -> onSessionExpired()
                else -> res.errorTextOrNull()?.let(onError)
            }
        }
    }

    fun restage(gatewayUrl: GatewayUrl, proposalId: String) {
        update(gatewayUrl, proposalId, UpdateProposalRequest(status = ScheduleProposal.STATUS_STAGED))
    }

    fun delete(gatewayUrl: GatewayUrl, proposalId: String) {
        val api = calendarApi ?: return
        runMutation(proposalId) {
            when (val res = api.deleteProposal(gatewayUrl, proposalId)) {
                is ApiResult.Success -> {
                    _deletedProposalIds.update { it + proposalId }
                    _updatedProposals.update { it - proposalId }
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
        const val EVENT_PROPOSAL_DELETED = "plugin.motherbase-calendar.calendar.proposal_deleted"
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

private enum class ProposalItemPicker { DATE, START_TIME, END_TIME }

/**
 * Dialog for editing an individual [ScheduleOperation] inside a schedule proposal using visual
 * date and clock pickers, or removing that item from the proposal.
 */
@Composable
internal fun ScheduleProposalOperationEditorDialog(
    operation: ScheduleOperation?,
    canRemove: Boolean,
    use24Hour: Boolean = false,
    busy: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (ProposalOperationPatch) -> Unit,
    onRemove: () -> Unit,
) {
    val fallbackIso = remember { calendarToday().iso }
    val initialZonedStart = remember(operation) {
        operation?.startAt?.let { calendarZonedFromIso(it, operation.timezone) }
    }
    val initialZonedEnd = remember(operation) {
        operation?.endAt?.let { calendarZonedFromIso(it, operation.timezone) }
    }
    val allowAllDayToggle = operation != null && operation.kind != "schedule_task" && operation.kind != "unschedule_block"

    val titleState = rememberTextFieldState(operation?.title.orEmpty())
    var allDay by remember(operation) { mutableStateOf(operation?.allDay ?: false) }
    var dateIso by remember(operation) {
        mutableStateOf(
            operation?.startDate?.ifBlank { null }
                ?: initialZonedStart?.date?.iso
                ?: fallbackIso,
        )
    }
    var endDateIso by remember(operation) {
        mutableStateOf(
            operation?.endDate?.ifBlank { null }
                ?: initialZonedEnd?.date?.iso
                ?: dateIso,
        )
    }
    var startTime by remember(operation) {
        mutableStateOf(
            initialZonedStart?.let { formatClock24(it.minuteOfDay / 60, it.minuteOfDay % 60) } ?: "09:00",
        )
    }
    var endTime by remember(operation) {
        mutableStateOf(
            initialZonedEnd?.let { formatClock24(it.minuteOfDay / 60, it.minuteOfDay % 60) } ?: "10:00",
        )
    }
    var activePicker by remember(operation) { mutableStateOf<ProposalItemPicker?>(null) }

    LaunchedEffect(operation) {
        if (operation != null) {
            titleState.setTextAndPlaceCursorAtEnd(operation.title)
            allDay = operation.allDay
            val zs = operation.startAt?.let { calendarZonedFromIso(it, operation.timezone) }
            val ze = operation.endAt?.let { calendarZonedFromIso(it, operation.timezone) }
            val dIso = operation.startDate?.ifBlank { null } ?: zs?.date?.iso ?: fallbackIso
            dateIso = dIso
            endDateIso = operation.endDate?.ifBlank { null } ?: ze?.date?.iso ?: dIso
            startTime = zs?.let { formatClock24(it.minuteOfDay / 60, it.minuteOfDay % 60) } ?: "09:00"
            endTime = ze?.let { formatClock24(it.minuteOfDay / 60, it.minuteOfDay % 60) } ?: "10:00"
            activePicker = null
        }
    }

    val parsedDate = calendarDateFromIso(dateIso.trim())
    val (sh, sm) = parseTimeHHMM(startTime) ?: (9 to 0)
    val (eh, em) = parseTimeHHMM(endTime) ?: (10 to 0)
    val timeValid = allDay || operation?.kind == "unschedule_block" || (eh * 60 + em > sh * 60 + sm)
    val titleValid = titleState.text.toString().trim().isNotEmpty()

    Dialog(
        visible = operation != null,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.cal_proposal_edit_item),
        maxWidth = 460.dp,
        actions = {
            if (canRemove && operation != null) {
                Button(
                    text = stringResource(Res.string.cal_proposal_remove_item),
                    onClick = onRemove,
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.Small,
                    enabled = !busy,
                )
            }
            Button(
                text = stringResource(Res.string.common_cancel),
                onClick = onDismiss,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Small,
                enabled = !busy,
            )
            Button(
                text = stringResource(Res.string.cal_proposal_save_item),
                onClick = {
                    val op = operation ?: return@Button
                    val cleanTitle = titleState.text.toString().trim()
                    val date = parsedDate ?: return@Button
                    val tz = op.timezone
                    val patch = when {
                        op.kind == "unschedule_block" -> ProposalOperationPatch(
                            opId = op.opId,
                            title = cleanTitle,
                        )
                        allDay -> {
                            val eDate = calendarDateFromIso(endDateIso.trim())?.let {
                                if (it.epochDay < date.epochDay) date else it
                            } ?: date
                            ProposalOperationPatch(
                                opId = op.opId,
                                title = cleanTitle,
                                allDay = true,
                                startDate = date.iso,
                                endDate = eDate.iso,
                                timezone = tz,
                            )
                        }
                        else -> ProposalOperationPatch(
                            opId = op.opId,
                            title = cleanTitle,
                            allDay = false,
                            startAt = calendarToUtcIso(date, sh, sm, tz),
                            endAt = calendarToUtcIso(date, eh, em, tz),
                            timezone = tz,
                        )
                    }
                    onSave(patch)
                },
                variant = ButtonVariant.Primary,
                size = ButtonSize.Small,
                enabled = !busy && titleValid && timeValid && parsedDate != null,
            )
        },
    ) {
        if (operation != null) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextField(
                    state = titleState,
                    label = stringResource(Res.string.cal_proposal_item_title),
                    autofocus = true,
                )
                if (allowAllDayToggle) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Switch) {
                                allDay = !allDay
                                activePicker = null
                            }
                            .handCursor()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(Res.string.cal_event_all_day),
                            style = Theme[typography][bodySmall],
                            color = Theme[colors][textColor],
                        )
                        Switch(checked = allDay)
                    }
                }
                if (operation.kind != "unschedule_block") {
                    CalendarDatePickerTrigger(
                        label = stringResource(Res.string.cal_event_date),
                        valueIso = dateIso,
                        expanded = activePicker == ProposalItemPicker.DATE,
                        onClick = {
                            activePicker = if (activePicker == ProposalItemPicker.DATE) null else ProposalItemPicker.DATE
                        },
                    )
                    if (activePicker == ProposalItemPicker.DATE) {
                        CalendarDatePickerPanel(
                            selectedIso = dateIso,
                            onSelectDate = { picked ->
                                dateIso = picked
                                endDateIso = picked
                                activePicker = null
                            },
                            onDone = { activePicker = null },
                        )
                    }
                    if (!allDay) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CalendarTimePickerTrigger(
                                label = stringResource(Res.string.cal_event_start),
                                valueHHMM = startTime,
                                use24Hour = use24Hour,
                                expanded = activePicker == ProposalItemPicker.START_TIME,
                                onClick = {
                                    activePicker = if (activePicker == ProposalItemPicker.START_TIME) null else ProposalItemPicker.START_TIME
                                },
                                modifier = Modifier.weight(1f),
                            )
                            CalendarTimePickerTrigger(
                                label = stringResource(Res.string.cal_event_end),
                                valueHHMM = endTime,
                                use24Hour = use24Hour,
                                expanded = activePicker == ProposalItemPicker.END_TIME,
                                onClick = {
                                    activePicker = if (activePicker == ProposalItemPicker.END_TIME) null else ProposalItemPicker.END_TIME
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        when (activePicker) {
                            ProposalItemPicker.START_TIME -> {
                                val baseStart = remember(operation, activePicker) { startTime }
                                val baseEnd = remember(operation, activePicker) { endTime }
                                CalendarTimePickerPanel(
                                    valueHHMM = startTime,
                                    use24Hour = use24Hour,
                                    onSelectTime = { picked ->
                                        startTime = picked
                                        endTime = shiftEndTimeIfNeeded(baseStart, picked, baseEnd)
                                    },
                                    onDone = { activePicker = null },
                                )
                            }
                            ProposalItemPicker.END_TIME -> CalendarTimePickerPanel(
                                valueHHMM = endTime,
                                use24Hour = use24Hour,
                                onSelectTime = { picked -> endTime = picked },
                                onDone = { activePicker = null },
                            )
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}
