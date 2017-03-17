package io.bisq.wire.payload.payment;

import com.google.protobuf.util.JsonFormat;
import io.bisq.common.wire.proto.Messages;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.fail;

/**
 * Created by mike on 27/02/2017.
 */
public class OKPayAccountContractDataTest {
    @Test
    public void toProtoBuf() throws Exception {
        OKPayAccountContractData accountContractData = new OKPayAccountContractData("method", "id", 100);
        accountContractData.setAccountNr("AccNr");
        try {
            String buffer = JsonFormat.printer().print(accountContractData.toProtoBuf().getOKPayAccountContractData());
            JsonFormat.Parser parser = JsonFormat.parser();
            Messages.OKPayAccountContractData.Builder builder = Messages.OKPayAccountContractData.newBuilder();
            parser.merge(buffer, builder);
            //assertEquals(accountContractData, new OKPayAccountContractData()ProtoBufferUtilities.getOkPayAccountContractData(builder.build()));
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }

    }

}