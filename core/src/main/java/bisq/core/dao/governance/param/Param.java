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

package bisq.core.dao.governance.param;

import bisq.core.locale.Res;

import bisq.common.config.Config;
import bisq.common.proto.ProtoUtil;

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
 *
 * Name of the params must not change as that is used for serialisation in Protobuffer. The data fields are not part of
 * the PB serialisation so changes for those would not change the hash for the dao state hash chain.
 * Though changing the values might break consensus as the validations might return a different result (e.g. a param
 * change proposal was accepted with older min/max values but then after a change it is not valid anymore and
 * might break the consequences of that change. So in fact we MUST not change anything here, only way is to add new
 * entries and don't use the deprecated enum in future releases anymore.
 */
@Slf4j
public enum Param {
    UNDEFINED("N/A", ParamType.UNDEFINED),

    // Fee in BTC for a 1 BTC trade. 0.001 is 0.1%. @5000 USD/BTC price 0.1% fee is 5 USD.
    DEFAULT_MAKER_FEE_BTC("0.001", ParamType.BTC, 5, 5),
    DEFAULT_TAKER_FEE_BTC("0.003", ParamType.BTC, 5, 5),       // 0.2% of trade amount
    MIN_MAKER_FEE_BTC("0.00005", ParamType.BTC, 5, 5),         // 0.005% of trade amount
    MIN_TAKER_FEE_BTC("0.00005", ParamType.BTC, 5, 5),

    // Fee in BSQ satoshis for a 1 BTC trade. 100 satoshis = 1 BSQ
    // If 1 BTS is 1 USD the fee @5000 USD/BTC is 0.5 USD which is 10% of the BTC fee of 5 USD.
    // Might need adjustment if BSQ/BTC rate changes.
    DEFAULT_MAKER_FEE_BSQ("0.50", ParamType.BSQ, 5, 5),     // ~ 0.01% of trade amount
    DEFAULT_TAKER_FEE_BSQ("1.5", ParamType.BSQ, 5, 5),
    // Min fee is the  smallest fee allowed for a trade. If the default fee would be less than min fee the
    // min fee is used instead.
    // 0.03 BSQ (3 satoshis) for a 1 BTC trade. 0.05 USD if 1 BSQ = 1 USD, 10 % of the BTC fee
    MIN_MAKER_FEE_BSQ("0.03", ParamType.BSQ, 5, 5),           // 0.0003%.
    MIN_TAKER_FEE_BSQ("0.03", ParamType.BSQ, 5, 5),

    // Fees proposal/voting. Atm we don't use diff. fees for diff. proposal types
    // See: https://github.com/bisq-network/proposals/issues/46
    PROPOSAL_FEE("2", ParamType.BSQ, 5, 5),          // 2 BSQ
    BLIND_VOTE_FEE("2", ParamType.BSQ, 5, 5),        // 2 BSQ

    // As BSQ based validation values can change over time if BSQ value rise we need to support that in the Params as well
    COMPENSATION_REQUEST_MIN_AMOUNT("10", ParamType.BSQ, 4, 2),
    COMPENSATION_REQUEST_MAX_AMOUNT("100000", ParamType.BSQ, 4, 2),
    REIMBURSEMENT_MIN_AMOUNT("10", ParamType.BSQ, 4, 2),
    REIMBURSEMENT_MAX_AMOUNT("10000", ParamType.BSQ, 4, 2),

    // Quorum required for voting result to be valid.
    // Quorum is the min. amount of total BSQ (earned+stake) which was used for voting on a request.
    // E.g. If only 2000 BSQ was used on a vote but 10 000 is required the result is invalid even if the voters voted
    // 100% for acceptance. This should prevent that changes can be done with low stakeholder participation.
    QUORUM_COMP_REQUEST("20000", ParamType.BSQ, 2, 2),
    QUORUM_REIMBURSEMENT("20000", ParamType.BSQ, 2, 2),
    QUORUM_CHANGE_PARAM("100000", ParamType.BSQ, 2, 2),
    QUORUM_ROLE("50000", ParamType.BSQ, 2, 2),
    QUORUM_CONFISCATION("200000", ParamType.BSQ, 2, 2),
    QUORUM_GENERIC("5000", ParamType.BSQ, 2, 2),
    QUORUM_REMOVE_ASSET("10000", ParamType.BSQ, 2, 2),

    // Threshold for voting in % with precision of 2 (e.g. 5000 -> 50.00%)
    // This is the required amount of weighted vote result needed for acceptance of the result.
    // E.g. If the result ends up in 65% weighted vote for acceptance and threshold was 50% it is accepted.
    // The result must be larger than the threshold. A 50% vote result for a threshold with 50% is not sufficient,
    // it requires min. 50.01%.
    // The maxDecrease value is only relevant if the decreased value will not result in a value below 50.01%.
    THRESHOLD_COMP_REQUEST("50.01", ParamType.PERCENT, 1.2, 1.2),
    THRESHOLD_REIMBURSEMENT("50.01", ParamType.PERCENT, 1.2, 1.2),
    THRESHOLD_CHANGE_PARAM("75.01", ParamType.PERCENT, 1.2, 1.2),      // That might change the THRESHOLD_CHANGE_PARAM and QUORUM_CHANGE_PARAM as well. So we have to be careful here!
    THRESHOLD_ROLE("50.01", ParamType.PERCENT, 1.2, 1.2),
    THRESHOLD_CONFISCATION("85.01", ParamType.PERCENT, 1.2, 1.2),      // Confiscation is considered an exceptional case and need very high consensus among the stakeholders.
    THRESHOLD_GENERIC("50.01", ParamType.PERCENT, 1.2, 1.2),
    THRESHOLD_REMOVE_ASSET("50.01", ParamType.PERCENT, 1.2, 1.2),

    // BTC address as recipient for BTC trade fee once the arbitration system is replaced as well as destination for
    // the time locked payout tx in case the traders do not cooperate. Will be likely a donation address (Bisq, Tor,...)
    // but can be also a burner address if we prefer to burn the BTC
    @SuppressWarnings("SpellCheckingInspection")
    RECIPIENT_BTC_ADDRESS(Config.baseCurrencyNetwork().isMainnet() ? "1BVxNn3T12veSK6DgqwU4Hdn7QHcDDRag7" :  // mainnet
            Config.baseCurrencyNetwork().isDaoBetaNet() ? "1BVxNn3T12veSK6DgqwU4Hdn7QHcDDRag7" :  // daoBetaNet
                    Config.baseCurrencyNetwork().isTestnet() ? "2N4mVTpUZAnhm9phnxB7VrHB4aBhnWrcUrV" : // testnet
                            "2MzBNTJDjjXgViKBGnatDU3yWkJ8pJkEg9w", // regtest or DAO testnet (regtest)
            ParamType.ADDRESS),

    // Fee for activating an asset or re-listing after deactivation due lack of trade activity. Fee per day of trial period without activity checks.
    ASSET_LISTING_FEE_PER_DAY("1", ParamType.BSQ, 10, 10),
    // Min required trade volume to not get de-listed. Check starts after trial period and use trial period afterwards to look back for trade activity.
    ASSET_MIN_VOLUME("0.01", ParamType.BTC, 10, 10),

    LOCK_TIME_TRADE_PAYOUT("4320", ParamType.BLOCK), // 30 days, can be disabled by setting to 0
    ARBITRATOR_FEE("0", ParamType.PERCENT),  // % of trade. For new trade protocol. Arbitration will become optional and we can apply a fee to it. Initially we start with no fee.
    MAX_TRADE_LIMIT("2", ParamType.BTC, 2, 2), // max trade limit for lowest risk payment method. Others will get derived from that.

    // The base factor to multiply the bonded role amount. E.g. If Twitter admin has 20 as amount and BONDED_ROLE_FACTOR is 1000 we get 20000 BSQ as required bond.
    // Using BSQ as type is not really the best option but we don't want to introduce a new ParamType just for that one Param.
    // As the end rules is in fact BSQ it is not completely incorrect as well.
    BONDED_ROLE_FACTOR("1000", ParamType.BSQ, 2, 2),
    ISSUANCE_LIMIT("200000", ParamType.BSQ, 2, 2), // Max. issuance+reimbursement per cycle.

    // The last block in the proposal and vote phases are not shown to the user as he cannot make a tx there as it would be
    // confirmed in the next block which would be the following break phase. To hide that complexity we show only the
    // blocks where the user can be active. To have still round numbers for the durations we add 1 block to those
    // phases and subtract 1 block from the following breaks.
    // So in the UI the user will see 3600 blocks and the last
    // block of the technical 3601 blocks is displayed as part of the break1 phase.
    // For testnet we want to have a short cycle of about a week (1012 blocks)
    // For regtest we use very short periods
    PHASE_UNDEFINED("0", ParamType.BLOCK),
    PHASE_PROPOSAL(Config.baseCurrencyNetwork().isMainnet() ?
            "3601" :    // mainnet; 24 days
            Config.baseCurrencyNetwork().isRegtest() ?
                    "4" :       // regtest
                    Config.baseCurrencyNetwork().isDaoBetaNet() ?
                            "144" :       // daoBetaNet; 1 day
                            Config.baseCurrencyNetwork().isDaoRegTest() ?
                                    "134" :      // dao regtest; 0.93 days
                                    "380",       // testnet or dao testnet (server side regtest); 2.6 days
            ParamType.BLOCK, 2, 2),
    PHASE_BREAK1(Config.baseCurrencyNetwork().isMainnet() ?
            "149" :     // mainnet; 1 day
            Config.baseCurrencyNetwork().isRegtest() ?
                    "1" :       // regtest
                    Config.baseCurrencyNetwork().isDaoBetaNet() ?
                            "10" :       // daoBetaNet
                            Config.baseCurrencyNetwork().isDaoRegTest() ?
                                    "10" :      // dao regtest
                                    "10",       // testnet or dao testnet (server side regtest)
            ParamType.BLOCK, 2, 2),
    PHASE_BLIND_VOTE(Config.baseCurrencyNetwork().isMainnet() ?
            "451" :     // mainnet; 3 days
            Config.baseCurrencyNetwork().isRegtest() ?
                    "2" :       // regtest
                    Config.baseCurrencyNetwork().isDaoBetaNet() ?
                            "144" :       // daoBetaNet; 1 day
                            Config.baseCurrencyNetwork().isDaoRegTest() ?
                                    "134" :      // dao regtest; 0.93 days
                                    "300",      // testnet or dao testnet (server side regtest); 2 days
            ParamType.BLOCK, 2, 2),
    PHASE_BREAK2(Config.baseCurrencyNetwork().isMainnet() ?
            "9" :       // mainnet
            Config.baseCurrencyNetwork().isRegtest() ?
                    "1" :       // regtest
                    Config.baseCurrencyNetwork().isDaoBetaNet() ?
                            "10" :       // daoBetaNet
                            Config.baseCurrencyNetwork().isDaoRegTest() ?
                                    "10" :      // dao regtest
                                    "10",       // testnet or dao testnet (server side regtest)
            ParamType.BLOCK, 2, 2),
    PHASE_VOTE_REVEAL(Config.baseCurrencyNetwork().isMainnet() ?
            "451" :     // mainnet; 3 days
            Config.baseCurrencyNetwork().isRegtest() ?
                    "2" :       // regtest
                    Config.baseCurrencyNetwork().isDaoBetaNet() ?
                            "144" :       // daoBetaNet; 1 day
                            Config.baseCurrencyNetwork().isDaoRegTest() ?
                                    "132" :      // dao regtest; 0.93 days
                                    "300",      // testnet or dao testnet (server side regtest); 2 days
            ParamType.BLOCK, 2, 2),
    PHASE_BREAK3(Config.baseCurrencyNetwork().isMainnet() ?
            "9" :       // mainnet
            Config.baseCurrencyNetwork().isRegtest() ?
                    "1" :       // regtest
                    Config.baseCurrencyNetwork().isDaoBetaNet() ?
                            "10" :       // daoBetaNet
                            Config.baseCurrencyNetwork().isDaoRegTest() ?
                                    "10" :      // dao regtest
                                    "10",       // testnet or dao testnet (server side regtest)
            ParamType.BLOCK, 2, 2),
    PHASE_RESULT(Config.baseCurrencyNetwork().isMainnet() ?
            "10" :      // mainnet
            Config.baseCurrencyNetwork().isRegtest() ?
                    "2" :       // regtest
                    Config.baseCurrencyNetwork().isDaoBetaNet() ?
                            "10" :       // daoBetaNet
                            Config.baseCurrencyNetwork().isDaoRegTest() ?
                                    "2" :      // dao regtest
                                    "2",        // testnet or dao testnet (server side regtest)
            ParamType.BLOCK, 2, 2);

    @Getter
    private final String defaultValue;
    @Getter
    private final ParamType paramType;
    // If 0 we ignore check for max decrease
    @Getter
    private final double maxDecrease;
    // If 0 we ignore check for max increase
    @Getter
    private final double maxIncrease;

    Param(String defaultValue, ParamType paramType) {
        this(defaultValue, paramType, 0, 0);
    }

    /**
     * @param defaultValue  Value at the start of the DAO
     * @param paramType     Type of parameter
     * @param maxDecrease   Decrease of param value limited to current value / maxDecrease. If 0 we don't apply the check and any change is possible
     * @param maxIncrease   Increase of param value limited to current value * maxIncrease. If 0 we don't apply the check and any change is possible
     */
    Param(String defaultValue, ParamType paramType, double maxDecrease, double maxIncrease) {
        this.defaultValue = defaultValue;
        this.paramType = paramType;
        this.maxDecrease = maxDecrease;
        this.maxIncrease = maxIncrease;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Param fromProto(protobuf.ChangeParamProposal proposalProto) {
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

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getDisplayString() {
        return name().startsWith("PHASE_") ?
                Res.get("dao.phase." + name()) :
                Res.get("dao.param." + name());
    }
}
