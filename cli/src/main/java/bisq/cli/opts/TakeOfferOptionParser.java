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

import static bisq.cli.opts.OptLabel.OPT_FEE_CURRENCY;
import static bisq.cli.opts.OptLabel.OPT_PAYMENT_ACCOUNT_ID;

public class TakeOfferOptionParser extends OfferIdOptionParser implements MethodOpts {

    final OptionSpec<String> paymentAccountIdOpt = parser.accepts(OPT_PAYMENT_ACCOUNT_ID, "id of payment account used for trade")
            .withRequiredArg();

    final OptionSpec<String> takerFeeCurrencyCodeOpt = parser.accepts(OPT_FEE_CURRENCY, "taker fee currency code (bsq|btc)")
            .withOptionalArg()
            .defaultsTo("btc");

    public TakeOfferOptionParser(String[] args) {
        super(args, true);
    }

    public TakeOfferOptionParser parse() {
        super.parse();

        // Super class will short-circuit parsing if help option is present.

        if (!options.has(paymentAccountIdOpt) || options.valueOf(paymentAccountIdOpt).isEmpty())
            throw new IllegalArgumentException("no payment account id specified");

        return this;
    }

    public String getPaymentAccountId() {
        return options.valueOf(paymentAccountIdOpt);
    }

    public String getTakerFeeCurrencyCode() {
        return options.has(takerFeeCurrencyCodeOpt) ? options.valueOf(takerFeeCurrencyCodeOpt) : "btc";
    }
}
