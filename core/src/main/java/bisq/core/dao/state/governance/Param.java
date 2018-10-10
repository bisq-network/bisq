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

    // TODO trade fee is not implemented yet to be actually used.
    // FeeService is setting the fee atm....
    // Value of 100 means 1%, value of 1 means 0.01%
    BSQ_MAKER_FEE_IN_PERCENT(5),    // 0.05%
    BSQ_TAKER_FEE_IN_PERCENT(5),
    BTC_MAKER_FEE_IN_PERCENT(20),   // 0.2% base fee for 1% distance to market price
    BTC_TAKER_FEE_IN_PERCENT(20),   // 0.2%

    // Fees proposal/voting. Atm we don't use diff. fees for diff. proposal types
    // See https://github.com/bisq-network/proposals/issues/46
    PROPOSAL_FEE(200),          // 2 BSQ
    BLIND_VOTE_FEE(200),        // 2 BSQ

    // As BSQ based validation values can change over time if BSQ value rise we need to support that in the Params as well
    COMPENSATION_REQUEST_MIN_AMOUNT(1_000),         // 10 BSQ
    COMPENSATION_REQUEST_MAX_AMOUNT(10_000_000),    // 100 000 BSQ

    // Quorum for voting in BSQ stake
    QUORUM_COMP_REQUEST(100),       // 10 000 BSQ  TODO change low dev value
    QUORUM_CHANGE_PARAM(300),       // 100 000 BSQ TODO change low dev value
    QUORUM_ROLE(500),       // 10 000 BSQ  TODO change low dev value
    QUORUM_CONFISCATION(500),       // 10 000 BSQ  TODO change low dev value
    QUORUM_GENERIC(100),           // 10 000 BSQ  TODO change low dev value
    QUORUM_REMOVE_ASSET(400),       // 10 000 BSQ  TODO change low dev value

    // Threshold for voting in % with precision of 2 (e.g. 5000 -> 50.00%)
    THRESHOLD_COMP_REQUEST(5_000),      // 50%
    THRESHOLD_CHANGE_PARAM(7_500),      // 75% -> that might change the THRESHOLD_CHANGE_PARAM and QUORUM_CHANGE_PARAM!
    THRESHOLD_ROLE(5_000),      // 50%
    THRESHOLD_CONFISCATION(8_500),      // 85%
    THRESHOLD_GENERIC(5_000),          // 50%
    THRESHOLD_REMOVE_ASSET(5_000),      // 50%

    //TODO add asset listing params (nr. of trades, volume, time, fee which defines listing state)

    // Period phase (16 blocks atm)
    PHASE_UNDEFINED(0),
    PHASE_PROPOSAL(2),      // 24 days
    PHASE_BREAK1(1),        // 10 blocks
    PHASE_BLIND_VOTE(2),    // 4 days
    PHASE_BREAK2(1),        // 10 blocks
    PHASE_VOTE_REVEAL(2),   // 2 days
    PHASE_BREAK3(1),        // 10 blocks
    PHASE_RESULT(2);        // 1 block

    // See https://github.com/bisq-network/proposals/issues/46
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
