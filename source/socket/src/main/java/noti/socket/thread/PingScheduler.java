package noti.socket.thread;

import java.net.URI;
import java.util.Timer;
import java.util.TimerTask;

import noti.common.utils.ConfigurationService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class PingScheduler {
    private static final Logger LOG = LogManager.getLogger(PingScheduler.class);
    private static final long PING_INTERVAL = 2 * 60 * 1000; // 2 minutes
    private Timer timer;
    private WebSocketClient wsClient;

    public void start() {
        try {
            ConfigurationService config = new ConfigurationService("configuration.properties");
            wsClient = new WebSocketClient(new URI(config.getConfig("URL", "app.url"))) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    LOG.info("Connected to WebSocket server");
                    startPingTimer();
                }

                @Override
                public void onMessage(String message) {
                    LOG.info("Received message: " + message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    LOG.info("Connection closed: " + reason);
                    reconnectWithDelay();
                }

                @Override
                public void onError(Exception ex) {
                    LOG.error("Error occurred: " + ex.getMessage());
                    reconnectWithDelay();
                }
            };

            wsClient.connect();

        } catch (Exception e) {
            LOG.error("Error starting ping client: " + e.getMessage());
        }
    }

    private void startPingTimer() {
        if (timer != null) {
            timer.cancel();
        }
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                sendPing();
            }
        }, 0, PING_INTERVAL);
    }

    private void reconnectWithDelay() {
        try {
            Thread.sleep(5000); // Wait 5 seconds before reconnecting
            if (!wsClient.isOpen()) {
                wsClient.reconnect();
            }
        } catch (InterruptedException e) {
            LOG.error("Reconnection interrupted: " + e.getMessage());
        }
    }

    private void sendPing() {
        if (wsClient.isOpen()) {
            LOG.error("ACTIVE PING CALLED");
            wsClient.send("{\"cmd\":\"ACTIVE_PING\",\"app\":\"CLIENT_APP\"}");
        } else {
            LOG.warn("WebSocket not connected, attempting to reconnect...");
            wsClient.reconnect();
        }
    }

    public void stop() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (wsClient != null) {
            wsClient.close();
        }
    }
}