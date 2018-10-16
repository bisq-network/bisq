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

package bisq.core.dao.state.governance;

import bisq.core.locale.Res;

import bisq.common.proto.ProtoUtil;

import io.bisq.generated.protobuffer.PB;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * All parameters in the Bisq DAO which can be changed by voting.
 * We will add more on demand.
 * We need to support updates with new types in future. We use in the persisted data only the enum name, thus the names
 * must not change once the dao has started. Default values must not be changed as well.
 * For parameters which are used by Bisq users (trade fee,...) we have more strict requirements for backward compatibility.
 * Parameters which are only used in proposals and voting are less strict limited as we can require that those users are
 * using the latest software version.
 * The UNDEFINED entry is used as fallback for error cases and will get ignored.
 */
@Slf4j
public enum Param {
    UNDEFINED(0),

    // Fee in BSQ satoshi for a 1 BTC trade. 200 Satoshi = 2 BSQ = 0.02%.
    // About 2 USD if 1 BSQ = 1 USD for a 1 BTC trade which is about 10% of the BTC fee.,
    // Might need adjustment if BSQ/BTC rate changes.
    DEFAULT_MAKER_FEE_BSQ(200),     // 0.02%
    DEFAULT_TAKER_FEE_BSQ(200),
    // 0.05 BSQ (5 satoshi) for a 1 BTC trade. 0.05 USD if 1 BSQ = 1 USD, 10 % of the BTC fee
    MIN_MAKER_FEE_BSQ(5),           // 0.0005%.
    MIN_TAKER_FEE_BSQ(5),


    // Fee in BTC satoshi for a 1 BTC trade. 200_000 Satoshi =  0.00200000 BTC = 0.2%.
    // 20 USD at BTC price 10_000 USD for a 1 BTC trade;
    DEFAULT_MAKER_FEE_BTC(200_000),
    DEFAULT_TAKER_FEE_BTC(200_000),   // 0.2%
    MIN_MAKER_FEE_BTC(5_000),         // 0.005%.
    MIN_TAKER_FEE_BTC(5_000),

    // Fees proposal/voting. Atm we don't use diff. fees for diff. proposal types
    // See: https://github.com/bisq-network/proposals/issues/46
    PROPOSAL_FEE(200),          // 2 BSQ
    BLIND_VOTE_FEE(200),        // 2 BSQ

    // As BSQ based validation values can change over time if BSQ value rise we need to support that in the Params as well
    COMPENSATION_REQUEST_MIN_AMOUNT(1_000),         // 10 BSQ
    COMPENSATION_REQUEST_MAX_AMOUNT(10_000_000),    // 100 000 BSQ

    // Quorum required for voting result to be valid.
    // Quorum is the min. amount of total BSQ (earned+stake) which was used for voting on a request.
    // E.g. If only 2000 BSQ was used on a vote but 10 000 is required the result is invalid even if the voters voted
    // 100% for acceptance. This should prevent that changes can be done with low stakeholder participation.
    QUORUM_COMP_REQUEST(2_000_000),         // 20 000 BSQ
    QUORUM_CHANGE_PARAM(10_000_000),        // 100 000 BSQ
    QUORUM_ROLE(5_000_000),                 // 50 000 BSQ
    QUORUM_CONFISCATION(20_000_000),        // 200 000 BSQ
    QUORUM_GENERIC(500_000),                // 5 000 BSQ
    QUORUM_REMOVE_ASSET(1_000_000),         // 10 000 BSQ

    // Threshold for voting in % with precision of 2 (e.g. 5000 -> 50.00%)
    // This is the required amount of weighted vote result needed for acceptance of the result.
    // E.g. If the result ends up in 65% weighted vote for acceptance and threshold was 50% it is accepted.
    // The result must be larger than the threshold. A 50% vote result for a threshold with 50% is not sufficient,
    // it requires min. 50.01%.
    THRESHOLD_COMP_REQUEST(5_000),      // 50%
    THRESHOLD_CHANGE_PARAM(7_500),      // 75% That might change the THRESHOLD_CHANGE_PARAM and QUORUM_CHANGE_PARAM as well. So we have to be careful here!
    THRESHOLD_ROLE(5_000),              // 50%
    THRESHOLD_CONFISCATION(8_500),      // 85% Confiscation is considered an exceptional case and need very high consensus among the stakeholders.
    THRESHOLD_GENERIC(5_000),           // 50%
    THRESHOLD_REMOVE_ASSET(5_000),      // 50%

    //TODO add asset listing params (nr. of trades, volume, time, fee which defines listing state)

    // TODO for dev testing we use short periods...
    // Period phase (11 blocks atm)
    PHASE_UNDEFINED(0),
    PHASE_PROPOSAL(2),
    PHASE_BREAK1(1),
    PHASE_BLIND_VOTE(2),
    PHASE_BREAK2(1),
    PHASE_VOTE_REVEAL(2),
    PHASE_BREAK3(1),
    PHASE_RESULT(2);

    // See: https://github.com/bisq-network/proposals/issues/46
    /*
    PHASE_UNDEFINED(0),
    PHASE_PROPOSAL(3600),      // 24 days
    PHASE_BREAK1(150),        // 1 day
    PHASE_BLIND_VOTE(600),    // 4 days
    PHASE_BREAK2(10),        // 10 blocks
    PHASE_VOTE_REVEAL(300),   // 2 days
    PHASE_BREAK3(10),        // 10 blocks
    PHASE_RESULT(10);        // 10 block
    */

    @Getter
    private long defaultValue;

    /**
     * @param defaultValue for param. If not set it is 0.
     */
    Param(int defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getDisplayString() {
        return name().startsWith("PHASE_") ?
                Res.get("dao.phase." + name()) :
                Res.get("dao.param." + name());
    }

    public String getParamName() {
        String name;
        try {
            name = this.name();
        } catch (Throwable t) {
            log.error(t.toString());
            name = Param.UNDEFINED.name();
        }
        return name;
    }

    public static Param fromProto(PB.ChangeParamProposal proposalProto) {
        Param param;
        try {
            param = ProtoUtil.enumFromProto(Param.class, proposalProto.getParam());
            checkNotNull(param, "param must not be null");
        } catch (Throwable t) {
            log.error("fromProto: " + t.toString());
            param = Param.UNDEFINED;
        }
        return param;
    }
}
