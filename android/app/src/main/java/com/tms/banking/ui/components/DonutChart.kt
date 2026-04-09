package com.tms.banking.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tms.banking.ui.theme.CategoryColors
import com.tms.banking.ui.theme.OnBackground
import com.tms.banking.ui.theme.OnSurface
import com.tms.banking.ui.theme.Primary
import java.text.NumberFormat
import java.util.Locale

data class DonutSlice(
    val label: String,
    val value: Double,
    val color: Color
)

@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    totalLabel: String,
    totalValue: String,
    modifier: Modifier = Modifier,
    chartSize: Dp = 180.dp,
    strokeWidth: Dp = 32.dp,
    onSliceClick: ((Int) -> Unit)? = null,
    selectedIndices: Set<Int> = emptySet()
) {
    val total = slices.sumOf { it.value }
    if (total == 0.0 || slices.isEmpty()) return

    var animationPlayed by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "donut_anim"
    )

    LaunchedEffect(slices) {
        animationPlayed = true
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(chartSize)) {
                val stroke = Stroke(width = strokeWidth.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Butt)
                val diameter = size.minDimension - strokeWidth.toPx()
                val topLeft = Offset(strokeWidth.toPx() / 2, strokeWidth.toPx() / 2)
                val arcSize = Size(diameter, diameter)
                var startAngle = -90f
                val gap = 2f

                slices.forEach { slice ->
                    val sweep = ((slice.value.toFloat() / total.toFloat()) * 360f * animatedProgress - gap).coerceAtLeast(0f)
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke
                    )
                    startAngle += sweep + gap
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = totalLabel,
                    color = OnSurface,
                    fontSize = 11.sp
                )
                Text(
                    text = totalValue,
                    color = OnBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        slices.forEachIndexed { index, slice ->
            val isSelected = index in selectedIndices
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onSliceClick != null) Modifier.clickable { onSliceClick(index) }
                        else Modifier
                    )
                    .then(
                        if (isSelected) Modifier.background(Primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        else Modifier
                    )
                    .padding(vertical = 4.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .then(Modifier.then(
                            Modifier.size(10.dp)
                        ))
                ) {
                    Canvas(modifier = Modifier.size(10.dp)) {
                        drawCircle(color = slice.color)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = slice.label,
                    color = OnSurface,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                val pct = if (total > 0) (slice.value / total * 100).toInt() else 0
                Text(
                    text = "$pct%",
                    color = OnBackground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatAmountShort(slice.value),
                    color = OnBackground,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun formatAmountShort(amount: Double): String {
    val fmt = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 0
    }
    return "AED ${fmt.format(amount)}"
}
