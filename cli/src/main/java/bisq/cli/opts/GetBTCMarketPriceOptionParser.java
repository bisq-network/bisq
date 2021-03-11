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

public class GetBTCMarketPriceOptionParser extends AbstractMethodOptionParser implements MethodOpts {

    final OptionSpec<String> currencyCodeOpt = parser.accepts(OPT_CURRENCY_CODE, "currency-code")
            .withRequiredArg();

    public GetBTCMarketPriceOptionParser(String[] args) {
        super(args);
    }

    public GetBTCMarketPriceOptionParser parse() {
        super.parse();

        // Short circuit opt validation if user just wants help.
        if (options.has(helpOpt))
            return this;

        if (!options.has(currencyCodeOpt) || options.valueOf(currencyCodeOpt).isEmpty())
            throw new IllegalArgumentException("no currency code specified");

        return this;
    }

    public String getCurrencyCode() {
        return options.valueOf(currencyCodeOpt);
    }
}
