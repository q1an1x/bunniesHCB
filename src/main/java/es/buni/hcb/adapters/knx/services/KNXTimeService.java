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


public final class KNXTimeService {
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
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::publishDateTime, 0, 60, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdown();
    }


    private void publishDateTime() {
        try {
            var now = ZonedDateTime.now();

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

            adapter.communicator().write(timeAddress, time);
            adapter.communicator().write(dateAddress, date);

        } catch (Exception e) {
            Logger.error("KNX time service failed", e);
        }
    }
}
