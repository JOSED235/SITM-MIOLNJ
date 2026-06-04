package SITM.common;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CsvParser {

    public static List<DatagramRecord> parse(String path) throws IOException {
        List<DatagramRecord> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = line.split(",");
                if (f.length < 12) continue;
                try {
                    DatagramRecord r = new DatagramRecord();
                    r.eventType    = Integer.parseInt(f[0].trim());
                    r.registerDate = f[1].trim();
                    r.stopId       = Integer.parseInt(f[2].trim());
                    r.odometer     = Integer.parseInt(f[3].trim());
                    r.latitude     = Integer.parseInt(f[4].trim());
                    r.longitude    = Integer.parseInt(f[5].trim());
                    r.taskId       = Integer.parseInt(f[6].trim());
                    r.lineId       = Integer.parseInt(f[7].trim());
                    r.tripId       = Integer.parseInt(f[8].trim());
                    r.unknown1     = (int) Double.parseDouble(f[9].trim());
                    r.datagramDate = f[10].trim();
                    r.busId        = Integer.parseInt(f[11].trim());
                    list.add(r);
                } catch (NumberFormatException ignored) {
                    // skip malformed rows
                }
            }
        }
        return list;
    }
}
