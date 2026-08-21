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

package bisq.apitest.linux;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;



import bisq.apitest.config.ApiTestConfig;

@Slf4j
public class BitcoinCli extends AbstractLinuxProcess implements LinuxProcess {

    private final String command;

    private String commandWithOptions;
    private String output;
    private boolean error;
    private String errorMessage;

    public BitcoinCli(ApiTestConfig config, String command) {
        super("bitcoin-cli", config);
        this.command = command;
        this.error = false;
        this.errorMessage = null;
    }

    public BitcoinCli run() throws IOException, InterruptedException {
        this.start();
        return this;
    }

    public String getCommandWithOptions() {
        return commandWithOptions;
    }

    public String getOutput() {
        if (isError())
            throw new IllegalStateException(output);

        // Some responses are not in json format, such as what is returned by
        // 'getnewaddress'.  The raw output string is the value.

        return output;
    }

    public String[] getOutputValueAsStringArray() {
        if (isError())
            throw new IllegalStateException(output);

        if (!output.startsWith("[") && !output.endsWith("]"))
            throw new IllegalStateException(output + "\nis not a json array");

        String[] lines = output.split("\n");
        String[] array = new String[lines.length - 2];
        for (int i = 1; i < lines.length - 1; i++) {
            array[i - 1] = lines[i].replaceAll("[^a-zA-Z0-9.]", "");
        }

        return array;
    }

    public String getOutputValueAsString(String key) {
        if (isError())
            throw new IllegalStateException(output);

        // Some assumptions about bitcoin-cli json string parsing:
        // Every multi valued, non-error bitcoin-cli response will be a json string.
        // Every key/value in the json string will terminate with a newline.
        // Most key/value lines in json strings have a ',' char in front of the newline.
        // e.g., bitcoin-cli 'getwalletinfo' output:
        // {
        //  "walletname": "",
        //  "walletversion": 159900,
        //  "balance": 527.49941568,
        //  "unconfirmed_balance": 0.00000000,
        //  "immature_balance": 5000.00058432,
        //  "txcount": 114,
        //  "keypoololdest": 1528018235,
        //  "keypoolsize": 1000,
        //  "keypoolsize_hd_internal": 1000,
        //  "paytxfee": 0.00000000,
        //  "hdseedid": "179b609a60c2769138844c3e36eb430fd758a9c6",
        //  "private_keys_enabled": true,
        //  "avoid_reuse": false,
        //  "scanning": false
        // }

        int keyIdx = output.indexOf("\"" + key + "\":");
        int eolIdx = output.indexOf("\n", keyIdx);
        String valueLine = output.substring(keyIdx, eolIdx); // "balance": 527.49941568,
        String[] keyValue = valueLine.split(":");

        // Remove all but alphanumeric chars and decimal points from the return value,
        // including quotes around strings, and trailing commas.
        // Adjustments will be necessary as we begin to work with more complex
        // json values, such as arrays.
        return keyValue[1].replaceAll("[^a-zA-Z0-9.]", "");
    }

    public boolean getOutputValueAsBoolean(String key) {
        String valueStr = getOutputValueAsString(key);
        return Boolean.parseBoolean(valueStr);
    }


    public int getOutputValueAsInt(String key) {
        String valueStr = getOutputValueAsString(key);
        return Integer.parseInt(valueStr);
    }

    public double getOutputValueAsDouble(String key) {
        String valueStr = getOutputValueAsString(key);
        return Double.parseDouble(valueStr);
    }

    public long getOutputValueAsLong(String key) {
        String valueStr = getOutputValueAsString(key);
        return Long.parseLong(valueStr);
    }

    public boolean isError() {
        return error;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public void start() throws InterruptedException, IOException {
        verifyBitcoinPathsExist(false);
        verifyBitcoindRunning();
        commandWithOptions = config.bitcoinPath + "/bitcoin-cli -regtest "
                + " -rpcport=" + config.bitcoinRpcPort
                + " -rpcuser=" + config.bitcoinRpcUser
                + " -rpcpassword=" + config.bitcoinRpcPassword
                + " " + command;
        BashCommand bashCommand = new BashCommand(commandWithOptions).run();

        error = bashCommand.getExitStatus() != 0;
        if (error) {
            errorMessage = bashCommand.getError();
            if (errorMessage == null || errorMessage.isEmpty())
                throw new IllegalStateException("bitcoin-cli returned an error without a message");

        } else {
            output = bashCommand.getOutput();
        }
    }

    @Override
    public long getPid() {
        // We don't cache the pid.  The bitcoin-cli will quickly return a
        // response, including server error info if any.
        throw new UnsupportedOperationException("getPid not supported");
    }

    @Override
    public void shutdown() {
        // We don't try to shutdown the bitcoin-cli.  It will quickly return a
        // response, including server error info if any.
        throw new UnsupportedOperationException("shutdown not supported");
    }
}
