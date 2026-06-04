package SITM;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.Identity;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.Ice.Util;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

public class Main extends Application {

    private Communicator communicator;
    private WebEngine webEngine;

    @Override
    public void start(Stage stage) {
        WebView webView = new WebView();
        webEngine = webView.getEngine();

        URL url = getClass().getResource("/map.html");
        if (url != null) {
            webEngine.load(url.toExternalForm());
        } else {
            System.err.println("No se encontro el archivo map.html en resources");
        }

        stage.setTitle("SITM-MIO - Monitoreo en Tiempo Real");
        stage.setScene(new Scene(webView, 1024, 768));
        stage.show();

        new Thread(this::initIce).start();
    }

    private void initIce() {
        List<String> extArgs = new ArrayList<>();
        try {
            communicator = Util.initialize(new String[]{}, "visualizer-client.cfg", extArgs);

            ObjectPrx base = communicator.propertyToProxy("EventProcessor.Proxy");
            DatagramReceiverPrx receiver = DatagramReceiverPrx.checkedCast(base);

            if (receiver == null) {
                System.err.println("Error: No se pudo conectar con el Event Processor.");
                return;
            }

            String callbackEndpoint = communicator.getProperties()
                    .getPropertyWithDefault("VisualizerCallback.Endpoints", "default -h localhost");

            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints(
                    "VisualizerCallbackAdapter", callbackEndpoint);

            MonitoringSubscriberI servant = new MonitoringSubscriberI(update -> {
                Platform.runLater(() -> {
                    String script = String.format(java.util.Locale.US,
                            "updateBus(%d, %f, %f, %d, '%s')",
                            update.busId, update.pos.latitude, update.pos.longitude,
                            update.lineId, update.timestamp);
                    webEngine.executeScript(script);
                });
            });

            ObjectPrx proxy = adapter.add(servant, new Identity("VisualizerCallback", ""));
            adapter.activate();

            MonitoringSubscriberPrx subPrx = MonitoringSubscriberPrx.uncheckedCast(proxy);
            receiver.subscribe(subPrx);

            System.out.println("Conectado y suscrito al Event Processor.");
            communicator.waitForShutdown();
        } catch (java.lang.Exception e) {
            System.err.println("Error en la comunicacion Ice: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        if (communicator != null) {
            communicator.destroy();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
