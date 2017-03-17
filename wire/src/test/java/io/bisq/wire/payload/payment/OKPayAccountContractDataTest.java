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
import io.bisq.wire.proto.Messages;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.fail;

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