package com.bento.calendar.ui.notes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bento.calendar.ui.AppViewModel
import com.bento.calendar.ui.PinMode
import com.bento.calendar.ui.components.BentoSheet
import com.bento.calendar.ui.components.pressable
import com.bento.calendar.ui.theme.LocalBento

/**
 * PIN bottom sheet: create ("Create a PIN") or enter ("Enter PIN") flow with
 * 4 dots, shake-on-error and a 3x4 keypad (prototype markup line 275, logic
 * lines 693-698).
 */
@Composable
fun PinSheet(vm: AppViewModel) {
    val c = LocalBento.current
    val ctx = vm.pinCtx ?: return

    BentoSheet(onDismiss = vm::pinCancel) {
        // ---- Title + sub (.sh-t / .bt-m) ----
        Text(
            if (ctx.mode == PinMode.Set) "Create a PIN" else "Enter PIN",
            fontSize = 16.sp,
            fontWeight = FontWeight.W700,
            color = c.tx,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (ctx.mode == PinMode.Set) "Protects your locked notes" else "Unlock “${ctx.noteTitle}”",
            fontSize = 12.sp,
            color = c.sub,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )

        // ---- PIN dots (.pdots) with wrong-PIN shake ----
        val shake = remember { Animatable(0f) }
        LaunchedEffect(vm.pinErr) {
            if (vm.pinErr) {
                shake.snapTo(0f)
                shake.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 400
                        -7f at 80
                        7f at 160
                        -7f at 240
                        7f at 320
                    },
                )
            } else {
                // pinErr can flip false mid-shake (next digit press cancels the
                // animateTo above); re-center so the dots don't stay offset.
                shake.snapTo(0f)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 2.dp)
                .offset { IntOffset(shake.value.dp.roundToPx(), 0) },
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            repeat(4) { i ->
                val filled = i < vm.pinBuf.length
                Box(
                    Modifier
                        .size(12.dp)
                        .background(if (filled) c.acc else Color.Transparent, CircleShape)
                        .border(1.5.dp, if (filled) c.acc else c.cbb, CircleShape),
                )
            }
        }

        // ---- Error line (.perr) ----
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .height(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (vm.pinErr) {
                Text(
                    "Wrong PIN — try again",
                    fontSize = 11.sp,
                    color = c.dng,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // ---- Keypad (.kpad) ----
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "back")
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            keys.chunked(3).forEach { rowKeys ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowKeys.forEach { key ->
                        KeypadKey(
                            key = key,
                            modifier = Modifier.weight(1f),
                            onPress = { vm.pinPress(key) },
                        )
                    }
                }
            }
        }

        // ---- Biometric re-trigger ----
        // The system sheet auto-opens with the pad; this brings it back after
        // a dismissal without forcing the user to type the PIN.
        if (ctx.mode == PinMode.Enter && vm.canNoteBio) {
            Text(
                "Use fingerprint or face",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.W600,
                color = c.acc,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .pressable(onClick = { vm.requestNoteBio() })
                    .padding(vertical = 4.dp),
            )
        }
    }
}

/** 52dp keypad key (.kkey); the blank cell and backspace have no fill (.kx). */
@Composable
private fun KeypadKey(key: String, modifier: Modifier = Modifier, onPress: () -> Unit) {
    val c = LocalBento.current
    val transparent = key == "" || key == "back"
    Box(
        modifier
            .height(52.dp)
            .then(if (key == "") Modifier else Modifier.pressable(onClick = onPress))
            .background(if (transparent) Color.Transparent else c.inp, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (key != "") {
            Text(
                if (key == "back") "⌫" else key,
                fontSize = 17.sp,
                fontWeight = FontWeight.W600,
                color = c.tx,
            )
        }
    }
}
