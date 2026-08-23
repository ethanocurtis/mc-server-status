package com.mcserverstatus.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** The actual AppWidgetProvider Android talks to; delegates rendering to [McStatusWidget]. */
class McStatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = McStatusWidget()
}
