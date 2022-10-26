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
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.state.model.blockchain.TxInput;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.dao.state.model.governance.CompensationProposal;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.util.validation.BtcAddressValidator;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class FeeReceiverSelector {
    public static final int REDUCED_ISSUANCE_AMOUNT_FACTOR = 2;
    public static final int MAX_AGE = 76896; // 1.5 years with 144 blocks/day;

    public static String getBtcFeeReceiverAddress(DaoFacade daoFacade) {
        int height = daoFacade.getChainHeight();
        List<Long> amountList = new ArrayList<>();
        List<String> receiverAddressList = new ArrayList<>();
        Map<TxOutputKey, Optional<String>> addressByOutputKey = daoFacade.getAddressByOutputKeyMap();
        // Iteration for about 700 entries takes about 130 ms.
        daoFacade.getIssuanceSetForType(IssuanceType.COMPENSATION)
                .forEach(issuance -> {
                    Optional<CompensationProposal> compensationProposal = daoFacade.findCompensationProposal(issuance.getTxId());
                    int issuanceHeight = issuance.getChainHeight();
                    checkArgument(issuanceHeight <= height,
                            "issuanceHeight must not be larger as currentChainHeight");
                    long weightedAmount = getWeightedAmount(issuance.getAmount(), issuanceHeight, height);
                    boolean isReducedIssuanceAmount = compensationProposal.map(CompensationProposal::isReducedIssuanceAmount).orElse(false);
                    long amount = isReducedIssuanceAmount ? weightedAmount * 10 * REDUCED_ISSUANCE_AMOUNT_FACTOR : weightedAmount;

                    // We take the btcFeeReceiverAddress from the compensationProposal if set, otherwise we
                    // use the address connected to the first input which is a BSQ input.
                    // We cannot use the BTC input as we do not have that spending BTC transaction to derive the address from.
                    // The BTC wallet has access only to our own transactions.
                    // Only for BSQ we have all transactions. The trade fee will be received as BTC in the BSQ and the contributor can transfer it to their BTC wallet.
                    Optional<String> address = compensationProposal.filter(proposal -> proposal.getBtcFeeReceiverAddress() != null)
                            .map(CompensationProposal::getBtcFeeReceiverAddress)
                            .or(() -> daoFacade.getTx(issuance.getTxId())
                                    .flatMap(tx -> {
                                        try {
                                            checkArgument(!tx.getTxInputs().isEmpty());
                                            TxInput firstBsqTxInput = tx.getTxInputs().get(0);
                                            return addressByOutputKey.get(firstBsqTxInput.getConnectedTxOutputKey());
                                        } catch (Throwable t) {
                                            return Optional.empty();
                                        }
                                    }));
                    if (address.isPresent() && new BtcAddressValidator().validate(address.get()).isValid && amount > 0) {
                        receiverAddressList.add(address.get());
                        //  Only if we found a valid address we add the amount
                        amountList.add(amount);
                    }
                });
        addressByOutputKey.clear();
        if (!amountList.isEmpty()) {
            int index = getRandomIndex(amountList, new Random());
            return receiverAddressList.get(index);
        } else {
            // If there are no compensation requests (e.g. at dev testing) we fall back to the default address
            return Param.RECIPIENT_BTC_ADDRESS.getDefaultValue();
        }
    }

    @VisibleForTesting
    public static int getRandomIndex(List<Long> weights, Random random) {
        long sum = weights.stream().mapToLong(n -> n).sum();
        long target = random.longs(0, sum).findFirst().orElseThrow() + 1;
        return findIndex(weights, target);
    }

    @VisibleForTesting
    public static int findIndex(List<Long> weights, long target) {
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
    public static long getWeightedAmount(long amount, int issuanceHeight, int blockHeight) {
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
