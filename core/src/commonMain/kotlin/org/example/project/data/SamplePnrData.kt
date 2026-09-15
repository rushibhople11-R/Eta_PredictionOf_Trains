package org.example.project.data

import org.example.project.model.*

object SamplePnrData {

    val pnr1 = PnrDetails(
        pnrNumber = "2849103842",
        trainNumber = "12424",
        trainName = "Dibrugarh Rajdhani Express",
        trainType = TrainType.RAJDHANI,
        sourceStation = SampleRailwayData.NDLS,
        destinationStation = SampleRailwayData.PNBE,
        dateOfJourney = "Tomorrow, 14 Sep",
        departureTime = "16:20",
        arrivalTime = "04:15",
        passengers = listOf(
            PassengerBooking(
                passengerNumber = 1,
                name = "Rahul Sharma",
                coach = "B3",
                berth = "42",
                berthType = "Side Lower (SL)",
                status = BookingStatus.CONFIRMED
            ),
            PassengerBooking(
                passengerNumber = 2,
                name = "Pooja Sharma",
                coach = "B3",
                berth = "45",
                berthType = "Side Upper (SU)",
                status = BookingStatus.CONFIRMED
            )
        )
    )

    val pnr2 = PnrDetails(
        pnrNumber = "6510382941",
        trainNumber = "22436",
        trainName = "Vande Bharat Express",
        trainType = TrainType.VANDE_BHARAT,
        sourceStation = SampleRailwayData.NDLS,
        destinationStation = SampleRailwayData.BSB,
        dateOfJourney = "Today, 13 Sep",
        departureTime = "06:00",
        arrivalTime = "14:00",
        passengers = listOf(
            PassengerBooking(
                passengerNumber = 1,
                name = "Amit Verma",
                coach = "C4",
                berth = "21",
                berthType = "Window Seat",
                status = BookingStatus.CONFIRMED
            )
        )
    )

    val pnr3 = PnrDetails(
        pnrNumber = "8920194821",
        trainNumber = "12302",
        trainName = "Howrah Rajdhani Express",
        trainType = TrainType.RAJDHANI,
        sourceStation = SampleRailwayData.NDLS,
        destinationStation = SampleRailwayData.HWH,
        dateOfJourney = "15 Sep",
        departureTime = "16:50",
        arrivalTime = "09:55",
        passengers = listOf(
            PassengerBooking(
                passengerNumber = 1,
                name = "Sneha Sen",
                coach = "A1",
                berth = "18",
                berthType = "Lower Berth",
                status = BookingStatus.CONFIRMED
            )
        )
    )

    val pnrNashik1 = PnrDetails(
        pnrNumber = "4529184029",
        trainNumber = "22223",
        trainName = "CSMT - Sainagar Shirdi Vande Bharat",
        trainType = TrainType.VANDE_BHARAT,
        sourceStation = NashikStationData.CSMT,
        destinationStation = NashikStationData.NK,
        dateOfJourney = "Today, 13 Sep",
        departureTime = "06:20",
        arrivalTime = "09:30",
        passengers = listOf(
            PassengerBooking(
                passengerNumber = 1,
                name = "Suresh Deshmukh",
                coach = "C2",
                berth = "24",
                berthType = "Executive Chair Car (Window)",
                status = BookingStatus.CONFIRMED
            ),
            PassengerBooking(
                passengerNumber = 2,
                name = "Kavita Deshmukh",
                coach = "C2",
                berth = "25",
                berthType = "Executive Chair Car (Aisle)",
                status = BookingStatus.CONFIRMED
            )
        )
    )

    val pnrNashik2 = PnrDetails(
        pnrNumber = "8192039481",
        trainNumber = "12109",
        trainName = "Panchavati Superfast Express",
        trainType = TrainType.SUPERFAST,
        sourceStation = NashikStationData.CSMT,
        destinationStation = NashikStationData.NK,
        dateOfJourney = "Today, 13 Sep",
        departureTime = "18:15",
        arrivalTime = "21:32",
        passengers = listOf(
            PassengerBooking(
                passengerNumber = 1,
                name = "Aniket Kulkarni",
                coach = "D3",
                berth = "48",
                berthType = "Second Sitting (Window)",
                status = BookingStatus.CONFIRMED
            )
        )
    )

    val allSamplePnrs = listOf(pnrNashik1, pnrNashik2, pnr1, pnr2, pnr3)

    fun findPnr(pnrNumber: String): PnrDetails? {
        val clean = pnrNumber.trim().replace(" ", "").replace("-", "")
        return allSamplePnrs.find { it.pnrNumber == clean }
    }
}
