package es.buni.hcb.adapters.homeassistant;

import com.google.gson.*;
import es.buni.hcb.adapters.Adapter;
import es.buni.hcb.adapters.homeassistant.entities.HomeAssistantEntity;
import es.buni.hcb.core.Entity;
import es.buni.hcb.core.EntityRegistry;
import es.buni.hcb.utils.Logger;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Optional integration: authentication/connection failures cannot terminate KNX control. */
public class HomeAssistantAdapter extends Adapter {
    private final URI uri;
    private final String token;
    private final HttpClient client;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("ha-manager").factory());
    private final Map<String, HomeAssistantEntity> entitiesByHaId = new ConcurrentHashMap<>();
    private final Map<Integer, Pending> pending = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger();
    private final Gson gson = new Gson();
    private volatile WebSocket socket;
    private volatile boolean stopping, authenticated, authRejected;
    private long generation;
    private ScheduledFuture<?> retry;
    private CompletableFuture<WebSocket> connecting;
    private CompletableFuture<Void> outbound = CompletableFuture.completedFuture(null);
    private final Set<String> updatesDuringSnapshot = new HashSet<>();
    private boolean awaitingSnapshot;
    private record Pending(String kind, CompletableFuture<JsonObject> result, ScheduledFuture<?> timeout) { }

    public HomeAssistantAdapter(EntityRegistry registry, String host, String token) {
        super("homeassistant", registry);
        uri = websocketUri(host);
        if (token == null || token.isBlank()) throw new IllegalArgumentException("HA token required");
        this.token = token;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }
    static URI websocketUri(String host) {
        String value = host.contains("://") ? host : "ws://" + host;
        URI parsed = URI.create(value);
        String scheme = switch (parsed.getScheme().toLowerCase(Locale.ROOT)) {
            case "http", "ws" -> "ws"; case "https", "wss" -> "wss";
            default -> throw new IllegalArgumentException("HA URL must use HTTP(S) or WS(S)");
        };
        if (parsed.getHost() == null || parsed.getUserInfo() != null || parsed.getQuery() != null || parsed.getFragment() != null)
            throw new IllegalArgumentException("Invalid HA URL");
        String path = parsed.getPath();
        if (path == null || path.isEmpty() || path.equals("/")) path = "/api/websocket";
        else if (!path.equals("/api/websocket")) throw new IllegalArgumentException("HA URL must be a host or /api/websocket");
        try { return new URI(scheme, null, parsed.getHost(), parsed.getPort(), path, null, null); }
        catch (java.net.URISyntaxException e) { throw new IllegalArgumentException("Invalid HA URL"); }
    }
    public void register(HomeAssistantEntity entity) { register(entity, entity.getHomeAssistantEntityId()); }
    public void register(HomeAssistantEntity entity, String... aliases) {
        super.register(entity);
        entitiesByHaId.put(entity.getHomeAssistantEntityId(), entity);
        for (String id : aliases) entitiesByHaId.put(id, entity);
    }
    @Override public void unregister(Entity entity) {
        entitiesByHaId.values().removeIf(value -> value == entity);
        super.unregister(entity);
    }
    @Override public void start() throws Exception { connect(); super.start(); }
    @Override public synchronized void stop() throws Exception {
        if (stopping) { super.stop(); return; }
        stopping = true; authenticated = false; generation++;
        if (retry != null) retry.cancel(false);
        if (connecting != null) connecting.cancel(true);
        if (socket != null) socket.abort(); socket = null;
        failPending("HA stopped"); scheduler.shutdownNow(); client.shutdownNow(); super.stop();
    }
    private synchronized void connect() {
        if (stopping) return;
        long epoch = ++generation;
        connecting = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
                .buildAsync(uri, listener(epoch));
        connecting.whenComplete((ws, failure) -> {
            if (failure != null) disconnected(epoch, true);
            else if (stopping || epoch != generation) ws.abort();
        });
    }
    private synchronized void disconnected(long epoch, boolean reconnect) {
        if (epoch != generation) return;
        authenticated = false;
        awaitingSnapshot = false; updatesDuringSnapshot.clear();
        if (socket != null) socket.abort(); socket = null;
        failPending("HA disconnected; command outcome may be unknown and will not be replayed");
        entitiesByHaId.values().stream().distinct().forEach(HomeAssistantEntity::unavailable);
        if (reconnect && !stopping && !authRejected && (retry == null || retry.isDone())) {
            retry = scheduler.schedule(() -> { synchronized(this){retry = null;} connect(); },10,TimeUnit.SECONDS);
        }
    }
    private void failPending(String reason) {
        for (Pending request : pending.values()) { request.timeout.cancel(false); request.result.completeExceptionally(new IllegalStateException(reason)); }
        pending.clear();
    }
    private synchronized CompletableFuture<Void> send(WebSocket ws, JsonObject message) {
        // java.net.http.WebSocket allows one outstanding text send at a time.
        long epoch = generation;
        outbound = outbound.thenCompose(ignored -> {
            if (stopping || socket != ws || epoch != generation) return CompletableFuture.failedFuture(new IllegalStateException("Stale HA session"));
            return ws.sendText(gson.toJson(message),true).thenApply(sent -> null);
        });
        outbound.whenComplete((value, failure) -> { if (failure != null) disconnected(epoch, true); });
        return outbound;
    }
    private synchronized CompletableFuture<JsonObject> request(WebSocket ws, JsonObject message, String kind) {
        if (pending.size() >= 128) return CompletableFuture.failedFuture(new IllegalStateException("HA request limit reached"));
        int id = ids.incrementAndGet(); message.addProperty("id",id);
        var result = new CompletableFuture<JsonObject>();
        var timeout = scheduler.schedule(() -> {
            var expired = pending.remove(id);
            if (expired != null) expired.result.completeExceptionally(new TimeoutException("HA response timed out; request not replayed"));
        },10,TimeUnit.SECONDS);
        pending.put(id,new Pending(kind,result,timeout));
        send(ws,message);
        return result;
    }
    public synchronized CompletableFuture<Void> callService(String domain, String service, String entity, Map<String,Object> data) {
        if (!authenticated || stopping || socket == null) return CompletableFuture.failedFuture(new IllegalStateException("HA is not authenticated"));
        JsonObject request = new JsonObject(); request.addProperty("type","call_service");
        request.addProperty("domain",domain); request.addProperty("service",service);
        JsonObject target = new JsonObject(); target.addProperty("entity_id",entity); request.add("target",target);
        if (data != null && !data.isEmpty()) request.add("service_data",gson.toJsonTree(data));
        return request(socket,request,"service").thenApply(ignored -> null);
    }
    synchronized void handleMessage(WebSocket ws, JsonObject message) {
        if (stopping || socket != ws) return;
        switch (message.get("type").getAsString()) {
            case "auth_required" -> {
                var auth = new JsonObject(); auth.addProperty("type","auth"); auth.addProperty("access_token",token); send(ws,auth);
            }
            case "auth_ok" -> {
                authenticated = true;
                awaitingSnapshot = true; updatesDuringSnapshot.clear();
                var subscribe = new JsonObject(); subscribe.addProperty("type","subscribe_events"); subscribe.addProperty("event_type","state_changed");
                request(ws,subscribe,"subscribe").whenComplete((v,e) -> { if(e!=null) stateRequestFailed(ws, "subscription"); });
                var snapshot = new JsonObject(); snapshot.addProperty("type","get_states");
                request(ws,snapshot,"snapshot").whenComplete((v,e) -> { if(e!=null) stateRequestFailed(ws, "snapshot"); });
            }
            case "auth_invalid" -> {
                Logger.error("HA authentication rejected; KNX remains active");
                authRejected = true;
                disconnected(generation,false);
            }
            case "result" -> {
                Pending request = pending.remove(message.get("id").getAsInt());
                if(request == null) return; request.timeout.cancel(false);
                if(!message.get("success").getAsBoolean()) { request.result.completeExceptionally(new IllegalStateException("HA rejected " + request.kind)); return; }
                if(request.kind.equals("snapshot")) {
                    for(var state : message.getAsJsonArray("result")) {
                        JsonObject value = state.getAsJsonObject();
                        if (!updatesDuringSnapshot.contains(value.get("entity_id").getAsString())) update(value);
                    }
                    awaitingSnapshot = false; updatesDuringSnapshot.clear();
                }
                request.result.complete(message);
            }
            case "event" -> {
                var event = message.getAsJsonObject("event");
                if(!event.has("event_type") || !event.get("event_type").getAsString().equals("state_changed")) return;
                var data = event.getAsJsonObject("data");
                String id = data.get("entity_id").getAsString();
                if (awaitingSnapshot && entitiesByHaId.containsKey(id)) updatesDuringSnapshot.add(id);
                var state = data.get("new_state");
                if(state != null && !state.isJsonNull()) update(state.getAsJsonObject());
                else {
                    var entity = entitiesByHaId.get(data.get("entity_id").getAsString());
                    if(entity != null) entity.unavailable();
                }
            }
            default -> { }
        }
    }
    private synchronized void stateRequestFailed(WebSocket ws, String stage) {
        if (socket != ws || stopping) return;
        Logger.error("HA state " + stage + " failed; invalidating cached states");
        disconnected(generation, true);
    }
    private void update(JsonObject state) {
        if(!state.has("entity_id")) return;
        var entity = entitiesByHaId.get(state.get("entity_id").getAsString());
        if(entity != null) try { entity.onStateChanged(state); }
        catch(RuntimeException e) { Logger.error("Invalid HA entity state for " + entity.getNamedId()); }
    }
    WebSocket.Listener listener(long epoch) { return new Listener(epoch); }
    private final class Listener implements WebSocket.Listener {
        private final long epoch;
        private final StringBuilder buffer = new StringBuilder();
        Listener(long epoch) { this.epoch = epoch; }
        @Override public void onOpen(WebSocket ws) {
            synchronized(HomeAssistantAdapter.this) {
                if(stopping || epoch != generation) { ws.abort(); return; }
                socket = ws; authenticated = false; outbound = CompletableFuture.completedFuture(null);
            }
            ws.request(1);
        }
        @Override public CompletionStage<?> onText(WebSocket ws, CharSequence text, boolean last) {
            if(epoch != generation || stopping) return CompletableFuture.completedFuture(null);
            if(buffer.length() + text.length() > 2_097_152) { disconnected(epoch,true); return CompletableFuture.completedFuture(null); }
            buffer.append(text);
            if(last) {
                try { handleMessage(ws,JsonParser.parseString(buffer.toString()).getAsJsonObject()); }
                catch(RuntimeException e) { Logger.error("Malformed HA message"); disconnected(epoch,true); }
                finally { buffer.setLength(0); }
            }
            ws.request(1); return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<?> onClose(WebSocket ws,int code,String reason) { disconnected(epoch,true); return CompletableFuture.completedFuture(null); }
        @Override public void onError(WebSocket ws,Throwable error) { disconnected(epoch,true); }
    }
}
