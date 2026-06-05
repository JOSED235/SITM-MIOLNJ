package SITM;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.Ice.Util;

public class Main {
    public static void main(String[] args) {
        String rawFile = "data/chunck.csv";
        if (args.length > 0) {
            rawFile = args[0];
        }
        String csvFile = resolvePath(rawFile);

        List<String> extArgs = new ArrayList<>();
        try (Communicator communicator = Util.initialize(args, "bus-simulator.cfg", extArgs)) {
            ObjectPrx base = communicator.propertyToProxy("EventProcessor.Proxy");
            DatagramReceiverPrx receiver = DatagramReceiverPrx.checkedCast(base);

            if (receiver == null) {
                System.err.println("Error: no se pudo conectar con el Event Processor.");
                return;
            }

            System.out.println("Iniciando simulacion desde: " + csvFile);

            try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] data = line.split(",");
                    if (data.length < 12) continue;

                    Datagram d = new Datagram();
                    try {
                        d.eventType    = Integer.parseInt(data[0].trim());
                        d.registerDate = data[1].trim();
                        d.stopId       = Integer.parseInt(data[2].trim());
                        d.odometer     = Integer.parseInt(data[3].trim());
                        d.latitude     = Integer.parseInt(data[4].trim());
                        d.longitude    = Integer.parseInt(data[5].trim());
                        d.taskId       = Integer.parseInt(data[6].trim());
                        d.lineId       = Integer.parseInt(data[7].trim());
                        d.tripId       = Integer.parseInt(data[8].trim());
                        d.unknown1     = (int) Double.parseDouble(data[9].trim());
                        d.datagramDate = data[10].trim();
                        d.busId        = Integer.parseInt(data[11].trim());

                        receiver.postDatagram(d);
                        System.out.println("Enviado datagrama del bus: " + d.busId);

                        Thread.sleep(500);
                    } catch (java.lang.Exception e) {
                        System.err.println("Error procesando linea: " + line + " - " + e.getMessage());
                    }
                }
            } catch (java.lang.Exception e) {
                System.err.println("Error leyendo CSV: " + e.getMessage());
            }
        }
    }

    private static String resolvePath(String path) {
        java.io.File file = new java.io.File(path);
        if (file.exists()) return path;
        if (path.startsWith("/opt/sitm-mio/")) {
            String local = "data/" + path.substring("/opt/sitm-mio/".length());
            if (new java.io.File(local).exists()) return local;
        }
        String fileName = file.getName();
        String localData = "data/" + fileName;
        if (new java.io.File(localData).exists()) return localData;
        return path;
    }
}
