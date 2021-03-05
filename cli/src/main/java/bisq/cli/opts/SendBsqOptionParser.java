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

import static bisq.cli.opts.OptLabel.OPT_ADDRESS;
import static bisq.cli.opts.OptLabel.OPT_AMOUNT;
import static bisq.cli.opts.OptLabel.OPT_TX_FEE_RATE;
import static joptsimple.internal.Strings.EMPTY;

public class SendBsqOptionParser extends AbstractMethodOptionParser implements MethodOpts {

    final OptionSpec<String> addressOpt = parser.accepts(OPT_ADDRESS, "destination bsq address")
            .withRequiredArg();

    final OptionSpec<String> amountOpt = parser.accepts(OPT_AMOUNT, "amount of bsq to send")
            .withRequiredArg();

    final OptionSpec<String> feeRateOpt = parser.accepts(OPT_TX_FEE_RATE, "optional tx fee rate (sats/byte)")
            .withOptionalArg()
            .defaultsTo(EMPTY);

    public SendBsqOptionParser(String[] args) {
        super(args);
    }

    public SendBsqOptionParser parse() {
        super.parse();

        // Short circuit opt validation if user just wants help.
        if (options.has(helpOpt))
            return this;

        if (!options.has(addressOpt) || options.valueOf(addressOpt).isEmpty())
            throw new IllegalArgumentException("no bsq address specified");

        if (!options.has(amountOpt) || options.valueOf(amountOpt).isEmpty())
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
