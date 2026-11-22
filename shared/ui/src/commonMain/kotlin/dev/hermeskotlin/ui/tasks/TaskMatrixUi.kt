package dev.hermeskotlin.ui.tasks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.GripVertical
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Lucide
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.tasks.DirectusProject
import dev.hermeskotlin.core.tasks.DirectusTask
import dev.hermeskotlin.core.tasks.EisenhowerQuadrant
import dev.hermeskotlin.core.tasks.MatrixLevel
import dev.hermeskotlin.designsystem.accent
import dev.hermeskotlin.designsystem.accentSoft
import dev.hermeskotlin.designsystem.accentText
import dev.hermeskotlin.designsystem.bodySmall
import dev.hermeskotlin.designsystem.caption
import dev.hermeskotlin.designsystem.code
import dev.hermeskotlin.designsystem.colors
import dev.hermeskotlin.designsystem.components.Button
import dev.hermeskotlin.designsystem.components.ButtonSize
import dev.hermeskotlin.designsystem.components.ButtonVariant
import dev.hermeskotlin.designsystem.components.Dialog
import dev.hermeskotlin.designsystem.components.handCursor
import dev.hermeskotlin.designsystem.danger
import dev.hermeskotlin.designsystem.dangerSoft
import dev.hermeskotlin.designsystem.eyebrow
import dev.hermeskotlin.designsystem.label as labelStyle
import dev.hermeskotlin.designsystem.radii
import dev.hermeskotlin.designsystem.radiusFull
import dev.hermeskotlin.designsystem.radiusLarge
import dev.hermeskotlin.designsystem.radiusMedium
import dev.hermeskotlin.designsystem.radiusSmall
import dev.hermeskotlin.designsystem.stroke
import dev.hermeskotlin.designsystem.strokeStrong
import dev.hermeskotlin.designsystem.surface
import dev.hermeskotlin.designsystem.surface2
import dev.hermeskotlin.designsystem.surface3
import dev.hermeskotlin.designsystem.text as textColor
import dev.hermeskotlin.designsystem.textMuted
import dev.hermeskotlin.designsystem.textSecondary
import dev.hermeskotlin.designsystem.textTertiary
import dev.hermeskotlin.designsystem.typography
import dev.hermeskotlin.designsystem.warning
import dev.hermeskotlin.designsystem.warningSoft
import dev.hermeskotlin.ui.resources.Res
import dev.hermeskotlin.ui.resources.common_cancel
import dev.hermeskotlin.ui.resources.tasks_matrix_apply
import dev.hermeskotlin.ui.resources.tasks_matrix_drag_hint
import dev.hermeskotlin.ui.resources.tasks_matrix_due_boost_note
import dev.hermeskotlin.ui.resources.tasks_matrix_empty_quadrant
import dev.hermeskotlin.ui.resources.tasks_matrix_impact
import dev.hermeskotlin.ui.resources.tasks_matrix_level_high
import dev.hermeskotlin.ui.resources.tasks_matrix_level_low
import dev.hermeskotlin.ui.resources.tasks_matrix_level_medium
import dev.hermeskotlin.ui.resources.tasks_matrix_pad_hint
import dev.hermeskotlin.ui.resources.tasks_matrix_q1
import dev.hermeskotlin.ui.resources.tasks_matrix_q1_hint
import dev.hermeskotlin.ui.resources.tasks_matrix_q2
import dev.hermeskotlin.ui.resources.tasks_matrix_q2_hint
import dev.hermeskotlin.ui.resources.tasks_matrix_q3
import dev.hermeskotlin.ui.resources.tasks_matrix_q3_hint
import dev.hermeskotlin.ui.resources.tasks_matrix_q4
import dev.hermeskotlin.ui.resources.tasks_matrix_q4_hint
import dev.hermeskotlin.ui.resources.tasks_matrix_title
import dev.hermeskotlin.ui.resources.tasks_matrix_urgency
import dev.hermeskotlin.ui.resources.tasks_reply_ack
import dev.hermeskotlin.ui.resources.tasks_reply_awaiting
import dev.hermeskotlin.ui.resources.tasks_reply_unread
import dev.hermeskotlin.ui.resources.tasks_reply_waiting_input
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
internal fun quadrantTitle(quadrant: EisenhowerQuadrant): String = when (quadrant) {
    EisenhowerQuadrant.DO_NOW -> stringResource(Res.string.tasks_matrix_q1)
    EisenhowerQuadrant.SCHEDULE -> stringResource(Res.string.tasks_matrix_q2)
    EisenhowerQuadrant.DELEGATE -> stringResource(Res.string.tasks_matrix_q3)
    EisenhowerQuadrant.LATER -> stringResource(Res.string.tasks_matrix_q4)
}

@Composable
internal fun quadrantHint(quadrant: EisenhowerQuadrant): String = when (quadrant) {
    EisenhowerQuadrant.DO_NOW -> stringResource(Res.string.tasks_matrix_q1_hint)
    EisenhowerQuadrant.SCHEDULE -> stringResource(Res.string.tasks_matrix_q2_hint)
    EisenhowerQuadrant.DELEGATE -> stringResource(Res.string.tasks_matrix_q3_hint)
    EisenhowerQuadrant.LATER -> stringResource(Res.string.tasks_matrix_q4_hint)
}

@Composable
internal fun matrixLevelLabel(level: MatrixLevel): String = when (level) {
    MatrixLevel.LOW -> stringResource(Res.string.tasks_matrix_level_low)
    MatrixLevel.MEDIUM -> stringResource(Res.string.tasks_matrix_level_medium)
    MatrixLevel.HIGH -> stringResource(Res.string.tasks_matrix_level_high)
}

internal fun matrixLevelShortCode(level: MatrixLevel): String = when (level) {
    MatrixLevel.LOW -> "L"
    MatrixLevel.MEDIUM -> "M"
    MatrixLevel.HIGH -> "H"
}

@Composable
internal fun quadrantAccentColor(quadrant: EisenhowerQuadrant): Color = when (quadrant) {
    EisenhowerQuadrant.DO_NOW -> Theme[colors][danger]
    EisenhowerQuadrant.SCHEDULE -> Theme[colors][accentText]
    EisenhowerQuadrant.DELEGATE -> Theme[colors][warning]
    EisenhowerQuadrant.LATER -> Theme[colors][textTertiary]
}

@Composable
internal fun quadrantSoftColor(quadrant: EisenhowerQuadrant): Color = when (quadrant) {
    EisenhowerQuadrant.DO_NOW -> Theme[colors][dangerSoft]
    EisenhowerQuadrant.SCHEDULE -> Theme[colors][accentSoft]
    EisenhowerQuadrant.DELEGATE -> Theme[colors][warningSoft]
    EisenhowerQuadrant.LATER -> Theme[colors][surface2]
}

@Composable
internal fun replyBadgeLabel(badge: TaskReplyBadge): String = when (badge) {
    TaskReplyBadge.WAITING_INPUT -> stringResource(Res.string.tasks_reply_waiting_input)
    TaskReplyBadge.UNREAD -> stringResource(Res.string.tasks_reply_unread)
    TaskReplyBadge.AWAITING_REPLY -> stringResource(Res.string.tasks_reply_awaiting)
    TaskReplyBadge.NONE -> ""
}

/** Compact pill placed inside TaskDetailInspector metadata pickers; opens the 3×3 2D matrix pad. */
@Composable
internal fun MatrixPickerPill(
    position: TaskMatrixPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Theme[radii][radiusMedium])
    val accentCol = quadrantAccentColor(position.quadrant)
    val bg = quadrantSoftColor(position.quadrant).compositeOver(Theme[colors][surface])
    val codeText = "${matrixLevelShortCode(position.impact)}/${matrixLevelShortCode(position.effectiveUrgency)}"
    Row(
        modifier
            .testTag("task_matrix_picker_pill")
            .clip(shape)
            .background(bg)
            .border(1.dp, accentCol.copy(alpha = 0.45f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .handCursor()
            .padding(horizontal = 9.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UnstyledIcon(Lucide.LayoutGrid, contentDescription = null, tint = accentCol, modifier = Modifier.size(13.dp))
        Text(
            "${quadrantTitle(position.quadrant)} · $codeText",
            style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.Medium),
            color = Theme[colors][textColor],
            singleLine = true,
            overflow = TextOverflow.Ellipsis,
        )
        if (position.boostedByDue) {
            UnstyledIcon(Lucide.Clock, contentDescription = null, tint = Theme[colors][warning], modifier = Modifier.size(12.dp))
        }
    }
}

/**
 * Interactive 3×3 Impact × Urgency picker dialog. Supports both direct tap on any cell and
 * smooth drag across the 2D surface on Desktop and mobile touchscreens without scroll conflict.
 */
@Composable
internal fun TaskMatrixPadDialog(
    task: DirectusTask,
    todayEpochDay: Long,
    onDismiss: () -> Unit,
    onApply: (impact: MatrixLevel, urgency: MatrixLevel) -> Unit,
) {
    val currentPos = remember(task, todayEpochDay) { resolveTaskMatrix(task, todayEpochDay) }
    var draftImpact by remember(task.id) { mutableStateOf(currentPos.impact) }
    var draftUrgency by remember(task.id) { mutableStateOf(currentPos.rawUrgency) }

    val previewTask = remember(task, draftImpact, draftUrgency) {
        task.copy(
            context = (task.context ?: dev.hermeskotlin.core.tasks.TaskContext(taskId = task.id)).copy(
                impact = draftImpact.wire,
                urgency = draftUrgency.wire,
            ),
        )
    }
    val previewPos = remember(previewTask, todayEpochDay) { resolveTaskMatrix(previewTask, todayEpochDay) }

    Dialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.tasks_matrix_title),
        message = task.title,
        maxWidth = 390.dp,
        actions = {
            Button(
                text = stringResource(Res.string.common_cancel),
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Small,
                onClick = onDismiss,
            )
            Button(
                text = stringResource(Res.string.tasks_matrix_apply),
                size = ButtonSize.Small,
                onClick = {
                    onApply(draftImpact, draftUrgency)
                    onDismiss()
                },
                modifier = Modifier.testTag("matrix_pad_apply"),
            )
        },
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(Res.string.tasks_matrix_pad_hint),
                style = Theme[typography][caption],
                color = Theme[colors][textSecondary],
            )

            ImpactUrgencyGridPad(
                selectedImpact = draftImpact,
                selectedUrgency = draftUrgency,
                effectiveUrgency = previewPos.effectiveUrgency,
                boostedByDue = previewPos.boostedByDue,
                onSelect = { imp, urg ->
                    draftImpact = imp
                    draftUrgency = urg
                },
            )

            val previewAccent = quadrantAccentColor(previewPos.quadrant)
            val summaryShape = RoundedCornerShape(Theme[radii][radiusMedium])
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(summaryShape)
                    .background(quadrantSoftColor(previewPos.quadrant).compositeOver(Theme[colors][surface2]))
                    .border(1.dp, previewAccent.copy(alpha = 0.4f), summaryShape)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${stringResource(Res.string.tasks_matrix_impact)}: ${matrixLevelLabel(draftImpact)} · ${stringResource(Res.string.tasks_matrix_urgency)}: ${matrixLevelLabel(draftUrgency)}",
                        style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.SemiBold),
                        color = Theme[colors][textColor],
                    )
                    Text(
                        quadrantTitle(previewPos.quadrant),
                        style = Theme[typography][labelStyle].copy(fontWeight = FontWeight.SemiBold),
                        color = previewAccent,
                    )
                }
                if (previewPos.boostedByDue) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UnstyledIcon(Lucide.Clock, contentDescription = null, tint = Theme[colors][warning], modifier = Modifier.size(12.dp))
                        Text(
                            stringResource(Res.string.tasks_matrix_due_boost_note, matrixLevelLabel(previewPos.effectiveUrgency)),
                            style = Theme[typography][caption],
                            color = Theme[colors][warning],
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImpactUrgencyGridPad(
    selectedImpact: MatrixLevel,
    selectedUrgency: MatrixLevel,
    effectiveUrgency: MatrixLevel,
    boostedByDue: Boolean,
    onSelect: (MatrixLevel, MatrixLevel) -> Unit,
) {
    val latestOnSelect by rememberUpdatedState(onSelect)
    val impactRows = remember { listOf(MatrixLevel.HIGH, MatrixLevel.MEDIUM, MatrixLevel.LOW) }
    val urgencyCols = remember { listOf(MatrixLevel.LOW, MatrixLevel.MEDIUM, MatrixLevel.HIGH) }

    val dangerFill = Theme[colors][dangerSoft].compositeOver(Theme[colors][surface])
    val accentFill = Theme[colors][accentSoft].compositeOver(Theme[colors][surface])
    val warningFill = Theme[colors][warningSoft].compositeOver(Theme[colors][surface])
    val neutralFill = Theme[colors][surface2]
    val gridBorder = Theme[colors][strokeStrong]
    val markerColor = quadrantAccentColor(EisenhowerQuadrant.of(selectedImpact, selectedUrgency))
    val effectiveMarkerColor = Theme[colors][warning]
    val surfaceBg = Theme[colors][surface]

    fun mapOffsetToLevels(pos: Offset, widthPx: Float, heightPx: Float): Pair<MatrixLevel, MatrixLevel> {
        if (widthPx <= 0f || heightPx <= 0f) return selectedImpact to selectedUrgency
        val colIdx = ((pos.x / widthPx) * 3f).toInt().coerceIn(0, 2)
        val rowIdx = ((pos.y / heightPx) * 3f).toInt().coerceIn(0, 2)
        return impactRows[rowIdx] to urgencyCols[colIdx]
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UnstyledIcon(Lucide.ArrowUp, contentDescription = null, tint = Theme[colors][textTertiary], modifier = Modifier.size(12.dp))
            Text(
                stringResource(Res.string.tasks_matrix_impact).uppercase(),
                style = Theme[typography][eyebrow].copy(fontSize = 10.sp),
                color = Theme[colors][textTertiary],
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Y-axis labels
            Column(
                Modifier.width(52.dp).height(216.dp),
                verticalArrangement = Arrangement.SpaceAround,
                horizontalAlignment = Alignment.End,
            ) {
                impactRows.forEach { level ->
                    Text(
                        matrixLevelLabel(level),
                        style = Theme[typography][caption].copy(
                            fontWeight = if (level == selectedImpact) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = if (level == selectedImpact) Theme[colors][textColor] else Theme[colors][textTertiary],
                        maxLines = 1,
                    )
                }
            }

            // 3×3 Interactive Pad
            val padShape = RoundedCornerShape(Theme[radii][radiusLarge])
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .height(216.dp)
                    .clip(padShape)
                    .border(1.dp, gridBorder, padShape)
                    .pointerInput(Unit) {
                        var currentPos = Offset.Zero
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                currentPos = startOffset
                                val (imp, urg) = mapOffsetToLevels(currentPos, size.width.toFloat(), size.height.toFloat())
                                latestOnSelect(imp, urg)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                currentPos += dragAmount
                                val (imp, urg) = mapOffsetToLevels(currentPos, size.width.toFloat(), size.height.toFloat())
                                latestOnSelect(imp, urg)
                            },
                        )
                    },
            ) {
                Column(Modifier.fillMaxSize()) {
                    impactRows.forEachIndexed { rIdx, imp ->
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            urgencyCols.forEachIndexed { cIdx, urg ->
                                val cellQuad = EisenhowerQuadrant.of(imp, urg)
                                val cellBg = when (cellQuad) {
                                    EisenhowerQuadrant.DO_NOW -> dangerFill
                                    EisenhowerQuadrant.SCHEDULE -> accentFill
                                    EisenhowerQuadrant.DELEGATE -> warningFill
                                    EisenhowerQuadrant.LATER -> neutralFill
                                }
                                val isSelected = imp == selectedImpact && urg == selectedUrgency
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .testTag("matrix_pad_cell_${imp.wire}_${urg.wire}")
                                        .background(cellBg)
                                        .border(
                                            width = if (isSelected) 1.5.dp else 0.5.dp,
                                            color = if (isSelected) markerColor else gridBorder.copy(alpha = 0.5f),
                                        )
                                        .clickable(role = Role.Button) { onSelect(imp, urg) }
                                        .handCursor(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (rIdx == 0 && cIdx == 2) {
                                        Text(
                                            quadrantTitle(EisenhowerQuadrant.DO_NOW),
                                            style = Theme[typography][caption].copy(fontSize = 10.sp),
                                            color = Theme[colors][danger].copy(alpha = 0.75f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                        )
                                    } else if (rIdx == 0 && cIdx == 0) {
                                        Text(
                                            quadrantTitle(EisenhowerQuadrant.SCHEDULE),
                                            style = Theme[typography][caption].copy(fontSize = 10.sp),
                                            color = Theme[colors][accentText].copy(alpha = 0.75f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                                        )
                                    } else if (rIdx == 2 && cIdx == 2) {
                                        Text(
                                            quadrantTitle(EisenhowerQuadrant.DELEGATE),
                                            style = Theme[typography][caption].copy(fontSize = 10.sp),
                                            color = Theme[colors][warning].copy(alpha = 0.75f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                                        )
                                    } else if (rIdx == 2 && cIdx == 0) {
                                        Text(
                                            quadrantTitle(EisenhowerQuadrant.LATER),
                                            style = Theme[typography][caption].copy(fontSize = 10.sp),
                                            color = Theme[colors][textMuted],
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Draw draggable thumb + optional dashed effective-urgency ring
                Canvas(Modifier.fillMaxSize()) {
                    val colW = size.width / 3f
                    val rowH = size.height / 3f
                    val selRow = impactRows.indexOf(selectedImpact).coerceIn(0, 2)
                    val selCol = urgencyCols.indexOf(selectedUrgency).coerceIn(0, 2)
                    val center = Offset((selCol + 0.5f) * colW, (selRow + 0.5f) * rowH)

                    if (boostedByDue) {
                        val effCol = urgencyCols.indexOf(effectiveUrgency).coerceIn(0, 2)
                        val effCenter = Offset((effCol + 0.5f) * colW, (selRow + 0.5f) * rowH)
                        drawLine(
                            color = effectiveMarkerColor.copy(alpha = 0.75f),
                            start = center,
                            end = effCenter,
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                        )
                        drawCircle(
                            color = effectiveMarkerColor,
                            radius = 11.dp.toPx(),
                            center = effCenter,
                            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
                        )
                    }

                    drawCircle(
                        color = markerColor.copy(alpha = 0.22f),
                        radius = 16.dp.toPx(),
                        center = center,
                    )
                    drawCircle(
                        color = surfaceBg,
                        radius = 10.dp.toPx(),
                        center = center,
                    )
                    drawCircle(
                        color = markerColor,
                        radius = 7.dp.toPx(),
                        center = center,
                    )
                }
            }
        }

        // Bottom X-axis labels
        Row(
            Modifier.fillMaxWidth().padding(start = 60.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            urgencyCols.forEach { level ->
                Text(
                    matrixLevelLabel(level),
                    style = Theme[typography][caption].copy(
                        fontWeight = if (level == selectedUrgency) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (level == selectedUrgency) Theme[colors][textColor] else Theme[colors][textTertiary],
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 60.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.tasks_matrix_urgency).uppercase(),
                style = Theme[typography][eyebrow].copy(fontSize = 10.sp),
                color = Theme[colors][textTertiary],
            )
            Spacer(Modifier.width(4.dp))
            UnstyledIcon(Lucide.ArrowRight, contentDescription = null, tint = Theme[colors][textTertiary], modifier = Modifier.size(12.dp))
        }
    }
}

/**
 * Responsive 2D Eisenhower Impact × Urgency Matrix for multi-task triage:
 * - Wide/Desktop (`>= 620.dp`): Full 2×2 quadrant grid with live drag-and-drop between quadrants.
 * - Narrow/Mobile (`< 620.dp`): Interactive 2×2 minimap at the top (acts as quadrant filter AND drop targets)
 *   paired with full-width readable task rows below + one-tap 3×3 pad dialog.
 */
@Composable
internal fun TasksEisenhowerMatrixBoard(
    tasks: List<DirectusTask>,
    projectById: Map<String, DirectusProject>,
    unassignedLabel: String,
    showProjectBadge: Boolean,
    todayEpochDay: Long,
    replyBadges: Map<String, TaskReplyBadge>,
    wideLayout: Boolean,
    selectedTaskId: String?,
    busyTaskIds: Set<String>,
    onSelectTask: (String?) -> Unit,
    onUpdateTaskMatrix: (taskId: String, impact: MatrixLevel, urgency: MatrixLevel) -> Unit,
    onAckReply: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val quadrants = remember(tasks, todayEpochDay, replyBadges) {
        buildMatrixQuadrants(tasks, todayEpochDay, replyBadges)
    }
    var mobileSelectedQuadrant by remember { mutableStateOf(EisenhowerQuadrant.DO_NOW) }
    var padTargetTask by remember { mutableStateOf<DirectusTask?>(null) }

    // Cross-platform drag state shared by Desktop 2×2 cards and Mobile minimap drop targets
    val dropBounds = remember { mutableStateMapOf<EisenhowerQuadrant, Rect>() }
    var containerRootOrigin by remember { mutableStateOf(Offset.Zero) }
    var draggingTask by remember { mutableStateOf<DirectusTask?>(null) }
    var dragPointerRoot by remember { mutableStateOf(Offset.Zero) }
    var hoveredQuadrant by remember { mutableStateOf<EisenhowerQuadrant?>(null) }

    fun resolveHover(pointerRoot: Offset): EisenhowerQuadrant? =
        dropBounds.entries.firstOrNull { (_, rect) -> rect.contains(pointerRoot) }?.key

    fun startDrag(task: DirectusTask, pointerRoot: Offset) {
        draggingTask = task
        dragPointerRoot = pointerRoot
        hoveredQuadrant = resolveHover(pointerRoot)
    }

    fun updateDrag(delta: Offset) {
        val next = dragPointerRoot + delta
        dragPointerRoot = next
        hoveredQuadrant = resolveHover(next)
    }

    fun finishDrag() {
        val droppedTask = draggingTask
        val targetQuad = hoveredQuadrant
        draggingTask = null
        hoveredQuadrant = null
        if (droppedTask != null && targetQuad != null) {
            val currentQuad = resolveTaskMatrix(droppedTask, todayEpochDay).quadrant
            if (targetQuad != currentQuad) {
                onUpdateTaskMatrix(droppedTask.id, targetQuad.defaultImpact, targetQuad.defaultUrgency)
                if (!wideLayout) mobileSelectedQuadrant = targetQuad
            }
        }
    }

    fun cancelDrag() {
        draggingTask = null
        hoveredQuadrant = null
    }

    Box(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { containerRootOrigin = it.positionInRoot() },
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Axis guide + drag hint bar
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UnstyledIcon(Lucide.ArrowUp, contentDescription = null, tint = Theme[colors][textTertiary], modifier = Modifier.size(12.dp))
                    Text(
                        stringResource(Res.string.tasks_matrix_impact).uppercase(),
                        style = Theme[typography][eyebrow].copy(fontSize = 10.sp),
                        color = Theme[colors][textTertiary],
                    )
                    Text("·", style = Theme[typography][caption], color = Theme[colors][textMuted])
                    Text(
                        stringResource(Res.string.tasks_matrix_urgency).uppercase(),
                        style = Theme[typography][eyebrow].copy(fontSize = 10.sp),
                        color = Theme[colors][textTertiary],
                    )
                    UnstyledIcon(Lucide.ArrowRight, contentDescription = null, tint = Theme[colors][textTertiary], modifier = Modifier.size(12.dp))
                }
                if (wideLayout) {
                    Text(
                        stringResource(Res.string.tasks_matrix_drag_hint),
                        style = Theme[typography][caption],
                        color = Theme[colors][textMuted],
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (wideLayout) {
                // Cartesian 2×2 Desktop Grid:
                // Top Row: Q2 Schedule (High Impact, Lower Urgency) | Q1 Do Now (High Impact, High Urgency)
                // Bottom Row: Q4 Later (Lower Impact, Lower Urgency) | Q3 Urgent (Lower Impact, High Urgency)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MatrixQuadrantCard(
                        quadrant = EisenhowerQuadrant.SCHEDULE,
                        tasks = quadrants[EisenhowerQuadrant.SCHEDULE].orEmpty(),
                        projectById = projectById,
                        unassignedLabel = unassignedLabel,
                        showProjectBadge = showProjectBadge,
                        todayEpochDay = todayEpochDay,
                        replyBadges = replyBadges,
                        selectedTaskId = selectedTaskId,
                        busyTaskIds = busyTaskIds,
                        draggingTaskId = draggingTask?.id,
                        dropHighlighted = hoveredQuadrant == EisenhowerQuadrant.SCHEDULE,
                        onBoundsChanged = { dropBounds[EisenhowerQuadrant.SCHEDULE] = it },
                        onSelectTask = onSelectTask,
                        onOpenPad = { padTargetTask = it },
                        onAckReply = onAckReply,
                        onStartDrag = ::startDrag,
                        onUpdateDrag = ::updateDrag,
                        onFinishDrag = ::finishDrag,
                        onCancelDrag = ::cancelDrag,
                        modifier = Modifier.weight(1f),
                    )
                    MatrixQuadrantCard(
                        quadrant = EisenhowerQuadrant.DO_NOW,
                        tasks = quadrants[EisenhowerQuadrant.DO_NOW].orEmpty(),
                        projectById = projectById,
                        unassignedLabel = unassignedLabel,
                        showProjectBadge = showProjectBadge,
                        todayEpochDay = todayEpochDay,
                        replyBadges = replyBadges,
                        selectedTaskId = selectedTaskId,
                        busyTaskIds = busyTaskIds,
                        draggingTaskId = draggingTask?.id,
                        dropHighlighted = hoveredQuadrant == EisenhowerQuadrant.DO_NOW,
                        onBoundsChanged = { dropBounds[EisenhowerQuadrant.DO_NOW] = it },
                        onSelectTask = onSelectTask,
                        onOpenPad = { padTargetTask = it },
                        onAckReply = onAckReply,
                        onStartDrag = ::startDrag,
                        onUpdateDrag = ::updateDrag,
                        onFinishDrag = ::finishDrag,
                        onCancelDrag = ::cancelDrag,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MatrixQuadrantCard(
                        quadrant = EisenhowerQuadrant.LATER,
                        tasks = quadrants[EisenhowerQuadrant.LATER].orEmpty(),
                        projectById = projectById,
                        unassignedLabel = unassignedLabel,
                        showProjectBadge = showProjectBadge,
                        todayEpochDay = todayEpochDay,
                        replyBadges = replyBadges,
                        selectedTaskId = selectedTaskId,
                        busyTaskIds = busyTaskIds,
                        draggingTaskId = draggingTask?.id,
                        dropHighlighted = hoveredQuadrant == EisenhowerQuadrant.LATER,
                        onBoundsChanged = { dropBounds[EisenhowerQuadrant.LATER] = it },
                        onSelectTask = onSelectTask,
                        onOpenPad = { padTargetTask = it },
                        onAckReply = onAckReply,
                        onStartDrag = ::startDrag,
                        onUpdateDrag = ::updateDrag,
                        onFinishDrag = ::finishDrag,
                        onCancelDrag = ::cancelDrag,
                        modifier = Modifier.weight(1f),
                    )
                    MatrixQuadrantCard(
                        quadrant = EisenhowerQuadrant.DELEGATE,
                        tasks = quadrants[EisenhowerQuadrant.DELEGATE].orEmpty(),
                        projectById = projectById,
                        unassignedLabel = unassignedLabel,
                        showProjectBadge = showProjectBadge,
                        todayEpochDay = todayEpochDay,
                        replyBadges = replyBadges,
                        selectedTaskId = selectedTaskId,
                        busyTaskIds = busyTaskIds,
                        draggingTaskId = draggingTask?.id,
                        dropHighlighted = hoveredQuadrant == EisenhowerQuadrant.DELEGATE,
                        onBoundsChanged = { dropBounds[EisenhowerQuadrant.DELEGATE] = it },
                        onSelectTask = onSelectTask,
                        onOpenPad = { padTargetTask = it },
                        onAckReply = onAckReply,
                        onStartDrag = ::startDrag,
                        onUpdateDrag = ::updateDrag,
                        onFinishDrag = ::finishDrag,
                        onCancelDrag = ::cancelDrag,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                // Mobile / Narrow layout: 2×2 Interactive Minimap (filter + drop zones) + readable full-width list below
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MatrixMinimapTile(
                            quadrant = EisenhowerQuadrant.SCHEDULE,
                            count = quadrants[EisenhowerQuadrant.SCHEDULE].orEmpty().size,
                            selected = mobileSelectedQuadrant == EisenhowerQuadrant.SCHEDULE,
                            dropHighlighted = hoveredQuadrant == EisenhowerQuadrant.SCHEDULE,
                            onBoundsChanged = { dropBounds[EisenhowerQuadrant.SCHEDULE] = it },
                            onClick = { mobileSelectedQuadrant = EisenhowerQuadrant.SCHEDULE },
                            modifier = Modifier.weight(1f),
                        )
                        MatrixMinimapTile(
                            quadrant = EisenhowerQuadrant.DO_NOW,
                            count = quadrants[EisenhowerQuadrant.DO_NOW].orEmpty().size,
                            selected = mobileSelectedQuadrant == EisenhowerQuadrant.DO_NOW,
                            dropHighlighted = hoveredQuadrant == EisenhowerQuadrant.DO_NOW,
                            onBoundsChanged = { dropBounds[EisenhowerQuadrant.DO_NOW] = it },
                            onClick = { mobileSelectedQuadrant = EisenhowerQuadrant.DO_NOW },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MatrixMinimapTile(
                            quadrant = EisenhowerQuadrant.LATER,
                            count = quadrants[EisenhowerQuadrant.LATER].orEmpty().size,
                            selected = mobileSelectedQuadrant == EisenhowerQuadrant.LATER,
                            dropHighlighted = hoveredQuadrant == EisenhowerQuadrant.LATER,
                            onBoundsChanged = { dropBounds[EisenhowerQuadrant.LATER] = it },
                            onClick = { mobileSelectedQuadrant = EisenhowerQuadrant.LATER },
                            modifier = Modifier.weight(1f),
                        )
                        MatrixMinimapTile(
                            quadrant = EisenhowerQuadrant.DELEGATE,
                            count = quadrants[EisenhowerQuadrant.DELEGATE].orEmpty().size,
                            selected = mobileSelectedQuadrant == EisenhowerQuadrant.DELEGATE,
                            dropHighlighted = hoveredQuadrant == EisenhowerQuadrant.DELEGATE,
                            onBoundsChanged = { dropBounds[EisenhowerQuadrant.DELEGATE] = it },
                            onClick = { mobileSelectedQuadrant = EisenhowerQuadrant.DELEGATE },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Text(
                    stringResource(Res.string.tasks_matrix_drag_hint),
                    style = Theme[typography][caption],
                    color = Theme[colors][textMuted],
                )

                val visibleTasks = quadrants[mobileSelectedQuadrant].orEmpty()
                val panelShape = RoundedCornerShape(Theme[radii][radiusLarge])
                val activeAccent = quadrantAccentColor(mobileSelectedQuadrant)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(panelShape)
                        .background(Theme[colors][surface])
                        .border(1.dp, activeAccent.copy(alpha = 0.4f), panelShape)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            quadrantTitle(mobileSelectedQuadrant),
                            style = Theme[typography][labelStyle].copy(fontWeight = FontWeight.SemiBold),
                            color = activeAccent,
                        )
                        Text(
                            quadrantHint(mobileSelectedQuadrant),
                            style = Theme[typography][caption],
                            color = Theme[colors][textTertiary],
                        )
                    }
                    if (visibleTasks.isEmpty()) {
                        Text(
                            stringResource(Res.string.tasks_matrix_empty_quadrant),
                            style = Theme[typography][bodySmall],
                            color = Theme[colors][textMuted],
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        visibleTasks.forEach { task ->
                            MatrixTaskRow(
                                task = task,
                                projectName = task.projectId?.let { projectById[it]?.name } ?: unassignedLabel,
                                showProjectBadge = showProjectBadge,
                                todayEpochDay = todayEpochDay,
                                replyBadge = replyBadges[task.id] ?: TaskReplyBadge.NONE,
                                selected = task.id == selectedTaskId,
                                busy = task.id in busyTaskIds,
                                beingDragged = task.id == draggingTask?.id,
                                onSelect = { onSelectTask(task.id) },
                                onOpenPad = { padTargetTask = task },
                                onAckReply = { onAckReply(task.id) },
                                onStartDrag = { rootPos -> startDrag(task, rootPos) },
                                onUpdateDrag = ::updateDrag,
                                onFinishDrag = ::finishDrag,
                                onCancelDrag = ::cancelDrag,
                            )
                        }
                    }
                }
            }
        }

        // Floating drag ghost preview rendered cleanly above the matrix
        val ghost = draggingTask
        if (ghost != null) {
            val relX = (dragPointerRoot.x - containerRootOrigin.x - 90f).roundToInt()
            val relY = (dragPointerRoot.y - containerRootOrigin.y - 22f).roundToInt()
            val ghostShape = RoundedCornerShape(Theme[radii][radiusMedium])
            val targetColor = hoveredQuadrant?.let { quadrantAccentColor(it) } ?: Theme[colors][accent]
            Row(
                Modifier
                    .offset { IntOffset(relX, relY) }
                    .widthIn(min = 160.dp, max = 260.dp)
                    .clip(ghostShape)
                    .background(Theme[colors][surface3].copy(alpha = 0.96f))
                    .border(1.5.dp, targetColor, ghostShape)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(targetColor))
                Text(
                    ghost.title,
                    style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.SemiBold),
                    color = Theme[colors][textColor],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    padTargetTask?.let { target ->
        val fresh = tasks.firstOrNull { it.id == target.id } ?: target
        TaskMatrixPadDialog(
            task = fresh,
            todayEpochDay = todayEpochDay,
            onDismiss = { padTargetTask = null },
            onApply = { imp, urg ->
                onUpdateTaskMatrix(fresh.id, imp, urg)
                if (!wideLayout) {
                    val nextQuad = resolveTaskMatrix(
                        fresh.copy(
                            context = (fresh.context ?: dev.hermeskotlin.core.tasks.TaskContext(taskId = fresh.id)).copy(
                                impact = imp.wire,
                                urgency = urg.wire,
                            ),
                        ),
                        todayEpochDay,
                    ).quadrant
                    mobileSelectedQuadrant = nextQuad
                }
            },
        )
    }
}

@Composable
private fun MatrixMinimapTile(
    quadrant: EisenhowerQuadrant,
    count: Int,
    selected: Boolean,
    dropHighlighted: Boolean,
    onBoundsChanged: (Rect) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentCol = quadrantAccentColor(quadrant)
    val shape = RoundedCornerShape(Theme[radii][radiusMedium])
    val bg = when {
        dropHighlighted -> quadrantSoftColor(quadrant).compositeOver(Theme[colors][surface2])
        selected -> quadrantSoftColor(quadrant).compositeOver(Theme[colors][surface])
        else -> Theme[colors][surface]
    }
    val borderCol = when {
        dropHighlighted -> accentCol
        selected -> accentCol.copy(alpha = 0.8f)
        else -> Theme[colors][stroke]
    }
    Row(
        modifier
            .testTag("matrix_minimap_${quadrant.name.lowercase()}")
            .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
            .clip(shape)
            .background(bg)
            .border(if (selected || dropHighlighted) 1.5.dp else 1.dp, borderCol, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .handCursor()
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                quadrantTitle(quadrant),
                style = Theme[typography][labelStyle].copy(fontWeight = FontWeight.SemiBold),
                color = if (selected || dropHighlighted) accentCol else Theme[colors][textColor],
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                quadrantHint(quadrant),
                style = Theme[typography][caption].copy(fontSize = 10.sp),
                color = Theme[colors][textTertiary],
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(Theme[radii][radiusFull]))
                .background(if (count > 0) accentCol.copy(alpha = 0.16f) else Theme[colors][surface2])
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                count.toString(),
                style = Theme[typography][code].copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                color = if (count > 0) accentCol else Theme[colors][textMuted],
            )
        }
    }
}

@Composable
private fun MatrixQuadrantCard(
    quadrant: EisenhowerQuadrant,
    tasks: List<DirectusTask>,
    projectById: Map<String, DirectusProject>,
    unassignedLabel: String,
    showProjectBadge: Boolean,
    todayEpochDay: Long,
    replyBadges: Map<String, TaskReplyBadge>,
    selectedTaskId: String?,
    busyTaskIds: Set<String>,
    draggingTaskId: String?,
    dropHighlighted: Boolean,
    onBoundsChanged: (Rect) -> Unit,
    onSelectTask: (String?) -> Unit,
    onOpenPad: (DirectusTask) -> Unit,
    onAckReply: (String) -> Unit,
    onStartDrag: (DirectusTask, Offset) -> Unit,
    onUpdateDrag: (Offset) -> Unit,
    onFinishDrag: () -> Unit,
    onCancelDrag: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentCol = quadrantAccentColor(quadrant)
    val shape = RoundedCornerShape(Theme[radii][radiusLarge])
    val headerBg = quadrantSoftColor(quadrant).compositeOver(Theme[colors][surface])
    val borderCol = if (dropHighlighted) accentCol else Theme[colors][stroke]

    Column(
        modifier
            .testTag("matrix_quadrant_${quadrant.name.lowercase()}")
            .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
            .heightIn(min = 176.dp)
            .clip(shape)
            .background(if (dropHighlighted) headerBg else Theme[colors][surface])
            .border(if (dropHighlighted) 2.dp else 1.dp, borderCol, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(headerBg)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(accentCol))
                Text(
                    quadrantTitle(quadrant),
                    style = Theme[typography][labelStyle].copy(fontWeight = FontWeight.SemiBold),
                    color = accentCol,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    quadrantHint(quadrant),
                    style = Theme[typography][caption].copy(fontSize = 10.sp),
                    color = Theme[colors][textTertiary],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                tasks.size.toString(),
                style = Theme[typography][code].copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                color = accentCol,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Theme[colors][stroke]))

        Column(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (tasks.isEmpty()) {
                Text(
                    stringResource(Res.string.tasks_matrix_empty_quadrant),
                    style = Theme[typography][bodySmall],
                    color = Theme[colors][textMuted],
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                )
            } else {
                tasks.forEach { task ->
                    MatrixTaskRow(
                        task = task,
                        projectName = task.projectId?.let { projectById[it]?.name } ?: unassignedLabel,
                        showProjectBadge = showProjectBadge,
                        todayEpochDay = todayEpochDay,
                        replyBadge = replyBadges[task.id] ?: TaskReplyBadge.NONE,
                        selected = task.id == selectedTaskId,
                        busy = task.id in busyTaskIds,
                        beingDragged = task.id == draggingTaskId,
                        onSelect = { onSelectTask(task.id) },
                        onOpenPad = { onOpenPad(task) },
                        onAckReply = { onAckReply(task.id) },
                        onStartDrag = { rootPos -> onStartDrag(task, rootPos) },
                        onUpdateDrag = onUpdateDrag,
                        onFinishDrag = onFinishDrag,
                        onCancelDrag = onCancelDrag,
                    )
                }
            }
        }
    }
}

@Composable
private fun MatrixTaskRow(
    task: DirectusTask,
    projectName: String,
    showProjectBadge: Boolean,
    todayEpochDay: Long,
    replyBadge: TaskReplyBadge,
    selected: Boolean,
    busy: Boolean,
    beingDragged: Boolean,
    onSelect: () -> Unit,
    onOpenPad: () -> Unit,
    onAckReply: () -> Unit,
    onStartDrag: (Offset) -> Unit,
    onUpdateDrag: (Offset) -> Unit,
    onFinishDrag: () -> Unit,
    onCancelDrag: () -> Unit,
) {
    val latestStart by rememberUpdatedState(onStartDrag)
    val latestUpdate by rememberUpdatedState(onUpdateDrag)
    val latestFinish by rememberUpdatedState(onFinishDrag)
    val latestCancel by rememberUpdatedState(onCancelDrag)

    val pos = remember(task, todayEpochDay) { resolveTaskMatrix(task, todayEpochDay) }
    val shape = RoundedCornerShape(Theme[radii][radiusMedium])
    val bg = when {
        beingDragged -> Theme[colors][surface2].copy(alpha = 0.5f)
        selected -> Theme[colors][accentSoft].compositeOver(Theme[colors][surface])
        else -> Theme[colors][surface2].copy(alpha = 0.55f)
    }
    val borderColor = when {
        selected -> Theme[colors][accent]
        replyBadge == TaskReplyBadge.UNREAD || replyBadge == TaskReplyBadge.WAITING_INPUT -> Theme[colors][danger].copy(alpha = 0.6f)
        replyBadge == TaskReplyBadge.AWAITING_REPLY -> Theme[colors][warning].copy(alpha = 0.6f)
        else -> Theme[colors][stroke]
    }
    var gripRootOrigin by remember { mutableStateOf(Offset.Zero) }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .clickable(role = Role.Button, onClick = onSelect)
            .handCursor()
            .padding(start = 4.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Dedicated drag handle: isolates drag gesture so vertical scrolling never conflicts on mobile
        Box(
            Modifier
                .testTag("matrix_drag_${task.id}")
                .onGloballyPositioned { gripRootOrigin = it.positionInRoot() }
                .pointerInput(task.id) {
                    detectDragGestures(
                        onDragStart = { local -> latestStart(gripRootOrigin + local) },
                        onDrag = { change, amount ->
                            change.consume()
                            latestUpdate(amount)
                        },
                        onDragEnd = { latestFinish() },
                        onDragCancel = { latestCancel() },
                    )
                }
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .handCursor(),
            contentAlignment = Alignment.Center,
        ) {
            UnstyledIcon(
                Lucide.GripVertical,
                contentDescription = null,
                tint = Theme[colors][textMuted],
                modifier = Modifier.size(14.dp),
            )
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                task.title,
                style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.Medium),
                color = if (beingDragged) Theme[colors][textMuted] else Theme[colors][textColor],
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showProjectBadge) {
                    Text(
                        projectName,
                        style = Theme[typography][caption].copy(fontSize = 10.sp),
                        color = Theme[colors][textTertiary],
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 120.dp),
                    )
                }
                if (replyBadge != TaskReplyBadge.NONE) {
                    val badgeBg = when (replyBadge) {
                        TaskReplyBadge.WAITING_INPUT, TaskReplyBadge.UNREAD -> Theme[colors][dangerSoft]
                        TaskReplyBadge.AWAITING_REPLY -> Theme[colors][warningSoft]
                        TaskReplyBadge.NONE -> Color.Transparent
                    }
                    val badgeFg = when (replyBadge) {
                        TaskReplyBadge.WAITING_INPUT, TaskReplyBadge.UNREAD -> Theme[colors][danger]
                        TaskReplyBadge.AWAITING_REPLY -> Theme[colors][warning]
                        TaskReplyBadge.NONE -> Theme[colors][textTertiary]
                    }
                    Text(
                        replyBadgeLabel(replyBadge),
                        style = Theme[typography][caption].copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = badgeFg,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Theme[radii][radiusSmall]))
                            .background(badgeBg)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                if (pos.boostedByDue) {
                    UnstyledIcon(Lucide.Clock, contentDescription = null, tint = Theme[colors][warning], modifier = Modifier.size(11.dp))
                }
            }
        }

        if (replyBadge == TaskReplyBadge.UNREAD || replyBadge == TaskReplyBadge.AWAITING_REPLY) {
            val ackShape = RoundedCornerShape(Theme[radii][radiusSmall])
            Box(
                Modifier
                    .testTag("matrix_ack_${task.id}")
                    .clip(ackShape)
                    .background(Theme[colors][surface])
                    .border(1.dp, Theme[colors][strokeStrong], ackShape)
                    .clickable(role = Role.Button, onClick = onAckReply)
                    .handCursor()
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                UnstyledIcon(
                    Lucide.Check,
                    contentDescription = stringResource(Res.string.tasks_reply_ack),
                    tint = Theme[colors][textSecondary],
                    modifier = Modifier.size(12.dp),
                )
            }
        }

        // 3×3 precision trigger badge
        val badgeShape = RoundedCornerShape(Theme[radii][radiusSmall])
        val badgeAccent = quadrantAccentColor(pos.quadrant)
        Row(
            Modifier
                .testTag("matrix_cell_badge_${task.id}")
                .clip(badgeShape)
                .background(quadrantSoftColor(pos.quadrant).compositeOver(Theme[colors][surface]))
                .border(1.dp, badgeAccent.copy(alpha = 0.4f), badgeShape)
                .clickable(enabled = !busy, role = Role.Button, onClick = onOpenPad)
                .handCursor(!busy)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${matrixLevelShortCode(pos.impact)}/${matrixLevelShortCode(pos.effectiveUrgency)}",
                style = Theme[typography][code].copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                color = badgeAccent,
            )
        }
    }
}
