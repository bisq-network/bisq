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

package bisq.cli.table.builder;

/**
 * Used as param in TableBuilder constructor instead of inspecting
 * protos to find out what kind of CLI output table should be built.
 */
public enum TableType {
    ADDRESS_BALANCE_TBL,
    BSQ_BALANCE_TBL,
    BTC_BALANCE_TBL,
    CLOSED_TRADES_TBL,
    FAILED_TRADES_TBL,
    OFFER_TBL,
    OPEN_TRADES_TBL,
    PAYMENT_ACCOUNT_TBL,
    TRADE_DETAIL_TBL,
    TRANSACTION_TBL
}
