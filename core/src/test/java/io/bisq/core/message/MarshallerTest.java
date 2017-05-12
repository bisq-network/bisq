package io.bisq.core.message;

import io.bisq.generated.protobuffer.PB;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertTrue;

@Slf4j
public class MarshallerTest {

    @Test
    public void getBaseEnvelopeTest() {
        PB.Ping Ping = PB.Ping.newBuilder().setNonce(100).build();
        PB.Pong Pong = PB.Pong.newBuilder().setRequestNonce(1000).build();
        PB.NetworkEnvelope envelope1 = PB.NetworkEnvelope.newBuilder().setPing(Ping).build();
        PB.NetworkEnvelope envelope2 = PB.NetworkEnvelope.newBuilder().setPong(Pong).build();
        log.info(Ping.toString());
        log.info(Pong.toString());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            envelope1.writeDelimitedTo(outputStream);
            envelope2.writeDelimitedTo(outputStream);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
            PB.NetworkEnvelope envelope3 = PB.NetworkEnvelope.parseDelimitedFrom(inputStream);
            PB.NetworkEnvelope envelope4 = PB.NetworkEnvelope.parseDelimitedFrom(inputStream);


            log.info("message: {}", envelope3.getPing());
            //log.info("peerseesd empty: '{}'",envelope3.getPong().equals(PB.NetworkEnvelope.) == "");
            assertTrue(isPing(envelope3));
            assertTrue(!isPing(envelope4));

            log.info("3 = {} 4 = {}", isPing(envelope3), isPing(envelope4));
            log.info(envelope3.toString());
            log.info(envelope4.toString());

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public boolean isPing(PB.NetworkEnvelope envelope) {
        return !envelope.getPing().getDefaultInstanceForType().equals(envelope.getPing());
    }
}
