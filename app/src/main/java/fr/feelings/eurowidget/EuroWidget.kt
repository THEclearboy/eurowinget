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
private val LINE = ColorProvider(Color(0xFF2A2A2A))
private val GHOST = ColorProvider(Color(0xFF2D2D2D))

val DIR_KEY = ActionParameters.Key<Int>("dir")

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
            Wheel(mono)
            Spacer(GlanceModifier.width(12.dp))
            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ligne 1 : LED + taux + date
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(GlanceModifier.size(6.dp).background(if (s.src == "bce") RED else DIM).cornerRadius(3.dp)) {}
                    Spacer(GlanceModifier.width(8.dp))
                    Text("1 € = ${Repo.fmtRate(s.rate)} ${s.cur}", style = TextStyle(color = DIM, fontSize = 10.sp, fontFamily = mono))
                    Spacer(GlanceModifier.defaultWeight())
                    Text(s.date, style = TextStyle(color = DIM, fontSize = 10.sp, fontFamily = mono))
                }
                Spacer(GlanceModifier.height(6.dp))
                // Ligne 2 : estimation (montant en devise -> €). Tap = remise à zéro.
                Row(
                    modifier = GlanceModifier.fillMaxWidth().clickable(actionRunCallback<WheelAction>(actionParametersOf(DIR_KEY to 0))),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (s.amt > 0) {
                        Text(Repo.fmtEur(s.amt / s.rate) + " €", style = TextStyle(color = WHITE, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = mono))
                        Spacer(GlanceModifier.defaultWeight())
                        Text("${Repo.fmtInt(s.amt.toLong())} ${s.cur}", style = TextStyle(color = DIM, fontSize = 12.sp, fontFamily = mono))
                    } else {
                        Text("0,00 €", style = TextStyle(color = GHOST, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = mono))
                        Spacer(GlanceModifier.defaultWeight())
                        Text("+ / − POUR ESTIMER", style = TextStyle(color = DIM, fontSize = 9.sp, fontFamily = mono))
                    }
                }
                Spacer(GlanceModifier.height(6.dp))
                // Ligne 3 : repères
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Repo.steps(s.rate).forEach { step ->
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(Repo.fmtEur(step / s.rate) + " €", style = TextStyle(color = WHITE, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = mono))
                            Text("${Repo.fmtInt(step)} ${s.cur}", style = TextStyle(color = DIM, fontSize = 9.sp, fontFamily = mono))
                        }
                    }
                }
            }
        }
    }

    /** Roue crantée : + en haut, crans au milieu (cran central = LED), − en bas. */
    @Composable
    private fun Wheel(mono: FontFamily) {
        Column(
            modifier = GlanceModifier.width(44.dp).fillMaxHeight().background(PANEL).cornerRadius(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                    .clickable(actionRunCallback<WheelAction>(actionParametersOf(DIR_KEY to 1))),
                contentAlignment = Alignment.Center
            ) { Text("+", style = TextStyle(color = WHITE, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = mono)) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(GlanceModifier.width(10.dp).height(1.dp).background(LINE)) {}
                Spacer(GlanceModifier.height(4.dp))
                Box(GlanceModifier.width(16.dp).height(1.dp).background(LINE)) {}
                Spacer(GlanceModifier.height(4.dp))
                Box(GlanceModifier.width(22.dp).height(2.dp).background(RED)) {}
                Spacer(GlanceModifier.height(4.dp))
                Box(GlanceModifier.width(16.dp).height(1.dp).background(LINE)) {}
                Spacer(GlanceModifier.height(4.dp))
                Box(GlanceModifier.width(10.dp).height(1.dp).background(LINE)) {}
            }
            Box(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                    .clickable(actionRunCallback<WheelAction>(actionParametersOf(DIR_KEY to -1))),
                contentAlignment = Alignment.Center
            ) { Text("−", style = TextStyle(color = WHITE, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = mono)) }
        }
    }

    companion object {
        suspend fun refreshAll(ctx: Context) = EuroWidget().updateAll(ctx)
    }
}

/** Clic sur la roue (+ / − / reset). */
class WheelAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Repo.wheel(context, parameters[DIR_KEY] ?: 0)
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
