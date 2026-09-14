package es.buni.hcb.adapters.knx;

import io.calimero.GroupAddress;

/** Expected project contract, exported for offline comparison against ETS. */
public record KnxBinding(String owner, String property, GroupAddress address, String dpt, Role role) {
    public enum Role { COMMAND, STATUS, SENSOR, SOFTWARE_STATE, EVENT }
    public boolean writable() { return role == Role.COMMAND || role == Role.SOFTWARE_STATE; }
    public boolean readable() { return role == Role.STATUS || role == Role.SENSOR || role == Role.SOFTWARE_STATE; }
}
