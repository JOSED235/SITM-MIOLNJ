package SITM.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class DatagramRecord {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public int eventType, stopId, odometer, latitude, longitude,
               taskId, lineId, tripId, unknown1, busId;
    public String registerDate, datagramDate;

    public LocalDateTime parsedDate() {
        if (datagramDate == null || datagramDate.isBlank()) return null;
        try {
            return LocalDateTime.parse(datagramDate.trim(), FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
