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

import bisq.cli.opts.OptLabel;

/**
 * Bot opt label definitions.
 */
public class BotOptLabel extends OptLabel {
    public final static String OPT_TARGET_BTC_AMOUNT = "target-btc-amount";
    public final static String OPT_TARGET_PRICE = "target-price";
    public final static String OPT_TARGET_SPREAD = "target-spread";
    public final static String OPT_TRADE_CYCLE_LIMIT = "trade-cycle-limit";
    public final static String OPT_NEW_PAYMENT_ACCOUNTS_LIMIT = "new-payment-accts-limit";
}
