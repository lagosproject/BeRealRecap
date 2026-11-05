package com.berealrecop.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BeRealParserTest {

    @Test
    fun testParseMonthNumber() {
        // Spanish
        assertEquals(1, BeRealParser.monthToNumber("enero"))
        assertEquals(2, BeRealParser.monthToNumber("febrero"))
        assertEquals(3, BeRealParser.monthToNumber("marzo"))
        assertEquals(4, BeRealParser.monthToNumber("abril"))
        assertEquals(5, BeRealParser.monthToNumber("mayo"))
        assertEquals(6, BeRealParser.monthToNumber("junio"))
        assertEquals(7, BeRealParser.monthToNumber("julio"))
        assertEquals(8, BeRealParser.monthToNumber("agosto"))
        assertEquals(9, BeRealParser.monthToNumber("septiembre"))
        assertEquals(9, BeRealParser.monthToNumber("setiembre"))
        assertEquals(10, BeRealParser.monthToNumber("octubre"))
        assertEquals(11, BeRealParser.monthToNumber("noviembre"))
        assertEquals(12, BeRealParser.monthToNumber("diciembre"))

        // French
        assertEquals(1, BeRealParser.monthToNumber("janvier"))
        assertEquals(2, BeRealParser.monthToNumber("février"))
        assertEquals(3, BeRealParser.monthToNumber("mars"))
        assertEquals(4, BeRealParser.monthToNumber("avril"))
        assertEquals(5, BeRealParser.monthToNumber("mai"))
        assertEquals(6, BeRealParser.monthToNumber("juin"))
        assertEquals(7, BeRealParser.monthToNumber("juillet"))
        assertEquals(8, BeRealParser.monthToNumber("août"))
        assertEquals(9, BeRealParser.monthToNumber("septembre"))
        assertEquals(10, BeRealParser.monthToNumber("octobre"))
        assertEquals(11, BeRealParser.monthToNumber("novembre"))
        assertEquals(12, BeRealParser.monthToNumber("décembre"))

        // English
        assertEquals(1, BeRealParser.monthToNumber("january"))
        assertEquals(2, BeRealParser.monthToNumber("february"))
        assertEquals(3, BeRealParser.monthToNumber("march"))
        assertEquals(4, BeRealParser.monthToNumber("april"))
        assertEquals(5, BeRealParser.monthToNumber("may"))
        assertEquals(6, BeRealParser.monthToNumber("june"))
        assertEquals(7, BeRealParser.monthToNumber("july"))
        assertEquals(8, BeRealParser.monthToNumber("august"))
        assertEquals(9, BeRealParser.monthToNumber("september"))
        assertEquals(10, BeRealParser.monthToNumber("october"))
        assertEquals(11, BeRealParser.monthToNumber("november"))
        assertEquals(12, BeRealParser.monthToNumber("december"))
    }

    @Test
    fun testParseSpanishRealDeviceFileNames() {
        val f1 = BeRealParser.parseFromFileName("bereal-10_de_octubre_de_2026_14_45_59.jpeg")
        assertNotNull(f1)
        assertEquals(10, f1!!.day)
        assertEquals("octubre", f1.monthName)
        assertEquals(2026, f1.year)
        assertEquals(14, f1.hour)
        assertEquals(45, f1.minute)
        assertEquals(59, f1.second)
        assertEquals(10, BeRealParser.monthToNumber(f1.monthName))

        val f2 = BeRealParser.parseFromFileName("bereal-1_de_octubre_de_2026_22_52_33.jpeg")
        assertNotNull(f2)
        assertEquals(1, f2!!.day)
        assertEquals("octubre", f2.monthName)
        assertEquals(2026, f2.year)
        assertEquals(22, f2.hour)
        assertEquals(52, f2.minute)
        assertEquals(33, f2.second)

        val f3 = BeRealParser.parseFromFileName("bereal-7_de_octubre_de_2026_0_28_19.jpeg")
        assertNotNull(f3)
        assertEquals(7, f3!!.day)
        assertEquals("octubre", f3.monthName)
        assertEquals(2026, f3.year)
        assertEquals(0, f3.hour)
        assertEquals(28, f3.minute)
        assertEquals(19, f3.second)

        val f4 = BeRealParser.parseFromFileName("bereal-31_de_octubre_de_2026_13_05_49.jpeg")
        assertNotNull(f4)
        assertEquals(31, f4!!.day)
        assertEquals("octubre", f4.monthName)
        assertEquals(2026, f4.year)
        assertEquals(13, f4.hour)
        assertEquals(5, f4.minute)
        assertEquals(49, f4.second)
    }

    @Test
    fun testParseFrenchAndEnglishFileNames() {
        val french1 = BeRealParser.parseFromFileName("bereal-1_septembre_2026_12_21_28.jpeg")
        assertNotNull(french1)
        assertEquals(1, french1!!.day)
        assertEquals("septembre", french1.monthName)
        assertEquals(2026, french1.year)
        assertEquals(9, BeRealParser.monthToNumber(french1.monthName))

        val french2 = BeRealParser.parseFromFileName("bereal-15_août_2026_10_00_00.jpeg")
        assertNotNull(french2)
        assertEquals(15, french2!!.day)
        assertEquals("août", french2.monthName)
        assertEquals(8, BeRealParser.monthToNumber(french2.monthName))

        val englishDayFirst = BeRealParser.parseFromFileName("bereal-10_october_2026_14_45_59.jpeg")
        assertNotNull(englishDayFirst)
        assertEquals(10, englishDayFirst!!.day)
        assertEquals("october", englishDayFirst.monthName)
        assertEquals(10, BeRealParser.monthToNumber(englishDayFirst.monthName))

        val englishMonthFirst = BeRealParser.parseFromFileName("bereal-october-10-2026_14_45_59.jpeg")
        assertNotNull(englishMonthFirst)
        assertEquals(10, englishMonthFirst!!.day)
        assertEquals("october", englishMonthFirst.monthName)
        assertEquals(10, BeRealParser.monthToNumber(englishMonthFirst.monthName))
    }

    @Test
    fun testParseIsoAndNumericFileNames() {
        val iso = BeRealParser.parseFromFileName("bereal-2026-10-31-13-05-49.jpeg")
        assertNotNull(iso)
        assertEquals(31, iso!!.day)
        assertEquals("10", iso.monthName)
        assertEquals(2026, iso.year)
        assertEquals(10, BeRealParser.monthToNumber(iso.monthName))

        val numeric = BeRealParser.parseFromFileName("bereal-31-10-2026_13-05-49.jpeg")
        assertNotNull(numeric)
        assertEquals(31, numeric!!.day)
        assertEquals("10", numeric.monthName)
        assertEquals(2026, numeric.year)
        assertEquals(10, BeRealParser.monthToNumber(numeric.monthName))
    }

    @Test
    fun testSortOrder() {
        val item1 = BeRealItem(
            id = "1",
            uri = null,
            filePath = null,
            fileName = "bereal-1_de_octubre_de_2026_22_52_33.jpeg",
            dayNumber = 1,
            monthName = "octubre",
            year = 2026,
            hour = 22,
            minute = 52,
            second = 33
        )
        val item10 = BeRealItem(
            id = "10",
            uri = null,
            filePath = null,
            fileName = "bereal-10_de_octubre_de_2026_14_45_59.jpeg",
            dayNumber = 10,
            monthName = "octubre",
            year = 2026,
            hour = 14,
            minute = 45,
            second = 59
        )
        val item31 = BeRealItem(
            id = "31",
            uri = null,
            filePath = null,
            fileName = "bereal-31_de_octubre_de_2026_13_05_49.jpeg",
            dayNumber = 31,
            monthName = "octubre",
            year = 2026,
            hour = 13,
            minute = 5,
            second = 49
        )

        val list = listOf(item31, item1, item10).sorted()
        assertEquals(listOf(item1, item10, item31), list)
    }
}

