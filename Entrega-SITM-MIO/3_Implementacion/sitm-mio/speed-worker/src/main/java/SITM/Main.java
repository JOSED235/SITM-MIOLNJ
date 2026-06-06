package SITM;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

public class Main {
    public static void main(String[] args) {
        WorkerConfig workerConfig = WorkerConfig.fromArgs(args);
        List<String> extArgs = new ArrayList<>();
        try (Communicator communicator = Util.initialize(workerConfig.iceArgs, "speed-worker.cfg", extArgs)) {
            String endpoint = workerConfig.port == null
                    ? communicator.getProperties().getProperty("SpeedWorker.Endpoints")
                    : "default -h localhost -p " + workerConfig.port;
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

    private static class WorkerConfig {
        final String port;
        final String[] iceArgs;

        private WorkerConfig(String port, String[] iceArgs) {
            this.port = port;
            this.iceArgs = iceArgs;
        }

        static WorkerConfig fromArgs(String[] args) {
            List<String> remaining = new ArrayList<>(Arrays.asList(args));
            String port = null;

            if (!remaining.isEmpty() && remaining.get(0).matches("\\d+")) {
                port = remaining.remove(0);
            } else {
                for (int i = 0; i < remaining.size(); i++) {
                    String arg = remaining.get(i);
                    if (arg.startsWith("--port=")) {
                        port = arg.substring("--port=".length());
                        remaining.remove(i);
                        break;
                    }
                }
            }

            if (port != null && !port.matches("\\d+")) {
                throw new IllegalArgumentException("Puerto invalido: " + port);
            }

            return new WorkerConfig(port, remaining.toArray(new String[0]));
        }
    }
}
