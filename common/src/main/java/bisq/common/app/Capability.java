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
// We don't use the Enum in any serialized data, as changes in the enum would break backwards compatibility.
// We use the ordinal integer instead.
// Sequence in the enum must not be changed (append only).
public enum Capability {
    @Deprecated TRADE_STATISTICS,       // Not required anymore as no old clients out there not having that support
    @Deprecated TRADE_STATISTICS_2,     // Not required anymore as no old clients out there not having that support
    @Deprecated ACCOUNT_AGE_WITNESS,    // Not required anymore as no old clients out there not having that support
    SEED_NODE,                          // Node is a seed node
    DAO_FULL_NODE,                      // DAO full node can deliver BSQ blocks
    @Deprecated PROPOSAL,               // Not required anymore as no old clients out there not having that support
    @Deprecated BLIND_VOTE,             // Not required anymore as no old clients out there not having that support
    @Deprecated ACK_MSG,                // Not required anymore as no old clients out there not having that support
    RECEIVE_BSQ_BLOCK,                  // Signaling that node which wants to receive BSQ blocks (DAO lite node)
    @Deprecated DAO_STATE,              // Not required anymore as no old clients out there not having that support

    @Deprecated BUNDLE_OF_ENVELOPES,    // Supports bundling of messages if many messages are sent in short interval

    SIGNED_ACCOUNT_AGE_WITNESS,         // Supports the signed account age witness feature
    MEDIATION,                          // Supports mediation feature
    REFUND_AGENT,                       // Supports refund agents
    TRADE_STATISTICS_HASH_UPDATE,       // We changed the hash method in 1.2.0 and that requires update to 1.2.2 for handling it correctly, otherwise the seed nodes have to process too much data.
    NO_ADDRESS_PRE_FIX,                 // At 1.4.0 we removed the prefix filter for mailbox messages. If a peer has that capability we do not sent the prefix.
    TRADE_STATISTICS_3,                 // We used a new reduced trade statistics model from v1.4.0 on
    BSQ_SWAP_OFFER                     // Supports new message type BsqSwapOffer
}
