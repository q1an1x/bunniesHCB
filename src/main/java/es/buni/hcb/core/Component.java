package es.buni.hcb.core;

public abstract class Component extends Entity {
    public Component(Entity entity, String id) {
        super(entity.getRegistry(), entity.getLocation(), entity.getIId() + "." + id);
    }
}
