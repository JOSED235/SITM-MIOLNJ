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

        // Forzar recálculo del mapa cuando el WebView cambia de tamaño
        webView.widthProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                try {
                    webEngine.executeScript("if(typeof forceResize === 'function') forceResize();");
                } catch (Exception ignored) {}
            });
        });
        webView.heightProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                try {
                    webEngine.executeScript("if(typeof forceResize === 'function') forceResize();");
                } catch (Exception ignored) {}
            });
        });

        // Asegurar que el mapa se ajuste al cargar
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                Platform.runLater(() -> {
                    try {
                        webEngine.executeScript("if(typeof forceResize === 'function') forceResize();");
                    } catch (Exception ignored) {}
                });
            }
        });

        URL url = getClass().getResource("/map.html");
        if (url != null) {
            webEngine.load(url.toExternalForm());
        } else {
            System.err.println("No se encontro el archivo map.html en resources");
        }

        stage.setTitle("SITM-MIO - Monitoreo en Tiempo Real");
        stage.setScene(new Scene(webView, 1024, 768));

        // Disparar forceResize despues de que la ventana este completamente visible
        // para que Leaflet lea las dimensiones reales del WebView
        stage.setOnShown(e -> new Thread(() -> {
            try { Thread.sleep(400); } catch (Exception ignored) {}
            Platform.runLater(() -> {
                try {
                    webEngine.executeScript("if(typeof forceResize === 'function') forceResize();");
                } catch (Exception ignored) {}
            });
        }).start());

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
                    try {
                        String script = String.format(java.util.Locale.US,
                                "if(typeof updateBus === 'function') updateBus(%d, %f, %f, %d, '%s')",
                                update.busId, update.pos.latitude, update.pos.longitude,
                                update.lineId, update.timestamp);
                        webEngine.executeScript(script);
                    } catch (Exception ignored) {}
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
