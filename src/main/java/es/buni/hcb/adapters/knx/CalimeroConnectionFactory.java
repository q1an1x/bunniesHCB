package es.buni.hcb.adapters.knx;

import es.buni.hcb.core.NetworkContext;
import io.calimero.CloseEvent;
import io.calimero.DetachEvent;
import io.calimero.link.KNXNetworkLinkIP;
import io.calimero.link.NetworkLinkListener;
import io.calimero.link.medium.TPSettings;
import io.calimero.process.ProcessCommunicator;
import io.calimero.process.ProcessCommunicatorImpl;
import io.calimero.process.ProcessEvent;
import io.calimero.process.ProcessListener;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class CalimeroConnectionFactory implements KnxConnectionFactory {
    private final NetworkContext network;
    private final KnxSettings settings;

    public CalimeroConnectionFactory(NetworkContext network, KnxSettings settings) {
        this.network = network;
        this.settings = settings;
    }

    @Override
    public KnxConnection connect(Consumer<ProcessEvent> receiver, Runnable disconnected) throws Exception {
        var link = KNXNetworkLinkIP.newTunnelingLink(
                new InetSocketAddress(network.getLocalAddress(), 0),
                new InetSocketAddress(settings.gateway(), settings.port()), settings.nat(), new TPSettings());
        var closing = new AtomicBoolean();
        try {
            var pc = new ProcessCommunicatorImpl(link);
            pc.responseTimeout(settings.responseTimeout());
            pc.addProcessListener(new ProcessListener() {
                @Override public void groupReadRequest(ProcessEvent e) { receiver.accept(e); }
                @Override public void groupReadResponse(ProcessEvent e) { receiver.accept(e); }
                @Override public void groupWrite(ProcessEvent e) { receiver.accept(e); }
                @Override public void detached(DetachEvent e) {
                    if (!closing.get()) disconnected.run();
                }
            });
            link.addLinkListener(new NetworkLinkListener() {
                @Override public void linkClosed(CloseEvent e) {
                    if (!closing.get()) disconnected.run();
                }
            });
            return new KnxConnection() {
                @Override public ProcessCommunicator communicator() { return pc; }
                @Override public boolean isLocalSource(io.calimero.IndividualAddress source) {
                    return source.equals(link.getKNXMedium().getDeviceAddress());
                }
                @Override public boolean isOpen() { return !closing.get() && link.isOpen(); }
                @Override public void close() {
                    if (closing.compareAndSet(false, true)) {
                        try { pc.detach(); } finally { link.close(); }
                    }
                }
            };
        } catch (Exception e) {
            closing.set(true);
            link.close();
            throw e;
        }
    }
}
