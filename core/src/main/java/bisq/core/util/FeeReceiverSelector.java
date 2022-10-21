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

package bisq.core.util;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.model.blockchain.BaseTxOutput;
import bisq.core.dao.state.model.governance.CompensationProposal;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.util.validation.BtcAddressValidator;

import bisq.common.config.Config;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class FeeReceiverSelector {
    public static final String BTC_FEE_RECEIVER_ADDRESS = "38bZBj5peYS3Husdz7AH3gEUiUbYRD951t";
    public static final int REDUCED_ISSUANCE_AMOUNT_FACTOR = 2;
    public static final int MAX_AGE = 76896; // 1.5 years with 144 blocks/day;

    public static String getMostRecentAddress() {
        return Config.baseCurrencyNetwork().isMainnet() ? BTC_FEE_RECEIVER_ADDRESS :
                Config.baseCurrencyNetwork().isTestnet() ? "2N4mVTpUZAnhm9phnxB7VrHB4aBhnWrcUrV" :
                        "2MzBNTJDjjXgViKBGnatDU3yWkJ8pJkEg9w";
    }

    public static String getBtcFeeReceiverAddress(DaoFacade daoFacade) {
        int height = daoFacade.getChainHeight();
        List<Long> amountList = new ArrayList<>();
        List<String> receiverAddressList = new ArrayList<>();
        daoFacade.getIssuanceSetForType(IssuanceType.COMPENSATION)
                .forEach(issuance -> {
                    Optional<CompensationProposal> compensationProposal = daoFacade.findCompensationProposal(issuance.getTxId());

                    int issuanceHeight = issuance.getChainHeight();
                    checkArgument(issuanceHeight <= height,
                            "issuanceHeight must not be larger as currentChainHeight");
                    long weightedAmount = getWeightedAmount(issuance.getAmount(), issuanceHeight, height);
                    boolean isReducedIssuanceAmount = compensationProposal.map(CompensationProposal::isReducedIssuanceAmount).orElse(false);
                    long amount = isReducedIssuanceAmount ? weightedAmount * 10 * REDUCED_ISSUANCE_AMOUNT_FACTOR : weightedAmount;

                    // We take the btcFeeReceiverAddress from the compensationProposal if set, otherwise we take the
                    // address from the btc input from the proposal transaction which is at index 1.
                    Optional<String> address = compensationProposal.filter(proposal -> proposal.getBtcFeeReceiverAddress() != null)
                            .map(CompensationProposal::getBtcFeeReceiverAddress)
                            .or(() -> daoFacade.getTx(issuance.getTxId())
                                    .flatMap(tx -> daoFacade.getTxOutput(tx.getTxInputs().get(1).getConnectedTxOutputKey())
                                            .map(BaseTxOutput::getAddress)));
                    if (address.isPresent() && new BtcAddressValidator().validate(address.get()).isValid) {
                        receiverAddressList.add(address.get());
                        //  Only if we found a valid address we add the amount
                        amountList.add(amount);
                    }
                });
        if (!amountList.isEmpty()) {
            int index = getRandomIndex(amountList, new Random());
            return receiverAddressList.get(index);
        } else {
            return getMostRecentAddress();
        }
    }

    @VisibleForTesting
    static int getRandomIndex(List<Long> weights, Random random) {
        long sum = weights.stream().mapToLong(n -> n).sum();
        long target = random.longs(0, sum).findFirst().orElseThrow() + 1;
        return findIndex(weights, target);
    }

    @VisibleForTesting
    static int findIndex(List<Long> weights, long target) {
        int currentRange = 0;
        for (int i = 0; i < weights.size(); i++) {
            currentRange += weights.get(i);
            if (currentRange >= target) {
                return i;
            }
        }
        return 0;
    }

    // Borrowed from MeritConsensus (unit tested there)
    private static long getWeightedAmount(long amount, int issuanceHeight, int blockHeight) {
        if (issuanceHeight > blockHeight)
            throw new IllegalArgumentException("issuanceHeight must not be larger than blockHeight. issuanceHeight=" + issuanceHeight + "; blockHeight=" + blockHeight);
        if (blockHeight < 0)
            throw new IllegalArgumentException("blockHeight must not be negative. blockHeight=" + blockHeight);
        if (amount < 0)
            throw new IllegalArgumentException("amount must not be negative. amount" + amount);
        if (issuanceHeight < 0)
            throw new IllegalArgumentException("issuanceHeight must not be negative. issuanceHeight=" + issuanceHeight);

        long age = Math.min(MAX_AGE, blockHeight - issuanceHeight);
        long inverseAge = MAX_AGE - age;
        long weightedAmount = (amount * inverseAge) / MAX_AGE;

        log.debug("getWeightedAmount: age={}, inverseAge={}, weightedAmount={}, amount={}", age, inverseAge, weightedAmount, amount);
        return weightedAmount;
    }
}
