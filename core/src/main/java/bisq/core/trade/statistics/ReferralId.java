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

package bisq.core.trade.statistics;

/**
 * Those are random ids which can be assigned to a market maker or API provider who generates trade volume for Bisq.
 *
 * The assignment process is that a partner requests a referralId from the core developers and if accepted they get
 * assigned an ID. With the ID we can quantify the generated trades from that partner from analysing the trade
 * statistics. Compensation requests will be based on that data.
 */
public enum ReferralId {
    REF_ID_342,
    REF_ID_768,
    REF_ID_196,
    REF_ID_908,
    REF_ID_023,
    REF_ID_605,
    REF_ID_896,
    REF_ID_183
}
