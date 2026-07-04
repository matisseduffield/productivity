package com.bento.calendar

import com.bento.calendar.ui.calendar.layoutOverlaps
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlapLayoutTest {
    private data class Ev(val id: String, val s: Int, val e: Int)

    private fun layout(vararg evs: Ev) =
        layoutOverlaps(evs.toList(), { it.s }, { it.e }).associateBy { it.item.id }

    @Test
    fun `non-overlapping events each get full width`() {
        val r = layout(Ev("a", 60, 120), Ev("b", 120, 180), Ev("c", 300, 360))
        assertEquals(1, r["a"]!!.cols)
        assertEquals(1, r["b"]!!.cols)
        assertEquals(1, r["c"]!!.cols)
        assertEquals(0, r["a"]!!.col)
    }

    @Test
    fun `two simultaneous events split in half`() {
        val r = layout(Ev("a", 60, 120), Ev("b", 60, 120))
        assertEquals(2, r["a"]!!.cols)
        assertEquals(2, r["b"]!!.cols)
        assertEquals(setOf(0, 1), setOf(r["a"]!!.col, r["b"]!!.col))
    }

    @Test
    fun `chain overlap forms one cluster`() {
        // a overlaps b, b overlaps c, but a doesn't overlap c — still one
        // cluster, but only 2 columns needed (c reuses a's).
        val r = layout(Ev("a", 60, 120), Ev("b", 90, 180), Ev("c", 150, 210))
        assertEquals(2, r["a"]!!.cols)
        assertEquals(2, r["b"]!!.cols)
        assertEquals(2, r["c"]!!.cols)
        assertEquals(r["a"]!!.col, r["c"]!!.col)
    }

    @Test
    fun `three-way overlap gets three columns`() {
        val r = layout(Ev("a", 60, 200), Ev("b", 90, 200), Ev("c", 120, 200))
        assertEquals(3, r["a"]!!.cols)
        assertEquals(setOf(0, 1, 2), setOf(r["a"]!!.col, r["b"]!!.col, r["c"]!!.col))
    }

    @Test
    fun `touching events do not overlap`() {
        val r = layout(Ev("a", 60, 120), Ev("b", 120, 180))
        assertEquals(1, r["a"]!!.cols)
        assertEquals(1, r["b"]!!.cols)
    }

    @Test
    fun `separate clusters size independently`() {
        val r = layout(Ev("a", 60, 120), Ev("b", 60, 120), Ev("c", 300, 360))
        assertEquals(2, r["a"]!!.cols)
        assertEquals(1, r["c"]!!.cols)
        assertEquals(0, r["c"]!!.col)
    }

    @Test
    fun `zero-length event still claims a column`() {
        val r = layout(Ev("a", 60, 60), Ev("b", 60, 90))
        assertEquals(2, r["a"]!!.cols)
    }
}
