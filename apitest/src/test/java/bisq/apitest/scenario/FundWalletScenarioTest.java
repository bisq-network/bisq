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

package bisq.apitest.scenario;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.Scaffold.BitcoinCoreApp.bitcoind;
import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.apitest.config.BisqAppConfig.seednode;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FundWalletScenarioTest extends ScenarioTest {

    @BeforeAll
    public static void setUp() {
        try {
            setUpScaffold(bitcoind, seednode, alicedaemon);
            bitcoinCli.generateBlocks(1);
            MILLISECONDS.sleep(1500);
        } catch (Exception ex) {
            fail(ex);
        }
    }

    @Test
    @Order(1)
    public void testFundWallet() {
        // bisq wallet was initialized with 10 btc
        long balance = getBalance(alicedaemon);
        assertEquals(1000000000, balance);

        String unusedAddress = getUnusedBtcAddress(alicedaemon);
        bitcoinCli.sendToAddress(unusedAddress, "2.5");

        bitcoinCli.generateBlocks(1);
        sleep(1500);

        balance = getBalance(alicedaemon);
        assertEquals(1250000000L, balance); // new balance is 12.5 btc
    }

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }
}
