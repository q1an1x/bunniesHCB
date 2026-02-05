package es.buni.hcb.adapters.homeassistant;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import es.buni.hcb.adapters.Adapter;
import es.buni.hcb.adapters.homeassistant.entities.HomeAssistantEntity;
import es.buni.hcb.core.EntityRegistry;
import es.buni.hcb.utils.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class HomeAssistantAdapter extends Adapter {

    private static final String HA_WEBSOCKET_PATH = "/api/websocket";

    private final String url;
    private final String accessToken;
    private final Gson gson;
    private final AtomicInteger messageIdCounter = new AtomicInteger(1);

    private final HttpClient httpClient;

    private final Map<String, HomeAssistantEntity> entitiesByHaId = new ConcurrentHashMap<>();

    private volatile WebSocket webSocket;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "HA-Manager");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean isStopping = false;

    public HomeAssistantAdapter(EntityRegistry registry, String host, String accessToken) {
        super("homeassistant", registry);
        String baseUrl = host.contains("://") ? host : "ws://" + host;
        this.url = baseUrl + HA_WEBSOCKET_PATH;
        this.accessToken = accessToken;
        this.gson = new Gson();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void register(HomeAssistantEntity entity) {
        super.register(entity);
        entitiesByHaId.put(entity.getHomeAssistantEntityId(), entity);
    }

    @Override
    public void unregister(es.buni.hcb.core.Entity entity) {
        if (entity instanceof HomeAssistantEntity) {
            entitiesByHaId.remove(((HomeAssistantEntity) entity).getHomeAssistantEntityId());
        }
        super.unregister(entity);
    }

    @Override
    public void start() throws Exception {
        isStopping = false;
        connect();
        super.start();
    }

    @Override
    public void stop() throws Exception {
        isStopping = true;

        executor.execute(() -> {
            if (webSocket != null) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Adapter stopping");
            }
        });

        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        super.stop();
    }

    private void connect() {
        if (isStopping) return;

        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(url), new HAListener())
                .thenAccept(ws -> {
                    this.webSocket = ws;
                    Logger.info("Connected to Home Assistant WebSocket: " + url);
                })
                .exceptionally(ex -> {
                    Logger.error("Failed to connect to HA, retrying in 10s: " + ex.getCause());
                    if (!isStopping) {
                        executor.schedule(this::connect, 10, TimeUnit.SECONDS);
                    }
                    return null;
                });
    }

    public void callService(String domain, String service, String targetEntityId, Map<String, Object> serviceData) {
        executor.execute(() -> {
            WebSocket ws = this.webSocket;
            if (ws == null) {
                Logger.warn("Dropped HA command (Disconnected): " + domain + "." + service);
                return;
            }

            try {
                JsonObject root = new JsonObject();
                root.addProperty("id", messageIdCounter.getAndIncrement());
                root.addProperty("type", "call_service");
                root.addProperty("domain", domain);
                root.addProperty("service", service);

                JsonObject target = new JsonObject();
                target.addProperty("entity_id", targetEntityId);
                root.add("target", target);

                if (serviceData != null && !serviceData.isEmpty()) {
                    JsonElement dataElement = gson.toJsonTree(serviceData);
                    root.add("service_data", dataElement);
                }

                sendJson(ws, root);
            } catch (Exception e) {
                Logger.error("Error sending HA command", e);
            }
        });
    }

    private void sendJson(WebSocket ws, JsonObject json) {
        if (ws == null) return;
        ws.sendText(gson.toJson(json), true);
    }

    private class HAListener implements WebSocket.Listener {
        StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            HomeAssistantAdapter.this.webSocket = webSocket;
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                try {
                    String fullMessage = buffer.toString();
                    buffer = new StringBuilder();
                    handleMessage(webSocket, fullMessage);
                } catch (Exception e) {
                    Logger.error("Error handling HA message", e);
                }
            }
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            Logger.warn("HA Connection closed: " + reason);
            HomeAssistantAdapter.this.webSocket = null;
            if (!isStopping) {
                executor.schedule(() -> connect(), 10, TimeUnit.SECONDS);
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            Logger.error("HA WebSocket error", error);
        }

        private void handleMessage(WebSocket ws, String text) {
            JsonObject message = JsonParser.parseString(text).getAsJsonObject();
            String type = message.get("type").getAsString();

            switch (type) {
                case "auth_required":
                    JsonObject auth = new JsonObject();
                    auth.addProperty("type", "auth");
                    auth.addProperty("access_token", accessToken);
                    sendJson(ws, auth);
                    break;

                case "auth_ok":
                    Logger.info("Home Assistant Auth Successful");
                    JsonObject sub = new JsonObject();
                    sub.addProperty("id", messageIdCounter.getAndIncrement());
                    sub.addProperty("type", "subscribe_events");
                    sub.addProperty("event_type", "state_changed");
                    sendJson(ws, sub);
                    break;

                case "event":
                    handleEvent(message.get("event").getAsJsonObject());
                    break;

                case "auth_invalid":
                    Logger.critical("Home Assistant Auth Failed. Adapter stopping.");
                    isStopping = true;
                    break;
            }
        }

        private void handleEvent(JsonObject event) {
            if ("state_changed".equals(event.get("event_type").getAsString())) {
                JsonObject data = event.get("data").getAsJsonObject();
                String entityId = data.get("entity_id").getAsString();
                JsonElement newStateElement = data.get("new_state");

                if (newStateElement != null && !newStateElement.isJsonNull()) {
                    HomeAssistantEntity entity = entitiesByHaId.get(entityId);
                    if (entity != null) {
                        try {
                            entity.onStateChanged(newStateElement.getAsJsonObject());
                        } catch (Exception e) {
                            Logger.error("Error updating HA entity " + entityId, e);
                        }
                    }
                }
            }
        }
    }
}