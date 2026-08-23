package com.mcserverstatus.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mcserverstatus.app.MainActivity
import com.mcserverstatus.app.data.AppDatabase
import com.mcserverstatus.app.data.ServerEntry

/**
 * A small, deliberately simple v1: shows whichever single server is
 * favorited in the app (see the star on a server's card). Picking which
 * server to show per-widget-instance would need a full configuration
 * Activity, which felt like more than a first cut needed - one favorite,
 * app-wide, covers the core "glance at a server without opening the app"
 * value with much less moving parts.
 */
class McStatusWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val favorite = AppDatabase.getInstance(context).serverDao().getFavorite()
        val openAppIntent = Intent(context, MainActivity::class.java)
        provideContent {
            WidgetContent(favorite, openAppIntent)
        }
    }
}

@Composable
private fun WidgetContent(favorite: ServerEntry?, openAppIntent: Intent) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(12.dp)
            .clickable(actionStartActivity(openAppIntent)),
    ) {
        if (favorite == null) {
            Text(
                "No favorite server",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
            Text("Star a server in the app to show it here", style = TextStyle(fontSize = 11.sp))
        } else {
            val faviconBitmap = remember(favorite.lastKnownFaviconBase64) {
                decodeFavicon(favorite.lastKnownFaviconBase64)
            }

            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                if (faviconBitmap != null) {
                    Image(
                        provider = ImageProvider(faviconBitmap),
                        contentDescription = null,
                        modifier = GlanceModifier.size(28.dp),
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                }
                Text(
                    favorite.displayName,
                    maxLines = 1,
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
            }
            Text(favorite.addressLabel, maxLines = 1, style = TextStyle(fontSize = 11.sp))

            val motd = favorite.lastKnownMotd
            if (!motd.isNullOrBlank()) {
                Text(motd, maxLines = 1, style = TextStyle(fontSize = 11.sp))
            }

            Row(modifier = GlanceModifier.padding(top = 6.dp)) {
                val statusText = when (favorite.widgetOnline) {
                    true -> "● Online"
                    false -> "● Offline"
                    null -> "Not checked yet"
                }
                Text(statusText, style = TextStyle(fontSize = 13.sp))

                val playersOnline = favorite.lastKnownPlayersOnline
                val playersMax = favorite.lastKnownPlayersMax
                if (playersOnline != null && playersMax != null) {
                    Spacer(modifier = GlanceModifier.width(10.dp))
                    Text("$playersOnline/$playersMax", style = TextStyle(fontSize = 13.sp))
                }

                val latencyMs = favorite.lastKnownLatencyMs
                if (latencyMs != null) {
                    Spacer(modifier = GlanceModifier.width(10.dp))
                    Text("$latencyMs ms", style = TextStyle(fontSize = 13.sp))
                }
            }

            Row(modifier = GlanceModifier.padding(top = 4.dp)) {
                Text(lastCheckedLabel(favorite.lastCheckedAt), style = TextStyle(fontSize = 11.sp))
                Spacer(modifier = GlanceModifier.width(12.dp))
                Text(
                    "Refresh",
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.clickable(actionRunCallback<RefreshWidgetAction>()),
                )
            }
        }
    }
}

private fun decodeFavicon(faviconBase64: String?): Bitmap? {
    val encoded = faviconBase64
        ?.substringAfter(",", missingDelimiterValue = "")
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    return runCatching {
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

private fun lastCheckedLabel(lastCheckedAt: Long?): String {
    if (lastCheckedAt == null) return "Never checked"
    val minutes = (System.currentTimeMillis() - lastCheckedAt) / 60_000
    return when {
        minutes < 1 -> "Checked just now"
        minutes < 60 -> "Checked ${minutes}m ago"
        else -> "Checked ${minutes / 60}h ago"
    }
}
