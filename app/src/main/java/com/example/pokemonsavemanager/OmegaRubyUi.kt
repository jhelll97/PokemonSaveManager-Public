package com.example.pokemonsavemanager

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Shared presentation primitives taken from the Omega Ruby reference screens. */
object OmegaRubyUi {
    val pageBackground: Int = Color.rgb(5, 18, 28)
    val cardBackground: Int = Color.rgb(8, 39, 51)
    val fieldBackground: Int = Color.rgb(10, 46, 59)
    val cardStroke: Int = Color.rgb(22, 151, 164)
    val accent: Int = Color.rgb(35, 118, 229)
    val text: Int = Color.rgb(244, 250, 252)
    val mutedText: Int = Color.rgb(158, 184, 194)
    val sectionText: Int = Color.rgb(80, 213, 218)

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun roundedCard(
        context: Context,
        fill: Int = cardBackground,
        stroke: Int = cardStroke,
        radiusDp: Int = 16
    ): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(context, radiusDp).toFloat()
        setStroke(dp(context, 1), stroke)
    }

    fun styleTrainerEditor(context: Context, root: LinearLayout) {
        root.setBackgroundColor(pageBackground)
        root.setPadding(dp(context, 16), dp(context, 24), dp(context, 16), dp(context, 20))

        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            when (child) {
                is EditText -> styleInput(context, child)
                is Button -> styleAction(context, child)
                is TextView -> styleText(context, child, index)
            }
            val current = child.layoutParams
            child.layoutParams = LinearLayout.LayoutParams(
                current?.width ?: LinearLayout.LayoutParams.MATCH_PARENT,
                current?.height ?: LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(context, if (child is EditText || child is Button) 10 else 4)
            }
        }
    }

    fun scrollingPage(context: Context, content: View): View = ScrollView(context).apply {
        setBackgroundColor(pageBackground)
        isFillViewport = true
        addView(content)
    }

    private fun styleText(context: Context, view: TextView, index: Int) {
        val sizeSp = view.textSize / context.resources.displayMetrics.scaledDensity
        when {
            index == 0 -> {
                view.textSize = 28f
                view.typeface = Typeface.DEFAULT_BOLD
                view.setTextColor(text)
                view.setPadding(0, 0, 0, dp(context, 4))
            }
            index == 1 -> {
                view.textSize = 14f
                view.setTextColor(mutedText)
                view.setPadding(0, 0, 0, dp(context, 12))
            }
            index == 2 -> {
                view.textSize = 14f
                view.setTextColor(text)
                view.background = roundedCard(context)
                view.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
            }
            sizeSp >= 18f -> {
                view.textSize = 15f
                view.typeface = Typeface.DEFAULT_BOLD
                view.setTextColor(sectionText)
                view.setPadding(dp(context, 4), dp(context, 10), dp(context, 4), 0)
            }
            else -> {
                view.textSize = 13f
                view.setTextColor(mutedText)
                view.setPadding(dp(context, 4), 0, dp(context, 4), dp(context, 2))
            }
        }
    }

    private fun styleInput(context: Context, input: EditText) {
        input.textSize = 16f
        input.setTextColor(text)
        input.setHintTextColor(mutedText)
        input.background = roundedCard(context, fieldBackground)
        input.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10))
        input.minimumHeight = dp(context, 52)
    }

    private fun styleAction(context: Context, button: Button) {
        button.isAllCaps = false
        button.textSize = 15f
        button.typeface = Typeface.DEFAULT_BOLD
        button.setTextColor(text)
        button.stateListAnimator = null
        button.minimumHeight = dp(context, 52)
        button.background = if (
            button.text.toString().startsWith("Review") ||
            button.text.toString().startsWith("Inspect")
        ) {
            roundedCard(context, accent, accent, 14)
        } else {
            roundedCard(context, cardBackground, cardStroke, 14)
        }
    }
}
