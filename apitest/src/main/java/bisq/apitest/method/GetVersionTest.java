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

package bisq.apitest.method;

import bisq.proto.grpc.GetVersionRequest;

import lombok.extern.slf4j.Slf4j;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static bisq.common.app.Version.VERSION;
import static org.junit.Assert.assertEquals;



import bisq.apitest.OrderedRunner;
import bisq.apitest.annotation.Order;

@Slf4j
@RunWith(OrderedRunner.class)
public class GetVersionTest extends MethodTest {

    @BeforeClass
    public static void setUp() {
        setUpScaffold();
    }

    @Test
    @Order(1)
    public void testGetVersion() {
        var version = grpcStubs.versionService.getVersion(GetVersionRequest.newBuilder().build()).getVersion();
        assertEquals(VERSION, version);
    }

    @AfterClass
    public static void tearDown() {
        tearDownScaffold();
    }
}
