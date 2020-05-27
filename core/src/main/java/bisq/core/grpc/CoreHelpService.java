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

package bisq.core.grpc;

import static java.lang.String.format;

class CoreHelpService {

    private final String help = "Bisq Client" +
            "\n" +
            "Usage: bisq-cli [options] <method> [params]" +
            "\n\n" +
            format("%-19s%-30s%s%n", "Method", "Params", "Description") +
            format("%-19s%-30s%s%n", "------", "------", "------------") +
            format("%-19s%-30s%s%n", "getversion", "", "Get server version") +
            format("%-19s%-30s%s%n", "getbalance", "", "Get server wallet balance") +
            format("%-19s%-30s%s%n", "lockwallet", "", "Remove wallet password from memory, locking the wallet") +
            format("%-19s%-30s%s%n", "unlockwallet", "password timeout",
                    "Store wallet password in memory for timeout seconds") +
            format("%-19s%-30s%s%n", "setwalletpassword", "password [newpassword]",
                    "Encrypt wallet with password, or set new password on encrypted wallet") +
            "\n";

    public String getHelp() {
        return this.help;
    }

    public String getHelp(Method method) {
        // TODO return detailed help for method
        return this.help;
    }
}
