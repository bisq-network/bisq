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

package bisq.apitest;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import static java.lang.String.format;



import bisq.apitest.config.ApiTestConfig;
import bisq.apitest.linux.BitcoinCli;

@Slf4j
class SmokeTestBitcoind {

    private final ApiTestConfig config;

    public SmokeTestBitcoind(ApiTestConfig config) {
        this.config = config;
    }

    public void run() throws IOException, InterruptedException {
        runBitcoinGetWalletInfo();   // smoke test bitcoin-cli
        String newBitcoinAddress = getNewAddress();
        generateToAddress(1, newBitcoinAddress);
    }

    public void runBitcoinGetWalletInfo() throws IOException, InterruptedException {
        // This might be good for a sanity check to make sure the regtest data was installed.
        log.info("Smoke test bitcoin-cli getwalletinfo");
        BitcoinCli walletInfo = new BitcoinCli(config, "getwalletinfo").run();
        log.info("{}\n{}", walletInfo.getCommandWithOptions(), walletInfo.getOutput());
        log.info("balance str = {}", walletInfo.getOutputValueAsString("balance"));
        log.info("balance dbl = {}", walletInfo.getOutputValueAsDouble("balance"));
        log.info("keypoololdest long = {}", walletInfo.getOutputValueAsLong("keypoololdest"));
        log.info("paytxfee dbl = {}", walletInfo.getOutputValueAsDouble("paytxfee"));
        log.info("keypoolsize_hd_internal int = {}", walletInfo.getOutputValueAsInt("keypoolsize_hd_internal"));
        log.info("private_keys_enabled bool = {}", walletInfo.getOutputValueAsBoolean("private_keys_enabled"));
        log.info("hdseedid str = {}", walletInfo.getOutputValueAsString("hdseedid"));
    }

    public String getNewAddress() throws IOException, InterruptedException {
        BitcoinCli newAddress = new BitcoinCli(config, "getnewaddress").run();
        log.info("{}\n{}", newAddress.getCommandWithOptions(), newAddress.getOutput());
        return newAddress.getOutput();
    }

    public void generateToAddress(int blocks, String address) throws IOException, InterruptedException {
        String generateToAddressCmd = format("generatetoaddress %d \"%s\"", blocks, address);
        BitcoinCli generateToAddress = new BitcoinCli(config, generateToAddressCmd).run();
        // Return value is an array of TxIDs.
        log.info("{}\n{}", generateToAddress.getCommandWithOptions(), generateToAddress.getOutputValueAsStringArray());
    }
}
