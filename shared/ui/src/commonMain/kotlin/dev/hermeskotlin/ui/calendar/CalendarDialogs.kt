package dev.hermeskotlin.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.composables.icons.lucide.Calendar
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Users
import dev.hermeskotlin.core.calendar.CalendarAccount
import dev.hermeskotlin.core.calendar.CalendarEvent
import dev.hermeskotlin.core.calendar.CalendarHealthStatus
import dev.hermeskotlin.core.calendar.CalendarInfo
import dev.hermeskotlin.core.calendar.CalendarSettings
import dev.hermeskotlin.core.calendar.FreeSlot
import dev.hermeskotlin.core.calendar.calendarZonedFromIso
import dev.hermeskotlin.core.calendar.formatClockCompact
import dev.hermeskotlin.designsystem.accent
import dev.hermeskotlin.designsystem.accentText
import dev.hermeskotlin.designsystem.bodySmall
import dev.hermeskotlin.designsystem.caption
import dev.hermeskotlin.designsystem.colors
import dev.hermeskotlin.designsystem.components.Button
import dev.hermeskotlin.designsystem.components.ButtonSize
import dev.hermeskotlin.designsystem.components.ButtonVariant
import dev.hermeskotlin.designsystem.components.Chip
import dev.hermeskotlin.designsystem.components.Dialog
import dev.hermeskotlin.designsystem.components.Switch
import dev.hermeskotlin.designsystem.components.TextField
import dev.hermeskotlin.designsystem.components.handCursor
import dev.hermeskotlin.designsystem.danger
import dev.hermeskotlin.designsystem.dangerSoft
import dev.hermeskotlin.designsystem.stroke
import dev.hermeskotlin.designsystem.surface
import dev.hermeskotlin.designsystem.surface2
import dev.hermeskotlin.designsystem.text as textColor
import dev.hermeskotlin.designsystem.textSecondary
import dev.hermeskotlin.designsystem.textTertiary
import dev.hermeskotlin.designsystem.typography
import dev.hermeskotlin.designsystem.warning
import dev.hermeskotlin.ui.resources.Res
import dev.hermeskotlin.ui.resources.app_save
import dev.hermeskotlin.ui.resources.cal_accounts_heading
import dev.hermeskotlin.ui.resources.cal_all_day
import dev.hermeskotlin.ui.resources.cal_connect_caldav_hint
import dev.hermeskotlin.ui.resources.cal_connect_caldav_url
import dev.hermeskotlin.ui.resources.cal_connect_client_id
import dev.hermeskotlin.ui.resources.cal_connect_client_secret
import dev.hermeskotlin.ui.resources.cal_connect_complete
import dev.hermeskotlin.ui.resources.cal_connect_ics_url
import dev.hermeskotlin.ui.resources.cal_connect_import_hermes
import dev.hermeskotlin.ui.resources.cal_connect_import_hermes_generic
import dev.hermeskotlin.ui.resources.cal_connect_label
import dev.hermeskotlin.ui.resources.cal_connect_lark_region_cn
import dev.hermeskotlin.ui.resources.cal_connect_lark_region_intl
import dev.hermeskotlin.ui.resources.cal_connect_open_auth
import dev.hermeskotlin.ui.resources.cal_connect_password
import dev.hermeskotlin.ui.resources.cal_connect_paste_code
import dev.hermeskotlin.ui.resources.cal_connect_provider_caldav
import dev.hermeskotlin.ui.resources.cal_connect_provider_google
import dev.hermeskotlin.ui.resources.cal_connect_provider_ics
import dev.hermeskotlin.ui.resources.cal_connect_provider_lark
import dev.hermeskotlin.ui.resources.cal_connect_provider_microsoft
import dev.hermeskotlin.ui.resources.cal_connect_subcalendar
import dev.hermeskotlin.ui.resources.cal_connect_tenant_id
import dev.hermeskotlin.ui.resources.cal_connect_title
import dev.hermeskotlin.ui.resources.cal_connect_username
import dev.hermeskotlin.ui.resources.cal_delete_attendee_confirm
import dev.hermeskotlin.ui.resources.cal_disconnect
import dev.hermeskotlin.ui.resources.cal_edit_event
import dev.hermeskotlin.ui.resources.cal_estimated_minutes
import dev.hermeskotlin.ui.resources.cal_event_all_day
import dev.hermeskotlin.ui.resources.cal_event_attendees
import dev.hermeskotlin.ui.resources.cal_event_busy
import dev.hermeskotlin.ui.resources.cal_event_calendar
import dev.hermeskotlin.ui.resources.cal_event_date
import dev.hermeskotlin.ui.resources.cal_event_delete
import dev.hermeskotlin.ui.resources.cal_event_description
import dev.hermeskotlin.ui.resources.cal_event_end
import dev.hermeskotlin.ui.resources.cal_event_free
import dev.hermeskotlin.ui.resources.cal_event_location
import dev.hermeskotlin.ui.resources.cal_event_notify_attendees
import dev.hermeskotlin.ui.resources.cal_event_save
import dev.hermeskotlin.ui.resources.cal_event_start
import dev.hermeskotlin.ui.resources.cal_event_title
import dev.hermeskotlin.ui.resources.cal_find_free_slots
import dev.hermeskotlin.ui.resources.cal_new_event
import dev.hermeskotlin.ui.resources.cal_no_free_slots
import dev.hermeskotlin.ui.resources.cal_read_only
import dev.hermeskotlin.ui.resources.cal_schedule_task
import dev.hermeskotlin.ui.resources.cal_settings_subcal_name
import dev.hermeskotlin.ui.resources.cal_settings_timezone
import dev.hermeskotlin.ui.resources.cal_settings_title
import dev.hermeskotlin.ui.resources.cal_settings_working_hours
import dev.hermeskotlin.ui.resources.cal_unschedule_block
import dev.hermeskotlin.ui.resources.common_cancel
import org.jetbrains.compose.resources.stringResource

private enum class ConnectProviderTab {
    GOOGLE,
    LARK,
    MICROSOFT,
    CALDAV,
    ICS,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConnectCalendarDialog(
    visible: Boolean,
    health: CalendarHealthStatus?,
    oauthPending: OAuthPendingState?,
    oauthBusy: Boolean,
    oauthError: String?,
    onDismiss: () -> Unit,
    onImportHermesGoogle: (label: String?) -> Unit,
    onStartGoogleOAuth: (clientId: String?, clientSecret: String?, label: String?, ensureSubcal: Boolean, openUrl: (String) -> Unit) -> Unit,
    onStartLarkOAuth: (appId: String, appSecret: String, region: String, label: String?, ensureSubcal: Boolean, openUrl: (String) -> Unit) -> Unit,
    onStartMicrosoftOAuth: (clientId: String, clientSecret: String?, tenantId: String, label: String?, ensureSubcal: Boolean, openUrl: (String) -> Unit) -> Unit,
    onCompleteOAuth: (rawCodeOrUrl: String) -> Unit,
    onConnectCalDav: (serverUrl: String, username: String, password: String, label: String?) -> Unit,
    onConnectIcs: (feedUrl: String, calName: String, label: String?) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var providerTab by remember(visible) { mutableStateOf(ConnectProviderTab.GOOGLE) }
    var ensureSubcalendar by remember(visible) { mutableStateOf(true) }
    var larkRegion by remember(visible) { mutableStateOf("intl") }

    val labelState = rememberTextFieldState("")
    val clientIdState = rememberTextFieldState("")
    val clientSecretState = rememberTextFieldState("")
    val tenantIdState = rememberTextFieldState("common")
    val oauthCodeState = rememberTextFieldState("")
    val caldavUrlState = rememberTextFieldState("https://caldav.larksuite.com")
    val usernameState = rememberTextFieldState("")
    val passwordState = rememberTextFieldState("")
    val icsUrlState = rememberTextFieldState("")
    val icsNameState = rememberTextFieldState("Holiday / External")

    Dialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.cal_connect_title),
        maxWidth = 520.dp,
        actions = {
            Button(
                text = stringResource(Res.string.common_cancel),
                onClick = onDismiss,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Small,
                enabled = !oauthBusy,
            )
            when (providerTab) {
                ConnectProviderTab.GOOGLE -> {
                    if (oauthPending?.provider == "google") {
                        Button(
                            text = stringResource(Res.string.cal_connect_complete),
                            onClick = { onCompleteOAuth(oauthCodeState.text.toString()) },
                            variant = ButtonVariant.Primary,
                            size = ButtonSize.Small,
                            enabled = !oauthBusy,
                        )
                    } else {
                        Button(
                            text = stringResource(Res.string.cal_connect_open_auth),
                            onClick = {
                                onStartGoogleOAuth(
                                    clientIdState.text.toString(),
                                    clientSecretState.text.toString(),
                                    labelState.text.toString(),
                                    ensureSubcalendar,
                                ) { url -> runCatching { uriHandler.openUri(url) } }
                            },
                            variant = ButtonVariant.Primary,
                            size = ButtonSize.Small,
                            enabled = !oauthBusy,
                        )
                    }
                }
                ConnectProviderTab.LARK -> {
                    if (oauthPending?.provider == "lark") {
                        Button(
                            text = stringResource(Res.string.cal_connect_complete),
                            onClick = { onCompleteOAuth(oauthCodeState.text.toString()) },
                            variant = ButtonVariant.Primary,
                            size = ButtonSize.Small,
                            enabled = !oauthBusy,
                        )
                    } else {
                        Button(
                            text = stringResource(Res.string.cal_connect_open_auth),
                            onClick = {
                                onStartLarkOAuth(
                                    clientIdState.text.toString(),
                                    clientSecretState.text.toString(),
                                    larkRegion,
                                    labelState.text.toString(),
                                    ensureSubcalendar,
                                ) { url -> runCatching { uriHandler.openUri(url) } }
                            },
                            variant = ButtonVariant.Primary,
                            size = ButtonSize.Small,
                            enabled = !oauthBusy,
                        )
                    }
                }
                ConnectProviderTab.MICROSOFT -> {
                    if (oauthPending?.provider == "microsoft") {
                        Button(
                            text = stringResource(Res.string.cal_connect_complete),
                            onClick = { onCompleteOAuth(oauthCodeState.text.toString()) },
                            variant = ButtonVariant.Primary,
                            size = ButtonSize.Small,
                            enabled = !oauthBusy,
                        )
                    } else {
                        Button(
                            text = stringResource(Res.string.cal_connect_open_auth),
                            onClick = {
                                onStartMicrosoftOAuth(
                                    clientIdState.text.toString(),
                                    clientSecretState.text.toString(),
                                    tenantIdState.text.toString(),
                                    labelState.text.toString(),
                                    ensureSubcalendar,
                                ) { url -> runCatching { uriHandler.openUri(url) } }
                            },
                            variant = ButtonVariant.Primary,
                            size = ButtonSize.Small,
                            enabled = !oauthBusy,
                        )
                    }
                }
                ConnectProviderTab.CALDAV -> {
                    Button(
                        text = stringResource(Res.string.cal_connect_complete),
                        onClick = {
                            onConnectCalDav(
                                caldavUrlState.text.toString(),
                                usernameState.text.toString(),
                                passwordState.text.toString(),
                                labelState.text.toString(),
                            )
                        },
                        variant = ButtonVariant.Primary,
                        size = ButtonSize.Small,
                        enabled = !oauthBusy,
                    )
                }
                ConnectProviderTab.ICS -> {
                    Button(
                        text = stringResource(Res.string.cal_connect_complete),
                        onClick = {
                            onConnectIcs(
                                icsUrlState.text.toString(),
                                icsNameState.text.toString(),
                                labelState.text.toString(),
                            )
                        },
                        variant = ButtonVariant.Primary,
                        size = ButtonSize.Small,
                        enabled = !oauthBusy,
                    )
                }
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Chip(stringResource(Res.string.cal_connect_provider_google), providerTab == ConnectProviderTab.GOOGLE, { providerTab = ConnectProviderTab.GOOGLE })
                Chip(stringResource(Res.string.cal_connect_provider_lark), providerTab == ConnectProviderTab.LARK, { providerTab = ConnectProviderTab.LARK })
                Chip(stringResource(Res.string.cal_connect_provider_microsoft), providerTab == ConnectProviderTab.MICROSOFT, { providerTab = ConnectProviderTab.MICROSOFT })
                Chip(stringResource(Res.string.cal_connect_provider_caldav), providerTab == ConnectProviderTab.CALDAV, { providerTab = ConnectProviderTab.CALDAV })
                Chip(stringResource(Res.string.cal_connect_provider_ics), providerTab == ConnectProviderTab.ICS, { providerTab = ConnectProviderTab.ICS })
            }

            if (oauthError != null) {
                Text(
                    oauthError,
                    style = Theme[typography][bodySmall],
                    color = Theme[colors][danger],
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Theme[colors][dangerSoft], RoundedCornerShape(8.dp))
                        .padding(8.dp),
                )
            }

            if (providerTab == ConnectProviderTab.GOOGLE && health?.hasHermesGoogleToken == true) {
                val hintEmail = health.hermesGoogleEmail
                val importLabel = if (!hintEmail.isNullOrBlank()) {
                    stringResource(Res.string.cal_connect_import_hermes, hintEmail)
                } else {
                    stringResource(Res.string.cal_connect_import_hermes_generic)
                }
                Button(
                    text = importLabel,
                    onClick = { onImportHermesGoogle(labelState.text.toString().ifBlank { null }) },
                    variant = ButtonVariant.Primary,
                    size = ButtonSize.Small,
                    leadingIcon = KeyRoundIcon,
                    enabled = !oauthBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            TextField(
                state = labelState,
                label = stringResource(Res.string.cal_connect_label),
                placeholder = "Work / Personal",
            )

            when (providerTab) {
                ConnectProviderTab.GOOGLE -> {
                    val hasDefaultClient = health?.hasHermesGoogleClient == true
                    TextField(
                        state = clientIdState,
                        label = stringResource(Res.string.cal_connect_client_id),
                        placeholder = if (hasDefaultClient) "(Optional — uses Hermes Google client)" else "Google OAuth Client ID",
                    )
                    TextField(
                        state = clientSecretState,
                        label = stringResource(Res.string.cal_connect_client_secret),
                        placeholder = if (hasDefaultClient) "(Optional — uses Hermes Google client)" else "Google OAuth Client Secret",
                        password = true,
                    )
                    SubcalendarSwitchRow(ensureSubcalendar) { ensureSubcalendar = it }
                    if (oauthPending?.provider == "google") {
                        OAuthCodePasteSection(oauthPending.authUrl, oauthCodeState, uriHandler::openUri)
                    }
                }
                ConnectProviderTab.LARK -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(stringResource(Res.string.cal_connect_lark_region_intl), larkRegion == "intl", { larkRegion = "intl" })
                        Chip(stringResource(Res.string.cal_connect_lark_region_cn), larkRegion == "cn", { larkRegion = "cn" })
                    }
                    TextField(
                        state = clientIdState,
                        label = stringResource(Res.string.cal_connect_client_id),
                        placeholder = "cli_...",
                    )
                    TextField(
                        state = clientSecretState,
                        label = stringResource(Res.string.cal_connect_client_secret),
                        password = true,
                    )
                    SubcalendarSwitchRow(ensureSubcalendar) { ensureSubcalendar = it }
                    if (oauthPending?.provider == "lark") {
                        OAuthCodePasteSection(oauthPending.authUrl, oauthCodeState, uriHandler::openUri)
                    }
                }
                ConnectProviderTab.MICROSOFT -> {
                    TextField(
                        state = clientIdState,
                        label = stringResource(Res.string.cal_connect_client_id),
                        placeholder = "Entra App Client ID",
                    )
                    TextField(
                        state = tenantIdState,
                        label = stringResource(Res.string.cal_connect_tenant_id),
                        placeholder = "common",
                    )
                    TextField(
                        state = clientSecretState,
                        label = stringResource(Res.string.cal_connect_client_secret),
                        placeholder = "Optional for public client",
                        password = true,
                    )
                    SubcalendarSwitchRow(ensureSubcalendar) { ensureSubcalendar = it }
                    if (oauthPending?.provider == "microsoft") {
                        OAuthCodePasteSection(oauthPending.authUrl, oauthCodeState, uriHandler::openUri)
                    }
                }
                ConnectProviderTab.CALDAV -> {
                    TextField(
                        state = caldavUrlState,
                        label = stringResource(Res.string.cal_connect_caldav_url),
                        placeholder = stringResource(Res.string.cal_connect_caldav_hint),
                    )
                    TextField(
                        state = usernameState,
                        label = stringResource(Res.string.cal_connect_username),
                    )
                    TextField(
                        state = passwordState,
                        label = stringResource(Res.string.cal_connect_password),
                        password = true,
                    )
                }
                ConnectProviderTab.ICS -> {
                    TextField(
                        state = icsUrlState,
                        label = stringResource(Res.string.cal_connect_ics_url),
                        placeholder = "https://.../calendar.ics",
                    )
                    TextField(
                        state = icsNameState,
                        label = stringResource(Res.string.cal_event_calendar),
                    )
                }
            }
        }
    }
}

private val KeyRoundIcon = Lucide.KeyRound

@Composable
private fun SubcalendarSwitchRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Switch) { onToggle(!checked) }
            .handCursor()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.cal_connect_subcalendar),
            style = Theme[typography][bodySmall],
            color = Theme[colors][textSecondary],
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked)
    }
}

@Composable
private fun OAuthCodePasteSection(
    authUrl: String,
    codeState: androidx.compose.foundation.text.input.TextFieldState,
    onOpenUrl: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Theme[colors][surface2], RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            text = stringResource(Res.string.cal_connect_open_auth),
            onClick = { runCatching { onOpenUrl(authUrl) } },
            variant = ButtonVariant.Outline,
            size = ButtonSize.Small,
            leadingIcon = Lucide.ExternalLink,
        )
        TextField(
            state = codeState,
            label = stringResource(Res.string.cal_connect_paste_code),
            placeholder = "http://localhost/...?code=...",
        )
    }
}

@Composable
internal fun EventDetailDialog(
    event: CalendarEvent?,
    use24Hour: Boolean,
    mutating: Boolean,
    onDismiss: () -> Unit,
    onEdit: (CalendarEvent) -> Unit,
    onDelete: (occurrenceId: String, notifyAttendees: Boolean) -> Unit,
    onUnscheduleBlock: (blockId: String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var notifyOnDelete by remember(event?.id) { mutableStateOf(false) }

    Dialog(
        visible = event != null,
        onDismissRequest = onDismiss,
        title = event?.title.orEmpty(),
        maxWidth = 460.dp,
        actions = {
            if (event != null) {
                val blockId = event.timeBlockId
                if (blockId != null) {
                    Button(
                        text = stringResource(Res.string.cal_unschedule_block),
                        onClick = { onUnscheduleBlock(blockId) },
                        variant = ButtonVariant.Ghost,
                        size = ButtonSize.Small,
                        leadingIcon = Lucide.Trash2,
                        enabled = !mutating,
                    )
                } else if (event.calendarWritable) {
                    Button(
                        text = stringResource(Res.string.cal_event_delete),
                        onClick = { onDelete(event.id, notifyOnDelete) },
                        variant = ButtonVariant.Ghost,
                        size = ButtonSize.Small,
                        leadingIcon = Lucide.Trash2,
                        enabled = !mutating,
                    )
                    Button(
                        text = stringResource(Res.string.cal_edit_event),
                        onClick = { onEdit(event) },
                        variant = ButtonVariant.Primary,
                        size = ButtonSize.Small,
                        leadingIcon = Lucide.Pencil,
                        enabled = !mutating,
                    )
                }
            }
            Button(
                text = stringResource(Res.string.common_cancel),
                onClick = onDismiss,
                variant = ButtonVariant.Outline,
                size = ButtonSize.Small,
            )
        },
    ) {
        if (event != null) {
            val dotColor = parseCalendarHexColor(event.colorHex, Theme[colors][accent])
            val startM = event.startAt?.let { calendarZonedFromIso(it, event.timezone.ifBlank { null }) }
            val endM = event.endAt?.let { calendarZonedFromIso(it, event.timezone.ifBlank { null }) }
            val timeSummary = when {
                event.allDay -> "${event.startDate ?: ""} · ${stringResource(Res.string.cal_all_day)}"
                startM != null && endM != null -> "${startM.date.iso} · ${formatClockCompact(startM.minuteOfDay, use24Hour)} – ${formatClockCompact(endM.minuteOfDay, use24Hour)}"
                startM != null -> "${startM.date.iso} · ${formatClockCompact(startM.minuteOfDay, use24Hour)}"
                else -> event.startDate ?: ""
            }
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(dotColor, CircleShape))
                    Text(event.calendarName, style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.Medium), color = Theme[colors][textSecondary])
                    if (!event.calendarWritable) {
                        Text(
                            stringResource(Res.string.cal_read_only),
                            style = Theme[typography][caption],
                            color = Theme[colors][textTertiary],
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    UnstyledIcon(Lucide.Clock, contentDescription = null, tint = Theme[colors][textSecondary], modifier = Modifier.size(15.dp))
                    Text(timeSummary, style = Theme[typography][bodySmall], color = Theme[colors][textColor])
                }
                if (!event.location.isNullOrBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        UnstyledIcon(Lucide.MapPin, contentDescription = null, tint = Theme[colors][textSecondary], modifier = Modifier.size(15.dp))
                        Text(event.location.orEmpty(), style = Theme[typography][bodySmall], color = Theme[colors][textColor])
                    }
                }
                if (!event.conferenceUrl.isNullOrBlank()) {
                    val confUrl = event.conferenceUrl.orEmpty()
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(role = Role.Button) { runCatching { uriHandler.openUri(confUrl) } }
                            .handCursor()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UnstyledIcon(Lucide.Link, contentDescription = null, tint = Theme[colors][accentText], modifier = Modifier.size(15.dp))
                        Text(confUrl, style = Theme[typography][bodySmall], color = Theme[colors][accentText], maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!event.description.isNullOrBlank()) {
                    Text(
                        event.description.orEmpty(),
                        style = Theme[typography][bodySmall],
                        color = Theme[colors][textSecondary],
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Theme[colors][surface2], RoundedCornerShape(8.dp))
                            .padding(10.dp),
                    )
                }
                if (event.attendees.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        UnstyledIcon(Lucide.Users, contentDescription = null, tint = Theme[colors][textSecondary], modifier = Modifier.size(14.dp))
                        Text(
                            event.attendees.joinToString(", ") { it.name.ifBlank { it.email } },
                            style = Theme[typography][caption],
                            color = Theme[colors][textSecondary],
                        )
                    }
                    if (event.hasExternalAttendees && event.calendarWritable && event.timeBlockId == null) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Switch) { notifyOnDelete = !notifyOnDelete }
                                .handCursor()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(Res.string.cal_delete_attendee_confirm),
                                style = Theme[typography][caption],
                                color = Theme[colors][warning],
                                modifier = Modifier.weight(1f),
                            )
                            Switch(checked = notifyOnDelete)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EventEditorDialog(
    draft: EventEditorDraft?,
    calendars: List<CalendarInfo>,
    mutating: Boolean,
    onDismiss: () -> Unit,
    onSave: (EventEditorDraft) -> Unit,
    onDelete: (String) -> Unit,
) {
    val writableCals = remember(calendars) { calendars.filter { it.writable } }
    var selectedCalId by remember(draft) { mutableStateOf(draft?.calendarId ?: writableCals.firstOrNull()?.id.orEmpty()) }
    var allDay by remember(draft) { mutableStateOf(draft?.allDay ?: false) }
    var busy by remember(draft) { mutableStateOf(draft?.busy ?: true) }
    var sendUpdates by remember(draft) { mutableStateOf(draft?.sendUpdates ?: false) }

    val titleState = rememberTextFieldState(draft?.title.orEmpty())
    val dateState = rememberTextFieldState(draft?.dateIso.orEmpty())
    val endDateState = rememberTextFieldState(draft?.endDateIso.orEmpty())
    val startState = rememberTextFieldState(draft?.startTime ?: "09:00")
    val endState = rememberTextFieldState(draft?.endTime ?: "10:00")
    val locationState = rememberTextFieldState(draft?.location.orEmpty())
    val descState = rememberTextFieldState(draft?.description.orEmpty())
    val attendeesState = rememberTextFieldState(draft?.attendeesText.orEmpty())

    LaunchedEffect(draft) {
        if (draft != null) {
            titleState.setTextAndPlaceCursorAtEnd(draft.title)
            dateState.setTextAndPlaceCursorAtEnd(draft.dateIso)
            endDateState.setTextAndPlaceCursorAtEnd(draft.endDateIso)
            startState.setTextAndPlaceCursorAtEnd(draft.startTime)
            endState.setTextAndPlaceCursorAtEnd(draft.endTime)
            locationState.setTextAndPlaceCursorAtEnd(draft.location)
            descState.setTextAndPlaceCursorAtEnd(draft.description)
            attendeesState.setTextAndPlaceCursorAtEnd(draft.attendeesText)
        }
    }

    Dialog(
        visible = draft != null,
        onDismissRequest = onDismiss,
        title = stringResource(if (draft?.existingOccurrenceId == null) Res.string.cal_new_event else Res.string.cal_edit_event),
        maxWidth = 500.dp,
        actions = {
            if (draft?.existingOccurrenceId != null) {
                Button(
                    text = stringResource(Res.string.cal_event_delete),
                    onClick = { onDelete(draft.existingOccurrenceId) },
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.Small,
                    enabled = !mutating,
                )
            }
            Button(
                text = stringResource(Res.string.common_cancel),
                onClick = onDismiss,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Small,
                enabled = !mutating,
            )
            Button(
                text = stringResource(Res.string.cal_event_save),
                onClick = {
                    if (draft != null) {
                        onSave(
                            draft.copy(
                                calendarId = selectedCalId,
                                title = titleState.text.toString(),
                                dateIso = dateState.text.toString(),
                                endDateIso = endDateState.text.toString(),
                                startTime = startState.text.toString(),
                                endTime = endState.text.toString(),
                                allDay = allDay,
                                location = locationState.text.toString(),
                                description = descState.text.toString(),
                                attendeesText = attendeesState.text.toString(),
                                busy = busy,
                                sendUpdates = sendUpdates,
                            ),
                        )
                    }
                },
                variant = ButtonVariant.Primary,
                size = ButtonSize.Small,
                enabled = !mutating,
            )
        },
    ) {
        if (draft != null) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextField(
                    state = titleState,
                    label = stringResource(Res.string.cal_event_title),
                    autofocus = true,
                )
                if (draft.existingOccurrenceId == null && writableCals.isNotEmpty()) {
                    Text(stringResource(Res.string.cal_event_calendar), style = Theme[typography][caption], color = Theme[colors][textSecondary])
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        writableCals.forEach { cal ->
                            Chip(
                                text = cal.name,
                                selected = selectedCalId == cal.id,
                                onClick = { selectedCalId = cal.id },
                            )
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Switch) { allDay = !allDay }
                        .handCursor()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(Res.string.cal_event_all_day), style = Theme[typography][bodySmall], color = Theme[colors][textColor])
                    Switch(checked = allDay)
                }
                TextField(state = dateState, label = stringResource(Res.string.cal_event_date))
                if (allDay) {
                    TextField(state = endDateState, label = stringResource(Res.string.cal_event_end))
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(state = startState, label = stringResource(Res.string.cal_event_start), modifier = Modifier.weight(1f))
                        TextField(state = endState, label = stringResource(Res.string.cal_event_end), modifier = Modifier.weight(1f))
                    }
                }
                TextField(state = locationState, label = stringResource(Res.string.cal_event_location))
                TextField(state = attendeesState, label = stringResource(Res.string.cal_event_attendees))
                if (attendeesState.text.isNotBlank()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Switch) { sendUpdates = !sendUpdates }
                            .handCursor(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(Res.string.cal_event_notify_attendees),
                            style = Theme[typography][caption],
                            color = Theme[colors][warning],
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = sendUpdates)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(Res.string.cal_event_busy), busy, { busy = true })
                    Chip(stringResource(Res.string.cal_event_free), !busy, { busy = false })
                }
                TextField(
                    state = descState,
                    label = stringResource(Res.string.cal_event_description),
                    singleLine = false,
                    maxLines = 4,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScheduleTaskBlockDialog(
    draft: TaskBlockDraft?,
    freeSlots: List<FreeSlot>,
    findingSlots: Boolean,
    mutating: Boolean,
    onDismiss: () -> Unit,
    onRefreshSlots: (Int) -> Unit,
    onSave: (TaskBlockDraft) -> Unit,
    onDeleteBlock: (String) -> Unit,
) {
    val labelState = rememberTextFieldState(draft?.label.orEmpty())
    val dateState = rememberTextFieldState(draft?.dateIso.orEmpty())
    val startState = rememberTextFieldState(draft?.startTime ?: "09:00")
    var durationMin by remember(draft) { mutableIntStateOf(draft?.durationMinutes ?: 60) }

    LaunchedEffect(draft) {
        if (draft != null) {
            labelState.setTextAndPlaceCursorAtEnd(draft.label)
            dateState.setTextAndPlaceCursorAtEnd(draft.dateIso)
            startState.setTextAndPlaceCursorAtEnd(draft.startTime)
            durationMin = draft.durationMinutes
        }
    }

    Dialog(
        visible = draft != null,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.cal_schedule_task),
        maxWidth = 480.dp,
        actions = {
            if (draft?.existingBlockId != null) {
                Button(
                    text = stringResource(Res.string.cal_unschedule_block),
                    onClick = { onDeleteBlock(draft.existingBlockId) },
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.Small,
                    enabled = !mutating,
                )
            }
            Button(
                text = stringResource(Res.string.common_cancel),
                onClick = onDismiss,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Small,
                enabled = !mutating,
            )
            Button(
                text = stringResource(Res.string.app_save),
                onClick = {
                    if (draft != null) {
                        onSave(
                            draft.copy(
                                label = labelState.text.toString(),
                                dateIso = dateState.text.toString(),
                                startTime = startState.text.toString(),
                                durationMinutes = durationMin,
                            ),
                        )
                    }
                },
                variant = ButtonVariant.Primary,
                size = ButtonSize.Small,
                enabled = !mutating,
            )
        },
    ) {
        if (draft != null) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        draft.task.title,
                        style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.SemiBold),
                        color = Theme[colors][textColor],
                        modifier = Modifier.weight(1f),
                    )
                    draft.task.estimatedMinutes?.let { est ->
                        Text(
                            stringResource(Res.string.cal_estimated_minutes, est),
                            style = Theme[typography][caption],
                            color = Theme[colors][accentText],
                        )
                    }
                }

                TextField(state = labelState, label = stringResource(Res.string.cal_event_title))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(state = dateState, label = stringResource(Res.string.cal_event_date), modifier = Modifier.weight(1f))
                    TextField(state = startState, label = stringResource(Res.string.cal_event_start), modifier = Modifier.weight(1f))
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30, 45, 60, 90, 120).forEach { mins ->
                        Chip(
                            text = "${mins}m",
                            selected = durationMin == mins,
                            onClick = {
                                durationMin = mins
                                onRefreshSlots(mins)
                            },
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.cal_find_free_slots), style = Theme[typography][caption].copy(fontWeight = FontWeight.SemiBold), color = Theme[colors][textSecondary])
                    Button(
                        text = if (findingSlots) "…" else stringResource(Res.string.cal_find_free_slots),
                        onClick = { onRefreshSlots(durationMin) },
                        variant = ButtonVariant.Ghost,
                        size = ButtonSize.Small,
                        enabled = !findingSlots,
                    )
                }

                if (freeSlots.isEmpty()) {
                    Text(stringResource(Res.string.cal_no_free_slots), style = Theme[typography][caption], color = Theme[colors][textTertiary])
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        freeSlots.take(6).forEach { slot ->
                            Chip(
                                text = "${slot.date.drop(5)} ${slot.startLocal}–${slot.endLocal}",
                                selected = dateState.text.toString() == slot.date && startState.text.toString() == slot.startLocal,
                                onClick = {
                                    dateState.setTextAndPlaceCursorAtEnd(slot.date)
                                    startState.setTextAndPlaceCursorAtEnd(slot.startLocal)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CalendarSettingsDialog(
    visible: Boolean,
    settings: CalendarSettings,
    accounts: List<CalendarAccount>,
    mutating: Boolean,
    onDismiss: () -> Unit,
    onSave: (timezone: String, workStart: String, workEnd: String, subcalendarName: String) -> Unit,
    onDisconnectAccount: (accountId: String) -> Unit,
) {
    val defaultHours = remember(settings) {
        settings.workingHours["mon"] ?: settings.workingHours.values.firstOrNull() ?: listOf("09:00", "18:00")
    }
    val tzState = rememberTextFieldState(settings.timezone)
    val startState = rememberTextFieldState(defaultHours.getOrNull(0) ?: "09:00")
    val endState = rememberTextFieldState(defaultHours.getOrNull(1) ?: "18:00")
    val subcalState = rememberTextFieldState(settings.dispatchSyncTarget.subcalendarName)

    LaunchedEffect(visible, settings) {
        if (visible) {
            tzState.setTextAndPlaceCursorAtEnd(settings.timezone)
            startState.setTextAndPlaceCursorAtEnd(defaultHours.getOrNull(0) ?: "09:00")
            endState.setTextAndPlaceCursorAtEnd(defaultHours.getOrNull(1) ?: "18:00")
            subcalState.setTextAndPlaceCursorAtEnd(settings.dispatchSyncTarget.subcalendarName)
        }
    }

    Dialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.cal_settings_title),
        maxWidth = 500.dp,
        actions = {
            Button(
                text = stringResource(Res.string.common_cancel),
                onClick = onDismiss,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Small,
            )
            Button(
                text = stringResource(Res.string.app_save),
                onClick = {
                    onSave(
                        tzState.text.toString(),
                        startState.text.toString(),
                        endState.text.toString(),
                        subcalState.text.toString(),
                    )
                },
                variant = ButtonVariant.Primary,
                size = ButtonSize.Small,
                enabled = !mutating,
            )
        },
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextField(state = tzState, label = stringResource(Res.string.cal_settings_timezone), leadingIcon = GlobeIcon)
            Text(stringResource(Res.string.cal_settings_working_hours), style = Theme[typography][caption], color = Theme[colors][textSecondary])
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(state = startState, label = stringResource(Res.string.cal_event_start), modifier = Modifier.weight(1f))
                TextField(state = endState, label = stringResource(Res.string.cal_event_end), modifier = Modifier.weight(1f))
            }
            TextField(state = subcalState, label = stringResource(Res.string.cal_settings_subcal_name), leadingIcon = CalendarIcon)

            if (accounts.isNotEmpty()) {
                Text(
                    stringResource(Res.string.cal_accounts_heading),
                    style = Theme[typography][caption].copy(fontWeight = FontWeight.SemiBold),
                    color = Theme[colors][textSecondary],
                    modifier = Modifier.padding(top = 6.dp),
                )
                accounts.forEach { acc ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Theme[colors][surface], RoundedCornerShape(8.dp))
                            .border(1.dp, Theme[colors][stroke], RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                acc.label.ifBlank { acc.email ?: acc.provider },
                                style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.SemiBold),
                                color = Theme[colors][textColor],
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOfNotNull(acc.provider, acc.authMode, acc.email).joinToString(" · "),
                                style = Theme[typography][caption],
                                color = Theme[colors][textTertiary],
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            acc.lastError?.takeIf { it.isNotBlank() }?.let { err ->
                                Text(err, style = Theme[typography][caption], color = Theme[colors][danger], maxLines = 2)
                            }
                        }
                        Button(
                            text = stringResource(Res.string.cal_disconnect),
                            onClick = { onDisconnectAccount(acc.id) },
                            variant = ButtonVariant.Ghost,
                            size = ButtonSize.Small,
                            enabled = !mutating,
                        )
                    }
                }
            }
        }
    }
}

private val GlobeIcon = Lucide.Globe
private val CalendarIcon = Lucide.Calendar
