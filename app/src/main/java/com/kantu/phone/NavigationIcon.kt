package com.kantu.phone

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

/** Drawn shapes rather than emoji, so older devices show the same pictograms. */
@Composable
internal fun NavigationIcon(contacts: Boolean, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val u = size.minDimension
        val x = (size.width - u) / 2
        val y = (size.height - u) / 2
        fun p(a: Float, b: Float) = Offset(x + a * u, y + b * u)
        if (contacts) {
            drawRoundRect(color, p(.12f, .07f), Size(u * .76f, u * .86f),
                CornerRadius(u * .08f), style = Stroke(u * .065f))
            drawCircle(color, u * .13f, p(.5f, .35f))
            drawRoundRect(color, p(.28f, .55f), Size(u * .44f, u * .23f),
                CornerRadius(u * .11f))
        } else {
            val path = Path()
            fun move(a: Float, b: Float) { val q = p(a, b); path.moveTo(q.x, q.y) }
            fun line(a: Float, b: Float) { val q = p(a, b); path.lineTo(q.x, q.y) }
            fun curve(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {
                val q = p(a, b); val r = p(c, d); val s = p(e, f)
                path.cubicTo(q.x, q.y, r.x, r.y, s.x, s.y)
            }
            move(.14f, .08f)
            curve(.02f, .13f, .1f, .42f, .28f, .62f)
            curve(.5f, .87f, .8f, .99f, .9f, .84f)
            line(.94f, .71f); line(.7f, .55f); line(.58f, .68f)
            curve(.45f, .62f, .35f, .5f, .29f, .37f)
            line(.43f, .25f); line(.27f, .06f)
            path.close()
            drawPath(path, color)
        }
    }
}

@Composable
internal fun KeypadToggleIcon(expanded: Boolean, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val u = size.minDimension
        val left = (size.width - u) / 2
        val top = (size.height - u) / 2
        val dotRadius = u * .075f
        for (row in 0..2) {
            for (column in 0..2) {
                drawCircle(
                    color = color,
                    radius = dotRadius,
                    center = Offset(
                        left + u * (.2f + column * .2f),
                        top + u * (.2f + row * .2f),
                    ),
                )
            }
        }
        val path = Path()
        if (expanded) {
            path.moveTo(left + u * .72f, top + u * .3f)
            path.lineTo(left + u * .86f, top + u * .44f)
            path.lineTo(left + u, top + u * .3f)
        } else {
            path.moveTo(left + u * .72f, top + u * .44f)
            path.lineTo(left + u * .86f, top + u * .3f)
            path.lineTo(left + u, top + u * .44f)
        }
        drawPath(path, color = color, style = Stroke(u * .075f))
    }
}
