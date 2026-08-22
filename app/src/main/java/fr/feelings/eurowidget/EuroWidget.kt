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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.GridCells
import androidx.glance.appwidget.lazy.LazyVerticalGrid
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

/** Clé d'extra (= MainActivity.EXTRA_AMOUNT) : montant local à pré-remplir dans l'app. */
private val AMOUNT_PARAM = ActionParameters.Key<Double>(MainActivity.EXTRA_AMOUNT)

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
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // ----- En-tête (tap = ouvre l'app) : LED · devise · taux · date
            Row(
                modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(GlanceModifier.size(6.dp).background(if (s.src == "bce") RED else DIM).cornerRadius(3.dp)) {}
                Spacer(GlanceModifier.width(8.dp))
                Text(s.cur, style = TextStyle(color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = mono))
                Spacer(GlanceModifier.width(10.dp))
                Text("1 € = ${Repo.fmtRate(s.rate)}", style = TextStyle(color = DIM, fontSize = 10.sp, fontFamily = mono))
                Spacer(GlanceModifier.defaultWeight())
                Text(s.date, style = TextStyle(color = DIM, fontSize = 10.sp, fontFamily = mono))
            }
            // Règle de tête
            val ref = Repo.mentalRef(s.rate)
            Text(
                "${Repo.fmtAmt(ref)} ${s.cur} ≈ ${Repo.fmtEur(ref / s.rate)} €",
                style = TextStyle(color = DIM, fontSize = 10.sp, fontFamily = mono),
                modifier = GlanceModifier.padding(start = 14.dp, top = 2.dp)
            )
            Spacer(GlanceModifier.height(8.dp))
            // ----- Grille de prix : montant local en gros, € dessous. Tap = app pré-remplie.
            LazyVerticalGrid(gridCells = GridCells.Fixed(3), modifier = GlanceModifier.fillMaxSize()) {
                items(Repo.ladder(s.rate), itemId = { (it * 100).toLong() }) { v ->
                    Box(GlanceModifier.padding(3.dp)) {
                        Column(
                            modifier = GlanceModifier.fillMaxWidth().background(PANEL).cornerRadius(12.dp)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .clickable(actionStartActivity<MainActivity>(actionParametersOf(AMOUNT_PARAM to v)))
                        ) {
                            Text(Repo.fmtAmt(v), style = TextStyle(color = WHITE, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = mono))
                            Text(Repo.fmtEur(v / s.rate) + " €", style = TextStyle(color = DIM, fontSize = 10.sp, fontFamily = mono))
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
