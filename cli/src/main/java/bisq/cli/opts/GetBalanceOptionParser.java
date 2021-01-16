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

import static bisq.cli.opts.OptLabel.OPT_CURRENCY_CODE;
import static joptsimple.internal.Strings.EMPTY;

public class GetBalanceOptionParser extends AbstractMethodOptionParser implements MethodOpts {

    final OptionSpec<String> currencyCodeOpt = parser.accepts(OPT_CURRENCY_CODE, "wallet currency code (bsq|btc)")
            .withOptionalArg()
            .defaultsTo(EMPTY);

    public GetBalanceOptionParser(String[] args) {
        super(args);
    }

    public GetBalanceOptionParser parse() {
        return (GetBalanceOptionParser) super.parse();
    }

    public String getCurrencyCode() {
        return options.has(currencyCodeOpt) ? options.valueOf(currencyCodeOpt) : "";
    }
}
