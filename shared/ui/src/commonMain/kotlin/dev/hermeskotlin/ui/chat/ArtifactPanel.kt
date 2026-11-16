package dev.hermeskotlin.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.FileImage
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PanelRight
import com.composables.icons.lucide.X
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.chat.AttachmentKind
import dev.hermeskotlin.core.chat.ChatArtifacts
import dev.hermeskotlin.core.chat.ModifiedFileArtifact
import dev.hermeskotlin.core.chat.UploadArtifact
import dev.hermeskotlin.core.media.GatewayFile
import dev.hermeskotlin.core.media.GatewayFileKind
import dev.hermeskotlin.designsystem.accentSoft
import dev.hermeskotlin.designsystem.accentText
import dev.hermeskotlin.designsystem.background
import dev.hermeskotlin.designsystem.bodySmall
import dev.hermeskotlin.designsystem.caption
import dev.hermeskotlin.designsystem.colors
import dev.hermeskotlin.designsystem.components.IconButton
import dev.hermeskotlin.designsystem.components.Spinner
import dev.hermeskotlin.designsystem.components.handCursor
import dev.hermeskotlin.designsystem.radii
import dev.hermeskotlin.designsystem.radiusMedium
import dev.hermeskotlin.designsystem.radiusSmall
import dev.hermeskotlin.designsystem.stroke
import dev.hermeskotlin.designsystem.surface
import dev.hermeskotlin.designsystem.surface2
import dev.hermeskotlin.designsystem.text as textColor
import dev.hermeskotlin.designsystem.textMuted
import dev.hermeskotlin.designsystem.textSecondary
import dev.hermeskotlin.designsystem.textTertiary
import dev.hermeskotlin.designsystem.title
import dev.hermeskotlin.designsystem.typography
import dev.hermeskotlin.ui.clearOfWindowControls
import dev.hermeskotlin.ui.resources.Res
import dev.hermeskotlin.ui.resources.chat_artifacts_editing
import dev.hermeskotlin.ui.resources.chat_artifacts_empty_body
import dev.hermeskotlin.ui.resources.chat_artifacts_empty_title
import dev.hermeskotlin.ui.resources.chat_artifacts_modified
import dev.hermeskotlin.ui.resources.chat_artifacts_no_preview
import dev.hermeskotlin.ui.resources.chat_artifacts_title
import dev.hermeskotlin.ui.resources.chat_artifacts_uploads
import dev.hermeskotlin.ui.resources.chat_close
import dev.hermeskotlin.ui.resources.chat_kind_file
import dev.hermeskotlin.ui.resources.chat_kind_pdf
import dev.hermeskotlin.ui.resources.chat_kind_picture
import org.jetbrains.compose.resources.stringResource

/**
 * Right-sidebar history listing per-conversation [ModifiedFileArtifact]s and [UploadArtifact]s.
 * Clicking a previewable entry switches to [FileViewer] (or opens [ImageViewer] for images).
 */
@Composable
internal fun ArtifactHistoryPanel(
    artifacts: ChatArtifacts,
    onOpenFile: (GatewayFile) -> Unit,
    onOpenImage: (ViewerImage) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Theme[colors][background]).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            Modifier.fillMaxWidth().clearOfWindowControls().padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UnstyledIcon(Lucide.PanelRight, contentDescription = null, tint = Theme[colors][textSecondary], modifier = Modifier.size(18.dp))
            Text(
                stringResource(Res.string.chat_artifacts_title),
                style = Theme[typography][title].copy(fontWeight = FontWeight.SemiBold),
                color = Theme[colors][textColor],
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(Lucide.X, contentDescription = stringResource(Res.string.chat_close), onClick = onClose, tint = Theme[colors][textSecondary])
        }

        if (artifacts.isEmpty) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(Res.string.chat_artifacts_empty_title),
                        style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.SemiBold),
                        color = Theme[colors][textSecondary],
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(Res.string.chat_artifacts_empty_body),
                        style = Theme[typography][caption],
                        color = Theme[colors][textTertiary],
                        textAlign = TextAlign.Center,
                    )
                }
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (artifacts.modifiedFiles.isNotEmpty()) {
                item(key = "header-modified") {
                    SectionHeader(stringResource(Res.string.chat_artifacts_modified), artifacts.modifiedFiles.size)
                }
                items(artifacts.modifiedFiles, key = { "mod|${it.path}" }) { entry ->
                    val file = GatewayFile(entry.path)
                    ArtifactRow(
                        name = entry.name,
                        subtitle = if (entry.running) stringResource(Res.string.chat_artifacts_editing) else entry.folder.ifEmpty { "/" },
                        icon = kindIcon(file.kind),
                        running = entry.running,
                        enabled = true,
                        onClick = {
                            if (file.kind == GatewayFileKind.Image) onOpenImage(ViewerImage(entry.name, source = entry.path))
                            else onOpenFile(file)
                        },
                    )
                }
            }

            if (artifacts.uploads.isNotEmpty()) {
                item(key = "header-uploads") {
                    if (artifacts.modifiedFiles.isNotEmpty()) Spacer(Modifier.height(6.dp))
                    SectionHeader(stringResource(Res.string.chat_artifacts_uploads), artifacts.uploads.size)
                }
                items(artifacts.uploads, key = { "up|${it.gatewayPath ?: it.attachment.key}|${it.attachment.key}" }) { entry ->
                    val att = entry.attachment
                    val file = entry.gatewayPath?.let(::GatewayFile)
                    val kindWord = when (att.kind) {
                        AttachmentKind.Image -> stringResource(Res.string.chat_kind_picture)
                        AttachmentKind.Pdf -> stringResource(Res.string.chat_kind_pdf)
                        AttachmentKind.File -> stringResource(Res.string.chat_kind_file)
                    }
                    val subtitle = if (entry.canPreview) kindWord else "$kindWord · ${stringResource(Res.string.chat_artifacts_no_preview)}"
                    ArtifactRow(
                        name = att.name,
                        subtitle = subtitle,
                        icon = file?.let { kindIcon(it.kind) } ?: when (att.kind) {
                            AttachmentKind.Image -> Lucide.FileImage
                            AttachmentKind.Pdf -> kindIcon(GatewayFileKind.Pdf)
                            AttachmentKind.File -> kindIcon(GatewayFileKind.Other)
                        },
                        thumbnail = att.thumbnail ?: att.original,
                        enabled = entry.canPreview,
                        onClick = {
                            when {
                                att.kind == AttachmentKind.Image ->
                                    onOpenImage(ViewerImage(att.name, source = att.gatewayPath, bytes = att.original))
                                file != null -> onOpenFile(file)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            style = Theme[typography][caption].copy(fontWeight = FontWeight.SemiBold),
            color = Theme[colors][textTertiary],
        )
        Box(
            Modifier
                .background(Theme[colors][surface2], RoundedCornerShape(Theme[radii][radiusSmall]))
                .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            Text(count.toString(), style = Theme[typography][caption], color = Theme[colors][textSecondary])
        }
    }
}

@Composable
private fun ArtifactRow(
    name: String,
    subtitle: String,
    icon: ImageVector,
    thumbnail: ByteArray? = null,
    running: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Theme[radii][radiusMedium])
    val iconBoxShape = RoundedCornerShape(Theme[radii][radiusSmall])
    val thumbBitmap = thumbnail?.let { rememberImageBitmap(it, maxEdge = 96) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme[colors][surface], shape)
            .border(1.dp, Theme[colors][stroke], shape)
            .alpha(if (enabled) 1f else 0.6f)
            .handCursor(enabled)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (thumbBitmap != null) {
            Image(
                bitmap = thumbBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(32.dp).clip(iconBoxShape).border(1.dp, Theme[colors][stroke], iconBoxShape),
            )
        } else {
            Box(
                Modifier.size(32.dp).background(Theme[colors][accentSoft], iconBoxShape),
                contentAlignment = Alignment.Center,
            ) {
                UnstyledIcon(icon, contentDescription = null, tint = Theme[colors][accentText], modifier = Modifier.size(16.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                name,
                style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.Medium),
                color = Theme[colors][textColor],
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = Theme[typography][caption],
                color = Theme[colors][textTertiary],
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        when {
            running -> Spinner(Modifier.size(14.dp))
            enabled -> UnstyledIcon(Lucide.ChevronRight, contentDescription = null, tint = Theme[colors][textMuted], modifier = Modifier.size(15.dp))
        }
    }
}
