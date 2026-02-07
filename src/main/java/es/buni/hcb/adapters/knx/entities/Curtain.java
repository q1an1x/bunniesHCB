package es.buni.hcb.adapters.knx.entities;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.utils.Logger;
import io.calimero.GroupAddress;
import io.calimero.process.ProcessCommunication;
import io.calimero.process.ProcessEvent;
import io.calimero.process.ProcessListener;
import io.github.hapjava.accessories.WindowCoveringAccessory;
import io.github.hapjava.accessories.optionalcharacteristic.AccessoryWithHoldPosition;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.impl.windowcovering.PositionStateEnum;

import java.util.Set;
import java.util.concurrent.*;

public class Curtain extends KNXEntity
        implements WindowCoveringAccessory, AccessoryWithHoldPosition {
    public static final boolean STATE_OPEN = false;
    public static final boolean STATE_CLOSED = true;

    public static final int POSITION_FULLY_OPEN = 100;
    public static final int POSITION_FULLY_CLOSED = 0;

    private final boolean inverted;

    protected final GroupAddress stateAddress;
    protected final GroupAddress stopValueAddress;
    protected final GroupAddress positionAddress;
    protected final GroupAddress statusPositionAddress;

    private volatile int position;
    private volatile int targetPosition;
    private volatile PositionStateEnum positionState = PositionStateEnum.STOPPED;

    private HomekitCharacteristicChangeCallback positionCallback;
    private HomekitCharacteristicChangeCallback targetPositionCallback;
    private HomekitCharacteristicChangeCallback positionStateCallback;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> timeoutTask;
    private static final long POSITION_TIMEOUT_SECONDS = 10;

    @Override
    public Set<GroupAddress> groupAddresses() {
        return Set.of(
                stateAddress,
                stopValueAddress,
                positionAddress,
                statusPositionAddress
        );
    }

    public static Curtain fromConvention(
            KNXAdapter adapter, String location, String id,
            int stateAddressMainGroup, int stateAddressMiddleGroup, int stateAddressSubGroup
    ) {
        return Curtain.fromConvention(
                adapter, location, id,
                stateAddressMainGroup, stateAddressMiddleGroup, stateAddressSubGroup,
                false
        );
    }

    public static Curtain fromConvention(
            KNXAdapter adapter, String location, String id,
            int stateAddressMainGroup, int stateAddressMiddleGroup, int stateAddressSubGroup,
            boolean inverted
    ) {
        return new Curtain(
                adapter, location, id,
                stateAddressMainGroup, stateAddressMiddleGroup, stateAddressSubGroup,
                stateAddressMainGroup, stateAddressMiddleGroup, stateAddressSubGroup + 1,
                stateAddressMainGroup, stateAddressMiddleGroup, stateAddressSubGroup + 3,
                stateAddressMainGroup, stateAddressMiddleGroup, stateAddressSubGroup + 4,
                inverted
        );
    }

    public Curtain(KNXAdapter adapter, String location, String id,
                      int stateAddressMainGroup, int stateAddressMiddleGroup, int stateAddressSubGroup,
                      int stopValueAddressMainGroup, int stopValueAddressMiddleGroup, int stopValueAddressSubGroup,
                      int positionAddressMainGroup, int positionAddressMiddleGroup, int positionAddressSubGroup,
                      int statusPositonAddressMainGroup, int statusPositionAddressMiddleGroup, int statusPositionAddressSubGroup,
                      boolean inverted
    ) {
        super(adapter, location, id);
        stateAddress = new GroupAddress(stateAddressMainGroup, stateAddressMiddleGroup, stateAddressSubGroup);
        stopValueAddress = new GroupAddress(stopValueAddressMainGroup, stopValueAddressMiddleGroup, stopValueAddressSubGroup);
        positionAddress = new GroupAddress(positionAddressMainGroup, positionAddressMiddleGroup, positionAddressSubGroup);
        statusPositionAddress =  new GroupAddress(statusPositonAddressMainGroup, statusPositionAddressMiddleGroup, statusPositionAddressSubGroup);

        this.inverted = inverted;
    }

    @Override
    public void initialize() throws Exception {
        readPosition();
        super.initialize();
    }

    @Override
    public void shutdown() {
        scheduler.shutdownNow();

        super.shutdown();
    }

    public void open() throws Exception {
        writeState(STATE_OPEN);
    }

    public void close() throws Exception {
        writeState(STATE_CLOSED);
    }

    public void setPosition(int position) throws Exception {
        this.targetPosition = position;
        updatePositionState();

        if (positionStateCallback != null) positionStateCallback.changed();
        if (targetPositionCallback != null) targetPositionCallback.changed();

        resetTimeoutWatcher();
        if (position == POSITION_FULLY_OPEN) {
            open();
        } else if (position == POSITION_FULLY_CLOSED) {
            close();
        } else {
            writePosition(position);
        }
    }

    private int invert(int value) {
        return inverted ? 100 - value : value;
    }

    private synchronized void resetTimeoutWatcher() {
        if (timeoutTask != null && !timeoutTask.isDone()) {
            timeoutTask.cancel(false);
        }

        if (positionState == PositionStateEnum.STOPPED) return;

        timeoutTask = scheduler.schedule(() -> {
            if (positionState != PositionStateEnum.STOPPED) {
                Logger.info(getNamedId() + " movement timeout reached. Forcing position to target: " + targetPosition);
                this.position = this.targetPosition;
                this.positionState = PositionStateEnum.STOPPED;

                if (positionCallback != null) positionCallback.changed();
                if (positionStateCallback != null) positionStateCallback.changed();
                publishStateChanged("position", position);
            }
        }, POSITION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void updatePositionState() {
        if (this.targetPosition > this.position) {
            this.positionState = PositionStateEnum.INCREASING;
        } else if (this.targetPosition < this.position) {
            this.positionState = PositionStateEnum.DECREASING;
        } else {
            this.positionState = PositionStateEnum.STOPPED;
        }
    }

    private void writeState(boolean state) throws Exception {
        adapter.communicator().write(stateAddress, state);
    }

    private void readPosition() throws Exception {
        targetPosition = invert(
                adapter.communicator().readUnsigned(positionAddress, ProcessCommunication.SCALING)
        );
        position = invert(
                adapter.communicator().readUnsigned(statusPositionAddress, ProcessCommunication.SCALING)
        );

        updatePositionState();
        if (targetPosition != position) {
            resetTimeoutWatcher();
        }
    }

    private void writePosition(int position) throws Exception {
        adapter.communicator().write(positionAddress, invert(position), ProcessCommunication.SCALING);
    }

    @Override
    protected boolean updateState(GroupAddress address, ProcessEvent event) throws Exception {
        if (address.equals(stopValueAddress)) {
            if (positionState != PositionStateEnum.STOPPED) {
                positionState = PositionStateEnum.STOPPED;

                if (timeoutTask != null) timeoutTask.cancel(false);
                return true;
            }
        }

        boolean changed = false;
        if (address.equals(statusPositionAddress)) {
            int newPosition = invert(
                    ProcessListener.asUnsigned(event, ProcessCommunication.SCALING)
            );

            if (position != newPosition) {
                position = newPosition;
                changed = true;
                resetTimeoutWatcher();
            }
        }

        if (address.equals(positionAddress)) {
            int newPosition = invert(
                    ProcessListener.asUnsigned(event, ProcessCommunication.SCALING)
            );

            if (targetPosition != newPosition) {
                targetPosition = newPosition;
                changed = true;
                updatePositionState();
                resetTimeoutWatcher();
            }
        }

        if (Math.abs(targetPosition - position) < 3) {
            if (positionState != PositionStateEnum.STOPPED) {
                positionState = PositionStateEnum.STOPPED;
                changed = true;
                if (timeoutTask != null) timeoutTask.cancel(false);
            }
            if (targetPosition != position) {
                targetPosition = position;
            }
        }

        return changed;
    }

    @Override
    protected void onStateUpdated(GroupAddress address, ProcessEvent event) {
        if (address.equals(statusPositionAddress)) {
            if (position == POSITION_FULLY_CLOSED || position == POSITION_FULLY_OPEN) {
                targetPosition = position;
                updatePositionState();
            }

            onPositionChanged(position);
            publishStateChanged("position", position);
        }

        if (address.equals(positionAddress)) {
            onTargetPositionChanged(targetPosition);
            publishStateChanged("targetPosition", targetPosition);
        }

        if (positionStateCallback != null) {
            positionStateCallback.changed();
        }
    }

    protected void onPositionChanged(int newValue) {
        Logger.info(getNamedId() + " position changed to " + newValue);

        if (positionCallback != null) {
            positionCallback.changed();
        }
    }

    protected void onTargetPositionChanged(int newValue) {
        Logger.info(getNamedId() + " target position changed to " + newValue);

        if (targetPositionCallback != null) {
            targetPositionCallback.changed();
        }
    }

    @Override
    public String toString() {
        return super.toString()
                + ", position: " + position + "%"
                + ", target position: " + targetPosition + "%";
    }

    @Override
    public CompletableFuture<Integer> getCurrentPosition() {
        return CompletableFuture.completedFuture(position);
    }

    @Override
    public CompletableFuture<Integer> getTargetPosition() {
        return CompletableFuture.completedFuture(targetPosition);
    }

    @Override
    public CompletableFuture<PositionStateEnum> getPositionState() {
        return CompletableFuture.completedFuture(positionState);
    }

    @Override
    public CompletableFuture<Void> setTargetPosition(int position) throws Exception {
        Logger.info("HomeKit set " + getNamedId() + " target position to " + position);

        setPosition(position);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void subscribeCurrentPosition(HomekitCharacteristicChangeCallback callback) {
        positionCallback = callback;
    }

    @Override
    public void subscribeTargetPosition(HomekitCharacteristicChangeCallback callback) {
        targetPositionCallback = callback;
    }

    @Override
    public void subscribePositionState(HomekitCharacteristicChangeCallback callback) {
        positionStateCallback = callback;
    }

    @Override
    public void unsubscribeCurrentPosition() {
        positionCallback = null;
    }

    @Override
    public void unsubscribeTargetPosition() {
        targetPositionCallback = null;
    }

    @Override
    public void unsubscribePositionState() {
        positionStateCallback = null;
    }

    @Override
    public CompletableFuture<Void> setHoldPosition(boolean hold) throws Exception {
        Logger.info("HomeKit set " + getNamedId() + " hold position to " + hold);

        if (hold) {
            adapter.communicator().write(stopValueAddress, true);
            positionState = PositionStateEnum.STOPPED;
        }
        return null;
    }
}
