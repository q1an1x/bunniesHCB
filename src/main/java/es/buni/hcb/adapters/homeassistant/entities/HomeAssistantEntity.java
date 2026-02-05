package es.buni.hcb.adapters.homeassistant.entities;

import com.google.gson.JsonObject;
import es.buni.hcb.adapters.homeassistant.HomeAssistantAdapter;
import es.buni.hcb.core.Entity;

import java.util.Collections;
import java.util.Map;

public abstract class HomeAssistantEntity extends Entity {

    protected final HomeAssistantAdapter adapter;
    private final String haEntityId;

    public HomeAssistantEntity(HomeAssistantAdapter adapter, String location, String id, String haEntityId) {
        super(adapter, location, id);
        this.adapter = adapter;
        this.haEntityId = haEntityId;
    }

    public String getHomeAssistantEntityId() {
        return haEntityId;
    }

    public abstract void onStateChanged(JsonObject newState);

    protected void callService(String domain, String service) {
        callService(domain, service, Collections.emptyMap());
    }

    protected void callService(String domain, String service, Map<String, Object> data) {
        adapter.callService(domain, service, this.haEntityId, data);
    }
}