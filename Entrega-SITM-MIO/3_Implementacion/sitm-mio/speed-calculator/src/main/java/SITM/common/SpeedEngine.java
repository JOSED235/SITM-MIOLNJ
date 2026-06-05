package SITM.common;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SpeedEngine {

    public static List<SpeedResult> compute(List<DatagramRecord> data) {
        Map<Integer, List<DatagramRecord>> byLine =
                data.stream().collect(Collectors.groupingBy(d -> d.lineId));

        List<SpeedResult> results = new ArrayList<>();
        for (Map.Entry<Integer, List<DatagramRecord>> e : byLine.entrySet()) {
            results.addAll(computeForLine(e.getKey(), e.getValue()));
        }
        return results;
    }

    public static List<SpeedResult> computeForLine(int lineId, List<DatagramRecord> lineData) {
        Map<String, List<DatagramRecord>> byMonth = lineData.stream()
                .filter(d -> d.parsedDate() != null)
                .collect(Collectors.groupingBy(d -> {
                    LocalDateTime dt = d.parsedDate();
                    return dt.getYear() + "-" + dt.getMonthValue();
                }));

        List<SpeedResult> results = new ArrayList<>();
        for (Map.Entry<String, List<DatagramRecord>> e : byMonth.entrySet()) {
            String[] parts = e.getKey().split("-");
            int year  = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            SpeedResult r = computeForLineMonth(lineId, year, month, e.getValue());
            if (r != null) results.add(r);
        }
        return results;
    }

    private static SpeedResult computeForLineMonth(int lineId, int year, int month,
                                                    List<DatagramRecord> data) {
        Map<Integer, List<DatagramRecord>> byTrip =
                data.stream().collect(Collectors.groupingBy(d -> d.tripId));

        List<Double> tripSpeeds = new ArrayList<>();
        for (List<DatagramRecord> trip : byTrip.values()) {
            Double spd = tripSpeed(trip);
            if (spd != null) tripSpeeds.add(spd);
        }
        if (tripSpeeds.isEmpty()) return null;

        SpeedResult r = new SpeedResult();
        r.lineId          = lineId;
        r.year            = year;
        r.month           = month;
        r.numTrips        = tripSpeeds.size();
        r.averageSpeedKmh = tripSpeeds.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return r;
    }

    private static Double tripSpeed(List<DatagramRecord> trip) {
        if (trip.size() < 2) return null;

        trip.sort(Comparator.comparing(DatagramRecord::parsedDate,
                Comparator.nullsLast(Comparator.naturalOrder())));

        double totalDistance = 0;
        long totalSeconds = 0;

        for (int i = 0; i < trip.size() - 1; i++) {
            DatagramRecord a = trip.get(i);
            DatagramRecord b = trip.get(i + 1);

            LocalDateTime ta = a.parsedDate();
            LocalDateTime tb = b.parsedDate();
            if (ta == null || tb == null) continue;

            long secs = Duration.between(ta, tb).getSeconds();
            if (secs <= 0) continue;

            int dist = b.odometer - a.odometer;
            // Descartar si el odómetro retrocedió o si la velocidad es absurda (> 120km/h)
            if (dist > 0) {
                double kmh = (dist / (double) secs) * 3.6;
                if (kmh <= 120) {
                    totalDistance += dist;
                    totalSeconds += secs;
                }
            }
        }

        if (totalSeconds == 0) return null;
        return (totalDistance / (double) totalSeconds) * 3.6;
    }
}
