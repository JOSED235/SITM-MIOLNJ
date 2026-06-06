package SITM.common;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class ActiveLinesParser {

    public static Set<Integer> parse(String path) throws IOException {
        Set<Integer> active = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; } // saltar header
                String[] f = line.split(",");
                if (f.length < 1) continue;
                try {
                    active.add(Integer.parseInt(f[0].trim().replace("\"", "")));
                } catch (NumberFormatException ignored) {}
            }
        }
        return active;
    }

    public static String defaultPath() {
        return PathResolver.resolve("/opt/sitm-mio/lines-241-ActiveGT.csv");
    }
}
