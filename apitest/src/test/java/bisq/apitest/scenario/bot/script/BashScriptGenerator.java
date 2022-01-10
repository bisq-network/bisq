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

package bisq.apitest.scenario.bot.script;

import bisq.common.file.FileUtil;

import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.TradeInfo;

import com.google.common.io.Files;

import java.nio.file.Paths;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.io.FileWriteMode.APPEND;
import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readAllBytes;

@Slf4j
@Getter
public class BashScriptGenerator {

    private final int apiPort;
    private final String apiPassword;
    private final String paymentAccountId;
    private final String cliBase;
    private final boolean printCliScripts;

    public BashScriptGenerator(String apiPassword,
                               int apiPort,
                               String paymentAccountId,
                               boolean printCliScripts) {
        this.apiPassword = apiPassword;
        this.apiPort = apiPort;
        this.paymentAccountId = paymentAccountId;
        this.printCliScripts = printCliScripts;
        this.cliBase = format("./bisq-cli --password=%s --port=%d", apiPassword, apiPort);
    }

    public File createMakeMarginPricedOfferScript(String direction,
                                                  String currencyCode,
                                                  String amount,
                                                  String marketPriceMargin,
                                                  String securityDeposit,
                                                  String feeCurrency) {
        String makeOfferCmd = format("%s createoffer --payment-account=%s "
                        + " --direction=%s"
                        + " --currency-code=%s"
                        + " --amount=%s"
                        + " --market-price-margin=%s"
                        + " --security-deposit=%s"
                        + " --fee-currency=%s",
                cliBase,
                this.getPaymentAccountId(),
                direction,
                currencyCode,
                amount,
                marketPriceMargin,
                securityDeposit,
                feeCurrency);
        String getOffersCmd = format("%s getmyoffers --direction=%s --currency-code=%s",
                cliBase,
                direction,
                currencyCode);
        return createCliScript("createoffer.sh",
                makeOfferCmd,
                "sleep 2",
                getOffersCmd);
    }

    public File createMakeFixedPricedOfferScript(String direction,
                                                 String currencyCode,
                                                 String amount,
                                                 String fixedPrice,
                                                 String securityDeposit,
                                                 String feeCurrency) {
        String makeOfferCmd = format("%s createoffer --payment-account=%s "
                        + " --direction=%s"
                        + " --currency-code=%s"
                        + " --amount=%s"
                        + " --fixed-price=%s"
                        + " --security-deposit=%s"
                        + " --fee-currency=%s",
                cliBase,
                this.getPaymentAccountId(),
                direction,
                currencyCode,
                amount,
                fixedPrice,
                securityDeposit,
                feeCurrency);
        String getOffersCmd = format("%s getmyoffers --direction=%s --currency-code=%s",
                cliBase,
                direction,
                currencyCode);
        return createCliScript("createoffer.sh",
                makeOfferCmd,
                "sleep 2",
                getOffersCmd);
    }

    public File createTakeOfferScript(OfferInfo offer) {
        String getOffersCmd = format("%s getoffers --direction=%s --currency-code=%s",
                cliBase,
                offer.getDirection(),
                offer.getCounterCurrencyCode());
        String takeOfferCmd = format("%s takeoffer --offer-id=%s --payment-account=%s --fee-currency=BSQ",
                cliBase,
                offer.getId(),
                this.getPaymentAccountId());
        String getTradeCmd = format("%s gettrade --trade-id=%s",
                cliBase,
                offer.getId());
        return createCliScript("takeoffer.sh",
                getOffersCmd,
                takeOfferCmd,
                "sleep 5",
                getTradeCmd);
    }

    public File createPaymentStartedScript(TradeInfo trade) {
        String paymentStartedCmd = format("%s confirmpaymentstarted --trade-id=%s",
                cliBase,
                trade.getTradeId());
        String getTradeCmd = format("%s gettrade --trade-id=%s", cliBase, trade.getTradeId());
        return createCliScript("confirmpaymentstarted.sh",
                paymentStartedCmd,
                "sleep 2",
                getTradeCmd);
    }

    public File createPaymentReceivedScript(TradeInfo trade) {
        String paymentStartedCmd = format("%s confirmpaymentreceived --trade-id=%s",
                cliBase,
                trade.getTradeId());
        String getTradeCmd = format("%s gettrade --trade-id=%s", cliBase, trade.getTradeId());
        return createCliScript("confirmpaymentreceived.sh",
                paymentStartedCmd,
                "sleep 2",
                getTradeCmd);
    }

    public File createKeepFundsScript(TradeInfo trade) {
        String paymentStartedCmd = format("%s closetrade --trade-id=%s", cliBase, trade.getTradeId());
        String getTradeCmd = format("%s gettrade --trade-id=%s", cliBase, trade.getTradeId());
        String getBalanceCmd = format("%s getbalance", cliBase);
        return createCliScript("closetrade.sh",
                paymentStartedCmd,
                "sleep 2",
                getTradeCmd,
                getBalanceCmd);
    }

    public File createGetBalanceScript() {
        String getBalanceCmd = format("%s getbalance", cliBase);
        return createCliScript("getbalance.sh", getBalanceCmd);
    }

    public File createGenerateBtcBlockScript(String address) {
        String bitcoinCliCmd = format("bitcoin-cli -regtest  -rpcport=19443 -rpcuser=apitest"
                        + " -rpcpassword=apitest generatetoaddress 1 \"%s\"",
                address);
        return createCliScript("genbtcblk.sh",
                bitcoinCliCmd);
    }

    public File createCliScript(String scriptName, String... commands) {
        String filename = getProperty("java.io.tmpdir") + File.separator + scriptName;
        File oldScript = new File(filename);
        if (oldScript.exists()) {
            try {
                FileUtil.deleteFileIfExists(oldScript);
            } catch (IOException ex) {
                throw new IllegalStateException("Unable to delete old script.", ex);
            }
        }
        File script = new File(filename);
        try {
            List<CharSequence> lines = new ArrayList<>();
            lines.add("#!/bin/bash");
            lines.add("############################################################");
            lines.add("# This example CLI script may be overwritten during the test");
            lines.add("# run, and will be deleted when the test harness shuts down.");
            lines.add("# Make a copy if you want to save it.");
            lines.add("############################################################");
            lines.add("set -x");
            Collections.addAll(lines, commands);
            Files.asCharSink(script, UTF_8, APPEND).writeLines(lines);
            if (!script.setExecutable(true))
                throw new IllegalStateException("Unable to set script owner's execute permission.");
        } catch (IOException ex) {
            log.error("", ex);
            throw new IllegalStateException(ex);
        } finally {
            script.deleteOnExit();
        }
        return script;
    }

    public void printCliScript(File cliScript,
                               org.slf4j.Logger logger) {
        try {
            String contents = new String(readAllBytes(Paths.get(cliScript.getPath())));
            logger.info("CLI script {}:\n{}", cliScript.getAbsolutePath(), contents);
        } catch (IOException ex) {
            throw new IllegalStateException("Error reading CLI script contents.", ex);
        }
    }
}
