package com.example.brainwave.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EEGGraph(
    dataPoints: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF2196F3),
    gridColor: Color = Color(0x1A000000),
    labelColor: Color = Color(0xFF666666),
    backgroundColor: Color = Color(0xFFFAFAFA)
) {
    val maxValue = remember(dataPoints) { dataPoints.maxOrNull()?.coerceAtLeast(1f) ?: 1f }
    val minValue = remember(dataPoints) { dataPoints.minOrNull()?.coerceAtMost(-1f) ?: -1f }

    val animatedPoints = remember(dataPoints) { Animatable(0f) }

    LaunchedEffect(dataPoints) {
        animatedPoints.animateTo(
            1f,
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(16.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
        ) {
            val width = size.width
            val height = size.height
            val valueRange = (maxValue - minValue).coerceAtLeast(1f)
            val pointWidth = width / (dataPoints.size - 1)

            // Draw grid lines
            val gridLines = 5
            val gridSpacing = height / gridLines

            // Horizontal grid lines
            for (i in 0..gridLines) {
                val y = i * gridSpacing
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                )
            }

            // Vertical grid lines
            val verticalLines = 6
            val verticalSpacing = width / verticalLines
            for (i in 0..verticalLines) {
                val x = i * verticalSpacing
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                )
            }

            // Draw the EEG line with gradient and smooth curve
            val path = Path()
            val points = mutableListOf<Offset>()

            dataPoints.forEachIndexed { index, value ->
                val x = index * pointWidth
                val y = height - ((value - minValue) / valueRange * height)
                points.add(Offset(x, y))
            }

            // Create smooth curve through points
            path.moveTo(points.first().x, points.first().y)
            for (i in 0 until points.size - 1) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val controlPoint1 = Offset(
                    x = p0.x + (p1.x - p0.x) / 2f,
                    y = p0.y
                )
                val controlPoint2 = Offset(
                    x = p0.x + (p1.x - p0.x) / 2f,
                    y = p1.y
                )
                path.cubicTo(
                    controlPoint1.x, controlPoint1.y,
                    controlPoint2.x, controlPoint2.y,
                    p1.x, p1.y
                )
            }

            // Create gradient for the line
            val gradient = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = 0.8f),
                    lineColor.copy(alpha = 0.5f)
                )
            )

            // Draw the EEG line with animation
            drawPath(
                path = path,
                brush = gradient,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                ),
                alpha = animatedPoints.value
            )

            // Add glow effect
            drawPath(
                path = path,
                color = lineColor.copy(alpha = 0.2f),
                style = Stroke(
                    width = 8.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                ),
                alpha = animatedPoints.value * 0.5f
            )
        }

        // Y-axis labels with custom styling
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            for (i in 5 downTo 0) {
                val value = minValue + (i * (maxValue - minValue) / 5)
                Text(
                    text = String.format("%.1f", value),
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    modifier = Modifier.padding(bottom = if (i == 0) 0.dp else 32.dp)
                )
            }
        }

        // X-axis labels
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 48.dp, top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val secondsLabels = List(7) { it * (dataPoints.size / 60) }
            secondsLabels.forEach { seconds ->
                Text(
                    text = "${seconds}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Add Y-axis title
        Text(
            text = "µV",
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            modifier = Modifier
                .rotate(-90f)
                .align(Alignment.CenterStart)
                .offset(x = (-32).dp)
        )

        // Add X-axis title
        Text(
            text = "Time (seconds)",
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(top = 24.dp)
        )
    }
}
