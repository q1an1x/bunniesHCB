package es.buni.hcb.core;

import es.buni.hcb.utils.Logger;

public class Entity {

    private final String id;
    private final String location;

    public Entity(String location, String id) {
        this.id = location + '.' + id;
        this.location = location;
    }

    public String getId() {
        return id;
    }

    public String getLocation() {
        return location;
    }

    @Override
    public String toString() {
        return getId() + ", " + getClass().getSimpleName();
    }

    public void initialize() throws Exception {
        Logger.info("Initialized entity " + this);
    };

    public void shutdown() {
    }
}
