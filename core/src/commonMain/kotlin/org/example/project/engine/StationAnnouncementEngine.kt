package org.example.project.engine

import org.example.project.model.StationTrainEntry
import org.example.project.model.TrainType

enum class AnnouncementLanguage(val label: String, val nativeName: String, val icon: String) {
    MARATHI("Marathi", "मराठी", "🚩"),
    HINDI("Hindi", "हिंदी", "🇮🇳"),
    ENGLISH("English", "English", "🌐")
}

data class StationAnnouncement(
    val language: AnnouncementLanguage,
    val trainNumber: String,
    val text: String,
    val phoneticCallout: String,
    val platformNumber: String,
    val chimeSequence: List<String> = listOf("Ding", "Dong", "Ding", "Dong"),
    val timestampEpochMillis: Long = 0L
)

/**
 * Indian Railways Multi-Lingual Public Address & Announcement Engine
 * Specially tuned for Nashik Road (NK) station broadcasts in Marathi, Hindi, and English.
 */
object StationAnnouncementEngine {

    private val marathiDigits = mapOf(
        '0' to "शून्य", '1' to "एक", '2' to "दोन", '3' to "तीन", '4' to "चार",
        '5' to "पाच", '6' to "सहा", '7' to "सात", '8' to "आठ", '9' to "नऊ"
    )

    private val hindiDigits = mapOf(
        '0' to "शून्य", '1' to "एक", '2' to "दो", '3' to "तीन", '4' to "चार",
        '5' to "पाँच", '6' to "छह", '7' to "सात", '8' to "आठ", '9' to "नौ"
    )

    fun toSpokenDigits(numberStr: String, language: AnnouncementLanguage): String {
        return when (language) {
            AnnouncementLanguage.MARATHI -> numberStr.map { marathiDigits[it] ?: it.toString() }.joinToString(" ")
            AnnouncementLanguage.HINDI -> numberStr.map { hindiDigits[it] ?: it.toString() }.joinToString(" ")
            AnnouncementLanguage.ENGLISH -> numberStr.toList().joinToString(" ")
        }
    }

    /**
     * Generates an authentic Indian Railways station announcement for an arriving/delayed train at Nashik Road.
     */
    fun generateArrivalAnnouncement(
        train: StationTrainEntry,
        language: AnnouncementLanguage = AnnouncementLanguage.MARATHI
    ): StationAnnouncement {
        val spokenNumber = toSpokenDigits(train.trainNumber, language)
        val pfNumber = train.platformNumber.replace("Pf ", "").trim()

        val text = when (language) {
            AnnouncementLanguage.MARATHI -> {
                if (train.delayMinutes <= 5) {
                    "कृपया लक्ष द्या! गाडी क्रमांक $spokenNumber, ${train.trainName}, " +
                    "${train.origin.name} येथून येणारी आणि ${train.destination.name} कडे जाणारी, " +
                    "काही वेळातच नाशिक रोड स्थानकाच्या प्लॅटफॉर्म क्रमांक $pfNumber वर येत आहे."
                } else {
                    "कृपया लक्ष द्या! गाडी क्रमांक $spokenNumber, ${train.trainName}, " +
                    "${train.origin.name} येथून येणारी, थळ घाट मार्गावरील सुरक्षिततेमुळे सुमारे ${train.delayMinutes} मिनिटे विलंबाने धावत आहे. " +
                    "ही गाडी अंदाजे ${train.predictedArrivalEta} वाजता प्लॅटफॉर्म क्रमांक $pfNumber वर येईल. प्रवाशांच्या गैरसोयीबद्दल आम्ही दिलगीर आहोत."
                }
            }
            AnnouncementLanguage.HINDI -> {
                if (train.delayMinutes <= 5) {
                    "कृपया ध्यान दीजिए! गाड़ी संख्या $spokenNumber, ${train.trainName}, " +
                    "${train.origin.name} से चलकर ${train.destination.name} को जाने वाली, " +
                    "कुछ ही समय में नाशिक रोड स्टेशन के प्लेटफॉर्म संख्या $pfNumber पर आ रही है."
                } else {
                    "कृपया ध्यान दीजिए! गाड़ी संख्या $spokenNumber, ${train.trainName}, " +
                    "${train.origin.name} से आने वाली, थल घाट परिचालन के कारण अपने निर्धारित समय से ${train.delayMinutes} मिनट की देरी से चल रही है. " +
                    "इसके अब ${train.predictedArrivalEta} बजे प्लेटफॉर्म संख्या $pfNumber पर आने की संभावना है. असुविधा के लिए हमें खेद है."
                }
            }
            AnnouncementLanguage.ENGLISH -> {
                if (train.delayMinutes <= 5) {
                    "May I have your attention please! Train number $spokenNumber, ${train.trainName}, " +
                    "from ${train.origin.name} to ${train.destination.name}, " +
                    "is arriving shortly on Platform Number $pfNumber at Nashik Road."
                } else {
                    "May I have your attention please! Train number $spokenNumber, ${train.trainName}, " +
                    "from ${train.origin.name}, is running late by ${train.delayMinutes} minutes due to Thal Ghat mountain banking operations. " +
                    "It is now expected to arrive at ${train.predictedArrivalEta} hours on Platform Number $pfNumber. Inconvenience caused is deeply regretted."
                }
            }
        }

        return StationAnnouncement(
            language = language,
            trainNumber = train.trainNumber,
            text = text,
            phoneticCallout = spokenNumber,
            platformNumber = train.platformNumber,
            timestampEpochMillis = System.currentTimeMillis()
        )
    }

    /**
     * Generates all three announcements (Marathi, Hindi, English) in sequence.
     */
    fun generateTrilingualSuite(train: StationTrainEntry): List<StationAnnouncement> {
        return listOf(
            generateArrivalAnnouncement(train, AnnouncementLanguage.MARATHI),
            generateArrivalAnnouncement(train, AnnouncementLanguage.HINDI),
            generateArrivalAnnouncement(train, AnnouncementLanguage.ENGLISH)
        )
    }
}
