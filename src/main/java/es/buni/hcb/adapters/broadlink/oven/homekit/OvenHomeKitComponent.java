package es.buni.hcb.adapters.broadlink.oven.homekit;

import es.buni.hcb.adapters.broadlink.oven.BSHOven;
import es.buni.hcb.core.Component;

public class OvenHomeKitComponent extends Component {
    protected final BSHOven oven;

    public OvenHomeKitComponent(BSHOven oven, String id) {
        super(oven, id);
        this.oven = oven;
    }
}
