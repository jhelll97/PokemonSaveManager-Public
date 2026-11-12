package com.example.pokemonsavemanager

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Shared presentation primitives taken from the Omega Ruby reference screen. */
object OmegaRubyUi {
    val pageBackground: Int = Color.rgb(7, 16, 24)
    val cardBackground: Int = Color.rgb(12, 35, 51)
    val cardOutline: Int = Color.rgb(25, 145, 220)
    val accent: Int = Color.rgb(70, 190, 255)
    val mutedText: Int = Color.rgb(160, 180, 195)
    val selectedAction: Int = Color.rgb(25, 115, 205)
    val inactiveTab: Int = Color.rgb(45, 45, 45)

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun roundedCard(
        context: Context,
        fill: Int = cardBackground,
        outline: Int = cardOutline,
        radiusDp: Int = 16
    ): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(context, radiusDp).toFloat()
        setStroke(dp(context, 1), outline)
    }

    fun sectionLabel(context: Context, value: String): TextView = TextView(context).apply {
        text = value
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(accent)
        setPadding(dp(context, 4), dp(context, 14), 0, dp(context, 8))
    }

    fun styleActionButton(context: Context, button: Button) {
        button.isAllCaps = false
        button.textSize = 16f
        button.gravity = Gravity.CENTER
        button.setTextColor(Color.WHITE)
        button.background = roundedCard(context)
        button.stateListAnimator = null
        button.minimumHeight = maxOf(button.minimumHeight, dp(context, 48))
        button.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10))
        button.alpha = if (button.isEnabled) 1f else 0.5f
    }

    fun styleTab(context: Context, button: Button, selected: Boolean) {
        button.isAllCaps = false
        button.textSize = 15f
        button.gravity = Gravity.CENTER
        button.setTextColor(Color.WHITE)
        button.background = roundedCard(
            context,
            fill = if (selected) selectedAction else inactiveTab,
            outline = if (selected) selectedAction else inactiveTab,
            radiusDp = 2
        )
        button.stateListAnimator = null
    }

    /** Applies the same card treatment to an existing action hierarchy without replacing listeners. */
    fun styleActionTree(context: Context, root: ViewGroup) {
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            when (child) {
                is Button -> {
                    styleActionButton(context, child)
                    (child.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                        if (params.leftMargin == 0 && params.topMargin == 0 &&
                            params.rightMargin == 0 && params.bottomMargin == 0
                        ) {
                            val gap = dp(context, 4)
                            params.setMargins(gap, gap, gap, gap)
                            child.layoutParams = params
                        }
                    }
                }
                is ViewGroup -> styleActionTree(context, child)
            }
        }
    }
}
