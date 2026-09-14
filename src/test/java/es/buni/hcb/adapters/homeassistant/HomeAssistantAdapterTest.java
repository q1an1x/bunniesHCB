package es.buni.hcb.adapters.homeassistant;

import com.google.gson.*;
import es.buni.hcb.adapters.homeassistant.entities.AndroidTV;
import es.buni.hcb.core.EntityRegistry;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.net.http.WebSocket;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class HomeAssistantAdapterTest {
    @Test void urlsAcceptCommonSchemesAndRejectEmbeddedCredentials() {
        assertEquals("wss://example.invalid:8123/api/websocket",HomeAssistantAdapter.websocketUri("https://example.invalid:8123/").toString());
        assertEquals("ws://example.invalid/api/websocket",HomeAssistantAdapter.websocketUri("example.invalid").toString());
        assertThrows(IllegalArgumentException.class,()->HomeAssistantAdapter.websocketUri("ftp://example.invalid"));
        assertThrows(IllegalArgumentException.class,()->HomeAssistantAdapter.websocketUri("http://user:secret@example.invalid"));
    }
    @Test void authenticationSnapshotServiceErrorsAndDisconnectAreHandledWithoutReplays() throws Exception {
        var registry = new EntityRegistry();
        var adapter = new HomeAssistantAdapter(registry,"example.invalid","fake-token");
        var tv = new AndroidTV(adapter,"test","tv","media_player.test"); adapter.register(tv,"remote.test");
        var sent = new ArrayList<JsonObject>();
        WebSocket socket = (WebSocket) Proxy.newProxyInstance(WebSocket.class.getClassLoader(),new Class[]{WebSocket.class},(proxy,method,args)->{
            if(method.getName().equals("sendText")){sent.add(JsonParser.parseString(args[0].toString()).getAsJsonObject());return CompletableFuture.completedFuture(proxy);}
            if(method.getName().equals("isInputClosed") || method.getName().equals("isOutputClosed"))return false;
            return null;
        });
        try {
            assertTrue(adapter.callService("media_player","turn_on","media_player.test",Map.of()).isCompletedExceptionally());
            var listener=adapter.listener(0); listener.onOpen(socket);
            adapter.handleMessage(socket,json("{'type':'auth_required'}")); assertEquals("auth",sent.getFirst().get("type").getAsString());
            adapter.handleMessage(socket,json("{'type':'auth_ok'}"));
            assertTrue(sent.stream().anyMatch(j->j.get("type").getAsString().equals("get_states")));
            int snapshot=sent.stream().filter(j->j.get("type").getAsString().equals("get_states")).findFirst().orElseThrow().get("id").getAsInt();
            adapter.handleMessage(socket,json("{'type':'result','id':"+snapshot+",'success':true,'result':[{'entity_id':'media_player.test','state':'off','attributes':{}}]}"));
            assertTrue(tv.hasKnownState()); assertFalse(tv.isOn());
            adapter.handleMessage(socket,json("{'type':'auth_ok'}"));
            int secondSnapshot=sent.getLast().get("id").getAsInt();
            adapter.handleMessage(socket,json("{'type':'event','event':{'event_type':'state_changed','data':{'entity_id':'media_player.test','new_state':{'entity_id':'media_player.test','state':'playing'}}}}"));
            adapter.handleMessage(socket,json("{'type':'result','id':"+secondSnapshot+",'success':true,'result':[{'entity_id':'media_player.test','state':'off'}]}"));
            assertTrue(tv.isPlaying(), "An older snapshot must not overwrite a live event");
            adapter.handleMessage(socket,json("{'type':'event','event':{'event_type':'state_changed','data':{'entity_id':'remote.test','new_state':{'entity_id':'remote.test','state':'off'}}}}"));
            assertTrue(tv.isPlaying(), "A remote alias is not the media player's power state");
            var result=adapter.callService("media_player","turn_on","media_player.test",Map.of());
            int command=sent.getLast().get("id").getAsInt();
            adapter.handleMessage(socket,json("{'type':'result','id':"+command+",'success':false,'error':{'code':'test'}}"));
            assertTrue(result.isCompletedExceptionally());
            var unanswered=adapter.callService("media_player","turn_on","media_player.test",Map.of());
            int count=sent.size(); listener.onClose(socket,1001,"test");
            assertTrue(unanswered.isCompletedExceptionally()); assertFalse(tv.hasKnownState()); assertEquals(count,sent.size());
        } finally { adapter.stop(); registry.getEventBus().close(); }
    }
    private static JsonObject json(String value){return JsonParser.parseString(value.replace('\'', '"')).getAsJsonObject();}
}
