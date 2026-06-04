package SITM;

import SITM.v1.SpeedCalculatorV1;
import SITM.v2.SpeedCalculatorV2;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Uso: java -jar speed-calculator.jar <v1|v2> [ruta/al/archivo.csv]");
            System.out.println("  v1  -  Solucion monolitica secuencial");
            System.out.println("  v2  -  Solucion concurrente (multi-hilo)");
            System.exit(1);
        }

        String version = args[0].toLowerCase();
        String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);

        switch (version) {
            case "v1": SpeedCalculatorV1.main(rest); break;
            case "v2": SpeedCalculatorV2.main(rest); break;
            default:
                System.err.println("Version desconocida: " + version + ". Use v1 o v2.");
                System.exit(1);
        }
    }
}
