package SITM.common;

import java.io.File;

public class PathResolver {
    public static String resolve(String path) {
        File file = new File(path);
        if (file.exists()) return path;

        // Fallback: si es /opt/sitm-mio/xxx.csv -> data/xxx.csv
        if (path.startsWith("/opt/sitm-mio/")) {
            String local = "data/" + path.substring("/opt/sitm-mio/".length());
            if (new File(local).exists()) return local;
        }
        
        // Otro fallback: si solo es el nombre del archivo
        String fileName = file.getName();
        String localData = "data/" + fileName;
        if (new File(localData).exists()) return localData;

        return path;
    }
}
