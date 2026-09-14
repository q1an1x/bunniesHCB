package es.buni.hcb.adapters.broadlink;
import es.buni.hcb.adapters.broadlink.oven.OvenCommandBuilder;
import es.buni.hcb.adapters.broadlink.oven.OvenMode;
import org.junit.jupiter.api.Test;
import java.nio.*;
import static org.junit.jupiter.api.Assertions.*;
class BroadlinkPayloadTest {
    @Test void malformedLengthsAreRejectedBeforeDecoding() {
        assertThrows(IllegalArgumentException.class,()->BroadlinkAdapter.decodeJsonPayload(new byte[4]));
        for(int length:new int[]{-1,0,Integer.MAX_VALUE}){
            byte[] payload=new byte[32]; ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).putInt(10,length);
            assertThrows(IllegalArgumentException.class,()->BroadlinkAdapter.decodeJsonPayload(payload));
        }
    }
    @Test void buildersDoNotExposeMutablePendingCommands() {
        var builder = new OvenCommandBuilder(); assertEquals(OvenMode.NONE,builder.getMode());
        assertThrows(IllegalArgumentException.class,()->builder.setDuration(-1));
        builder.setDuration(60); var first=builder.build(); first.addProperty("mu_heat_tm",999);
        assertEquals(60,builder.build().get("mu_heat_tm").getAsInt());
    }
}
