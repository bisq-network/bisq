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

package bisq.apitest.scenario.bot;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;

import static bisq.apitest.Scaffold.BitcoinCoreApp.bitcoind;
import static bisq.apitest.botsupport.shutdown.ManualShutdown.startShutdownTimer;
import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.apitest.config.BisqAppConfig.arbdaemon;
import static bisq.apitest.config.BisqAppConfig.bobdaemon;
import static bisq.apitest.config.BisqAppConfig.seednode;
import static org.junit.jupiter.api.Assertions.fail;



import bisq.apitest.botsupport.BotClient;
import bisq.apitest.botsupport.script.BashScriptGenerator;
import bisq.apitest.botsupport.shutdown.ManualBotShutdownException;

@EnabledIf("botScriptExists")
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MarketMakerBotTest extends AbstractBotTest {

    private RobotBobMMBot robotBobMM;

    @BeforeAll
    public static void startTestHarness() {
        botScript = deserializeBotScript();

        startSupportingApps(true,
                true,
                bitcoind,
                seednode,
                arbdaemon,
                alicedaemon,
                bobdaemon);

        makerBotClient = new BotClient(bobClient);
        takerBotClient = new BotClient(aliceClient);
    }

    @BeforeEach
    public void initRobotBob() {
        try {
            BashScriptGenerator bashScriptGenerator = getBashScriptGenerator();

            robotBobMM = new RobotBobMMBot(makerBotClient,
                    takerBotClient,
                    botScript,
                    bitcoinCli,
                    bashScriptGenerator);
        } catch (Exception ex) {
            fail(ex);
        }
    }

    @Test
    @Order(1)
    public void runRobotBob() {
        try {
            startShutdownTimer();

            log.info("Bob's Initial Bank Balance: {}", robotBobMM.getBobsBankBalance().get());

            robotBobMM.run();

            // A controlled bot shutdown is always desired in the event of a fatal error.
            // Check RobotBob's bot exception fields, and fail the test if one or more
            // is not null.
            if (robotBobMM.botDidFail())
                fail(robotBobMM.getBotFailureReason());

            log.info("Bob's Final Bank Balance: {}", robotBobMM.getBobsBankBalance().get());

        } catch (ManualBotShutdownException ex) {
            // This exception is thrown if a /tmp/bottest-shutdown file was found.
            // You can also kill -15 <pid>
            // of worker.org.gradle.process.internal.worker.GradleWorkerMain 'Gradle Test Executor #'
            //
            // This will cleanly shut everything down as well, but you will see a
            // Process 'Gradle Test Executor #' finished with non-zero exit value 143 error,
            // which you may think is a test failure.
            log.warn("{}  Shutting down test case before test completion;"
                            + "  this is not a test failure.",
                    ex.getMessage());
        } catch (Throwable t) {
            if (robotBobMM.botDidFail()) {
                fail(robotBobMM.getBotFailureReason());
            } else {
                log.error("Uncontrolled bot shutdown caused by uncaught bot exception:");
                fail(t);
            }
        }
    }

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }
}
