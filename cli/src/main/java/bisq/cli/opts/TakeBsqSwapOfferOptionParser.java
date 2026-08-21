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

import static bisq.cli.CurrencyFormat.toSatoshis;
import static bisq.cli.opts.OptLabel.OPT_AMOUNT;
import static bisq.cli.opts.OptLabel.OPT_FEE_CURRENCY;
import static bisq.cli.opts.OptLabel.OPT_PAYMENT_ACCOUNT_ID;

public class TakeBsqSwapOfferOptionParser extends OfferIdOptionParser implements MethodOpts {

    final OptionSpec<String> amountOpt = parser.accepts(OPT_AMOUNT, "intended amount of btc to buy or sell")
            .withRequiredArg()
            .defaultsTo("0");

    final OptionSpec<String> paymentAccountIdOpt = parser.accepts(OPT_PAYMENT_ACCOUNT_ID, "not used when taking bsq swaps")
            .withRequiredArg()
            .defaultsTo("invalid param");

    final OptionSpec<String> takerFeeCurrencyCodeOpt = parser.accepts(OPT_FEE_CURRENCY, "not used when taking bsq swaps")
            .withOptionalArg()
            .defaultsTo("invalid param");

    public TakeBsqSwapOfferOptionParser(String[] args) {
        super(args, true);
    }

    public TakeBsqSwapOfferOptionParser parse() {
        super.parse();

        // Super class will short-circuit parsing if help option is present.

        if (options.has(paymentAccountIdOpt)) {
            throw new IllegalArgumentException("the " + OPT_PAYMENT_ACCOUNT_ID
                    + " param is not used for swaps; the internal default swap account is always used");
        }

        if (options.has(takerFeeCurrencyCodeOpt)) {
            throw new IllegalArgumentException("the " + OPT_FEE_CURRENCY
                    + " param is not used for swaps; fees are always paid in bsq");
        }

        if (options.has(amountOpt)) {
            if (options.valueOf(amountOpt).isEmpty())
                throw new IllegalArgumentException("no intended btc trade amount specified");

            try {
                toSatoshis(options.valueOf(amountOpt));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("invalid amount: " + ex.getMessage());
            }
        }

        return this;
    }

    public String getAmount() {
        return options.valueOf(amountOpt);
    }
}
