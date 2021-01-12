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

package bisq.cli.opts;


import joptsimple.OptionSpec;

import static joptsimple.internal.Strings.EMPTY;

public class UnlockWalletOptionParser extends AbstractMethodOptionParser implements MethodOpts {

    final OptionSpec<String> passwordOpt = parser.accepts("p", "bisq wallet password")
            .withRequiredArg()
            .defaultsTo(EMPTY);

    final OptionSpec<Long> unlockTimeoutOpt = parser.accepts("t", "wallet unlock timeout (s)")
            .withRequiredArg()
            .ofType(long.class)
            .defaultsTo(0L);

    public UnlockWalletOptionParser(String[] args) {
        super(args);
    }

    public UnlockWalletOptionParser parse() {
        super.parse();

        if (!options.has(passwordOpt))
            throw new IllegalArgumentException("no password specified");

        if (!options.has(unlockTimeoutOpt) || options.valueOf(unlockTimeoutOpt) <= 0)
            throw new IllegalArgumentException("no unlock timeout specified");

        return this;
    }

    public String getPassword() {
        return options.valueOf(passwordOpt);
    }

    public long getUnlockTimeout() {
        return options.valueOf(unlockTimeoutOpt);
    }
}
