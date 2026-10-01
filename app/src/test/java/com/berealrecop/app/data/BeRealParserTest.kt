package com.berealrecop.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BeRealParserTest {

    @Test
    fun testParseMonthNumber() {
        assertEquals(9, BeRealParser.monthToNumber("septembre"))
        assertEquals(9, BeRealParser.monthToNumber("septiembre"))
        assertEquals(9, BeRealParser.monthToNumber("september"))
        assertEquals(1, BeRealParser.monthToNumber("enero"))
        assertEquals(10, BeRealParser.monthToNumber("octobre"))
        assertEquals(12, BeRealParser.monthToNumber("diciembre"))
    }

    @Test
    fun testSortOrder() {
        val item1 = BeRealItem(
            id = "1",
            uri = null,
            filePath = null,
            fileName = "bereal-1_septembre_2026_12_21_28.jpeg",
            dayNumber = 1,
            monthName = "septembre",
            year = 2026,
            hour = 12,
            minute = 21,
            second = 28
        )
        val item27Early = BeRealItem(
            id = "27_1",
            uri = null,
            filePath = null,
            fileName = "bereal-27_septembre_2026_11_43_38.jpeg",
            dayNumber = 27,
            monthName = "septembre",
            year = 2026,
            hour = 11,
            minute = 43,
            second = 38
        )
        val item27Late = BeRealItem(
            id = "27_2",
            uri = null,
            filePath = null,
            fileName = "bereal-27_septembre_2026_15_41_32.jpeg",
            dayNumber = 27,
            monthName = "septembre",
            year = 2026,
            hour = 15,
            minute = 41,
            second = 32
        )
        val item2 = BeRealItem(
            id = "2",
            uri = null,
            filePath = null,
            fileName = "bereal-2_septembre_2026_16_26_27.jpeg",
            dayNumber = 2,
            monthName = "septembre",
            year = 2026,
            hour = 16,
            minute = 26,
            second = 27
        )

        val list = listOf(item27Late, item2, item27Early, item1).sorted()
        assertEquals(listOf(item1, item2, item27Early, item27Late), list)
    }
}
