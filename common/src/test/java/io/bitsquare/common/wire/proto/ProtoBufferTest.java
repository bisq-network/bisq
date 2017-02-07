package io.bitsquare.common.wire.proto;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Slf4j
public class ProtoBufferTest {

    @Test
    public void protoTest() {
        Messages.Ping Ping = Messages.Ping.newBuilder().setNonce(100).build();
        Messages.Pong Pong = Messages.Pong.newBuilder().setRequestNonce(1000).build();
        Messages.Envelope envelope1 = Messages.Envelope.newBuilder().setPing(Ping).build();
        Messages.Envelope envelope2 = Messages.Envelope.newBuilder().setPong(Pong).build();
        log.info(Ping.toString());
        log.info(Pong.toString());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            envelope1.writeDelimitedTo(outputStream);
            envelope2.writeDelimitedTo(outputStream);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
            Messages.Envelope envelope3 = Messages.Envelope.parseDelimitedFrom(inputStream);
            Messages.Envelope envelope4 = Messages.Envelope.parseDelimitedFrom(inputStream);


            log.info("message: {}", envelope3.getPing());
            //log.info("peerseesd empty: '{}'",envelope3.getPong().equals(Messages.Envelope.) == "");
            assertTrue(isPing(envelope3));
            assertTrue(!isPing(envelope4));

            log.info("3 = {} 4 = {}",isPing(envelope3), isPing(envelope4));
            log.info(envelope3.toString());
            log.info(envelope4.toString());

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public boolean isPing(Messages.Envelope envelope) {
        return !envelope.getPing().getDefaultInstanceForType().equals(envelope.getPing());
    }
}
