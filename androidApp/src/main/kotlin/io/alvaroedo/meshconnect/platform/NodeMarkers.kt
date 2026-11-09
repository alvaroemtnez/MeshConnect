package io.alvaroedo.meshconnect.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import io.alvaroedo.meshconnect.R
import io.github.thatsfguy.meshcore.protocol.Codes

/**
 * Canvas-drawn map markers — colored pin + white type icon (the Nodes
 * tab's filter icons, see [iconFor]) and an optional always-visible name label chip
 * below the pin. Hand-drawn so the map needs no icon/marker libraries.
 */
object NodeMarkers {

    /** Marker bitmap + the anchor point (fraction of size) for osmdroid. */
    class Pin(val drawable: BitmapDrawable, val anchorU: Float, val anchorV: Float)

    fun colorFor(type: Int, isSelf: Boolean): Int = when {
        isSelf -> 0xFF43A047.toInt()                       // green — this node
        // Neutral gray for repeaters: infrastructure, not an alert
        // (red pins read as trouble). Matches the Nodes-tab avatar.
        type == Codes.ADV_TYPE_REPEATER -> 0xFF757575.toInt()
        type == Codes.ADV_TYPE_ROOM -> 0xFF8E24AA.toInt()     // purple
        type == Codes.ADV_TYPE_SENSOR -> 0xFFF4511E.toInt()   // orange
        else -> 0xFF1E88E5.toInt()                            // blue — companion/chat
    }

    /** The pin tip's height fraction for a pin with this [label], without drawing it. */
    fun anchorV(context: Context, label: String?): Float {
        // Same geometry as build(): the label's height depends only on the
        // text size, never on the text, so nothing needs drawing.
        val density = context.resources.displayMetrics.density
        val pinBottom = (34 * density).toInt() + (7 * density).toInt()
        if (label == null) return 1f
        val textSize = 11 * density
        val labelH = (textSize + 4 * density * 1.4f).toInt()
        return pinBottom.toFloat() / (pinBottom + (2 * density).toInt() + labelH)
    }

    fun build(
        context: Context,
        type: Int,
        label: String?,
        isSelf: Boolean = false,
    ): Pin {
        val density = context.resources.displayMetrics.density
        val pinD = (34 * density).toInt()          // pin circle diameter
        val tail = (7 * density).toInt()           // pointer below the circle
        val labelPad = (4 * density)
        val labelGap = (2 * density).toInt()

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11 * density
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            color = Color.WHITE
        }
        val labelText = label?.take(18)
        val labelW = labelText?.let { (textPaint.measureText(it) + labelPad * 2).toInt() } ?: 0
        val labelH = labelText?.let { (textPaint.textSize + labelPad * 1.4f).toInt() } ?: 0

        val width = maxOf(pinD, labelW)
        val pinBottom = pinD + tail
        val height = pinBottom + (if (labelText != null) labelGap + labelH else 0)

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = width / 2f
        val r = pinD / 2f

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = colorFor(type, isSelf)
        }
        // Pointer tail then circle (tail under circle edge).
        val tailPath = Path().apply {
            moveTo(cx - r * 0.35f, pinD * 0.82f)
            lineTo(cx + r * 0.35f, pinD * 0.82f)
            lineTo(cx, pinBottom.toFloat())
            close()
        }
        canvas.drawPath(tailPath, fill)
        canvas.drawCircle(cx, r, r, fill)
        // White rim for contrast on any tile.
        canvas.drawCircle(
            cx, r, r - 1 * density,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * density
                color = Color.argb(200, 255, 255, 255)
            },
        )

        drawGlyph(context, canvas, cx, r, r, type, isSelf)

        if (labelText != null) {
            val chip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.argb(210, 27, 43, 51)
            }
            val top = (pinBottom + labelGap).toFloat()
            val left = cx - labelW / 2f
            canvas.drawRoundRect(
                RectF(left, top, left + labelW, top + labelH),
                4 * density, 4 * density, chip,
            )
            val baseline = top + labelH / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(labelText, left + labelPad, baseline, textPaint)
        }

        // Anchor at the pin tip (center-x, bottom of the tail).
        return Pin(
            BitmapDrawable(context.resources, bmp),
            anchorU = 0.5f,
            anchorV = pinBottom.toFloat() / height,
        )
    }

    /**
     * Just the colored circle + glyph (no map pin tail, no label) —
     * the Nodes-tab avatar, so a repeater looks the same in the list
     * as it does on the map.
     */
    fun buildBadge(
        context: Context,
        type: Int,
        isSelf: Boolean = false,
        sizeDp: Int = 40,
    ): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val d = (sizeDp * density).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val r = d / 2f
        canvas.drawCircle(
            r, r, r,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = colorFor(type, isSelf)
            },
        )
        drawGlyph(context, canvas, r, r, r, type, isSelf)
        return BitmapDrawable(context.resources, bmp)
    }

    /**
     * A small filled chip carrying [text] — the signal reading drawn on
     * a neighbour link, at the line's midpoint.
     *
     * White text on the band's own colour, so the number and the line it
     * labels cannot disagree, and a thin light rim so it stays legible
     * over a dark tile.
     */
    fun buildChip(context: Context, text: String, argb: Int): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10 * density
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            color = Color.WHITE
        }
        val padX = 5 * density
        val padY = 3 * density
        val w = (paint.measureText(text) + padX * 2).toInt().coerceAtLeast(1)
        val h = (paint.textSize + padY * 2).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        canvas.drawRoundRect(
            rect, 3 * density, 3 * density,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = argb
            },
        )
        canvas.drawRoundRect(
            rect, 3 * density, 3 * density,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f * density
                color = Color.argb(180, 255, 255, 255)
            },
        )
        val baseline = h / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, padX, baseline, paint)
        return BitmapDrawable(context.resources, bmp)
    }

    /**
     * The node-type icon: the same Material Symbols drawable as the Nodes tab's filter chips
     * (`kindIcon` in NodesScreen), so a type reads the same on the map, in the list and in
     * the filter. This radio gets the location target.
     */
    @DrawableRes
    fun iconFor(type: Int, isSelf: Boolean): Int = when {
        isSelf -> R.drawable.ic_my_location
        type == Codes.ADV_TYPE_REPEATER -> R.drawable.ic_cell_tower
        type == Codes.ADV_TYPE_ROOM -> R.drawable.ic_forum
        type == Codes.ADV_TYPE_SENSOR -> R.drawable.ic_sensors
        else -> R.drawable.ic_person
    }

    /** White type icon inside the pin circle, centred at (cx, cy), radius r. */
    private fun drawGlyph(
        context: Context,
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        type: Int,
        isSelf: Boolean,
    ) {
        val icon = ContextCompat.getDrawable(context, iconFor(type, isSelf))?.mutate() ?: return
        icon.setTint(Color.WHITE)
        val half = r * 0.58f
        icon.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
        icon.draw(canvas)
    }
}
