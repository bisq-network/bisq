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

public class TakeOfferOptionParser extends AbstractMethodOptionParser implements MethodOpts {

    final OptionSpec<String> offerIdOpt = parser.accepts("o", "id of offer to take")
            .withRequiredArg()
            .defaultsTo(EMPTY);

    final OptionSpec<String> paymentAccountIdOpt = parser.accepts("p", "id of payment account used for trade")
            .withRequiredArg()
            .defaultsTo(EMPTY);

    final OptionSpec<String> takerFeeCurrencyCodeOpt = parser.accepts("c", "taker fee currency code (bsq|btc)")
            .withOptionalArg()
            .defaultsTo("btc");

    public TakeOfferOptionParser(String[] args) {
        super(args);
    }

    public TakeOfferOptionParser parse() {
        super.parse();

        // Short circuit opt validation if user just wants help.
        if (options.has(helpOpt))
            return this;

        if (!options.has(offerIdOpt))
            throw new IllegalArgumentException("no offer id specified");

        if (!options.has(paymentAccountIdOpt))
            throw new IllegalArgumentException("no payment account id specified");

        return this;
    }

    public String getOfferId() {
        return options.valueOf(offerIdOpt);
    }

    public String getPaymentAccountId() {
        return options.valueOf(paymentAccountIdOpt);
    }

    public String getTakerFeeCurrencyCode() {
        return options.has(takerFeeCurrencyCodeOpt) ? options.valueOf(takerFeeCurrencyCodeOpt) : "btc";
    }
}
