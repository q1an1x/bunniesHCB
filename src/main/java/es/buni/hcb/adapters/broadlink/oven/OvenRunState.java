package es.buni.hcb.adapters.broadlink.oven;

public enum OvenRunState {
    STANDBY,
    READY,
    SCHEDULED,
    RUNNING,
    PAUSED,
    ACTION_REQUIRED,
    FINISHED,
    ERROR,
    ABORTING
}
