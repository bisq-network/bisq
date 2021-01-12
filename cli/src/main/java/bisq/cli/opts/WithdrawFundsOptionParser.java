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

public class WithdrawFundsOptionParser extends AbstractMethodOptionParser implements MethodOpts {

    final OptionSpec<String> tradeIdOpt = parser.accepts("t", "id of trade to get")
            .withRequiredArg()
            .defaultsTo(EMPTY);

    final OptionSpec<String> addressOpt = parser.accepts("a", "destination btc address")
            .withRequiredArg()
            .defaultsTo(EMPTY);

    final OptionSpec<String> memoOpt = parser.accepts("memo", "optional tx memo")
            .withOptionalArg()
            .defaultsTo(EMPTY);

    public WithdrawFundsOptionParser(String[] args) {
        super(args);
    }

    public WithdrawFundsOptionParser parse() {
        super.parse();

        // Short circuit opt validation if user just wants help.
        if (options.has(helpOpt))
            return this;

        if (!options.has(tradeIdOpt))
            throw new IllegalArgumentException("no trade id specified");

        return this;
    }

    public String getTradeId() {
        return options.valueOf(tradeIdOpt);
    }

    public String getAddress() {
        return options.valueOf(addressOpt);
    }

    public String getMemo() {
        return options.has(memoOpt) ? options.valueOf(memoOpt) : "";
    }
}
