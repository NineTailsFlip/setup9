package io.github.ninetailsflip.setup

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import life.furrer.nine.core.ui.Dimens
import life.furrer.nine.core.ui.Palette
import life.furrer.nine.core.ui.dp

/** Height of a [TwoLineRowView] in dp, for a RowBinder. */
const val TWO_LINE_ROW_DP = 48

/** A list row the way Nine apps draw them: a title over a dim line of details. The selected row
 *  is white, the rest sit back in gray; a marked row (the language in use, say) carries a tick
 *  and is green while it isn't selected. Built once and re-bound in place, as RowListView re-binds visible rows on every key. */
class TwoLineRowView(context: Context) : LinearLayout(context) {
    private val title = TextView(context).apply {
        textSize = 16f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val meta = TextView(context).apply {
        textSize = 12f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(Palette.GRAY_MUTED)
    }
    private var bound: List<Any?>? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        val side = context.dp(Dimens.SIDE_PADDING_DP)
        setPadding(side, 0, side, 0)
        addView(title)
        addView(meta)
    }

    fun bind(titleText: String, metaText: String, selected: Boolean, marked: Boolean) {
        val key = listOf(titleText, metaText, selected, marked)
        if (key == bound) return
        bound = key
        title.text = if (marked) "✓ $titleText" else titleText
        title.setTextColor(
            when {
                // The selection always shows; a marked row keeps its tick when it's selected.
                selected -> Palette.WHITE
                marked -> Palette.GREEN
                else -> Palette.GRAY_DIM
            },
        )
        meta.text = metaText
        meta.visibility = if (metaText.isEmpty()) GONE else VISIBLE
    }
}
