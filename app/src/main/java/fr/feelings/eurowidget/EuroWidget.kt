package fr.feelings.eurowidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.*
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.*
import java.util.concurrent.TimeUnit

private val WHITE = ColorProvider(Color.White)
private val DIM = ColorProvider(Color(0xFF6E6E6E))
private val RED = ColorProvider(Color(0xFFD71921))

class EuroWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = Repo.state(context)
        provideContent { Content(s) }
    }

    @Composable
    private fun Content(s: State) {
        val mono = FontFamily.Monospace
        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(ImageProvider(R.drawable.widget_bg))
                .padding(16.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.size(6.dp).background(if (s.src == "bce") RED else DIM).cornerRadius(3.dp)) {}
                Spacer(GlanceModifier.width(8.dp))
                Text("1 € = ${Repo.fmtRate(s.rate)} ${s.cur}", style = TextStyle(color = DIM, fontSize = 11.sp, fontFamily = mono))
                Spacer(GlanceModifier.defaultWeight())
                Text(s.date, style = TextStyle(color = DIM, fontSize = 11.sp, fontFamily = mono))
            }
            Spacer(GlanceModifier.height(12.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Repo.steps(s.rate).forEach { step ->
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            Repo.fmtEur(step / s.rate) + " €",
                            style = TextStyle(color = WHITE, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = mono)
                        )
                        Text(
                            "${Repo.fmtInt(step)} ${s.cur}",
                            style = TextStyle(color = DIM, fontSize = 10.sp, fontFamily = mono)
                        )
                    }
                }
            }
        }
    }

    companion object {
        suspend fun refreshAll(ctx: Context) = EuroWidget().updateAll(ctx)
    }
}

class EuroWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EuroWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedule(context)
    }
}

/** Rafraîchit les taux BCE toutes les 6 h (réseau requis). */
class RefreshWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result = if (Repo.fetch(applicationContext)) Result.success() else Result.retry()

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("rates", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
