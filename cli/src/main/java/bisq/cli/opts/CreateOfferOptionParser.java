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

import java.math.BigDecimal;

import static bisq.cli.opts.OptLabel.*;
import static joptsimple.internal.Strings.EMPTY;

public class CreateOfferOptionParser extends AbstractMethodOptionParser implements MethodOpts {

    final OptionSpec<String> paymentAccountIdOpt = parser.accepts(OPT_PAYMENT_ACCOUNT,
            "id of payment account used for offer")
            .withRequiredArg()
            .defaultsTo(EMPTY);

    final OptionSpec<String> directionOpt = parser.accepts(OPT_DIRECTION, "offer direction (buy|sell)")
            .withRequiredArg();

    final OptionSpec<String> currencyCodeOpt = parser.accepts(OPT_CURRENCY_CODE, "currency code (eur|usd|...)")
            .withRequiredArg();

    final OptionSpec<String> amountOpt = parser.accepts(OPT_AMOUNT, "amount of btc to buy or sell")
            .withRequiredArg();

    final OptionSpec<String> minAmountOpt = parser.accepts(OPT_MIN_AMOUNT, "minimum amount of btc to buy or sell")
            .withOptionalArg();

    final OptionSpec<String> mktPriceMarginOpt = parser.accepts(OPT_MKT_PRICE_MARGIN, "market btc price margin (%)")
            .withOptionalArg()
            .defaultsTo("0.00");

    final OptionSpec<String> fixedPriceOpt = parser.accepts(OPT_FIXED_PRICE, "fixed btc price")
            .withOptionalArg()
            .defaultsTo("0");

    final OptionSpec<String> securityDepositOpt = parser.accepts(OPT_SECURITY_DEPOSIT, "maker security deposit (%)")
            .withRequiredArg();

    final OptionSpec<String> makerFeeCurrencyCodeOpt = parser.accepts(OPT_FEE_CURRENCY, "maker fee currency code (bsq|btc)")
            .withOptionalArg()
            .defaultsTo("btc");

    public CreateOfferOptionParser(String[] args) {
        super(args);
    }

    public CreateOfferOptionParser parse() {
        super.parse();

        // Short circuit opt validation if user just wants help.
        if (options.has(helpOpt))
            return this;

        if (!options.has(paymentAccountIdOpt) || options.valueOf(paymentAccountIdOpt).isEmpty())
            throw new IllegalArgumentException("no payment account id specified");

        if (!options.has(directionOpt) || options.valueOf(directionOpt).isEmpty())
            throw new IllegalArgumentException("no direction (buy|sell) specified");

        if (!options.has(currencyCodeOpt) || options.valueOf(currencyCodeOpt).isEmpty())
            throw new IllegalArgumentException("no currency code specified");

        if (!options.has(amountOpt) || options.valueOf(amountOpt).isEmpty())
            throw new IllegalArgumentException("no btc amount specified");

        if (!options.has(mktPriceMarginOpt) && !options.has(fixedPriceOpt))
            throw new IllegalArgumentException("no market price margin or fixed price specified");

        if (options.has(mktPriceMarginOpt) && options.valueOf(mktPriceMarginOpt).isEmpty())
            throw new IllegalArgumentException("no market price margin specified");

        if (options.has(fixedPriceOpt) && options.valueOf(fixedPriceOpt).isEmpty())
            throw new IllegalArgumentException("no fixed price specified");

        if (!options.has(securityDepositOpt) || options.valueOf(securityDepositOpt).isEmpty())
            throw new IllegalArgumentException("no security deposit specified");

        return this;
    }

    public String getPaymentAccountId() {
        return options.valueOf(paymentAccountIdOpt);
    }

    public String getDirection() {
        return options.valueOf(directionOpt);
    }

    public String getCurrencyCode() {
        return options.valueOf(currencyCodeOpt);
    }

    public String getAmount() {
        return options.valueOf(amountOpt);
    }

    public String getMinAmount() {
        return options.has(minAmountOpt) ? options.valueOf(minAmountOpt) : getAmount();
    }

    public boolean isUsingMktPriceMargin() {
        return options.has(mktPriceMarginOpt);
    }

    @SuppressWarnings("unused")
    public String getMktPriceMargin() {
        return isUsingMktPriceMargin() ? options.valueOf(mktPriceMarginOpt) : "0.00";
    }

    public BigDecimal getMktPriceMarginAsBigDecimal() {
        return isUsingMktPriceMargin() ? new BigDecimal(options.valueOf(mktPriceMarginOpt)) : BigDecimal.ZERO;
    }

    public String getFixedPrice() {
        return options.has(fixedPriceOpt) ? options.valueOf(fixedPriceOpt) : "0.00";
    }

    public String getSecurityDeposit() {
        return options.valueOf(securityDepositOpt);
    }

    public String getMakerFeeCurrencyCode() {
        return options.has(makerFeeCurrencyCodeOpt) ? options.valueOf(makerFeeCurrencyCodeOpt) : "btc";
    }
}
