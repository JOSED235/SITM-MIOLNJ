package SITM;

import com.zeroc.Ice.Current;

public class ArchiveServiceI implements ArchiveService {

    private final DataWarehouse warehouse;

    public ArchiveServiceI(DataWarehouse warehouse) {
        this.warehouse = warehouse;
    }

    @Override
    public void archiveDatagram(Datagram data, Current current) {
        warehouse.store(data);
        System.out.println("Datagrama almacenado - Bus: " + data.busId
                + " Linea: " + data.lineId
                + " Viaje: " + data.tripId
                + " Fecha: " + data.datagramDate);
    }
}
