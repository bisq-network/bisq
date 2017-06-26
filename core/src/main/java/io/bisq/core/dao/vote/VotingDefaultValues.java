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

package io.bisq.core.dao.vote;

import io.bisq.common.locale.Res;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import static com.google.common.base.Preconditions.checkArgument;

public class VotingDefaultValues {
    private static final Logger log = LoggerFactory.getLogger(VotingDefaultValues.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Default values at genesis
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static final long MAKER_FEE_IN_BTC_AT_GENESIS = 30_000;
    public static final long TAKER_FEE_IN_BTC_AT_GENESIS = 80_000;
    public static final long MAKER_FEE_IN_BSQ_AT_GENESIS = 333;
    public static final long TAKER_FEE_IN_BSQ_AT_GENESIS = 444;
    public static final long CREATE_COMPENSATION_REQUEST_FEE_IN_BSQ_AT_GENESIS = 7777;
    public static final long VOTING_FEE_IN_BSQ_AT_GENESIS = 8888;

    // 144 blocks is 1 day
    public static final long COMPENSATION_REQUEST_PERIOD_IN_BLOCKS_AT_GENESIS = 2880; // 20 days
    public static final long VOTING_PERIOD_IN_BLOCKS_AT_GENESIS = 432; // 3 days
    public static final long FUNDING_PERIOD_IN_BLOCKS_AT_GENESIS = 1008; // 7 days
    public static final long BREAK_BETWEEN_PERIODS_IN_BLOCKS_AT_GENESIS = 10;

    public static final long QUORUM_FOR_COMPENSATION_REQUEST_VOTING_AT_GENESIS = 500; // 5%
    public static final long QUORUM_FOR_PARAMETER_VOTING_AT_GENESIS = 500; // 5%

    public static final long MIN_BSQ_AMOUNT_COMPENSATION_REQUEST_AT_GENESIS = 20; // TODO hard to estimate....
    public static final long MAX_BSQ_AMOUNT_COMPENSATION_REQUEST_AT_GENESIS = 20_000; // TODO hard to estimate....

    //TODO remove
    public static final long CONVERSION_RATE_AT_GENESIS = 1000; //0.000001; // how many btc you get for 1 squ (e.g. 1 000 000 squ = 1 btc)
    public static final String ERROR_MSG_INVALID_VALUE = Res.get("dao.voting.error.invalidValue");


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private long makerFeeInBtc = MAKER_FEE_IN_BTC_AT_GENESIS;
    private long takerFeeInBtc = TAKER_FEE_IN_BTC_AT_GENESIS;
    private long makerFeeInBsq = MAKER_FEE_IN_BSQ_AT_GENESIS;
    private long takerFeeInBsq = TAKER_FEE_IN_BSQ_AT_GENESIS;
    private long createCompensationRequestFeeInBsq = CREATE_COMPENSATION_REQUEST_FEE_IN_BSQ_AT_GENESIS;
    private long votingFeeInBsq = VOTING_FEE_IN_BSQ_AT_GENESIS;

    private long compensationRequestPeriodInBlocks = COMPENSATION_REQUEST_PERIOD_IN_BLOCKS_AT_GENESIS;
    private long votingPeriodInBlocks = VOTING_PERIOD_IN_BLOCKS_AT_GENESIS;
    private long fundingPeriodInBlocks = FUNDING_PERIOD_IN_BLOCKS_AT_GENESIS;
    private long breakBetweenPeriodsInBlocks = BREAK_BETWEEN_PERIODS_IN_BLOCKS_AT_GENESIS;

    private long quorumForCompensationRequestVoting = QUORUM_FOR_COMPENSATION_REQUEST_VOTING_AT_GENESIS;
    private long quorumForParameterVoting = QUORUM_FOR_PARAMETER_VOTING_AT_GENESIS;

    private long minBtcAmountCompensationRequest = MIN_BSQ_AMOUNT_COMPENSATION_REQUEST_AT_GENESIS;
    private long maxBtcAmountCompensationRequest = MAX_BSQ_AMOUNT_COMPENSATION_REQUEST_AT_GENESIS;

    private long conversionRate = CONVERSION_RATE_AT_GENESIS;

    private boolean voteResultsApplied;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public VotingDefaultValues() {

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public VotingType getVotingTypeByCode(Byte code) {
        for (int i = 0; i < VotingType.values().length; i++) {
            VotingType votingType = VotingType.values()[i];
            if (votingType.code.equals(code))
                return votingType;
        }
        return null;
    }

    public long getValueByVotingType(VotingType votingType) {
        switch (votingType) {
            case MAKER_FEE_IN_BTC:
                return makerFeeInBtc;
            case TAKER_FEE_IN_BTC:
                return takerFeeInBtc;
            case MAKER_FEE_IN_BSQ:
                return makerFeeInBsq;
            case TAKER_FEE_IN_BSQ:
                return takerFeeInBsq;
            case CREATE_COMPENSATION_REQUEST_FEE_IN_BSQ:
                return createCompensationRequestFeeInBsq;
            case VOTING_FEE_IN_BSQ:
                return votingFeeInBsq;

            case COMPENSATION_REQUEST_PERIOD_IN_BLOCKS:
                return compensationRequestPeriodInBlocks;
            case VOTING_PERIOD_IN_BLOCKS:
                return votingPeriodInBlocks;
            case FUNDING_PERIOD_IN_BLOCKS:
                return fundingPeriodInBlocks;
            case BREAK_BETWEEN_PERIODS_IN_BLOCKS:
                return breakBetweenPeriodsInBlocks;

            case QUORUM_FOR_COMPENSATION_REQUEST_VOTING:
                return quorumForCompensationRequestVoting;
            case QUORUM_FOR_PARAMETER_VOTING:
                return quorumForParameterVoting;

            case MIN_BTC_AMOUNT_COMPENSATION_REQUEST:
                return minBtcAmountCompensationRequest;
            case MAX_BTC_AMOUNT_COMPENSATION_REQUEST:
                return maxBtcAmountCompensationRequest;

            case CONVERSION_RATE:
                return conversionRate;

            case COMP_REQUEST_MAPS:
                log.error("COMP_REQUEST_MAPS not supported at getValueByVotingType");
                return -1;

            default:
                log.error("Not supported code at getValueByVotingType: " + votingType);
                return -1;
        }
    }

    public void setValueByVotingType(VotingType votingType, long value) {
        switch (votingType) {
            case MAKER_FEE_IN_BTC:
                makerFeeInBtc = value;
                break;
            case TAKER_FEE_IN_BTC:
                takerFeeInBtc = value;
                break;
            case MAKER_FEE_IN_BSQ:
                makerFeeInBsq = value;
                break;
            case TAKER_FEE_IN_BSQ:
                takerFeeInBsq = value;
                break;
            case CREATE_COMPENSATION_REQUEST_FEE_IN_BSQ:
                createCompensationRequestFeeInBsq = value;
                break;
            case VOTING_FEE_IN_BSQ:
                votingFeeInBsq = value;
                break;

            case COMPENSATION_REQUEST_PERIOD_IN_BLOCKS:
                compensationRequestPeriodInBlocks = value;
                break;
            case VOTING_PERIOD_IN_BLOCKS:
                votingPeriodInBlocks = value;
                break;
            case FUNDING_PERIOD_IN_BLOCKS:
                fundingPeriodInBlocks = value;
                break;
            case BREAK_BETWEEN_PERIODS_IN_BLOCKS:
                breakBetweenPeriodsInBlocks = value;
                break;

            case QUORUM_FOR_COMPENSATION_REQUEST_VOTING:
                quorumForCompensationRequestVoting = value;
                break;
            case QUORUM_FOR_PARAMETER_VOTING:
                quorumForParameterVoting = value;
                break;

            case MIN_BTC_AMOUNT_COMPENSATION_REQUEST:
                minBtcAmountCompensationRequest = value;
                break;
            case MAX_BTC_AMOUNT_COMPENSATION_REQUEST:
                maxBtcAmountCompensationRequest = value;
                break;

            case CONVERSION_RATE:
                conversionRate = value;
                break;

            default:
                log.error("Not supported code at setValueByCode: " + votingType);
        }
    }

    // Interger value of byte values in java are split in 1 half positive and second half negative:
    // 0-127 is as expected, then it continues with -128 up to -1 for values 128-255. 
    // So we add 256 for values after 127 to get a continuous 0-255 range. 
    public long getAdjustedValue(long originalValue, Byte change) {
        int intValue = change.intValue();
        if (intValue < 0)
            intValue += 256;
        checkArgument(intValue < 256 && intValue > -1,
                ERROR_MSG_INVALID_VALUE + change);
        return getAdjustedValue(originalValue, intValue);
    }

    // We adjust in the range of 0.1*originalValue up to 10*originalValue
    public long getAdjustedValue(long originalValue, long change) {
        checkArgument(change < 255 && change > -1,
                ERROR_MSG_INVALID_VALUE + change);
        double fact = (change - 127) / 127d;
        return (long) (originalValue * Math.pow(10, fact));
    }

    // We return the change parameter (0-254)
    public long getChange(long originalValue, long newValue) {
        return Math.round(Math.log10((double) newValue / (double) originalValue) * 127 + 127);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters/Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean getVoteResultsApplied() {
        return voteResultsApplied;
    }

    public void setVoteResultsApplied(boolean voteResultsApplied) {
        this.voteResultsApplied = voteResultsApplied;
    }

    public long getMaxBtcAmountCompensationRequest() {
        return maxBtcAmountCompensationRequest;
    }

    public void setMaxBtcAmountCompensationRequest(long maxBtcAmountCompensationRequest) {
        this.maxBtcAmountCompensationRequest = maxBtcAmountCompensationRequest;
    }

    public long getMakerFeeInBtc() {
        return makerFeeInBtc;
    }

    public void setMakerFeeInBtc(long makerFeeInBtc) {
        this.makerFeeInBtc = makerFeeInBtc;
    }

    public long getTakerFeeInBtc() {
        return takerFeeInBtc;
    }

    public void setTakerFeeInBtc(long takerFeeInBtc) {
        this.takerFeeInBtc = takerFeeInBtc;
    }

    public long getCreateCompensationRequestFeeInBsq() {
        return createCompensationRequestFeeInBsq;
    }

    public void setCreateCompensationRequestFeeInBsq(long createCompensationRequestFeeInBsq) {
        this.createCompensationRequestFeeInBsq = createCompensationRequestFeeInBsq;
    }

    public long getVotingFeeInBsq() {
        return votingFeeInBsq;
    }

    public void setVotingFeeInBsq(long votingFeeInBsq) {
        this.votingFeeInBsq = votingFeeInBsq;
    }

    public long getConversionRate() {
        return conversionRate;
    }

    public void setConversionRate(long conversionRate) {
        this.conversionRate = conversionRate;
    }

    public long getCompensationRequestPeriodInBlocks() {
        return compensationRequestPeriodInBlocks;
    }

    public void setCompensationRequestPeriodInBlocks(long compensationRequestPeriodInBlocks) {
        this.compensationRequestPeriodInBlocks = compensationRequestPeriodInBlocks;
    }

    public long getVotingPeriodInBlocks() {
        return votingPeriodInBlocks;
    }

    public void setVotingPeriodInBlocks(long votingPeriodInBlocks) {
        this.votingPeriodInBlocks = votingPeriodInBlocks;
    }

    public long getFundingPeriodInBlocks() {
        return fundingPeriodInBlocks;
    }

    public void setFundingPeriodInBlocks(long fundingPeriodInBlocks) {
        this.fundingPeriodInBlocks = fundingPeriodInBlocks;
    }

    public long getBreakBetweenPeriodsInBlocks() {
        return breakBetweenPeriodsInBlocks;
    }

    public void setBreakBetweenPeriodsInBlocks(long breakBetweenPeriodsInBlocks) {
        this.breakBetweenPeriodsInBlocks = breakBetweenPeriodsInBlocks;
    }

    public long getQuorumForCompensationRequestVoting() {
        return quorumForCompensationRequestVoting;
    }

    public void setQuorumForCompensationRequestVoting(long quorumForCompensationRequestVoting) {
        this.quorumForCompensationRequestVoting = quorumForCompensationRequestVoting;
    }

    public long getQuorumForParameterVoting() {
        return quorumForParameterVoting;
    }

    public void setQuorumForParameterVoting(long quorumForParameterVoting) {
        this.quorumForParameterVoting = quorumForParameterVoting;
    }

    public long getMinBtcAmountCompensationRequest() {
        return minBtcAmountCompensationRequest;
    }

    public void setMinBtcAmountCompensationRequest(long minBtcAmountCompensationRequest) {
        this.minBtcAmountCompensationRequest = minBtcAmountCompensationRequest;
    }
}
