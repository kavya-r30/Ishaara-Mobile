package com.ishaara.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryCodecTest {
    private fun entry(n: Int) = HistoryEntry("c$n", "s", true, n.toLong(), listOf(HistoryTask("t", "DONE")))

    @Test
    fun roundTrips() {
        val list = listOf(entry(1), entry(2))
        assertEquals(list, HistoryCodec.decode(HistoryCodec.encode(list)))
    }

    @Test
    fun badDataGivesEmptyList() {
        assertEquals(emptyList<HistoryEntry>(), HistoryCodec.decode("garbage"))
        assertEquals(emptyList<HistoryEntry>(), HistoryCodec.decode(null))
    }

    @Test
    fun addPutsNewestFirstAndCaps() {
        var list = emptyList<HistoryEntry>()
        (1..35).forEach { list = HistoryCodec.add(list, entry(it)) }
        assertEquals(30, list.size)
        assertEquals("c35", list.first().command)
    }
}
