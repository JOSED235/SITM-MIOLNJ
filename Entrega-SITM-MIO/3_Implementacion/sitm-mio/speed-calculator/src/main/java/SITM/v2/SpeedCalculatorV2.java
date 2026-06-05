package SITM.v2;

import SITM.common.CsvParser;
import SITM.common.DatagramRecord;
import SITM.common.PathResolver;
import SITM.common.SpeedEngine;
import SITM.common.SpeedResult;
import SITM.v1.SpeedCalculatorV1;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class SpeedCalculatorV2 {

    public static void main(String[] args) throws Exception {
        String rawPath = args.length > 0 ? args[0] : "/opt/sitm-mio/datagrams-MiniPilot.csv";
        String csvPath = PathResolver.resolve(rawPath);
        
        int numThreads = Runtime.getRuntime().availableProcessors();
        System.out.println("=== V2 CONCURRENTE ===");
        System.out.println("Archivo: " + csvPath);
        System.out.println("Hilos disponibles: " + numThreads);

        long t0 = System.currentTimeMillis();

        List<DatagramRecord> data = CsvParser.parse(csvPath);
        System.out.println("Registros leidos: " + data.size());

        // Particionar por lineId: cada linea se procesa en un hilo distinto
        Map<Integer, List<DatagramRecord>> byLine =
                data.stream().collect(Collectors.groupingBy(d -> d.lineId));

        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        List<Future<List<SpeedResult>>> futures = new ArrayList<>();

        for (Map.Entry<Integer, List<DatagramRecord>> entry : byLine.entrySet()) {
            int lineId = entry.getKey();
            List<DatagramRecord> lineData = entry.getValue();
            futures.add(pool.submit(() -> SpeedEngine.computeForLine(lineId, lineData)));
        }

        List<SpeedResult> results = new ArrayList<>();
        for (Future<List<SpeedResult>> f : futures) {
            results.addAll(f.get());
        }
        pool.shutdown();

        long elapsed = System.currentTimeMillis() - t0;

        SpeedCalculatorV1.printResults(results);
        System.out.printf("%nTiempo V2 (concurrente, %d hilos): %d ms%n", numThreads, elapsed);
    }
}
