package app.proyecta.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.proyecta.shared.ClientMessage

/** Anuncios: imágenes y videos (cumpleaños, eventos, la ofrenda…) que se muestran a pantalla completa. */
@Composable
fun AnnouncementsTab(
    announcements: List<String>,
    current: String?,
    modifier: Modifier,
    send: (ClientMessage) -> Unit,
    present: (ClientMessage) -> Unit,
    onAdd: () -> Unit,
    onOpenFolder: () -> Unit,
) {
    val look = LocalLook.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (announcements.isEmpty()) {
            Text(
                "Agrega imágenes o videos (eventos, cumpleaños, la ofrenda…). Se muestran a pantalla completa; " +
                    "los videos con sonido. También se pueden poner en la lista del culto.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyVerticalGrid(
            GridCells.Adaptive(140.dp), Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(announcements, key = { it }) { name ->
                val file = look.mediaFile(name)
                val selected = name == current
                Column(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .handClickable { present(ClientMessage.ShowMedia(name)) },
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black), contentAlignment = Alignment.Center) {
                        if (file != null && isVideoFile(name)) {
                            Text("▶ Video", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        } else {
                            rememberBackground(file)?.let {
                                Image(it, contentDescription = name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            }
                        }
                    }
                    Text(
                        name.substringBeforeLast('.'), style = MaterialTheme.typography.labelMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    TextButton({ send(ClientMessage.PlanAddMedia(name)) }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("+ Lista") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onAdd, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("+ Agregar imagen o video…") }
        }
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            TextButton(onOpenFolder) { Text("Abrir carpeta de anuncios") }
        }
    }
}
