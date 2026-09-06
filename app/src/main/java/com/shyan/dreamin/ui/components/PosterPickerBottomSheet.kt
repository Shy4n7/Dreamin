package com.shyan.dreamin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.service.OfficialArtworkService
import com.shyan.dreamin.ui.screens.LocalDreaminColors
import kotlinx.coroutines.launch

/**
 * Glassmorphic 1-tap theatrical poster selector bottom sheet.
 * Fetches and displays online official theatrical posters for the given track,
 * allowing users to choose their preferred movie artwork in 1 tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosterPickerBottomSheet(
    song: Song,
    onDismiss: () -> Unit,
    onPosterSelected: (String) -> Unit
) {
    val colors = LocalDreaminColors.current
    val scope = rememberCoroutineScope()
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    var candidatePosters by remember(song.id) { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember(song.id) { mutableStateOf(true) }

    LaunchedEffect(song.id) {
        isLoading = true
        val list = OfficialArtworkService.fetchCandidatePosters(song)
        candidatePosters = list
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceHigh.copy(alpha = 0.82f),
        contentColor = colors.onSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.3f))
            )
        },
        shape = sheetShape,
        modifier = Modifier.border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.35f),
                    colors.primary.copy(alpha = 0.15f),
                    Color.Transparent
                )
            ),
            shape = sheetShape
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Choose Theatrical Poster",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${song.displayTitle} • ${song.artist}",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colors.primary, strokeWidth = 3.dp)
                }
            } else if (candidatePosters.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No alternate movie posters found for this track.",
                        color = colors.onSurfaceVariant,
                        fontSize = 13.5.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    items(candidatePosters, key = { it }) { posterUrl ->
                        val isSelected = song.displayArtworkUrl == posterUrl
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surfaceHighest)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) colors.primary else Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    OfficialArtworkService.putCachedPoster(song, posterUrl)
                                    onPosterSelected(posterUrl)
                                    onDismiss()
                                }
                        ) {
                            AsyncImage(
                                model = posterUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(colors.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
