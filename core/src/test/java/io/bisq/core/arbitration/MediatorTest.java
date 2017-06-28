package io.bisq.core.arbitration;

import com.google.common.collect.Lists;
import io.bisq.common.crypto.PubKeyRing;
import io.bisq.network.p2p.NodeAddress;
import org.junit.Test;

import java.util.Date;

import static io.bisq.core.arbitration.ArbitratorTest.getBytes;

/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */
public class MediatorTest {

    @Test
    public void testRoundtrip() {
        Mediator Mediator = getMediatorMock();


        //noinspection AccessStaticViaInstance
        Mediator.fromProto(Mediator.toProtoMessage().getMediator());
    }

    public static Mediator getMediatorMock() {
        return new Mediator(new NodeAddress("host", 1000),
                new PubKeyRing(getBytes(100), getBytes(100), "key"),
                Lists.newArrayList(),
                new Date().getTime(),
                getBytes(100),
                "registrationSignature",
                "email",
                "info",
                null);
    }


}
