package es.buni.hcb.automation;

import es.buni.hcb.core.Lifecycle;

public interface LightingPolicy extends Lifecycle {
    void update();
}
