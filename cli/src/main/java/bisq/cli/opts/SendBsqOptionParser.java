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

public class SendBsqOptionParser extends AbstractMethodOptionParser implements MethodOpts {

    final OptionSpec<String> addressOpt = parser.accepts("address", "destination bsq address")
            .withRequiredArg()
            .defaultsTo(EMPTY);

    final OptionSpec<String> amountOpt = parser.accepts("amount", "amount of bsq to send")
            .withRequiredArg()
            .defaultsTo(EMPTY);

    final OptionSpec<String> feeRateOpt = parser.accepts("fee", "optional tx fee rate (sats/byte)")
            .withOptionalArg()
            .defaultsTo(EMPTY);

    public SendBsqOptionParser(String[] args) {
        super(args);
    }

    public SendBsqOptionParser parse() {
        super.parse();

        if (!options.has(addressOpt))
            throw new IllegalArgumentException("no bsq address specified");

        if (!options.has(amountOpt))
            throw new IllegalArgumentException("no bsq amount specified");

        return this;
    }

    public String getAddress() {
        return options.valueOf(addressOpt);
    }

    public String getAmount() {
        return options.valueOf(amountOpt);
    }

    public String getFeeRate() {
        return options.has(feeRateOpt) ? options.valueOf(feeRateOpt) : "";
    }
}
