package es.buni.hcb.adapters.knx.services;

import es.buni.hcb.adapters.knx.KNXAdapter;

import es.buni.hcb.utils.Logger;
import io.calimero.GroupAddress;
import io.calimero.dptxlator.DPTXlatorTime;
import io.calimero.dptxlator.DPTXlatorDate;

import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public final class KNXTimeService implements es.buni.hcb.core.Lifecycle {
    private final KNXAdapter adapter;
    private final GroupAddress timeAddress;
    private final GroupAddress dateAddress;

    private ScheduledExecutorService scheduler;

    public KNXTimeService(KNXAdapter adapter,
                          int timeAddressMainGroup, int timeAddressMiddleGroup, int timeAddressSubGroup,
                          int dateAddressMainGroup, int dateAddressMiddleGroup, int dateAddressSubGroup) {
        this.adapter = adapter;
        timeAddress = new GroupAddress(timeAddressMainGroup, timeAddressMiddleGroup, timeAddressSubGroup);
        dateAddress = new GroupAddress(dateAddressMainGroup, dateAddressMiddleGroup, dateAddressSubGroup);
        adapter.declareCommand("system.clock", "time", timeAddress, "10.001");
        adapter.declareCommand("system.clock", "date", dateAddress, "11.001");
    }

    public synchronized void start() {
        if (scheduler != null) return;
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::publishDateTime, 0, 60, TimeUnit.SECONDS);
    }

    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }


    private void publishDateTime() {
        if (!adapter.isReady()) return;
        try {
            var now = ZonedDateTime.now(adapter.clock());

            var time = new DPTXlatorTime(DPTXlatorTime.DPT_TIMEOFDAY);
            time.setValue(
                    now.getDayOfWeek().getValue(),
                    now.getHour(),
                    now.getMinute(),
                    now.getSecond()
            );

            var date = new DPTXlatorDate(DPTXlatorDate.DPT_DATE);
            date.setValue(
                    now.getYear(),
                    now.getMonthValue(),
                    now.getDayOfMonth()
            );

            adapter.bus().write(timeAddress, time);
            adapter.bus().write(dateAddress, date);

        } catch (Exception e) {
            Logger.error("KNX time service failed", e);
        }
    }
}
