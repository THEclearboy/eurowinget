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
import kotlin.math.pow

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
    val AMT = doublePreferencesKey("amt")   // palier sélectionné dans le widget
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

    /**
     * Grille de prix du widget : paliers linéaires calés sur les prix réels, exprimés pour une devise
     * de référence "100 = pas de base" puis mis à l'échelle de la devise (LKR ×1, IDR ×10, THB ×0,1, USD ×0,01…).
     * 50→500 par 50, →1 000 par 100, →3 000 par 250, →5 000 par 500, →10 000 par 1 000, puis 12,5k 15k 20k 25k 30k 40k 50k 75k 100k.
     */
    fun ladder(rate: Double): List<Double> {
        val f = steps(rate).first() / 100.0
        fun zone(from: Double, to: Double, step: Double) =
            generateSequence(from) { it + step }.takeWhile { it <= to + 1e-9 }.toList()
        return (zone(50.0, 500.0, 50.0) + zone(600.0, 1000.0, 100.0) + zone(1250.0, 3000.0, 250.0) +
                zone(3500.0, 5000.0, 500.0) + zone(6000.0, 10000.0, 1000.0) +
                listOf(12500.0, 15000.0, 20000.0, 25000.0, 30000.0, 40000.0, 50000.0, 75000.0, 100000.0))
            .map { it * f }
    }

    /** Repère de tête : 10 × pas de base (1 000 LKR, 10 000 IDR, 100 THB, 10 USD…). */
    fun mentalRef(rate: Double): Double = steps(rate).first() * 10.0

    fun fmtAmt(v: Double): String =
        if (v == floor(v)) fmtInt(v.toLong())
        else NumberFormat.getNumberInstance(fr).apply { maximumFractionDigits = 1 }.format(v)

    suspend fun setCurrency(ctx: Context, c: String) {
        ctx.dataStore.edit { it[Keys.CUR] = c; it[Keys.AMT] = 0.0 }
        EuroWidget.refreshAll(ctx)
    }

    suspend fun setManualRate(ctx: Context, c: String, r: Double) {
        ctx.dataStore.edit { it[Keys.rate(c)] = r; it[Keys.SRC] = "manuel" }
        EuroWidget.refreshAll(ctx)
    }

    /** Sélection d'un palier dans le widget (0 = aucun). */
    suspend fun setAmount(ctx: Context, v: Double) {
        ctx.dataStore.edit { it[Keys.AMT] = v }
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
