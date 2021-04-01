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

package bisq.apitest.scenario.bot.protocol;


import protobuf.PaymentAccount;

import java.text.DecimalFormat;

import java.io.File;

import java.math.RoundingMode;

import static bisq.apitest.botsupport.protocol.ProtocolStep.WAIT_FOR_TAKER_DEPOSIT_TX_CONFIRMED;
import static java.lang.Long.parseLong;



import bisq.apitest.botsupport.BotClient;
import bisq.apitest.botsupport.protocol.BotProtocol;
import bisq.apitest.botsupport.script.BashScriptGenerator;
import bisq.apitest.method.BitcoinCliHelper;

public abstract class ApiTestBotProtocol extends BotProtocol {

    // Don't use @Slf4j annotation to init log and use in Functions w/out IDE warnings.
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApiTestBotProtocol.class);

    // Used to show user how to run regtest bitcoin-cli commands.
    protected final BitcoinCliHelper bitcoinCli;

    public ApiTestBotProtocol(String botDescription,
                              BotClient botClient,
                              PaymentAccount paymentAccount,
                              long protocolStepTimeLimitInMs,
                              BitcoinCliHelper bitcoinCli,
                              BashScriptGenerator bashScriptGenerator) {
        super(botDescription,
                botClient,
                paymentAccount,
                protocolStepTimeLimitInMs,
                bashScriptGenerator);
        this.bitcoinCli = bitcoinCli;
    }

    @Override
    protected void printBotProtocolStep() {
        super.printBotProtocolStep();
        if (currentProtocolStep.equals(WAIT_FOR_TAKER_DEPOSIT_TX_CONFIRMED)) {
            log.info("Generate a btc block to trigger taker's deposit fee tx confirmation.");
            createGenerateBtcBlockScript();
        }
    }

    @Override
    protected void printCliHintAndOrScript(File script, String hint) {
        super.printCliHintAndOrScript(script, hint);
        sleep(5000); // Allow 5s for CLI user to read the hint.
    }

    protected void createGenerateBtcBlockScript() {
        String newBitcoinCoreAddress = bitcoinCli.getNewBtcAddress();
        File script = bashScriptGenerator.createGenerateBtcBlockScript(newBitcoinCoreAddress);
        printCliHintAndOrScript(script, "The manual CLI side can generate 1 btc block");
    }

    public static long toDollars(long volume) {
        DecimalFormat df = new DecimalFormat("#########");
        df.setMaximumFractionDigits(0);
        df.setRoundingMode(RoundingMode.UNNECESSARY);
        return parseLong(df.format((double) volume / 10000));
    }
}
