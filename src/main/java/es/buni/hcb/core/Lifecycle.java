package es.buni.hcb.core;

/** Owned by a runtime; registration must never start threads or perform I/O. */
public interface Lifecycle {
    void start();
    void stop();
}
