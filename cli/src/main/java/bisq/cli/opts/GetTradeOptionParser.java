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

import static bisq.cli.opts.OptLabel.OPT_SHOW_CONTRACT;
import static bisq.cli.opts.OptLabel.OPT_TRADE_ID;

public class GetTradeOptionParser extends AbstractMethodOptionParser implements MethodOpts {

    final OptionSpec<String> tradeIdOpt = parser.accepts(OPT_TRADE_ID, "id of trade")
            .withRequiredArg();

    final OptionSpec<Boolean> showContractOpt = parser.accepts(OPT_SHOW_CONTRACT, "show trade's json contract")
            .withOptionalArg()
            .ofType(boolean.class)
            .defaultsTo(Boolean.FALSE);

    public GetTradeOptionParser(String[] args) {
        super(args);
    }

    public GetTradeOptionParser parse() {
        super.parse();

        // Short circuit opt validation if user just wants help.
        if (options.has(helpOpt))
            return this;

        if (!options.has(tradeIdOpt) || options.valueOf(tradeIdOpt).isEmpty())
            throw new IllegalArgumentException("no trade id specified");

        return this;
    }

    public String getTradeId() {
        return options.valueOf(tradeIdOpt);
    }

    public boolean getShowContract() {
        return options.has(showContractOpt) ? options.valueOf(showContractOpt) : false;
    }
}
