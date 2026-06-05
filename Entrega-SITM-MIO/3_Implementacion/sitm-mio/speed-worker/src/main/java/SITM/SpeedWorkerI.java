package SITM;

import com.zeroc.Ice.Current;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpeedWorkerI implements SpeedWorker {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public SpeedReport[] computeSpeeds(Datagram[] partition, Current current) {
        System.out.println("[Worker] Recibida particion con " + partition.length + " datagramas.");

        // Agrupar por lineId -> año-mes -> tripId
        Map<Integer, Map<String, Map<Integer, List<Datagram>>>> grouped = new HashMap<>();
        for (Datagram d : partition) {
            LocalDateTime dt = parseDate(d.datagramDate);
            if (dt == null) continue;
            String key = dt.getYear() + "-" + dt.getMonthValue();

            grouped
                .computeIfAbsent(d.lineId, k -> new HashMap<>())
                .computeIfAbsent(key,      k -> new HashMap<>())
                .computeIfAbsent(d.tripId, k -> new ArrayList<>())
                .add(d);
        }

        List<SpeedReport> results = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, Map<Integer, List<Datagram>>>> lineEntry : grouped.entrySet()) {
            int lineId = lineEntry.getKey();
            for (Map.Entry<String, Map<Integer, List<Datagram>>> monthEntry : lineEntry.getValue().entrySet()) {
                String[] parts = monthEntry.getKey().split("-");
                int year  = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);

                List<Double> tripSpeeds = new ArrayList<>();
                for (List<Datagram> trip : monthEntry.getValue().values()) {
                    Double spd = computeTripSpeed(trip);
                    if (spd != null) tripSpeeds.add(spd);
                }
                if (tripSpeeds.isEmpty()) continue;

                SpeedReport r = new SpeedReport();
                r.lineId       = lineId;
                r.month        = month;
                r.year         = year;
                r.averageSpeed = tripSpeeds.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                results.add(r);
                System.out.printf("[Worker] Linea %d %02d/%04d -> %.2f km/h (%d viajes)%n",
                        lineId, month, year, r.averageSpeed, tripSpeeds.size());
            }
        }

        System.out.println("[Worker] Particion procesada. Resultados: " + results.size());
        return results.toArray(new SpeedReport[0]);
    }

    private Double computeTripSpeed(List<Datagram> trip) {
        if (trip.size() < 2) return null;

        trip.sort(Comparator.comparing(d -> parseDate(d.datagramDate),
                Comparator.nullsLast(Comparator.naturalOrder())));

        double totalDistance = 0;
        long totalSeconds = 0;

        for (int i = 0; i < trip.size() - 1; i++) {
            Datagram a = trip.get(i);
            Datagram b = trip.get(i + 1);

            LocalDateTime ta = parseDate(a.datagramDate);
            LocalDateTime tb = parseDate(b.datagramDate);
            if (ta == null || tb == null) continue;

            long secs = Duration.between(ta, tb).getSeconds();
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

        if (totalSeconds == 0) return null;
        return (totalDistance / (double) totalSeconds) * 3.6;
    }

    private LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDateTime.parse(raw.trim(), FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
