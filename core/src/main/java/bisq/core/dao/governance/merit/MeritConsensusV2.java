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

package bisq.core.dao.governance.merit;

import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.governance.DaoArithmeticsV2;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.dao.state.model.governance.Merit;
import bisq.core.dao.state.model.governance.MeritList;

import bisq.common.util.Utilities;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;

import com.google.common.annotations.VisibleForTesting;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class MeritConsensusV2 {
    // Value with 144 blocks a day and 365 days would be 52560. We take a close round number instead.
    private static final int BLOCKS_PER_YEAR = 50_000;

    public static long getMeritStake(String blindVoteTxId,
                                     MeritList untrustedMeritList,
                                     DaoStateService daoStateService) {
        try {
            // We need to take the chain height when the blindVoteTx got published so we get the same merit for the vote even at
            // later blocks (merit decreases with each block).
            int blindVoteTxHeight = daoStateService.getTx(blindVoteTxId).map(Tx::getBlockHeight).orElse(0);
            if (blindVoteTxHeight <= 0) {
                log.error("Error at getMeritStake: blindVoteTx not found in daoStateService. blindVoteTxId=" + blindVoteTxId);
                return 0;
            }

            Set<String> alreadyUsedIssuanceTxIds = new HashSet<>();
            long totalMeritAmount = 0;

            for (Merit untrustedMerit : untrustedMeritList.getList()) {
                Issuance untrustedIssuance = untrustedMerit.getIssuance();
                String issuanceTxId = untrustedIssuance.getTxId();

                if (untrustedMerit.getIssuance().getIssuanceType() != IssuanceType.COMPENSATION) {
                    log.warn("Issuance type is not COMPENSATION. issuanceTxId={}, issuanceType={}",
                            issuanceTxId, untrustedMerit.getIssuance().getIssuanceType());
                    continue;
                }
                Optional<Issuance> optionalIssuance = daoStateService.getIssuance(issuanceTxId, IssuanceType.COMPENSATION);
                if (optionalIssuance.isEmpty()) {
                    log.warn("No DAO state compensation issuance found for merit. issuanceTxId={}", issuanceTxId);
                    continue;
                }
                Issuance issuance = optionalIssuance.get();
                if (!issuance.isValueEqual(untrustedIssuance)) {
                    log.warn("DAO state compensation issuance is not the same as the one from the unrusted merit. " +
                            "DAO state compensation issuance={}, untrustedMerit={}", issuance, untrustedMerit);
                    continue;
                }

                int issuanceHeight = issuance.getChainHeight();
                if (issuanceHeight > blindVoteTxHeight) {
                    log.warn("Future merit ignored. issuanceTxId={}, issuanceHeight={}, blindVoteTxHeight={}",
                            issuanceTxId, issuanceHeight, blindVoteTxHeight);
                    continue;
                }

                if (!isSignatureValid(untrustedMerit.getSignature(), issuance.getPubKey(), blindVoteTxId)) {
                    log.warn("Invalid signature in merit. merit={}", untrustedMerit);
                    continue;
                }

                boolean didNotContain = alreadyUsedIssuanceTxIds.add(issuanceTxId);
                if (!didNotContain) {
                    log.warn("Issuance was already used. merit={}", untrustedMerit);
                    continue;
                }

                long weightedMeritAmount = getWeightedMeritAmount(issuance.getAmount(),
                        issuance.getChainHeight(),
                        blindVoteTxHeight,
                        BLOCKS_PER_YEAR);
                totalMeritAmount = Math.addExact(totalMeritAmount, weightedMeritAmount);
            }
            return totalMeritAmount;
        } catch (ArithmeticException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Error at getMeritStake. merit={}", untrustedMeritList, e);
            return 0;
        }
    }

    @VisibleForTesting
    private static boolean isSignatureValid(byte[] signatureFromMerit, String pubKeyAsHex, String blindVoteTxId) {
        // We verify if signature of hash of blindVoteTxId is correct. EC key from first input for blind vote tx is
        // used for signature.
        if (pubKeyAsHex == null) {
            log.error("Error at isSignatureValid: pubKeyAsHex is null");
            return false;
        }

        boolean result = false;
        try {
            ECKey pubKey = ECKey.fromPublicOnly(Utilities.decodeFromHex(pubKeyAsHex));
            ECKey.ECDSASignature signature = ECKey.ECDSASignature.decodeFromDER(signatureFromMerit).toCanonicalised();
            Sha256Hash msg = Sha256Hash.wrap(blindVoteTxId);
            result = pubKey.verify(msg, signature);
        } catch (Throwable t) {
            log.error("Signature verification of issuance failed: " + t.toString());
        }
        if (!result) {
            log.error("Signature verification of issuance failed: blindVoteTxId={}, pubKeyAsHex={}",
                    blindVoteTxId, pubKeyAsHex);
        }
        return result;
    }

    public static long getWeightedMeritAmount(long amount, int issuanceHeight, int blockHeight, int blocksPerYear) {
        try {
            if (issuanceHeight > blockHeight) {
                throw new IllegalArgumentException("issuanceHeight must not be larger than blockHeight. issuanceHeight=" + issuanceHeight + "; blockHeight=" + blockHeight);
            }
            if (blockHeight <= 0) {
                throw new IllegalArgumentException("blockHeight must be positive. blockHeight=" + blockHeight);
            }
            if (amount < 0) {
                throw new IllegalArgumentException("amount must not be negative. amount" + amount);
            }
            if (amount == 0) {
                return 0;
            }
            if (blocksPerYear <= 0) {
                throw new IllegalArgumentException("blocksPerYear must be positive. blocksPerYear=" + blocksPerYear);
            }
            if (issuanceHeight <= 0) {
                throw new IllegalArgumentException("issuanceHeight must be positive. issuanceHeight=" + issuanceHeight);
            }
            // We use a linear function to apply a factor for the issuance amount of 1 if the issuance was recent and 0
            // if the issuance was 2 years old or older.
            // To avoid rounding issues with double values we multiply initially with a large number and divide at the end
            // by that number again. As we multiply the amount in satoshis we get a reasonable good precision even the long
            // division is not using rounding. Sticking with long values makes that operation safer against consensus
            // failures causes by rounding differences with double.

            long maxAge = DaoArithmeticsV2.multiplyLong(2, blocksPerYear); // maxAge=100 000 (MeritConsensus.BLOCKS_PER_YEAR is 50_000)
            //noinspection ConstantValue
            checkArgument(maxAge > 0, "maxAge must be positive. maxAge=" + maxAge);
            long age = Math.min(maxAge, blockHeight - issuanceHeight);
            long inverseAge = maxAge - age;

            // We want a resolution of 1 block so we use the inverseAge and divide by maxAge afterwards to get the
            // weighted amount
            long weightedAmount = DaoArithmeticsV2.multiplyAndDivide(amount, inverseAge, maxAge);
            checkArgument(weightedAmount >= 0, "weightedAmount must not be negative. " +
                    "weightedAmount=" + weightedAmount);

            log.debug("getWeightedMeritAmount: age={}, inverseAge={}, weightedAmount={}, amount={}", age, inverseAge, weightedAmount, amount);
            return weightedAmount;
        } catch (ArithmeticException e) {
            log.error("ArithmeticException at getWeightedMeritAmount ", e);
            throw e;
        } catch (Exception e) {
            log.error("Exception at getWeightedMeritAmount ", e);
            return 0;
        }
    }

    public static long getCurrentlyAvailableMerit(MeritList meritList, int currentChainHeight) {
        // We need to take the chain height when the blindVoteTx got published so we get the same merit for the vote even at
        // later blocks (merit decreases with each block).
        // We add 1 block to currentChainHeight so that the displayed merit would match the merit in case we get the
        // blind vote tx into the next block.
        int height = currentChainHeight + 1;
        return meritList.getList().stream()
                .mapToLong(merit -> {
                    try {
                        Issuance issuance = merit.getIssuance();
                        checkArgument(issuance.getIssuanceType() == IssuanceType.COMPENSATION, "issuance must be of type COMPENSATION");
                        int issuanceHeight = issuance.getChainHeight();
                        checkArgument(issuanceHeight <= height,
                                "issuanceHeight must not be larger as currentChainHeight");
                        return getWeightedMeritAmount(issuance.getAmount(),
                                issuanceHeight,
                                height,
                                BLOCKS_PER_YEAR);
                    } catch (Throwable t) {
                        log.error("Error at getCurrentlyAvailableMerit: " + t.toString());
                        return 0;
                    }
                })
                .sum();
    }

}
