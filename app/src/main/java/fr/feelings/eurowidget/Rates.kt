package fr.feelings.eurowidget

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

// Taux intégrés (1 € = x), utilisés hors-ligne / au premier lancement.
val CURRENCIES: List<Pair<String, Double>> = listOf(
    "LKR" to 345.0, "THB" to 38.0, "IDR" to 18900.0, "VND" to 30400.0, "INR" to 101.0,
    "MYR" to 4.9, "PHP" to 67.0, "USD" to 1.17, "GBP" to 0.86, "CHF" to 0.93,
    "JPY" to 172.0, "AUD" to 1.78, "AED" to 4.3, "MAD" to 10.6, "MXN" to 21.5
)

val Context.dataStore by preferencesDataStore("rates")

object Keys {
    val CUR = stringPreferencesKey("cur")
    val DATE = stringPreferencesKey("date")
    val SRC = stringPreferencesKey("src")
    fun rate(c: String) = doublePreferencesKey("rate_$c")
    // Roue d'estimation du widget
    val AMT = doublePreferencesKey("amt")
    val W_TS = longPreferencesKey("w_ts")
    val W_LVL = intPreferencesKey("w_lvl")
    val W_DIR = intPreferencesKey("w_dir")
}

data class State(val cur: String, val rate: Double, val date: String, val src: String, val amt: Double = 0.0)

object Repo {
    private val fr = Locale.FRANCE

    fun fmtEur(v: Double): String =
        NumberFormat.getNumberInstance(fr).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }.format(v)

    fun fmtRate(r: Double): String =
        if (r >= 100) NumberFormat.getIntegerInstance(fr).format(Math.round(r))
        else NumberFormat.getNumberInstance(fr).apply { maximumFractionDigits = 2 }.format(r)

    fun fmtInt(v: Long): String = NumberFormat.getIntegerInstance(fr).format(v)

    /** Paliers de repère adaptés à l'ordre de grandeur de la devise. */
    fun steps(rate: Double): List<Long> = when {
        rate >= 1000 -> listOf(1000L, 5000L, 10000L)
        rate >= 100 -> listOf(100L, 500L, 1000L)
        rate >= 10 -> listOf(10L, 50L, 100L)
        else -> listOf(1L, 5L, 20L)
    }

    fun stateFlow(ctx: Context) = ctx.dataStore.data.map { p -> toState(p) }

    suspend fun state(ctx: Context): State = toState(ctx.dataStore.data.first())

    private fun toState(p: Preferences): State {
        val cur = p[Keys.CUR] ?: CURRENCIES.first().first
        val def = CURRENCIES.first { it.first == cur }.second
        return State(cur, p[Keys.rate(cur)] ?: def, p[Keys.DATE] ?: "—", p[Keys.SRC] ?: "intégrée", p[Keys.AMT] ?: 0.0)
    }

    /** Multiplicateurs d'accélération : taps rapides successifs dans le même sens → paliers de plus en plus grands. */
    private val MULTS = listOf(1, 1, 2, 2, 5, 5, 10, 10, 20, 50)
    private const val WHEEL_IDLE_MS = 1200L

    /** Cran de la roue : dir = +1 / -1, 0 = remise à zéro. Le pas de base suit l'ordre de grandeur du montant. */
    suspend fun wheel(ctx: Context, dir: Int) {
        val p = ctx.dataStore.data.first()
        val s = toState(p)
        val now = System.currentTimeMillis()
        val lvl = if (dir != 0 && p[Keys.W_DIR] == dir && now - (p[Keys.W_TS] ?: 0L) < WHEEL_IDLE_MS)
            min((p[Keys.W_LVL] ?: 0) + 1, MULTS.lastIndex) else 0
        val unit = steps(s.rate).first().toDouble()
        val ref = if (dir > 0) s.amt else s.amt - 1
        val mag = if (ref >= 1) 10.0.pow(floor(log10(ref))) else 0.0
        val base = max(unit, mag / 10)
        val step = base * MULTS[lvl]
        val next = if (dir == 0) 0.0 else max(0.0, ((s.amt + dir * step) / base).roundToLong() * base)
        ctx.dataStore.edit { it[Keys.AMT] = next; it[Keys.W_TS] = now; it[Keys.W_LVL] = lvl; it[Keys.W_DIR] = dir }
        EuroWidget.refreshAll(ctx)
    }

    suspend fun setCurrency(ctx: Context, c: String) {
        ctx.dataStore.edit { it[Keys.CUR] = c }
        EuroWidget.refreshAll(ctx)
    }

    suspend fun setManualRate(ctx: Context, c: String, r: Double) {
        ctx.dataStore.edit { it[Keys.rate(c)] = r; it[Keys.SRC] = "manuel" }
        EuroWidget.refreshAll(ctx)
    }

    /** Taux BCE via frankfurter.dev. Renvoie true si mis à jour. */
    suspend fun fetch(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.frankfurter.dev/v1/latest?base=EUR&symbols=" + CURRENCIES.joinToString(",") { it.first }
            val con = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 5000; readTimeout = 5000 }
            val json = JSONObject(con.inputStream.bufferedReader().readText())
            val rates = json.getJSONObject("rates")
            val date = json.getString("date").split("-").reversed().joinToString("/")
            ctx.dataStore.edit { p ->
                rates.keys().forEach { k -> p[Keys.rate(k)] = rates.getDouble(k) }
                p[Keys.DATE] = date; p[Keys.SRC] = "bce"
            }
            EuroWidget.refreshAll(ctx)
            true
        } catch (e: Exception) { false }
    }
}
