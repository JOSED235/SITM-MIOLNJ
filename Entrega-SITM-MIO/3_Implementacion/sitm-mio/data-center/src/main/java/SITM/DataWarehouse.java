package SITM;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataWarehouse {

    private final List<Datagram> datagrams = new ArrayList<>();

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public synchronized void store(Datagram d) {
        datagrams.add(d);
    }

    public synchronized SpeedReport getAverageSpeed(int lineId, int month, int year) {
        Map<Integer, List<Datagram>> byTrip = groupByTrip(lineId, month, year);

        double totalSpeed = 0;
        int count = 0;

        for (Map.Entry<Integer, List<Datagram>> entry : byTrip.entrySet()) {
            List<Datagram> trip = entry.getValue();
            if (trip.size() < 2) continue;

            double speedKmh = calcSpeed(trip);
            if (speedKmh > 0) {
                totalSpeed += speedKmh;
                count++;
            }
        }

        SpeedReport report = new SpeedReport();
        report.lineId = lineId;
        report.month = month;
        report.year = year;
        report.averageSpeed = (count > 0) ? totalSpeed / count : 0.0;
        return report;
    }

    public synchronized SpeedReport[] getMonthlyReports(int year) {
        Map<Integer, Map<Integer, double[]>> lineMonthAcc = new HashMap<>();

        for (Datagram d : datagrams) {
            LocalDateTime dt = parseDate(d.datagramDate);
            if (dt == null || dt.getYear() != year) continue;

            int lid = d.lineId;
            int m = dt.getMonthValue();

            lineMonthAcc.computeIfAbsent(lid, k -> new HashMap<>())
                        .computeIfAbsent(m, k -> new double[]{0, 0});
        }

        List<SpeedReport> results = new ArrayList<>();
        for (int lineId : lineMonthAcc.keySet()) {
            for (int month : lineMonthAcc.get(lineId).keySet()) {
                results.add(getAverageSpeed(lineId, month, year));
            }
        }

        return results.toArray(new SpeedReport[0]);
    }

    private Map<Integer, List<Datagram>> groupByTrip(int lineId, int month, int year) {
        Map<Integer, List<Datagram>> byTrip = new HashMap<>();
        for (Datagram d : datagrams) {
            if (d.lineId != lineId) continue;
            LocalDateTime dt = parseDate(d.datagramDate);
            if (dt == null) continue;
            if (dt.getMonthValue() != month || dt.getYear() != year) continue;

            byTrip.computeIfAbsent(d.tripId, k -> new ArrayList<>()).add(d);
        }
        return byTrip;
    }

    private double calcSpeed(List<Datagram> trip) {
        if (trip.size() < 2) return 0;

        trip.sort(java.util.Comparator.comparing(d -> parseDate(d.datagramDate),
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));

        double totalDistance = 0;
        long totalSeconds = 0;

        for (int i = 0; i < trip.size() - 1; i++) {
            Datagram a = trip.get(i);
            Datagram b = trip.get(i + 1);

            LocalDateTime ta = parseDate(a.datagramDate);
            LocalDateTime tb = parseDate(b.datagramDate);
            if (ta == null || tb == null) continue;

            long secs = java.time.Duration.between(ta, tb).getSeconds();
            if (secs <= 0) continue;

            int dist = b.odometer - a.odometer;
            if (dist > 0) {
                double kmh = (dist / (double) secs) * 3.6;
                if (kmh <= 120) {
                    totalDistance += dist;
                    totalSeconds += secs;
                }
            }
        }

        if (totalSeconds == 0) return 0;
        return (totalDistance / (double) totalSeconds) * 3.6;
    }

    private LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDateTime.parse(raw.trim().toUpperCase(), FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
