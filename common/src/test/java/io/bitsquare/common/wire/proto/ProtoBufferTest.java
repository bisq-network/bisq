package io.bitsquare.common.wire.proto;

import com.google.protobuf.AbstractParser;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import com.sun.xml.internal.messaging.saaj.util.ByteInputStream;
import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import io.bitsquare.common.wire.proto.Messages;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertFalse;

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
        assertFalse(Messages.Envelope.newBuilder().setP2PNetworkVersion(1).isInitialized());
        try {
            envelope1.writeDelimitedTo(outputStream);
            envelope2.writeDelimitedTo(outputStream);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
            Messages.Envelope envelope3 = Messages.Envelope.parseDelimitedFrom(inputStream);
            Messages.Envelope envelope4 = Messages.Envelope.parseDelimitedFrom(inputStream);


            log.info("message: {}", envelope3.getPing());
            //log.info("peerseesd empty: '{}'",envelope3.getPong().equals(Messages.Envelope.) == "");
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
