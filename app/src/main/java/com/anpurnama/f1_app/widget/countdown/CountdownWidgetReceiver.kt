package com.anpurnama.f1_app.widget.countdown

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Broadcast receiver for the Countdown widget. The system delivers
 * `APPWIDGET_UPDATE` / bind / delete broadcasts here, and Glance
 * forwards each to the [glanceAppWidget] for rendering.
 *
 * Registered in the manifest with:
 * ```xml
 * <receiver
 *     android:name=".widget.countdown.CountdownWidgetReceiver"
 *     android:exported="true"
 *     android:label="@string/widget_label">
 *     <intent-filter>
 *         <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
 *     </intent-filter>
 *     <meta-data
 *         android:name="android.appwidget.provider"
 *         android:resource="@xml/countdown_widget_info" />
 * </receiver>
 * ```
 *
 * The metadata resource binds the receiver to
 * `res/xml/countdown_widget_info.xml` (sizing, resize bounds,
 * `updatePeriodMillis = 0`).
 */
class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()
}
