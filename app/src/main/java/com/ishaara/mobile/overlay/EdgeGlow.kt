package com.ishaara.mobile.overlay

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.SweepGradient
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.ishaara.mobile.ui.theme.SignalColors
import kotlin.math.PI

/**
 * Light that follows the true edge of the display, soft on the inside and brighter at the rim. Colour
 * drifts around the screen. Every term repeats after one turn of `t`, so the loop has no visible seam.
 */
private const val GLOW_SHADER = """
uniform float2 size;
uniform float radius;
uniform float reach;
uniform float strength;
uniform float t;
uniform float3 c0;
uniform float3 c1;
uniform float3 c2;

float box(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 xy) {
    float2 p = xy - size * 0.5;
    float d = max(-box(p, size * 0.5, radius), 0.0);
    float a = atan(p.y / size.y, p.x / size.x);
    float wave = 0.8 + 0.2 * sin(a * 2.0 + t) + 0.12 * sin(a * 3.0 - t * 2.0);
    float soft = exp(-d / (reach * wave));
    float rim = exp(-d / (reach * 0.14));
    float3 colour = mix(mix(c0, c1, 0.5 + 0.5 * sin(a + t)), c2, 0.5 + 0.5 * sin(a * 2.0 - t + 1.7));
    float alpha = clamp(soft * 0.6 + rim * 0.5, 0.0, 1.0) * strength;
    return half4(half3(colour * alpha), half(alpha));
}
"""

@Composable
fun EdgeGlow(active: Boolean, cornerRadiusPx: Float) {
    val strength by animateFloatAsState(
        if (active) 1f else 0f,
        tween(if (active) 500 else 420, easing = FastOutSlowInEasing),
        label = "strength",
    )
    // Blooms inward when work starts, then settles against the edge.
    val bloom = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (active) {
            bloom.snapTo(3.2f)
            bloom.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        }
    }
    val time by rememberInfiniteTransition(label = "glow").animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "time",
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ShaderGlow(strength, bloom.value, time, cornerRadiusPx)
    } else {
        BlurGlow(strength, time, cornerRadiusPx)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderGlow(strength: Float, bloom: Float, time: Float, cornerRadiusPx: Float) {
    val shader = remember {
        RuntimeShader(GLOW_SHADER).apply {
            listOf("c0", "c1", "c2").forEachIndexed { i, name ->
                val c = SignalColors[i]
                setFloatUniform(name, c.red, c.green, c.blue)
            }
        }
    }
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(Modifier.fillMaxSize()) {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("radius", cornerRadiusPx)
        shader.setFloatUniform("reach", 26.dp.toPx() * bloom)
        shader.setFloatUniform("strength", strength)
        shader.setFloatUniform("t", time)
        drawRect(brush)
    }
}

/** For Android 11 and 12, which can't run custom shaders. */
@Composable
private fun BlurGlow(strength: Float, time: Float, cornerRadiusPx: Float) {
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE } }
    val matrix = remember { android.graphics.Matrix() }
    val colors = remember {
        val (a, b, c) = SignalColors.map { it.toArgb() }
        intArrayOf(a, b, c, b, a)
    }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val shader = SweepGradient(w / 2, h / 2, colors, null)
        matrix.setRotate(Math.toDegrees(time.toDouble()).toFloat(), w / 2, h / 2)
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        paint.strokeWidth = 12.dp.toPx()
        paint.maskFilter = BlurMaskFilter(14.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
        paint.alpha = (255 * strength).toInt()
        drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, 0f, w, h, cornerRadiusPx, cornerRadiusPx, paint) }
    }
}
