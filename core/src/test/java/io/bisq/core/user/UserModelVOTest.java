package io.bisq.core.user;

import com.google.common.collect.Lists;
import io.bisq.core.alert.Alert;
import io.bisq.core.arbitration.ArbitratorTest;
import io.bisq.core.arbitration.MediatorTest;
import io.bisq.core.filter.Filter;
import org.junit.Test;

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
public class UserModelVOTest {


    @Test
    public void testRoundtrip() {
        User vo = new User();
        vo.setAccountID("accountId");

        User newVo = User.fromProto(vo.toProtoMessage().getUser());
    }

    @Test
    public void testRoundtripFull() {
        User vo = new User();
        vo.setAccountID("accountId");
        vo.setDisplayedAlert(new Alert("message", true, "version", new byte[]{12,-64,12}, "string", null));
        vo.setDevelopersFilter(new Filter(Lists.newArrayList(), Lists.newArrayList(), Lists.newArrayList(), "string", new byte[]{10,0,0}, null));
        vo.setRegisteredArbitrator(ArbitratorTest.getArbitratorMock());
        vo.setRegisteredMediator(MediatorTest.getMediatorMock());
        vo.setAcceptedArbitrators(Lists.newArrayList(ArbitratorTest.getArbitratorMock()));
        vo.setAcceptedMediators(Lists.newArrayList(MediatorTest.getMediatorMock()));
        User newVo = User.fromProto(vo.toProtoMessage().getUser());
    }
}
