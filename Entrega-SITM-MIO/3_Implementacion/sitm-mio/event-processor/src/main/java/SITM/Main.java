package SITM;

import java.util.ArrayList;
import java.util.List;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.Ice.Util;

public class Main {
    public static void main(String[] args) {
        List<String> extArgs = new ArrayList<>();
        try (Communicator communicator = Util.initialize(args, "event-processor.cfg", extArgs)) {
            ArchiveServicePrx archiveService = null;
            try {
                ObjectPrx base = communicator.propertyToProxy("DataCenter.Proxy");
                archiveService = ArchiveServicePrx.checkedCast(base);
                if (archiveService != null) {
                    System.out.println("Conectado al Data Center.");
                }
            } catch (java.lang.Exception e) {
                System.out.println("Data Center no detectado, operando en modo solo tiempo real.");
            }

            DatagramReceiverI servant = new DatagramReceiverI(archiveService);

            String endpoint = communicator.getProperties().getProperty("EventProcessor.Endpoints");
            String identity = communicator.getProperties().getPropertyWithDefault("EventProcessor.Identity", "DatagramReceiver");

            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("DatagramReceiverAdapter", endpoint);
            adapter.add(servant, Util.stringToIdentity(identity));
            adapter.activate();

            System.out.println("Event Processor iniciado en " + endpoint);
            communicator.waitForShutdown();
        }
    }
}
