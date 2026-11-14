@file:OptIn(ExperimentalMaterial3Api::class)

package com.shyan.dreamin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shyan.dreamin.data.local.Playlist
import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.ui.theme.DreaminShapes
import com.shyan.dreamin.ui.theme.LocalDreaminColors

data class SongMenuAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val isDestructive: Boolean = false,
    val isBusy: Boolean = false,
    val isRemoval: Boolean = false
)

object SongMenuActions {
    fun playNext(onClick: () -> Unit) = SongMenuAction("Play next", Icons.Filled.SkipNext, onClick)
    fun addToQueue(onClick: () -> Unit) = SongMenuAction("Add to queue", Icons.AutoMirrored.Outlined.QueueMusic, onClick)
    fun addToPlaylist(onClick: () -> Unit) = SongMenuAction("Add to playlist", Icons.AutoMirrored.Outlined.PlaylistAdd, onClick)
    fun goToAlbum(onClick: () -> Unit) = SongMenuAction("Go to album", Icons.Filled.Album, onClick)
    fun changePoster(onClick: () -> Unit) = SongMenuAction("Change poster", Icons.Outlined.Image, onClick)
    fun streamDetails(onClick: () -> Unit) = SongMenuAction("Stream details", Icons.Outlined.Info, onClick)
    fun removeFromQueue(onClick: () -> Unit) = SongMenuAction("Remove from queue", Icons.Outlined.DeleteOutline, onClick, isDestructive = true, isRemoval = true)
    fun removeFromPlaylist(onClick: () -> Unit) = SongMenuAction("Remove from playlist", Icons.Outlined.DeleteOutline, onClick, isDestructive = true, isRemoval = true)

    fun download(isDownloaded: Boolean, isDownloading: Boolean, onClick: () -> Unit) = when {
        isDownloaded -> SongMenuAction("Delete download", Icons.Outlined.Delete, onClick, isDestructive = true)
        isDownloading -> SongMenuAction("Downloading…", Icons.Outlined.Download, onClick, isBusy = true)
        else -> SongMenuAction("Download", Icons.Outlined.Download, onClick)
    }
}

@Composable
fun SongOptionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: List<SongMenuAction>
) {
    val colors = LocalDreaminColors.current
    val (removals, regular) = actions.partition { it.isRemoval }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceHighest,
        shape = DreaminShapes.medium,
        modifier = Modifier
            .border(1.dp, colors.outlineVariant, DreaminShapes.medium)
            .width(220.dp)
    ) {
        regular.forEach { SongMenuItem(it, onDismiss) }
        if (regular.isNotEmpty() && removals.isNotEmpty()) {
            HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
        }
        removals.forEach { SongMenuItem(it, onDismiss) }
    }
}

@Composable
private fun SongMenuItem(action: SongMenuAction, onDismiss: () -> Unit) {
    val colors = LocalDreaminColors.current
    val tint = if (action.isDestructive) colors.error else colors.onSurface
    DropdownMenuItem(
        text = {
            Text(action.label, color = tint, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        },
        leadingIcon = {
            if (action.isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.secondary, strokeWidth = 2.dp)
            } else {
                Icon(action.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
        },
        enabled = !action.isBusy,
        onClick = {
            onDismiss()
            action.onClick()
        }
    )
}

@Composable
fun AddToPlaylistSheet(
    song: Song,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    val colors = LocalDreaminColors.current
    val createPlaylistWithSong = LocalCreatePlaylistWithSong.current
    var showCreatePlaylist by remember { mutableStateOf(false) }

    if (showCreatePlaylist) {
        CreatePlaylistDialog(
            onDismiss = onDismiss,
            onCreate = { name, coverUri ->
                createPlaylistWithSong(name, coverUri, song)
                onDismiss()
            }
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceHigh,
        contentColor = colors.onSurface,
        shape = DreaminShapes.sheet
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                text = "Add to playlist",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Text(
                text = song.displayTitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.padding(top = 8.dp))
            PlaylistSheetRow(
                icon = Icons.Filled.Add,
                label = "New playlist",
                tint = colors.primary,
                onClick = { showCreatePlaylist = true }
            )
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistSheetRow(
                        icon = Icons.Outlined.LibraryMusic,
                        label = playlist.name,
                        tint = colors.onSurface,
                        onClick = {
                            onSelect(playlist.id)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistSheetRow(icon: ImageVector, label: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val colors = LocalDreaminColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(colors.surfaceHighest, DreaminShapes.small),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
