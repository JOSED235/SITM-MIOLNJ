package SITM.v1;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import SITM.common.ActiveLinesParser;
import SITM.common.CsvParser;
import SITM.common.DatagramRecord;
import SITM.common.PathResolver;
import SITM.common.SpeedEngine;
import SITM.common.SpeedResult;

public class SpeedCalculatorV1 {

    public static void main(String[] args) throws Exception {
        String rawPath = args.length > 0 ? args[0] : "/opt/sitm-mio/datagrams-MiniPilot.csv";
        String csvPath = PathResolver.resolve(rawPath);

        Set<Integer> activeLines = ActiveLinesParser.parse(ActiveLinesParser.defaultPath());

        System.out.println("=== V1 MONOLITICA ===");
        System.out.println("Archivo: " + csvPath);
        System.out.println("Rutas activas (lines-241-ActiveGT.csv): " + activeLines.size());

        long t0 = System.currentTimeMillis();

        List<DatagramRecord> data = CsvParser.parse(csvPath);
        System.out.println("Registros leidos: " + data.size());

        List<DatagramRecord> filtered = data.stream()
                .filter(d -> activeLines.contains(d.lineId))
                .collect(Collectors.toList());
        System.out.println("Registros de rutas activas: " + filtered.size());

        List<SpeedResult> results = SpeedEngine.compute(filtered);

        long elapsed = System.currentTimeMillis() - t0;

        printResults(results);
        System.out.printf("%nTiempo V1 (monolitica): %d ms%n", elapsed);
    }

    public static void printResults(List<SpeedResult> results) {
        results.sort(Comparator.comparingInt((SpeedResult r) -> r.lineId)
                .thenComparingInt(r -> r.year)
                .thenComparingInt(r -> r.month));

        System.out.printf("%n%-8s %-10s %-16s %-8s%n", "LineId", "Mes/Año", "VelProm(km/h)", "Viajes");
        System.out.println("-".repeat(46));
        for (SpeedResult r : results) {
            System.out.printf("%-8d %02d/%04d    %-16.2f %-8d%n",
                    r.lineId, r.month, r.year, r.averageSpeedKmh, r.numTrips);
        }
        System.out.println("-".repeat(46));
        System.out.println("Total combinaciones ruta-mes: " + results.size());
    }
}
