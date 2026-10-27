import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

// Genera datagramas sintéticos con el mismo formato que data/chunck.csv.
// Uso: java GenDatagrams <lines-241-ActiveGT.csv> <salida.csv> <numRegistros> <semilla>
// Cada viaje: un bus de una línea activa que reporta cada 20-40 s, con el odómetro (metros)
// avanzando a una velocidad de 10-35 km/h. Los viajes se reparten entre enero y junio de 2019.
public class GenDatagrams {
    public static void main(String[] a) throws Exception {
        List<Integer> lines = new ArrayList<>();
        List<String> raw = Files.readAllLines(Paths.get(a[0]));
        for (int i = 1; i < raw.size(); i++) {
            String f = raw.get(i).split(",")[0].replace("\"", "").trim();
            if (!f.isEmpty()) lines.add(Integer.parseInt(f));
        }
        long n = Long.parseLong(a[2]);
        Random r = new Random(Long.parseLong(a[3]));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime base = LocalDateTime.of(2019, 1, 1, 5, 0, 0);
        long written = 0;
        int tripId = 1;
        try (BufferedWriter w = new BufferedWriter(new FileWriter(a[1]), 1 << 20)) {
            while (written < n) {
                int line = lines.get(r.nextInt(lines.size()));
                int bus = 1000 + r.nextInt(900);
                int points = 40 + r.nextInt(41);
                double kmh = 10 + r.nextDouble() * 25;
                LocalDateTime t = base.plusDays(r.nextInt(181)).plusMinutes(r.nextInt(17 * 60));
                int odo = r.nextInt(5000);
                for (int p = 0; p < points && written < n; p++) {
                    int secs = 20 + r.nextInt(21);
                    t = t.plusSeconds(secs);
                    odo += (int) Math.round(kmh / 3.6 * secs * (0.8 + r.nextDouble() * 0.4));
                    w.write("0,28-MAY-19," + (500000 + r.nextInt(20000)) + "," + odo + ","
                            + (34000000 + r.nextInt(1000000)) + "," + (-765000000 + r.nextInt(1000000)) + ","
                            + (700 + r.nextInt(200)) + "," + line + "," + tripId + ","
                            + (6255400000L + written) + "," + t.format(fmt) + "," + bus);
                    w.newLine();
                    written++;
                }
                tripId++;
            }
        }
        System.out.println("Generados " + written + " registros en " + (tripId - 1) + " viajes -> " + a[1]);
    }
}
