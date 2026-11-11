package io.github.artcraftandroid.procraft.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.procraft.canvas.CanvasViewModel
import io.github.artcraftandroid.procraft.canvas.Combo
import io.github.artcraftandroid.procraft.canvas.HotkeySlot
import io.github.artcraftandroid.procraft.canvas.Hotkeys

private val PageFill get() = if (Pc.light) Color(0xFFF2F2F2) else Color(0xFF1B1B1B)
private val CardFill get() = if (Pc.light) Color.White else Color(0xFF2A2A2A)
private val ChipFill get() = if (Pc.light) Color(0xFFE9E9EB) else Color(0xFF3A3A3A)
private val Ink get() = if (Pc.light) Color(0xFF414141) else Color(0xFFC1C1C1)
private val Detail get() = if (Pc.light) Color(0xFF6A6A6A) else Color(0xFF9A9A9A)
private val Line get() = if (Pc.light) Color(0xFFE2E2E2) else Color(0xFF373737)

/**
 * The hotkeys, full screen: every key of the shortcuts' list under its heading, the press it is set
 * to shown on a chip. A tap on a chip asks for the press to set it to; Escape cancels that. Done
 * (or back) closes it.
 */
@Composable
fun HotkeysScreen(vm: CanvasViewModel, onClose: () -> Unit) {
    val hotkeys = vm.hotkeys
    var capturing by remember { mutableStateOf<HotkeySlot?>(null) }
    var resetting by remember { mutableStateOf(false) }
    BackHandler {
        if (capturing != null) capturing = null else onClose()
    }
    Box(Modifier.fillMaxSize().background(PageFill).pointerInput(Unit) { detectTapGestures { } }) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 20.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Hotkeys", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("Tap a key to set it, with a keyboard attached", color = Detail, fontSize = 13.sp, maxLines = 1)
            }
            Box(
                Modifier.size(44.dp, 45.dp).clip(CircleShape).background(Pc.Accent).clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(PcIcons.DoneTick, contentDescription = "Done", tint = Color.White, modifier = Modifier.requiredSize(40.dp))
            }
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(top = 86.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            for ((heading, slots) in hotkeys.groups) {
                item(key = "h:$heading") {
                    Text(heading, color = Detail, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 8.dp))
                }
                items(slots, key = { it.id }) { slot ->
                    val index = slots.indexOf(slot)
                    val shape = RoundedCornerShape(
                        topStart = if (index == 0) 16.dp else 0.dp,
                        topEnd = if (index == 0) 16.dp else 0.dp,
                        bottomStart = if (index == slots.lastIndex) 16.dp else 0.dp,
                        bottomEnd = if (index == slots.lastIndex) 16.dp else 0.dp,
                    )
                    Column(Modifier.fillMaxWidth().clip(shape).background(CardFill)) {
                        if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                        HotkeyRow(hotkeys, slot) { capturing = slot }
                    }
                }
            }
            item(key = "reset") {
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(CardFill).clickable { resetting = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Reset every hotkey", color = Ink, fontSize = 15.sp, maxLines = 1)
                }
                Spacer(Modifier.height(60.dp))
            }
        }
        capturing?.let { slot ->
            PressCapture(
                slot,
                onPress = { combo ->
                    val took = hotkeys.assign(slot, combo)
                    if (took != null) vm.showNotice("${combo.label()} now does ${slot.action}; $took has none", warning = false)
                    capturing = null
                },
                onNone = {
                    hotkeys.assign(slot, null)
                    capturing = null
                },
                onCancel = { capturing = null },
            )
        }
    }
    Alert(
        if (resetting) Unit else null,
        title = { "Reset the hotkeys?" },
        message = { "Every hotkey goes back to how the app starts out." },
        action = "Reset",
        onCancel = { resetting = false },
        onAction = {
            hotkeys.resetAll()
            resetting = false
        },
    )
}

/** A key of the list: what it does (and which of the row's keys it is) and its chip. */
@Composable
private fun HotkeyRow(hotkeys: Hotkeys, slot: HotkeySlot, onTap: () -> Unit) {
    val combo = hotkeys.combo(slot)
    val changed = hotkeys.changed(slot)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onTap() }.padding(start = 18.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(slot.action, color = Ink, fontSize = 16.sp, maxLines = 2)
            val note = buildString {
                if (slot.count > 1) append("Key ${slot.position + 1} of ${slot.count}")
                if (changed) {
                    if (isNotEmpty()) append(" · ")
                    append("Was ${slot.default.label()}")
                }
            }
            if (note.isNotEmpty()) Text(note, color = Detail, fontSize = 12.sp, maxLines = 1)
        }
        if (changed) {
            Text(
                "Reset", color = Pc.Accent, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { hotkeys.reset(slot) }.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(ChipFill).padding(horizontal = 12.dp, vertical = 7.dp)) {
            Text(combo?.label() ?: "None", color = if (combo == null) Detail else Ink, fontSize = 15.sp, maxLines = 1)
        }
    }
}

/** The card asking for the press to set [slot] to: the next key pressed (with its modifiers) is it. */
@Composable
private fun PressCapture(slot: HotkeySlot, onPress: (Combo) -> Unit, onNone: () -> Unit, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).pointerInput(Unit) { detectTapGestures { onCancel() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(320.dp).clip(RoundedCornerShape(22.dp)).background(CardFill)
                .pointerInput(Unit) { detectTapGestures { } }
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown) {
                        val code = e.nativeKeyEvent.keyCode
                        if (code == android.view.KeyEvent.KEYCODE_ESCAPE) {
                            onCancel()
                        } else {
                            // a modifier on its own is not a key yet
                            val name = Hotkeys.nameOf(code)
                            if (name != null) onPress(Combo(name, e.isCtrlPressed || e.isMetaPressed, e.isShiftPressed, e.isAltPressed))
                        }
                    }
                    true
                }
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Press the keys for", color = Detail, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(slot.action, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 3)
            if (slot.count > 1) Text("(key ${slot.position + 1} of ${slot.count})", color = Detail, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            Text("Escape cancels", color = Detail, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(ChipFill).clickable { onNone() },
                    contentAlignment = Alignment.Center,
                ) { Text("None", color = Ink, fontSize = 15.sp) }
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(ChipFill).clickable { onCancel() },
                    contentAlignment = Alignment.Center,
                ) { Text("Cancel", color = Ink, fontSize = 15.sp) }
            }
        }
    }
}
