package hr.divanguz.homepanel.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.kxml2.io.KXmlParser

class DhmzWarningsTest {
    @Before fun jvmParser() {
        XmlParsers.factory = { KXmlParser() }
    }

    private fun info(lang: String, level: String, type: String, area: String) = """
        <info><language>$lang</language><event>x</event><expires>2030-10-19 10:00:00+02:00</expires>
        <parameter><valueName>awareness_level</valueName><value>$level</value></parameter>
        <parameter><valueName>awareness_type</valueName><value>$type</value></parameter>
        <area><areaDesc>$area</areaDesc><geocode><valueName>EMMA_ID</valueName><value>HR011</value></geocode></area></info>"""

    @Test fun keepsCroatianBlocksWithLevelAndType() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">""" +
            info("hr", "3; orange; Severe", "1; Wind", "Istarska") +
            info("en", "3; orange; Severe", "1; Wind", "Istria") +
            info("hr", "2; yellow; Moderate", "2; snow-ice", "Ličko-senjska") + "</alert>"
        val alerts = DhmzWarnings.parse(xml)
        assertEquals(2, alerts.size)
        assertEquals(3, alerts[0].level)
        assertEquals("Wind", alerts[0].type)
        assertEquals("Istarska", alerts[0].area)
        assertEquals("Snow ice", alerts[1].type)
    }

    @Test fun matchesCountyNames() {
        assertTrue(DhmzWarnings.matches("Istarska županija", "Istarska"))
        assertTrue(DhmzWarnings.matches("Primorsko-goranska županija", "Primorsko-goranska"))
        assertFalse(DhmzWarnings.matches("Ličko-senjska županija", "Istarska"))
    }
}
