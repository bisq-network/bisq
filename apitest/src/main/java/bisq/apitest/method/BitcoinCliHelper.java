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

import java.io.IOException;

import static java.lang.String.format;
import static org.junit.Assert.fail;



import bisq.apitest.config.ApiTestConfig;
import bisq.apitest.linux.BitcoinCli;

public final class BitcoinCliHelper {

    private final ApiTestConfig config;

    public BitcoinCliHelper(ApiTestConfig config) {
        this.config = config;
    }

    // Convenience methods for making bitcoin-cli calls.

    public String getNewBtcAddress() {
        try {
            return new BitcoinCli(config, "getnewaddress").run().getOutput();
        } catch (IOException | InterruptedException ex) {
            fail(ex.getMessage());
            return null;
        }
    }

    public String[] generateToAddress(int blocks, String address) {
        try {
            String generateToAddressCmd = format("generatetoaddress %d \"%s\"", blocks, address);
            BitcoinCli generateToAddress = new BitcoinCli(config, generateToAddressCmd).run();
            // Return an array of transaction ids.
            return generateToAddress.getOutputValueAsStringArray();
        } catch (IOException | InterruptedException ex) {
            fail(ex.getMessage());
            return null;
        }
    }

    public void generateBlocks(int blocks) {
        generateToAddress(blocks, getNewBtcAddress());
    }
}
