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

package bisq.apitest.botsupport.opts;

import joptsimple.OptionSpec;

import static bisq.apitest.botsupport.opts.BotOptLabel.*;



import bisq.cli.opts.AbstractMethodOptionParser;
import bisq.cli.opts.MethodOpts;

public class BsqMarketMakerBotOptionParser extends AbstractMethodOptionParser implements MethodOpts {

    final OptionSpec<String> targetPriceOpt = parser.accepts(OPT_TARGET_PRICE,
            "target price in btc for 1 bsq")
            .withRequiredArg();

    final OptionSpec<String> targetBtcAmountOpt = parser.accepts(OPT_TARGET_BTC_AMOUNT,
            "target btc amount used to buy or sell bsq")
            .withRequiredArg();

    final OptionSpec<String> targetSpreadOpt = parser.accepts(OPT_TARGET_SPREAD,
            "mm bot target spread")
            .withRequiredArg();

    final OptionSpec<Integer> tradeCycleLimitOpt = parser.accepts(OPT_TRADE_CYCLE_LIMIT,
            "mm bot trade limit (1 sell plus 1 buy = count 1")
            .withRequiredArg()
            .ofType(Integer.class)
            .defaultsTo(1);

    final OptionSpec<Integer> newBsqPaymentAccountsLimitOpt = parser.accepts(OPT_NEW_PAYMENT_ACCOUNTS_LIMIT,
            "limit # of new bsq payment account created by mm bot")
            .withRequiredArg()
            .ofType(Integer.class)
            .defaultsTo(5);

    public BsqMarketMakerBotOptionParser(String[] args) {
        super(args);
    }

    public BsqMarketMakerBotOptionParser parse() {
        super.parse();

        // Short circuit opt validation if user just wants help.
        if (options.has(helpOpt))
            return this;

        if (!options.has(targetPriceOpt) || options.valueOf(targetPriceOpt).isEmpty())
            throw new IllegalArgumentException("no target price specified");

        if (!options.has(targetBtcAmountOpt) || options.valueOf(targetBtcAmountOpt).isEmpty())
            throw new IllegalArgumentException("no target btc amount specified");

        if (!options.has(targetSpreadOpt) || options.valueOf(targetSpreadOpt).isEmpty())
            throw new IllegalArgumentException("no target spread specified");

        if (!options.has(tradeCycleLimitOpt))
            throw new IllegalArgumentException("no trade cycle limit specified");

        return this;
    }

    public String getTargetPrice() {
        return options.valueOf(targetPriceOpt);
    }

    public String getTargetBtcAmount() {
        return options.valueOf(targetBtcAmountOpt);
    }

    public String getTargetSpread() {
        return options.valueOf(targetSpreadOpt);
    }

    public int getTradeCycleLimit() {
        return options.valueOf(tradeCycleLimitOpt);
    }

    public int getNewBsqPaymentAccountsLimit() {
        return options.valueOf(newBsqPaymentAccountsLimitOpt);
    }
}
