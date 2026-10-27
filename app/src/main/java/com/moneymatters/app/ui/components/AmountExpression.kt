package com.moneymatters.app.ui.components

import com.moneymatters.app.core.money.Paise
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

private val TOKEN = Regex("""\d+(?:\.\d*)?|\.\d+|[+−×÷]""")
private val OPERATORS = setOf("+", "−", "×", "÷")

/** Lets the system keyboard's own symbols through: `-` is a minus, `*` and `x` multiply, `/` divides. */
private fun String.normalised() = filterNot { it == ' ' || it == ',' }
    .replace('-', '−').replace('*', '×').replace('x', '×').replace('X', '×').replace('/', '÷')

/**
 * Evaluates what the amount field holds, e.g. `120+45×2−15`. `×` and `÷` bind tighter than `+` and
 * `−`; operands may carry decimals ("2.5×80"). Works in exact decimals, never floats, and rounds the
 * result to the nearest paisa. Null for anything that doesn't parse, ends mid-operator, or divides by zero.
 */
fun evaluateAmountExpression(expression: String): Paise? {
    val text = expression.normalised()
    if (text.isBlank()) return null
    val tokens = TOKEN.findAll(text).map { it.value }.toList()
    // Anything the tokenizer skipped over is junk, not an amount.
    if (tokens.joinToString("") != text) return null
    if (tokens.size % 2 == 0) return null
    if (tokens.filterIndexed { i, _ -> i % 2 == 0 }.any { it in OPERATORS }) return null
    if (tokens.filterIndexed { i, _ -> i % 2 == 1 }.any { it !in OPERATORS }) return null

    val terms = mutableListOf(tokens[0].toBigDecimal())
    val signs = mutableListOf(1)
    var i = 1
    while (i < tokens.size) {
        val operand = tokens[i + 1].toBigDecimal()
        when (tokens[i]) {
            "+" -> { signs += 1; terms += operand }
            "−" -> { signs += -1; terms += operand }
            "×" -> terms[terms.lastIndex] = terms.last().multiply(operand)
            else -> {
                if (operand.signum() == 0) return null
                terms[terms.lastIndex] = terms.last().divide(operand, MathContext(20, RoundingMode.HALF_UP))
            }
        }
        i += 2
    }
    val total = terms.indices.fold(BigDecimal.ZERO) { acc, k -> acc + terms[k].multiply(BigDecimal(signs[k])) }
    return Paise(total.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact())
}
