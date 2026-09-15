package org.example.project

import kotlin.test.*
import org.example.project.data.NashikStationData
import org.example.project.engine.AnnouncementLanguage
import org.example.project.engine.StationAnnouncementEngine

class StationAnnouncementTest {

    @Test
    fun testDigitTransliteration() {
        val marathiDigits = StationAnnouncementEngine.toSpokenDigits("22223", AnnouncementLanguage.MARATHI)
        assertEquals("दोन दोन दोन दोन तीन", marathiDigits)

        val hindiDigits = StationAnnouncementEngine.toSpokenDigits("12109", AnnouncementLanguage.HINDI)
        assertEquals("एक दो एक शून्य नौ", hindiDigits)

        val englishDigits = StationAnnouncementEngine.toSpokenDigits("12137", AnnouncementLanguage.ENGLISH)
        assertEquals("1 2 1 3 7", englishDigits)
    }

    @Test
    fun testTrilingualArrivalAnnouncements() {
        val train = NashikStationData.liveStationTrains.first() // Vande Bharat 22223
        val announcements = StationAnnouncementEngine.generateTrilingualSuite(train)

        assertEquals(3, announcements.size)

        val marathi = announcements[0]
        assertEquals(AnnouncementLanguage.MARATHI, marathi.language)
        assertTrue(marathi.text.contains("कृपया लक्ष द्या"))
        assertTrue(marathi.text.contains("नाशिक रोड"))

        val hindi = announcements[1]
        assertEquals(AnnouncementLanguage.HINDI, hindi.language)
        assertTrue(hindi.text.contains("कृपया ध्यान दीजिए"))

        val english = announcements[2]
        assertEquals(AnnouncementLanguage.ENGLISH, english.language)
        assertTrue(english.text.contains("May I have your attention please"))
        assertTrue(english.text.contains("Nashik Road"))
    }
}
