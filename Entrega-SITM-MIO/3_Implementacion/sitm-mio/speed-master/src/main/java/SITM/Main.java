package SITM;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.Ice.Util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Main {

    public static void main(String[] args) throws Exception {
        String csvPath = args.length > 0 ? args[0] : "/opt/sitm-mio/datagrams-MiniPilot.csv";

        List<String> extArgs = new ArrayList<>();
        try (Communicator communicator = Util.initialize(args, "speed-master.cfg", extArgs)) {

            // --- 1. Cargar Workers desde configuracion ---
            int workerCount = Integer.parseInt(
                    communicator.getProperties().getPropertyWithDefault("Worker.Count", "1"));

            List<SpeedWorkerPrx> workers = new ArrayList<>();
            for (int i = 0; i < workerCount; i++) {
                String proxyStr = communicator.getProperties().getProperty("Worker." + i);
                if (proxyStr == null || proxyStr.isBlank()) continue;
                ObjectPrx base = communicator.stringToProxy(proxyStr);
                SpeedWorkerPrx w = SpeedWorkerPrx.checkedCast(base);
                if (w != null) workers.add(w.ice_twoway());
            }

            if (workers.isEmpty()) {
                System.err.println("No hay workers disponibles. Verifique speed-master.cfg.");
                return;
            }
            System.out.println("=== V3 DISTRIBUIDA (Master-Worker) ===");
            System.out.println("Workers activos: " + workers.size());
            System.out.println("Archivo: " + csvPath);

            // --- 2. Leer y parsear CSV ---
            long t0 = System.currentTimeMillis();
            Map<Integer, List<Datagram>> byLine = readAndPartition(csvPath);
            System.out.println("Lineas activas encontradas: " + byLine.size());
            System.out.println("Total datagramas: " +
                    byLine.values().stream().mapToInt(List::size).sum());

            // --- 3. Distribuir particiones a workers en round-robin ---
            List<Integer> lineIds = new ArrayList<>(byLine.keySet());
            List<List<Datagram>> workerPartitions = new ArrayList<>();
            for (int i = 0; i < workers.size(); i++) workerPartitions.add(new ArrayList<>());

            for (int i = 0; i < lineIds.size(); i++) {
                workerPartitions.get(i % workers.size())
                        .addAll(byLine.get(lineIds.get(i)));
            }

            // --- 4. Enviar particiones en paralelo y recolectar resultados ---
            ExecutorService pool = Executors.newFixedThreadPool(workers.size());
            List<Future<SpeedReport[]>> futures = new ArrayList<>();

            for (int i = 0; i < workers.size(); i++) {
                final SpeedWorkerPrx worker = workers.get(i);
                final Datagram[] partition = workerPartitions.get(i).toArray(new Datagram[0]);
                System.out.println("Enviando " + partition.length + " datagramas al Worker " + i);
                futures.add(pool.submit(() -> worker.computeSpeeds(partition)));
            }

            List<SpeedReport> allResults = new ArrayList<>();
            for (Future<SpeedReport[]> f : futures) {
                for (SpeedReport r : f.get()) allResults.add(r);
            }
            pool.shutdown();

            long elapsed = System.currentTimeMillis() - t0;

            // --- 5. Mostrar resultados ---
            allResults.sort(Comparator.comparingInt((SpeedReport r) -> r.lineId)
                    .thenComparingInt(r -> r.year)
                    .thenComparingInt(r -> r.month));

            System.out.printf("%n%-8s %-10s %-16s%n", "LineId", "Mes/Año", "VelProm(km/h)");
            System.out.println("-".repeat(38));
            for (SpeedReport r : allResults) {
                System.out.printf("%-8d %02d/%04d    %-16.2f%n",
                        r.lineId, r.month, r.year, r.averageSpeed);
            }
            System.out.println("-".repeat(38));
            System.out.println("Total combinaciones ruta-mes: " + allResults.size());
            System.out.printf("%nTiempo V3 (distribuida, %d workers): %d ms%n",
                    workers.size(), elapsed);
        }
    }

    private static Map<Integer, List<Datagram>> readAndPartition(String csvPath) throws Exception {
        Map<Integer, List<Datagram>> byLine = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = line.split(",");
                if (f.length < 12) continue;
                try {
                    Datagram d = new Datagram();
                    d.eventType    = Integer.parseInt(f[0].trim());
                    d.registerDate = f[1].trim();
                    d.stopId       = Integer.parseInt(f[2].trim());
                    d.odometer     = Integer.parseInt(f[3].trim());
                    d.latitude     = Integer.parseInt(f[4].trim());
                    d.longitude    = Integer.parseInt(f[5].trim());
                    d.taskId       = Integer.parseInt(f[6].trim());
                    d.lineId       = Integer.parseInt(f[7].trim());
                    d.tripId       = Integer.parseInt(f[8].trim());
                    d.unknown1     = (int) Double.parseDouble(f[9].trim());
                    d.datagramDate = f[10].trim();
                    d.busId        = Integer.parseInt(f[11].trim());
                    byLine.computeIfAbsent(d.lineId, k -> new ArrayList<>()).add(d);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return byLine;
    }
}
