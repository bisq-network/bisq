package io.bisq.core.user;

import com.google.common.collect.Lists;
import io.bisq.core.alert.Alert;
import io.bisq.core.arbitration.ArbitratorTest;
import io.bisq.core.arbitration.MediatorTest;
import io.bisq.core.filter.Filter;
import io.bisq.core.proto.CoreProtoResolver;
import org.junit.Ignore;

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
@SuppressWarnings("UnusedAssignment")
public class UserPayloadModelVOTest {
    @Ignore("TODO InvalidKeySpecException at io.bisq.common.crypto.Sig.getPublicKeyFromBytes(Sig.java:135)")
    public void testRoundtrip() {
        UserPayload vo = new UserPayload();
        vo.setAccountId("accountId");
        UserPayload newVo = UserPayload.fromProto(vo.toProtoMessage().getUserPayload(), new CoreProtoResolver());
    }

    @Ignore("TODO InvalidKeySpecException at io.bisq.common.crypto.Sig.getPublicKeyFromBytes(Sig.java:135)")
    public void testRoundtripFull() {
        UserPayload vo = new UserPayload();
        vo.setAccountId("accountId");
        vo.setDisplayedAlert(new Alert("message", true, "version", new byte[]{12, -64, 12}, "string", null));
        vo.setDevelopersFilter(new Filter(Lists.newArrayList(), Lists.newArrayList(), Lists.newArrayList(),
                Lists.newArrayList(), Lists.newArrayList(), Lists.newArrayList(), Lists.newArrayList(), Lists.newArrayList(),
                false, Lists.newArrayList(), "string", new byte[]{10, 0, 0}, null));
        vo.setRegisteredArbitrator(ArbitratorTest.getArbitratorMock());
        vo.setRegisteredMediator(MediatorTest.getMediatorMock());
        vo.setAcceptedArbitrators(Lists.newArrayList(ArbitratorTest.getArbitratorMock()));
        vo.setAcceptedMediators(Lists.newArrayList(MediatorTest.getMediatorMock()));
        UserPayload newVo = UserPayload.fromProto(vo.toProtoMessage().getUserPayload(), new CoreProtoResolver());
    }
}
