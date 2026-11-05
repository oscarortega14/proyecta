package app.selah.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Mensaje corto abajo de la pantalla (como el Toast de Android, pero sirve también en iPhone). */
class Notices {
    /** id distinto cada vez: el mismo texto dos veces seguidas vuelve a mostrarse. */
    data class Notice(val text: String, val millis: Long, val id: Long)

    var current by mutableStateOf<Notice?>(null)
        private set
    private var count = 0L
    fun show(text: String, long: Boolean = false) { current = Notice(text, if (long) 3500L else 2000L, ++count) }
    internal fun clear(n: Notice) { if (current == n) current = null }
}

val LocalNotices = compositionLocalOf { Notices() }

@Composable
fun BoxScope.NoticeHost(notices: Notices) {
    val shown = notices.current ?: return
    LaunchedEffect(shown) { delay(shown.millis); notices.clear(shown) }
    Box(
        Modifier.align(Alignment.BottomCenter).padding(24.dp)
            .background(Color(0xEE303030), RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) { Text(shown.text, color = Color.White) }
}
