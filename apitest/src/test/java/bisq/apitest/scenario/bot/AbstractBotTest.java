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

import bisq.core.locale.Country;

import protobuf.PaymentAccount;

import com.google.gson.GsonBuilder;

import java.nio.file.Paths;

import java.io.File;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.locale.CountryUtil.findCountryByCode;
import static bisq.core.payment.payload.PaymentMethod.CLEAR_X_CHANGE_ID;
import static bisq.core.payment.payload.PaymentMethod.getPaymentMethod;
import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.nio.file.Files.readAllBytes;



import bisq.apitest.method.MethodTest;
import bisq.apitest.scenario.bot.script.BashScriptGenerator;
import bisq.apitest.scenario.bot.script.BotScript;

@Slf4j
public abstract class AbstractBotTest extends MethodTest {

    protected static final String BOT_SCRIPT_NAME = "bot-script.json";
    protected static BotScript botScript;
    protected static BotClient botClient;

    protected BashScriptGenerator getBashScriptGenerator() {
        if (botScript.isUseTestHarness()) {
            PaymentAccount alicesAccount = createAlicesPaymentAccount();
            botScript.setPaymentAccountIdForCliScripts(alicesAccount.getId());
        }
        return new BashScriptGenerator(config.apiPassword,
                botScript.getApiPortForCliScripts(),
                botScript.getPaymentAccountIdForCliScripts(),
                botScript.isPrintCliScripts());
    }

    private PaymentAccount createAlicesPaymentAccount() {
        BotPaymentAccountGenerator accountGenerator =
                new BotPaymentAccountGenerator(new BotClient(aliceClient));
        String paymentMethodId = botScript.getBotPaymentMethodId();
        if (paymentMethodId != null) {
            if (paymentMethodId.equals(CLEAR_X_CHANGE_ID)) {
                // Only Zelle test accts are supported now.
                return accountGenerator.createZellePaymentAccount(
                        "Alice's Zelle Account",
                        "Alice");
            } else {
                throw new UnsupportedOperationException(
                        format("This test harness bot does not work with %s payment accounts yet.",
                                getPaymentMethod(paymentMethodId).getDisplayString()));
            }
        } else {
            String countryCode = botScript.getCountryCode();
            Country country = findCountryByCode(countryCode).orElseThrow(() ->
                    new IllegalArgumentException(countryCode + " is not a valid iso country code."));
            return accountGenerator.createF2FPaymentAccount(country,
                    "Alice's " + country.name + " F2F Account");
        }
    }

    protected static BotScript deserializeBotScript() {
        try {
            File botScriptFile = new File(getProperty("java.io.tmpdir"), BOT_SCRIPT_NAME);
            String json = new String(readAllBytes(Paths.get(botScriptFile.getPath())));
            return new GsonBuilder().setPrettyPrinting().create().fromJson(json, BotScript.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Error reading script bot file contents.", ex);
        }
    }

    @SuppressWarnings("unused") // This is used by the jupiter framework.
    protected static boolean botScriptExists() {
        File botScriptFile = new File(getProperty("java.io.tmpdir"), BOT_SCRIPT_NAME);
        if (botScriptFile.exists()) {
            botScriptFile.deleteOnExit();
            log.info("Enabled, found {}.", botScriptFile.getPath());
            return true;
        } else {
            log.info("Skipped, no bot script.\n\tTo generate a bot-script.json file, see BotScriptGenerator.");
            return false;
        }
    }
}
