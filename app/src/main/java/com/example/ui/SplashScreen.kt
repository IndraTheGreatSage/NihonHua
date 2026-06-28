package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable
fun SplashScreen(onSplashComplete: () -> Unit) {

    var phase by remember { mutableStateOf(0) }

    val particleAnim = remember { Animatable(0f) }
    val lotusAnim    = remember { Animatable(0f) }
    val glowAnim     = remember { Animatable(0f) }
    val textAlpha    = remember { Animatable(0f) }
    val fadeOut      = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // Phase 0: particle streams fly in from both sides
        particleAnim.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
        phase = 1
        // Phase 1: lotus forms from particles
        lotusAnim.animateTo(1f, tween(900, easing = LinearOutSlowInEasing))
        phase = 2
        // Phase 2: glow blooms, text fades in
        glowAnim.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        textAlpha.animateTo(1f, tween(550))
        delay(900)
        // Phase 3: fade to main
        fadeOut.animateTo(0f, tween(400))
        onSplashComplete()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "glow_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue  = 1f,
        label        = "pulse",
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(fadeOut.value)
            .background(Color(0xFF0B0C0E)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f - 80.dp.toPx()

            // ── Particle streams (phase 0 and fading out in phase 1) ──────
            val streamOpacity = when {
                phase == 0              -> 1f
                phase == 1 && lotusAnim.value < 1f -> (1f - lotusAnim.value).coerceIn(0f, 1f)
                else                    -> 0f
            }
            if (streamOpacity > 0f) {
                drawParticleStreams(cx, cy, particleAnim.value, streamOpacity)
            }

            // ── Ambient radial glow behind logo ───────────────────────────
            if (phase >= 2) {
                val g = glowAnim.value * pulse
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x35D4B896),
                            Color(0x10C9A882),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = 180.dp.toPx() * g
                    ),
                    radius = 180.dp.toPx() * g,
                    center = Offset(cx, cy)
                )
            }

            // ── Lotus logo ────────────────────────────────────────────────
            if (lotusAnim.value > 0f) {
                val g = if (phase >= 2) glowAnim.value * pulse else 0f
                drawLotus(cx, cy, lotusAnim.value, g)
            }
        }

        // ── Text ──────────────────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 110.dp)
        ) {
            Text(
                text     = "NihonHua",
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                color    = Color(0xFFD4B896).copy(alpha = textAlpha.value),
                letterSpacing = 5.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text     = "ANIME  ·  DONGHUA",
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                color    = Color(0xFF7A6E60).copy(alpha = textAlpha.value * 0.85f),
                letterSpacing = 3.sp
            )
        }
    }
}

// ─── Particle streams flying in from left and right ───────────────────────────
private fun DrawScope.drawParticleStreams(cx: Float, cy: Float, progress: Float, opacity: Float) {
    val gold   = Color(0xFFC9A882)
    val bright = Color(0xFFF5E6CC)
    val count  = 48

    repeat(2) { side ->
        for (i in 0 until count) {
            val t      = i.toFloat() / count
            val delay  = t * 0.55f
            val localP = ((progress - delay) / 0.55f).coerceIn(0f, 1f)
            if (localP <= 0f) continue

            // x travels from screen edge towards centre
            val startX = if (side == 0) -60f else size.width + 60f
            val endX   = if (side == 0) cx - 8f else cx + 8f
            val x      = startX + (endX - startX) * localP

            // sinusoidal wave, phase offset per side so they mirror
            val wavePhase = if (side == 0) 0f else PI.toFloat()
            val amp   = 55f * (1f - t * 0.4f)
            val y     = cy + sin(t * PI.toFloat() * 2.8f + localP * PI.toFloat() * 1.5f + wavePhase) * amp

            val a  = sin(localP * PI.toFloat()).coerceIn(0f, 1f) * opacity
            val sz = 2f + (1f - t) * 2.5f
            drawCircle(gold.copy(alpha = a * 0.75f), sz, Offset(x, y))
            if (i % 5 == 0) {
                drawCircle(bright.copy(alpha = a * 0.35f), sz * 2.8f, Offset(x, y))
            }
        }
    }
}

// ─── Lotus drawing ────────────────────────────────────────────────────────────
private fun DrawScope.drawLotus(cx: Float, cy: Float, progress: Float, glowStrength: Float) {
    val scale = 1.35f                  // overall size multiplier (px per unit)
    val base  = cy + 28f * scale       // convergence point y (bottom of petals)

    // ── Tip highlight glow ────────────────────────────────────────────────
    if (glowStrength > 0f) {
        // centre petal tip
        val tipY = cy - 54f * scale
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x90FFF4E0), Color.Transparent),
                center = Offset(cx, tipY),
                radius = 22.dp.toPx() * glowStrength
            ),
            radius = 22.dp.toPx() * glowStrength,
            center = Offset(cx, tipY)
        )
    }

    // ── Paths ─────────────────────────────────────────────────────────────
    //  Centre petal  – tall upward flame
    val centre = Path().apply {
        moveTo(cx, base)
        cubicTo(
            cx - 13f * scale, base - 20f * scale,
            cx - 9f  * scale, cy   - 20f * scale,
            cx,               cy   - 54f * scale
        )
        cubicTo(
            cx + 9f  * scale, cy   - 20f * scale,
            cx + 13f * scale, base - 20f * scale,
            cx,               base
        )
        close()
    }

    //  Left petal  – sweeps left and slightly up
    val left = Path().apply {
        moveTo(cx, base)
        cubicTo(
            cx - 18f * scale, base - 12f * scale,
            cx - 44f * scale, cy   + 0f  * scale,
            cx - 54f * scale, cy   - 18f * scale
        )
        cubicTo(
            cx - 42f * scale, cy   - 26f * scale,
            cx - 22f * scale, cy   - 8f  * scale,
            cx,               base
        )
        close()
    }

    //  Right petal  – mirror of left
    val right = Path().apply {
        moveTo(cx, base)
        cubicTo(
            cx + 18f * scale, base - 12f * scale,
            cx + 44f * scale, cy   + 0f  * scale,
            cx + 54f * scale, cy   - 18f * scale
        )
        cubicTo(
            cx + 42f * scale, cy   - 26f * scale,
            cx + 22f * scale, cy   - 8f  * scale,
            cx,               base
        )
        close()
    }

    val champagne  = Color(0xFFD4B896)
    val lightChamp = Color(0xFFF5E6CC)
    val darkChamp  = Color(0xFF9A806A)

    // Fill alpha only appears in second half of progress
    val fillA = ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)

    if (fillA > 0f) {
        // Left fill – darker gradient
        drawPath(left, brush = Brush.linearGradient(
            colors = listOf(darkChamp.copy(alpha = fillA), champagne.copy(alpha = fillA * 0.85f)),
            start  = Offset(cx - 54f * scale, cy),
            end    = Offset(cx, base)
        ))
        // Right fill
        drawPath(right, brush = Brush.linearGradient(
            colors = listOf(darkChamp.copy(alpha = fillA), champagne.copy(alpha = fillA * 0.85f)),
            start  = Offset(cx + 54f * scale, cy),
            end    = Offset(cx, base)
        ))
        // Centre fill – brighter
        drawPath(centre, brush = Brush.linearGradient(
            colors = listOf(champagne.copy(alpha = fillA), lightChamp.copy(alpha = fillA)),
            start  = Offset(cx, base),
            end    = Offset(cx, cy - 54f * scale)
        ))
    }

    // Stroke outlines (appear from start of progress)
    val strokeA = progress.coerceIn(0f, 1f)
    val sw      = 1.8.dp.toPx()
    drawPath(left,   color = lightChamp.copy(alpha = strokeA * 0.55f), style = Stroke(sw))
    drawPath(right,  color = lightChamp.copy(alpha = strokeA * 0.55f), style = Stroke(sw))
    drawPath(centre, color = lightChamp.copy(alpha = strokeA * 0.9f),  style = Stroke(sw))

    // Bright tip dots at petal tips (appear when nearly complete)
    val dotA = ((progress - 0.65f) / 0.35f).coerceIn(0f, 1f) * (0.7f + glowStrength * 0.3f)
    if (dotA > 0f) {
        // Centre tip
        drawCircle(Color(0xFFFFF6E8).copy(alpha = dotA),             3.8.dp.toPx(), Offset(cx, cy - 54f * scale))
        drawCircle(Color(0x60FFFFFF).copy(alpha = dotA * 0.5f),      7.dp.toPx(),   Offset(cx, cy - 54f * scale))
        // Left tip
        drawCircle(Color(0xFFDCC8A8).copy(alpha = dotA * 0.6f),      2.8.dp.toPx(), Offset(cx - 54f * scale, cy - 18f * scale))
        // Right tip
        drawCircle(Color(0xFFDCC8A8).copy(alpha = dotA * 0.6f),      2.8.dp.toPx(), Offset(cx + 54f * scale, cy - 18f * scale))
    }
}