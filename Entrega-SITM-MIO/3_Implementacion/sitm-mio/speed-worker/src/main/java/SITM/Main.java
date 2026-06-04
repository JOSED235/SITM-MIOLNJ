package SITM;

import java.util.ArrayList;
import java.util.List;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

public class Main {
    public static void main(String[] args) {
        List<String> extArgs = new ArrayList<>();
        try (Communicator communicator = Util.initialize(args, "speed-worker.cfg", extArgs)) {
            String endpoint = communicator.getProperties().getProperty("SpeedWorker.Endpoints");
            String identity = communicator.getProperties()
                    .getPropertyWithDefault("SpeedWorker.Identity", "SpeedWorker");

            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints(
                    "SpeedWorkerAdapter", endpoint);
            adapter.add(new SpeedWorkerI(), Util.stringToIdentity(identity));
            adapter.activate();

            System.out.println("Speed Worker iniciado en " + endpoint + " [identidad: " + identity + "]");
            communicator.waitForShutdown();
        }
    }
}
