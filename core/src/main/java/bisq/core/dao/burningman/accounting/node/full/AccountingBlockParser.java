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

package bisq.core.dao.burningman.accounting.node.full;

import bisq.core.dao.burningman.accounting.BurningManAccountingService;
import bisq.core.dao.burningman.accounting.blockchain.AccountingBlock;
import bisq.core.dao.burningman.accounting.blockchain.AccountingTx;
import bisq.core.dao.burningman.accounting.blockchain.AccountingTxOutput;
import bisq.core.dao.burningman.accounting.blockchain.temp.TempAccountingTx;
import bisq.core.dao.burningman.accounting.blockchain.temp.TempAccountingTxInput;
import bisq.core.dao.burningman.accounting.blockchain.temp.TempAccountingTxOutput;
import bisq.core.dao.node.full.rpc.dto.RawDtoBlock;
import bisq.core.dao.state.model.blockchain.ScriptType;

import bisq.common.util.Hex;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.TransactionInput;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class AccountingBlockParser {
    private final BurningManAccountingService burningManAccountingService;

    @Inject
    public AccountingBlockParser(BurningManAccountingService burningManAccountingService) {
        this.burningManAccountingService = burningManAccountingService;
    }

    public AccountingBlock parse(RawDtoBlock rawDtoBlock) {
        Map<String, String> burningManNameByAddress = burningManAccountingService.getBurningManNameByAddress();
        String genesisTxId = burningManAccountingService.getGenesisTxId();

        // We filter early for first output address match. DPT txs have multiple outputs which need to match and will be checked later.
        Set<String> receiverAddresses = burningManNameByAddress.keySet();
        List<AccountingTx> txs = rawDtoBlock.getTx().stream()
                .map(TempAccountingTx::new)
                .filter(tempAccountingTx -> receiverAddresses.contains(tempAccountingTx.getOutputs().get(0).getAddress()))
                .map(tempAccountingTx -> toAccountingTx(tempAccountingTx, burningManNameByAddress, genesisTxId))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        // Time in rawDtoBlock is in seconds
        int timeInSec = rawDtoBlock.getTime().intValue();
        byte[] truncatedHash = Hex.decodeLast4Bytes(rawDtoBlock.getHash());
        byte[] truncatedPreviousBlockHash = Hex.decodeLast4Bytes(rawDtoBlock.getPreviousBlockHash());
        return new AccountingBlock(rawDtoBlock.getHeight(),
                timeInSec,
                truncatedHash,
                truncatedPreviousBlockHash,
                txs);
    }

    // We cannot know for sure if it's a DPT or BTC fee tx as we do not have the spending tx output with more
    // data for verification as we do not keep the full blockchain data for lookup unspent tx outputs.
    // The DPT can be very narrowly detected. The BTC fee txs might have false positives.
    private Optional<AccountingTx> toAccountingTx(TempAccountingTx tempAccountingTx,
                                                  Map<String, String> burningManNameByAddress,
                                                  String genesisTxId) {
        if (genesisTxId.equals(tempAccountingTx.getTxId())) {
            return Optional.empty();
        }

        Set<String> receiverAddresses = burningManNameByAddress.keySet();
        // DPT has 1 input from P2WSH with lock time and sequence number set.
        // We only use native segwit P2SH, so we expect a txInWitness
        List<TempAccountingTxInput> inputs = tempAccountingTx.getInputs();
        List<TempAccountingTxOutput> outputs = tempAccountingTx.getOutputs();
        // Max DPT output amount is currently 4 BTC. Max. security deposit is 50% of trade amount.
        // We give some extra headroom to cover potential future changes.
        // We store value as integer in protobuf to safe space, so max. possible value is 21.47483647 BTC.
        long maxDptValue = 6 * Coin.COIN.getValue();
        if (inputs.size() == 1 &&
                isValidTimeLock(tempAccountingTx, inputs.get(0)) &&
                doAllOutputsMatchReceivers(outputs, receiverAddresses) &&
                isWitnessDataWP2SH(inputs.get(0).getTxInWitness()) &&
                outputs.stream().allMatch(txOutput -> isExpectedScriptType(txOutput, tempAccountingTx)) &&
                outputs.stream().allMatch(txOutput -> txOutput.getValue() <= maxDptValue)) {

            List<AccountingTxOutput> accountingTxOutputs = outputs.stream()
                    .map(output -> new AccountingTxOutput(output.getValue(), burningManNameByAddress.get(output.getAddress())))
                    .collect(Collectors.toList());
            return Optional.of(new AccountingTx(AccountingTx.Type.DPT_TX, accountingTxOutputs, tempAccountingTx.getTxId()));
        }

        // BTC trade fee tx has 2 or 3 outputs.
        // First output to receiver, second reserved for trade and an optional 3rd for change.
        // Address check is done in parse method above already.
        // Amounts are in a certain range but as fee can change with DAO param changes we cannot set hard limits.
        // The min. trade fee in Nov. 2022 is 5000 sat. We use 2500 as lower bound.
        // The largest taker fee for a 2 BTC trade is about 0.023 BTC (2_300_000 sat).
        // We use 10_000_000 sat as upper bound to give some headroom for future fee increase and to cover some
        // exceptions like SiaCoin having a 4 BTC limit.
        // Inputs are not constrained.
        // We store value as integer in protobuf to safe space, so max. possible value is 21.47483647 BTC.
        TempAccountingTxOutput firstOutput = outputs.get(0);
        if (outputs.size() >= 2 &&
                outputs.size() <= 3 &&
                firstOutput.getValue() > 2500 &&
                firstOutput.getValue() < 10_000_000 &&
                isExpectedScriptType(firstOutput, tempAccountingTx)) {
            // We only keep first output.
            String name = burningManNameByAddress.get(firstOutput.getAddress());
            return Optional.of(new AccountingTx(AccountingTx.Type.BTC_TRADE_FEE_TX,
                    List.of(new AccountingTxOutput(firstOutput.getValue(), name)),
                    tempAccountingTx.getTxId()));
        }

        return Optional.empty();
    }

    // TODO not sure if other ScriptType are to be expected
    private boolean isExpectedScriptType(TempAccountingTxOutput txOutput, TempAccountingTx accountingTx) {
        boolean result = txOutput.getScriptType() != null &&
                (txOutput.getScriptType() == ScriptType.PUB_KEY_HASH ||
                        txOutput.getScriptType() == ScriptType.SCRIPT_HASH ||
                        txOutput.getScriptType() == ScriptType.WITNESS_V0_KEYHASH);
        if (!result) {
            log.error("isExpectedScriptType txOutput.getScriptType()={}, txIf={}", txOutput.getScriptType(), accountingTx.getTxId());
        }
        return result;
    }

    // All outputs need to be to receiver addresses (incl. legacy BM)
    private boolean doAllOutputsMatchReceivers(List<TempAccountingTxOutput> outputs, Set<String> receiverAddresses) {
        return outputs.stream().allMatch(output -> receiverAddresses.contains(output.getAddress()));
    }

    private boolean isValidTimeLock(TempAccountingTx accountingTx, TempAccountingTxInput firstInput) {
        // Need to be 0xfffffffe
        return accountingTx.isValidDptLockTime() && firstInput.getSequence() == TransactionInput.NO_SEQUENCE - 1;
    }

    /*
    Example txInWitness: [, 304502210098fcec3ac1c1383d40159587b42e8b79cb3e793004d6ccb080bfb93f02c15f93022039f014eb933c59f988d68a61aa7a1c787f08d94bd0b222104718792798e43e3c01, 304402201f8b37f3b8b5b9944ca88f18f6bb888c5a48dc5183edf204ae6d3781032122e102204dc6397538055d94de1ab683315aac7d87289be0c014569f7b3fa465bf70b6d401, 5221027f6da96ede171617ce79ec305a76871ecce21ad737517b667fc9735f2dc342342102d8d93e02fb0833201274b47a302b47ff81c0b3510508eb0444cb1674d0d8a6d052ae]
     */
    private boolean isWitnessDataWP2SH(List<String> txInWitness) {
        // txInWitness from the 2of2 multiSig has 4 chunks.
        // 0 byte, sig1, sig2, redeemScript
        if (txInWitness.size() != 4) {
            log.error("txInWitness chunks size not 4 .txInWitness={}", txInWitness);
            return false;
        }
        // First chunk is o byte (empty string)
        if (!txInWitness.get(0).isEmpty()) {
            log.error("txInWitness.get(0) not empty .txInWitness={}", txInWitness);
            return false;
        }

        // The 2 signatures are 70 - 73 bytes
        int minSigLength = 140;
        int maxSigLength = 146;
        int fistSigLength = txInWitness.get(1).length();
        if (fistSigLength < minSigLength || fistSigLength > maxSigLength) {
            log.error("fistSigLength wrong .txInWitness={}", txInWitness);
            return false;
        }
        int secondSigLength = txInWitness.get(2).length();
        if (secondSigLength < minSigLength || secondSigLength > maxSigLength) {
            log.error("secondSigLength wrong .txInWitness={}", txInWitness);
            return false;
        }

        String redeemScript = txInWitness.get(3);
        if (redeemScript.length() != 142) {
            log.error("redeemScript not valid length .txInWitness={}", txInWitness);
            return false;
        }

        // OP_2 pub1 pub2 OP_2 OP_CHECKMULTISIG
        // In hex: "5221" + PUB_KEY_1 + "21" + PUB_KEY_2 + "52ae";
        // PubKeys are 33 bytes -> length 66 in hex
        String separator = redeemScript.substring(70, 72);
        boolean result = redeemScript.startsWith("5221") &&
                redeemScript.endsWith("52ae") &&
                separator.equals("21");
        if (!result) {
            log.error("redeemScript not valid .txInWitness={}", txInWitness);
        }
        return result;
    }
}
