package SITM;

import java.util.ArrayList;
import java.util.List;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

public class Main {
    public static void main(String[] args) {
        List<String> extArgs = new ArrayList<>();
        try (Communicator communicator = Util.initialize(args, "data-center.cfg", extArgs)) {
            String endpoint = communicator.getProperties().getProperty("DataCenter.Endpoints");
            String identity = communicator.getProperties().getPropertyWithDefault("DataCenter.Identity", "ArchiveService");

            DataWarehouse warehouse = new DataWarehouse();

            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("DataCenterAdapter", endpoint);
            adapter.add(new ArchiveServiceI(warehouse), Util.stringToIdentity(identity));

            String rpIdentity = communicator.getProperties().getPropertyWithDefault("ReportProvider.Identity", "ReportProvider");
            adapter.add(new ReportProviderI(warehouse), Util.stringToIdentity(rpIdentity));

            adapter.activate();
            System.out.println("Data Center iniciado - ArchiveService en " + endpoint);
            System.out.println("ReportProvider activo con identidad: " + rpIdentity);
            communicator.waitForShutdown();
        }
    }
}
