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
import static bisq.cli.opts.OptLabel.OPT_MEMO;
import static bisq.cli.opts.OptLabel.OPT_TX_FEE_RATE;
import static joptsimple.internal.Strings.EMPTY;

public class SendBtcOptionParser extends AbstractMethodOptionParser implements MethodOpts {

    final OptionSpec<String> addressOpt = parser.accepts(OPT_ADDRESS, "destination btc address")
            .withRequiredArg();

    final OptionSpec<String> amountOpt = parser.accepts(OPT_AMOUNT, "amount of btc to send")
            .withRequiredArg();

    final OptionSpec<String> feeRateOpt = parser.accepts(OPT_TX_FEE_RATE, "optional tx fee rate (sats/byte)")
            .withOptionalArg()
            .defaultsTo(EMPTY);

    final OptionSpec<String> memoOpt = parser.accepts(OPT_MEMO, "optional tx memo")
            .withOptionalArg()
            .defaultsTo(EMPTY);

    public SendBtcOptionParser(String[] args) {
        super(args);
    }

    public SendBtcOptionParser parse() {
        super.parse();

        // Short circuit opt validation if user just wants help.
        if (options.has(helpOpt))
            return this;

        if (!options.has(addressOpt) || options.valueOf(addressOpt).isEmpty())
            throw new IllegalArgumentException("no btc address specified");

        if (!options.has(amountOpt) || options.valueOf(amountOpt).isEmpty())
            throw new IllegalArgumentException("no btc amount specified");

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

    public String getMemo() {
        return options.has(memoOpt) ? options.valueOf(memoOpt) : "";
    }
}
