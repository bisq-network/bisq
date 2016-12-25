/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.dao.vote;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VotingParameters {
    private static final Logger log = LoggerFactory.getLogger(VotingParameters.class);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enums
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum Code {
        CREATE_OFFER_FEE_IN_BTC((byte) 0x01),
        TAKE_OFFER_FEE_IN_BTC((byte) 0x02),
        CREATE_OFFER_FEE_IN_SQU((byte) 0x03),
        TAKE_OFFER_FEE_IN_SQU((byte) 0x04),
        CREATE_COMPENSATION_REQUEST_FEE_IN_SQU((byte) 0x05),
        VOTING_FEE_IN_SQU((byte) 0x06),

        COMPENSATION_REQUEST_PERIOD_IN_BLOCKS((byte) 0x10),
        VOTING_PERIOD_IN_BLOCKS((byte) 0x11),
        FUNDING_PERIOD_IN_BLOCKS((byte) 0x12),
        BREAK_BETWEEN_PERIODS_IN_BLOCKS((byte) 0x13),

        QUORUM_FOR_COMPENSATION_REQUEST_VOTING((byte) 0x20),
        QUORUM_FOR_PARAMETER_VOTING((byte) 0x21),

        MIN_BTC_AMOUNT_COMPENSATION_REQUEST((byte) 0x30),
        MAX_BTC_AMOUNT_COMPENSATION_REQUEST((byte) 0x31),

        COMP_REQUEST_MAPS((byte) 0x40);

        public final Byte code;

        Code(Byte code) {
            this.code = code;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private long createOfferFeeInBtc = 30_000;
    private long takeOfferFeeInBtc = 80_000;

    private long createOfferFeeInSqu = 333;
    private long takeOfferFeeInSqu = 444;

    private long createCompensationRequestFeeInSqu = 7777;
    private long votingFeeInSqu = 8888;

    private double conversionRate = 0.000001; // how many btc you get for 1 squ (e.g. 1 000 000 squ = 1 btc)

    // 144 blocks is 1 day
    private int compensationRequestPeriodInBlocks = 2880;  // 20 days
    private int votingPeriodInBlocks = 432; // 3 days
    private int fundingPeriodInBlocks = 1008; // 7 days
    private int breakBetweenPeriodsInBlocks = 10;

    private double quorumForCompensationRequestVoting = 5; // 5%
    private double quorumForParameterVoting = 5; // 5%

    private long minBtcAmountCompensationRequest = 20_000_000; // 0.2 BTC
    private long maxBtcAmountCompensationRequest = 2_000_000_000; // 20 btc

    private boolean voteResultsApplied;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public VotingParameters() {

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

    public long getCreateOfferFeeInBtc() {
        return createOfferFeeInBtc;
    }

    public void setCreateOfferFeeInBtc(long createOfferFeeInBtc) {
        this.createOfferFeeInBtc = createOfferFeeInBtc;
    }

    public long getTakeOfferFeeInBtc() {
        return takeOfferFeeInBtc;
    }

    public void setTakeOfferFeeInBtc(long takeOfferFeeInBtc) {
        this.takeOfferFeeInBtc = takeOfferFeeInBtc;
    }

    public long getCreateCompensationRequestFeeInSqu() {
        return createCompensationRequestFeeInSqu;
    }

    public void setCreateCompensationRequestFeeInSqu(long createCompensationRequestFeeInSqu) {
        this.createCompensationRequestFeeInSqu = createCompensationRequestFeeInSqu;
    }

    public long getVotingFeeInSqu() {
        return votingFeeInSqu;
    }

    public void setVotingFeeInSqu(long votingFeeInSqu) {
        this.votingFeeInSqu = votingFeeInSqu;
    }

    public double getConversionRate() {
        return conversionRate;
    }

    public void setConversionRate(double conversionRate) {
        this.conversionRate = conversionRate;
    }

    public int getCompensationRequestPeriodInBlocks() {
        return compensationRequestPeriodInBlocks;
    }

    public int getTotalPeriodInBlocks() {
        return compensationRequestPeriodInBlocks + votingPeriodInBlocks + fundingPeriodInBlocks + 3 * breakBetweenPeriodsInBlocks;
    }

    public void setCompensationRequestPeriodInBlocks(int compensationRequestPeriodInBlocks) {
        this.compensationRequestPeriodInBlocks = compensationRequestPeriodInBlocks;
    }

    public int getVotingPeriodInBlocks() {
        return votingPeriodInBlocks;
    }

    public void setVotingPeriodInBlocks(int votingPeriodInBlocks) {
        this.votingPeriodInBlocks = votingPeriodInBlocks;
    }

    public int getFundingPeriodInBlocks() {
        return fundingPeriodInBlocks;
    }

    public void setFundingPeriodInBlocks(int fundingPeriodInBlocks) {
        this.fundingPeriodInBlocks = fundingPeriodInBlocks;
    }

    public int getBreakBetweenPeriodsInBlocks() {
        return breakBetweenPeriodsInBlocks;
    }

    public void setBreakBetweenPeriodsInBlocks(int breakBetweenPeriodsInBlocks) {
        this.breakBetweenPeriodsInBlocks = breakBetweenPeriodsInBlocks;
    }

    public double getQuorumForCompensationRequestVoting() {
        return quorumForCompensationRequestVoting;
    }

    public void setQuorumForCompensationRequestVoting(double quorumForCompensationRequestVoting) {
        this.quorumForCompensationRequestVoting = quorumForCompensationRequestVoting;
    }

    public double getQuorumForParameterVoting() {
        return quorumForParameterVoting;
    }

    public void setQuorumForParameterVoting(double quorumForParameterVoting) {
        this.quorumForParameterVoting = quorumForParameterVoting;
    }

    public long getMinBtcAmountCompensationRequest() {
        return minBtcAmountCompensationRequest;
    }

    public void setMinBtcAmountCompensationRequest(long minBtcAmountCompensationRequest) {
        this.minBtcAmountCompensationRequest = minBtcAmountCompensationRequest;
    }
}
