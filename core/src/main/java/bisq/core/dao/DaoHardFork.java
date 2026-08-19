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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao;

import bisq.common.config.Config;

public final class DaoHardFork {
    private static final int ACTIVATE_HARD_FORK_3_HEIGHT_MAINNET = 963_350;
    private static final int ACTIVATE_HARD_FORK_3_HEIGHT_TESTNET = 3_000_000;
    private static final int ACTIVATE_HARD_FORK_3_HEIGHT_REGTEST = 1;

    private static final int DUPLICATE_VOTE_PROPOSAL_TX_ID_ACTIVATION_HEIGHT_MAINNET = 963_350;
    private static final int DUPLICATE_VOTE_PROPOSAL_TX_ID_ACTIVATION_HEIGHT_TESTNET = 3_000_000;
    private static final int DUPLICATE_VOTE_PROPOSAL_TX_ID_ACTIVATION_HEIGHT_REGTEST = 1;

    private static final int BLIND_VOTE_MERIT_DECRYPTABILITY_ACTIVATION_HEIGHT_MAINNET = 963_350;
    private static final int BLIND_VOTE_MERIT_DECRYPTABILITY_ACTIVATION_HEIGHT_TESTNET = 3_000_000;
    private static final int BLIND_VOTE_MERIT_DECRYPTABILITY_ACTIVATION_HEIGHT_REGTEST = 1;

    public static boolean isHardFork3Activated(int blockHeight) {
        return blockHeight >= getHardFork3ActivationHeight();
    }

    public static int getHardFork3ActivationHeight() {
        return switch (Config.baseCurrencyNetwork()) {
            case BTC_MAINNET -> ACTIVATE_HARD_FORK_3_HEIGHT_MAINNET;
            case BTC_TESTNET -> ACTIVATE_HARD_FORK_3_HEIGHT_TESTNET;
            case BTC_REGTEST, BTC_DAO_TESTNET, BTC_DAO_BETANET, BTC_DAO_REGTEST ->
                    ACTIVATE_HARD_FORK_3_HEIGHT_REGTEST;
        };
    }

    public static boolean isDuplicateVoteProposalTxIdValidationActivated(int blockHeight) {
        return blockHeight >= getDuplicateVoteProposalTxIdValidationActivationHeight();
    }

    public static int getDuplicateVoteProposalTxIdValidationActivationHeight() {
        return switch (Config.baseCurrencyNetwork()) {
            case BTC_MAINNET -> DUPLICATE_VOTE_PROPOSAL_TX_ID_ACTIVATION_HEIGHT_MAINNET;
            case BTC_TESTNET -> DUPLICATE_VOTE_PROPOSAL_TX_ID_ACTIVATION_HEIGHT_TESTNET;
            case BTC_REGTEST, BTC_DAO_TESTNET, BTC_DAO_BETANET, BTC_DAO_REGTEST ->
                    DUPLICATE_VOTE_PROPOSAL_TX_ID_ACTIVATION_HEIGHT_REGTEST;
        };
    }

    public static boolean isBlindVoteMeritDecryptabilityActivated(int blockHeight) {
        return blockHeight >= getBlindVoteMeritDecryptabilityActivationHeight();
    }

    public static int getBlindVoteMeritDecryptabilityActivationHeight() {
        return switch (Config.baseCurrencyNetwork()) {
            case BTC_MAINNET -> BLIND_VOTE_MERIT_DECRYPTABILITY_ACTIVATION_HEIGHT_MAINNET;
            case BTC_TESTNET -> BLIND_VOTE_MERIT_DECRYPTABILITY_ACTIVATION_HEIGHT_TESTNET;
            case BTC_REGTEST, BTC_DAO_TESTNET, BTC_DAO_BETANET, BTC_DAO_REGTEST ->
                    BLIND_VOTE_MERIT_DECRYPTABILITY_ACTIVATION_HEIGHT_REGTEST;
        };
    }

    private DaoHardFork() {
    }
}
