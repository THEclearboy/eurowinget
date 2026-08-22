package fr.feelings.eurowidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
private val PANEL = ColorProvider(Color(0xFF0A0A0A))
private val GHOST = ColorProvider(Color(0xFF2D2D2D))

val AMT_KEY = ActionParameters.Key<Double>("amt")

class EuroWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = Repo.state(context)
        provideContent { Content(s) }
    }

    @Composable
    private fun Content(s: State) {
        val mono = FontFamily.Monospace
        Row(
            modifier = GlanceModifier.fillMaxSize()
                .background(ImageProvider(R.drawable.widget_bg))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ----- Gauche : taux, estimation, repères (tap = ouvre l'app)
            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(GlanceModifier.size(6.dp).background(if (s.src == "bce") RED else DIM).cornerRadius(3.dp)) {}
                    Spacer(GlanceModifier.width(8.dp))
                    Text("1 € = ${Repo.fmtRate(s.rate)} ${s.cur}", style = TextStyle(color = DIM, fontSize = 10.sp, fontFamily = mono))
                    Spacer(GlanceModifier.defaultWeight())
                    Text(s.date, style = TextStyle(color = DIM, fontSize = 10.sp, fontFamily = mono))
                }
                Spacer(GlanceModifier.height(6.dp))
                if (s.amt > 0) {
                    Text(Repo.fmtEur(s.amt / s.rate) + " €", style = TextStyle(color = WHITE, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = mono))
                    Text("${Repo.fmtAmt(s.amt)} ${s.cur}", style = TextStyle(color = DIM, fontSize = 11.sp, fontFamily = mono))
                } else {
                    Text("0,00 €", style = TextStyle(color = GHOST, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = mono))
                    Text("SCROLL → CHOISIR UN PRIX", style = TextStyle(color = DIM, fontSize = 9.sp, fontFamily = mono))
                }
                Spacer(GlanceModifier.height(6.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Repo.steps(s.rate).forEach { step ->
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(Repo.fmtEur(step / s.rate) + " €", style = TextStyle(color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = mono))
                            Text("${Repo.fmtInt(step)} ${s.cur}", style = TextStyle(color = DIM, fontSize = 9.sp, fontFamily = mono))
                        }
                    }
                }
            }
            Spacer(GlanceModifier.width(10.dp))
            // ----- Droite : échelle de prix scrollable (tap = sélectionne)
            Box(modifier = GlanceModifier.width(124.dp).fillMaxHeight().background(PANEL).cornerRadius(16.dp)) {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(Repo.ladder(s.rate), itemId = { (it * 100).toLong() }) { v ->
                        val on = v == s.amt
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                                .clickable(actionRunCallback<SelectAmount>(actionParametersOf(AMT_KEY to v))),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(GlanceModifier.size(5.dp).background(if (on) RED else PANEL).cornerRadius(3.dp)) {}
                            Spacer(GlanceModifier.width(6.dp))
                            Text(Repo.fmtAmt(v), style = TextStyle(color = if (on) WHITE else DIM, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, fontFamily = mono))
                            Spacer(GlanceModifier.defaultWeight())
                            Text(Repo.fmtEur(v / s.rate) + "€", style = TextStyle(color = if (on) WHITE else DIM, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, fontFamily = mono))
                        }
                    }
                }
            }
        }
    }

    companion object {
        suspend fun refreshAll(ctx: Context) = EuroWidget().updateAll(ctx)
    }
}

/** Tap sur un palier de l'échelle. */
class SelectAmount : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val v = parameters[AMT_KEY] ?: 0.0
        val cur = Repo.state(context).amt
        Repo.setAmount(context, if (v == cur) 0.0 else v) // re-tap = désélection
        EuroWidget().update(context, glanceId)
        EuroWidget.refreshAll(context)
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
