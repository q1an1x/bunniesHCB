package es.buni.hcb.core;

import java.net.InetAddress;

public final class NetworkContext {

    private final InetAddress localAddress;

    public NetworkContext(InetAddress localAddress) {
        this.localAddress = localAddress;
    }

    public InetAddress getLocalAddress() {
        return localAddress;
    }
}
