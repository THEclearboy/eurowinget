package fr.feelings.eurowidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

private val Black = Color.Black
private val White = Color.White
private val Dim = Color(0xFF6E6E6E)
private val Line = Color(0xFF222222)
private val Red = Color(0xFFD71921)
// Pour la typo dot-matrix : ajoute un .ttf dans res/font (ex. doto_black.ttf) et remplace par FontFamily(Font(R.font.doto_black)).
private val Mono = FontFamily.Monospace

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        enableEdgeToEdge()
        RefreshWorker.schedule(this)
        setContent { Converter() }
    }
}

@Composable
fun Converter() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Repo.stateFlow(ctx).collectAsState(initial = null)
    var amount by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var online by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) { online = Repo.fetch(ctx) }

    val s = state ?: return
    val value = amount.replace(" ", "").replace(",", ".").toDoubleOrNull() ?: 0.0

    Column(
        Modifier.fillMaxSize().background(Black).systemBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (s.src == "bce") Red else Dim))
            Spacer(Modifier.width(8.dp))
            Label(if (s.src == "bce") "TAUX DU JOUR" else "TAUX HORS-LIGNE")
            Spacer(Modifier.weight(1f))
            Label(s.date)
        }

        // Currency chips
        LazyRow(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CURRENCIES.size) { i ->
                val c = CURRENCIES[i].first
                val on = c == s.cur
                Row(
                    Modifier.height(40.dp).clip(RoundedCornerShape(999.dp))
                        .background(if (on) White else Color(0xFF0A0A0A))
                        .border(1.dp, if (on) White else Line, RoundedCornerShape(999.dp))
                        .clickable { scope.launch { Repo.setCurrency(ctx, c) } }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (on) { Box(Modifier.size(6.dp).clip(CircleShape).background(Red)); Spacer(Modifier.width(8.dp)) }
                    Text(c, color = if (on) Black else Dim, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Input
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Label(s.cur, Modifier.width(52.dp).padding(bottom = 10.dp))
            BasicTextField(
                value = amount, onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
                textStyle = TextStyle(color = White, fontSize = 52.sp, fontFamily = Mono, fontWeight = FontWeight.Black),
                cursorBrush = SolidColor(Red),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true, modifier = Modifier.weight(1f),
                decorationBox = { inner -> if (amount.isEmpty()) Text("0", color = Color(0xFF2D2D2D), fontSize = 52.sp, fontFamily = Mono, fontWeight = FontWeight.Black); inner() }
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))

        // Result
        Row(Modifier.padding(top = 22.dp)) {
            Label("EN EUROS"); Spacer(Modifier.weight(1f)); Label("1 € = ${Repo.fmtRate(s.rate)} ${s.cur}")
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Repo.fmtEur(value / s.rate), color = White, fontSize = 84.sp, fontFamily = Mono, fontWeight = FontWeight.Black, lineHeight = 84.sp)
            Text("€", color = Dim, fontSize = 30.sp, fontFamily = Mono, modifier = Modifier.padding(start = 6.dp, bottom = 12.dp))
        }

        // Quick refs
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Repo.steps(s.rate).forEach { step ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF050505))
                        .border(1.dp, Line, RoundedCornerShape(14.dp))
                        .clickable { amount = step.toString() }.padding(10.dp)
                ) {
                    Text(Repo.fmtEur(step / s.rate) + " €", color = White, fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Label("${Repo.fmtInt(step)} ${s.cur}", Modifier.padding(top = 4.dp))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Footer
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("SOURCE · ${s.src.uppercase()}")
            Spacer(Modifier.weight(1f))
            Pill("AJUSTER LE TAUX") { editing = true }
            Spacer(Modifier.width(8.dp))
            Pill("↻") { scope.launch { online = Repo.fetch(ctx) } }
        }
    }

    if (editing) RateDialog(s) { r -> editing = false; r?.let { scope.launch { Repo.setManualRate(ctx, s.cur, it) } } }
}

@Composable private fun Label(t: String, m: Modifier = Modifier) =
    Text(t, m, color = Dim, fontSize = 11.sp, fontFamily = Mono, letterSpacing = 1.5.sp)

@Composable private fun Pill(t: String, onClick: () -> Unit) = Box(
    Modifier.clip(RoundedCornerShape(999.dp)).border(1.dp, Line, RoundedCornerShape(999.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
) { Text(t, color = Dim, fontSize = 10.sp, fontFamily = Mono, letterSpacing = 1.sp) }

@Composable
private fun RateDialog(s: State, onDone: (Double?) -> Unit) {
    var v by remember { mutableStateOf(s.rate.toString()) }
    Dialog(onDismissRequest = { onDone(null) }) {
        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(Black).border(1.dp, Line, RoundedCornerShape(20.dp)).padding(22.dp)) {
            Label("1 € = ? ${s.cur}")
            BasicTextField(
                v, { v = it }, textStyle = TextStyle(color = White, fontSize = 40.sp, fontFamily = Mono, fontWeight = FontWeight.Black),
                cursorBrush = SolidColor(Red), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth()
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(999.dp)).border(1.dp, Line, RoundedCornerShape(999.dp)).clickable { onDone(null) }.padding(12.dp), Alignment.Center) {
                    Text("ANNULER", color = White, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.weight(1f).clip(RoundedCornerShape(999.dp)).background(White).clickable { onDone(v.replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 }) }.padding(12.dp), Alignment.Center) {
                    Text("VALIDER", color = Black, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
