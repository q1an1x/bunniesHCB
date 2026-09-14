package es.buni.hcb.adapters.knx;

import io.calimero.process.ProcessCommunicator;

/** A single transport session. Entities and policies outlive this object. */
public interface KnxConnection extends AutoCloseable {
    ProcessCommunicator communicator();
    boolean isOpen();
    default boolean isLocalSource(io.calimero.IndividualAddress source) { return false; }
    @Override void close();
}
