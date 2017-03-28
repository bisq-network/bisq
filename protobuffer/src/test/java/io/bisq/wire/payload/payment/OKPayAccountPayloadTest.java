/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.wire.payload.payment;

import com.google.protobuf.util.JsonFormat;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.payload.payment.OKPayAccountPayload;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.fail;

public class OKPayAccountPayloadTest {
    @Test
    public void toProtoBuf() throws Exception {
        OKPayAccountPayload accountPayload = new OKPayAccountPayload("method", "id", 100);
        accountPayload.setAccountNr("AccNr");
        try {
            String buffer = JsonFormat.printer().print(accountPayload.toProto().getOKPayAccountPayload());
            JsonFormat.Parser parser = JsonFormat.parser();
            PB.OKPayAccountPayload.Builder builder = PB.OKPayAccountPayload.newBuilder();
            parser.merge(buffer, builder);
            //assertEquals(accountPayload, new OKPayAccountPayload()ProtoBufferUtilities.getOkPayAccountPayload(builder.build()));
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }

    }

}