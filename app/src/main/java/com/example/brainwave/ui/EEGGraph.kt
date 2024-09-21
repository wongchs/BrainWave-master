package com.example.brainwave.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EEGGraph(dataPoints: List<Float>) {
    val maxValue = dataPoints.maxOrNull() ?: 1f
    val minValue = dataPoints.minOrNull() ?: 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .padding(16.dp)
    ) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .padding(start = 40.dp, bottom = 40.dp, top = 20.dp, end = 20.dp)
        ) {
            val width = size.width
            val height = size.height
            val pointWidth = width / (dataPoints.size - 1)
            val valueRange = (maxValue - minValue).coerceAtLeast(1f)

            // Draw X and Y axes
            drawLine(Color.Black, Offset(0f, height), Offset(width, height), 2f)
            drawLine(Color.Black, Offset(0f, 0f), Offset(0f, height), 2f)

            // Draw data points
            dataPoints.forEachIndexed { index, value ->
                if (index < dataPoints.size - 1) {
                    val startX = index * pointWidth
                    val startY = height - ((value - minValue) / valueRange * height)
                    val endX = (index + 1) * pointWidth
                    val endY = height - ((dataPoints[index + 1] - minValue) / valueRange * height)

                    drawLine(
                        color = Color.Blue,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = 2f
                    )
                }
            }
        }

        // X-axis label
        Text(
            text = "Time (seconds)",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )

        // Y-axis label
        Text(
            text = "Amplitude (µV)",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .rotate(-90f)
                .padding(bottom = 40.dp)
        )

        // Y-axis values
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(end = 4.dp)
        ) {
            Text(text = "${maxValue.toInt()} µV", fontSize = 10.sp)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "${((maxValue + minValue) / 2).toInt()} µV", fontSize = 10.sp)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "${minValue.toInt()} µV", fontSize = 10.sp)
        }

        // X-axis values
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 40.dp, top = 4.dp)
                .fillMaxWidth()
        ) {
            Text(text = "0s", fontSize = 10.sp)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "${(dataPoints.size / 20).toInt()}s", fontSize = 10.sp)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "${(dataPoints.size / 10).toInt()}s", fontSize = 10.sp)
        }
    }
}