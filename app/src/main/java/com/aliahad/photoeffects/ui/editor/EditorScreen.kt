package com.aliahad.photoeffects.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurCircular
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aliahad.photoeffects.ui.theme.EditorColors
import kotlinx.coroutines.launch

/**
 * PREQUEL-style dark editor screen:
 * top bar (close / undo / save) -> photo preview -> Effects panel -> bottom tool tabs.
 */
@Composable
fun EditorScreen(
    state: EditorState,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EditorColors.Background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EditorTopBar(
                canUndo = state.canUndo,
                isSaving = state.isSaving,
                onClose = { state.resetAll() },
                onUndo = { state.undo() },
                onSave = { state.openExportSheet() },
                modifier = Modifier.statusBarsPadding(),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                val photo = state.photo
                if (photo == null) {
                    PhotoLoadingPlaceholder(Modifier.fillMaxSize())
                } else {
                    PhotoPreviewCard(
                        photo = photo,
                        effect = state.renderEffect,
                        intensity = 100,
                        showOriginal = state.showOriginal,
                        onChangePhoto = onPickPhoto,
                        onHoldChange = { state.holdOriginal(it) },
                        modifier = Modifier.fillMaxSize(),
                        strengthLabel = state.strengthLabel,
                    )
                }
            }

            if (state.activeTool == EditorTool.Adjust) {
                AdjustPanel(state = state)
            } else {
                EffectsPanel(state = state)
            }

            EditorToolBar(
                activeTool = state.activeTool,
                hasEffect = state.activePreset != null,
                onSelectTool = { tool ->
                    if (tool == EditorTool.Effects || tool == EditorTool.Filters || tool == EditorTool.Adjust) {
                        state.selectTool(tool)
                    } else {
                        state.showMessage("${tool.label} is coming soon")
                    }
                },
                onBack = {
                    if (state.panelStage != PanelStage.Categories) {
                        state.panelBack()
                    } else {
                        val selected = state.activePreset
                        if (selected != null) {
                            state.clearActivePreset()
                        } else {
                            val label = when (state.activeTool) {
                                EditorTool.Filters -> "filter"
                                EditorTool.Adjust -> "adjustment"
                                else -> "preset"
                            }
                            state.showMessage("Choose a $label below")
                        }
                    }
                },
            )
        }

        val plan = state.exportPlan
        if (state.showExportSheet && state.photo != null && plan != null) {
            ExportOptionsSheet(
                options = state.exportOptions,
                resolutions = state.resolutionOptions,
                plan = plan,
                onSelect = { state.updateExportOptions(it) },
                onSave = { scope.launch { state.save() } },
                onDismiss = { state.dismissExportSheet() },
            )
        }
    }
}

@Composable
private fun EditorTopBar(
    canUndo: Boolean,
    isSaving: Boolean,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 14.dp),
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(42.dp)
                .semantics { contentDescription = "close" },
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close",
                tint = EditorColors.TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(
            onClick = onUndo,
            enabled = canUndo,
            modifier = Modifier
                .align(Alignment.Center)
                .size(42.dp)
                .semantics { contentDescription = "undo" },
        ) {
            Icon(
                imageVector = Icons.Rounded.Undo,
                contentDescription = "Undo",
                tint = if (canUndo) EditorColors.TextPrimary else EditorColors.TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        SaveButton(
            isSaving = isSaving,
            onSave = onSave,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun SaveButton(
    isSaving: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(EditorColors.PanelElevated)
            .clickable(enabled = !isSaving, onClick = onSave)
            .padding(horizontal = 16.dp, vertical = 9.dp)
            .semantics { contentDescription = "save" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                color = EditorColors.Accent,
                strokeWidth = 1.5.dp,
                modifier = Modifier.size(14.dp),
            )
        } else {
            Text(
                text = "Save",
                color = EditorColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = null,
                tint = EditorColors.TextPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun EditorToolBar(
    activeTool: EditorTool,
    hasEffect: Boolean,
    onSelectTool: (EditorTool) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(EditorColors.Background)
            .navigationBarsPadding()
            .height(86.dp)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(EditorColors.PanelElevated)
                .clickable(onClick = onBack)
                .semantics { contentDescription = "panel_back" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = "Back",
                tint = EditorColors.TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (tool in EditorTool.entries) {
                ToolTab(
                    tool = tool,
                    selected = tool == activeTool,
                    onClick = { onSelectTool(tool) },
                )
            }
        }
    }
}

@Composable
private fun ToolTab(
    tool: EditorTool,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) EditorColors.Accent else EditorColors.TextSecondary
    Column(
        modifier = Modifier
            .size(width = 62.dp, height = 62.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "tool_${tool.name}" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = iconFor(tool),
            contentDescription = tool.label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = tool.label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

private fun iconFor(tool: EditorTool): ImageVector = when (tool) {
    EditorTool.Effects -> Icons.Rounded.AutoAwesome
    EditorTool.Filters -> Icons.Rounded.BlurCircular
    EditorTool.Adjust -> Icons.Rounded.WbSunny
    EditorTool.Canvas -> Icons.Rounded.Crop
    EditorTool.Text -> Icons.Rounded.TextFields
    EditorTool.Overlay -> Icons.Rounded.AddPhotoAlternate
}
