package SITM;

import com.zeroc.Ice.Current;

public class ReportProviderI implements ReportProvider {

    private final DataWarehouse warehouse;

    public ReportProviderI(DataWarehouse warehouse) {
        this.warehouse = warehouse;
    }

    @Override
    public SpeedReport getAverageSpeed(int lineId, int month, int year, Current current) {
        SpeedReport report = warehouse.getAverageSpeed(lineId, month, year);
        System.out.println("Consulta velocidad - Linea: " + lineId
                + " Mes: " + month + "/" + year
                + " -> " + String.format("%.2f", report.averageSpeed) + " km/h");
        return report;
    }

    @Override
    public SpeedReport[] getMonthlyReports(int year, Current current) {
        SpeedReport[] reports = warehouse.getMonthlyReports(year);
        System.out.println("Consulta reportes mensuales para " + year
                + " -> " + reports.length + " entradas.");
        return reports;
    }
}
