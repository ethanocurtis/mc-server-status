package com.mcserverstatus.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
        provideContent {
            WidgetContent(favorite)
        }
    }
}

@Composable
private fun WidgetContent(favorite: ServerEntry?) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (favorite == null) {
            Text("No favorite server", style = TextStyle(fontSize = 14.sp))
            Text("Star a server in the app to show it here", style = TextStyle(fontSize = 11.sp))
        } else {
            Text(
                favorite.displayName,
                maxLines = 1,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
            Text(favorite.addressLabel, maxLines = 1, style = TextStyle(fontSize = 11.sp))
            Row(modifier = GlanceModifier.padding(top = 6.dp)) {
                val statusText = when (favorite.lastKnownOnline) {
                    true -> "● Online"
                    false -> "● Offline"
                    null -> "Not checked yet"
                }
                Text(statusText, style = TextStyle(fontSize = 13.sp))
                Spacer(modifier = GlanceModifier.width(12.dp))
                Text(
                    "Refresh",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.clickable(actionRunCallback<RefreshWidgetAction>()),
                )
            }
        }
    }
}
