package com.bento.calendar

import com.bento.calendar.data.diffRows
import org.junit.Assert.assertEquals
import org.junit.Test

class StorageDiffTest {
    private data class Row(val id: String, val text: String)

    @Test
    fun `editing one row rewrites only that row`() {
        val before = listOf(Row("a", "1"), Row("b", "2"), Row("c", "3"))
        val after = listOf(Row("a", "1"), Row("b", "changed"), Row("c", "3"))
        val d = diffRows(before, after) { it.id }
        assertEquals(emptyList<String>(), d.removed)
        assertEquals(listOf(IndexedValue(1, Row("b", "changed"))), d.upserts)
    }

    @Test
    fun `removed rows are deleted and later rows move up`() {
        val before = listOf(Row("a", "1"), Row("b", "2"), Row("c", "3"))
        val after = listOf(Row("a", "1"), Row("c", "3"))
        val d = diffRows(before, after) { it.id }
        assertEquals(listOf("b"), d.removed)
        assertEquals(listOf(IndexedValue(1, Row("c", "3"))), d.upserts)
    }

    @Test
    fun `new rows are inserted and unchanged lists write nothing`() {
        val rows = listOf(Row("a", "1"))
        assertEquals(emptyList<IndexedValue<Row>>(), diffRows(rows, rows) { it.id }.upserts)
        val d = diffRows(rows, rows + Row("z", "new")) { it.id }
        assertEquals(listOf(IndexedValue(1, Row("z", "new"))), d.upserts)
        assertEquals(emptyList<String>(), d.removed)
    }
}
