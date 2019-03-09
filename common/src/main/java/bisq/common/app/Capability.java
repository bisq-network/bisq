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

package bisq.common.app;

// We can define here special features the client is supporting.
// Useful for updates to new versions where a new data type would break backwards compatibility or to
// limit a node to certain behaviour and roles like the seed nodes.
// We don't use the Enum in any serialized data, as changes in the enum would break backwards compatibility. We use the ordinal integer instead.
// Sequence in the enum must not be changed (append only).
public enum Capability {
    TRADE_STATISTICS,
    TRADE_STATISTICS_2,
    ACCOUNT_AGE_WITNESS,
    SEED_NODE,
    DAO_FULL_NODE,
    PROPOSAL,
    BLIND_VOTE,
    ACK_MSG,
    BSQ_BLOCK,
    DAO_STATE
}
