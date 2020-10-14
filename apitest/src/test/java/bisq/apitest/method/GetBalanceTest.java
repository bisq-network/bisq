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

import bisq.proto.grpc.GetBalanceRequest;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.Scaffold.BitcoinCoreApp.bitcoind;
import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.apitest.config.BisqAppConfig.seednode;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.MethodOrderer.OrderAnnotation;


@Slf4j
@TestMethodOrder(OrderAnnotation.class)
public class GetBalanceTest extends MethodTest {

    @BeforeAll
    public static void setUp() {
        try {
            setUpScaffold(bitcoind, seednode, alicedaemon);

            // Have to generate 1 regtest block for alice's wallet to show 10 BTC balance.
            bitcoinCli.generateBlocks(1);

            // Give the alicedaemon time to parse the new block.
            MILLISECONDS.sleep(1500);
        } catch (Exception ex) {
            fail(ex);
        }
    }

    @Test
    @Order(1)
    public void testGetBalance() {
        // All tests depend on the DAO / regtest environment, and Alice's wallet is
        // initialized with 10 BTC during the scaffolding setup.
        var balance = grpcStubs(alicedaemon).walletsService
                .getBalance(GetBalanceRequest.newBuilder().build()).getBalance();
        assertEquals(1000000000, balance);
    }

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }
}
