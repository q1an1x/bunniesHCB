package es.buni.hcb.config;

import es.buni.hcb.adapters.knx.entities.Toggle;
import es.buni.hcb.core.events.SceneRecalledEvent;
import es.buni.hcb.support.FakeKnx;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SceneAutomationManagerTest {
    private static final class ManualTimers {
        final List<Runnable> tasks = new ArrayList<>();
        final ScheduledExecutorService executor = (ScheduledExecutorService) Proxy.newProxyInstance(
                ScheduledExecutorService.class.getClassLoader(), new Class[]{ScheduledExecutorService.class}, (proxy,method,args) -> {
            if (!method.getName().equals("schedule")) throw new UnsupportedOperationException(method.getName());
            var task = new FutureTask<Void>((Runnable)args[0], null);
            tasks.add(task);
            return Proxy.newProxyInstance(ScheduledFuture.class.getClassLoader(),new Class[]{ScheduledFuture.class},(p,m,a)->switch(m.getName()){
                case "cancel" -> task.cancel((boolean)a[0]); case "isDone" -> task.isDone(); case "isCancelled" -> task.isCancelled();
                default -> throw new UnsupportedOperationException(m.getName());
            });
        });
        void fire(){for(var task:List.copyOf(tasks))task.run();tasks.clear();}
    }
    private static void flush(FakeKnx fake){fake.adapter.getRegistry().getEventBus().barrier().join();fake.adapter.getRegistry().getEventBus().barrier().join();}
    @Test void selectingAHouseModeInvalidatesOldSceneAndDelayedOffTimers() throws Exception {
        try(var fake=new FakeKnx()){
            var timers=new ManualTimers();fake.timersOverride=timers.executor;
            var toggle=new Toggle(fake.adapter,"bedroom.north","toggle.constantlighting",3,0,1);
            var button=new es.buni.hcb.config.knx.DelayedOffButton(fake.adapter,"bedroom.north","delayedoff",3,0,5,
                    java.util.Set.of(new io.calimero.GroupAddress("3/0/31")));
            fake.adapter.register(toggle);fake.adapter.register(button);
            fake.adapter.registerService(new SceneAutomationManager(fake.adapter));
            fake.adapter.configureHouseModes();fake.start();toggle.setSwitchState(true);flush(fake);
            fake.adapter.getRegistry().getEventBus().publish(SceneRecalledEvent.of("system.scenecontroller",11));flush(fake);
            button.handleBusUpdate(new io.calimero.GroupAddress("3/0/5"),fake.event("3/0/5",0x80,(byte)1));
            fake.adapter.houseModes().select(es.buni.hcb.automation.modes.HouseMode.CLEANING).get(3,TimeUnit.SECONDS);flush(fake);
            fake.writes.clear();timers.fire();flush(fake);
            assertFalse(toggle.isOn());assertTrue(fake.writes.isEmpty());
        }
    }
    @Test void manualOffCancelsAnAutomaticRestoreEvenWhenAlreadyOff() throws Exception {
        try(var fake=new FakeKnx()){
            var timers=new ManualTimers();fake.timersOverride=timers.executor;
            var toggle=new Toggle(fake.adapter,"bedroom.north","toggle.constantlighting",3,0,1);
            fake.adapter.register(toggle);fake.adapter.registerService(new SceneAutomationManager(fake.adapter,Duration.ofHours(8)));fake.start();
            toggle.setSwitchState(true);flush(fake);
            fake.adapter.getRegistry().getEventBus().publish(SceneRecalledEvent.of("system.scenecontroller",11));flush(fake);
            assertFalse(toggle.isOn());toggle.setSwitchState(false);flush(fake);fake.writes.clear();timers.fire();flush(fake);
            assertFalse(toggle.isOn());assertTrue(fake.writes.isEmpty());
        }
    }
    @Test void newerManualLightingInputCancelsAnEarlierDelayedOff() throws Exception {
        try(var fake=new FakeKnx()){
            var timers=new ManualTimers();fake.timersOverride=timers.executor;
            var button=new es.buni.hcb.config.knx.DelayedOffButton(fake.adapter,"bedroom.north","delayedoff",3,0,5,
                    java.util.Set.of(new io.calimero.GroupAddress("3/0/31")));
            fake.adapter.register(button);fake.start();
            button.handleBusUpdate(new io.calimero.GroupAddress("3/0/5"),fake.event("3/0/5",0x80,(byte)1));
            fake.adapter.manualOverrides().hold("bedroom.north",es.buni.hcb.automation.ManualOverrides.LIGHT_LEVEL);
            timers.fire();assertTrue(fake.writes.isEmpty());
        }
    }
    @Test void repeatedSceneReplacesItsTimerAndRestoresOnlyOnce() throws Exception {
        try(var fake=new FakeKnx()){
            var timers=new ManualTimers();fake.timersOverride=timers.executor;
            var toggle=new Toggle(fake.adapter,"bedroom.north","toggle.constantlighting",3,0,1);
            fake.adapter.register(toggle);fake.adapter.registerService(new SceneAutomationManager(fake.adapter,Duration.ofHours(8)));fake.start();
            toggle.setSwitchState(true);flush(fake);
            for(int i=0;i<2;i++){fake.adapter.getRegistry().getEventBus().publish(SceneRecalledEvent.of("system.scenecontroller",11));flush(fake);}
            fake.writes.clear();timers.fire();flush(fake);assertTrue(toggle.isOn());assertEquals(List.of("3/0/1=true"),fake.writes);
        }
    }
    @Test void oldSuspensionCannotRestoreIntoANewConnection() throws Exception {
        try(var fake=new FakeKnx()){
            var timers=new ManualTimers();fake.timersOverride=timers.executor;
            var toggle=new Toggle(fake.adapter,"bedroom.north","toggle.constantlighting",3,0,1);
            fake.adapter.register(toggle);fake.adapter.registerService(new SceneAutomationManager(fake.adapter,Duration.ofHours(8)));fake.start();
            toggle.setSwitchState(true);flush(fake);
            fake.adapter.getRegistry().getEventBus().publish(SceneRecalledEvent.of("system.scenecontroller",11));flush(fake);
            fake.latest.disconnect();fake.await(()->fake.connections.get()>=2&&fake.adapter.isReady());
            fake.writes.clear();timers.fire();flush(fake);assertFalse(toggle.isOn());assertTrue(fake.writes.isEmpty());
        }
    }
}
