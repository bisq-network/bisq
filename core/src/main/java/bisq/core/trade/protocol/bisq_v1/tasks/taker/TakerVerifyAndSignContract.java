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

package bisq.core.trade.protocol.bisq_v1.tasks.taker;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.SellerAsTakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.util.JsonUtil;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.Hash;
import bisq.common.crypto.Sig;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;

import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class TakerVerifyAndSignContract extends TradeTask {
    public TakerVerifyAndSignContract(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            String takerFeeTxId = checkNotNull(processModel.getTakeOfferFeeTxId());
            TradingPeer maker = processModel.getTradePeer();

            boolean isBuyerMakerAndSellerTaker = trade instanceof SellerAsTakerTrade;
            NodeAddress buyerNodeAddress = isBuyerMakerAndSellerTaker ?
                    processModel.getTempTradingPeerNodeAddress() :
                    processModel.getMyNodeAddress();
            NodeAddress sellerNodeAddress = isBuyerMakerAndSellerTaker ?
                    processModel.getMyNodeAddress() :
                    processModel.getTempTradingPeerNodeAddress();

            BtcWalletService walletService = processModel.getBtcWalletService();
            Offer offer = processModel.getOffer();
            String id = offer.getId();
            AddressEntry takerPayoutAddressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.TRADE_PAYOUT);
            String takerPayoutAddressString = takerPayoutAddressEntry.getAddressString();
            AddressEntry takerMultiSigAddressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.MULTI_SIG);
            byte[] takerMultiSigPubKey = processModel.getMyMultiSigPubKey();
            checkArgument(Arrays.equals(takerMultiSigPubKey,
                    takerMultiSigAddressEntry.getPubKey()),
                    "takerMultiSigPubKey from AddressEntry must match the one from the trade data. trade id =" + id);

            byte[] hashOfMakersPaymentAccountPayload = maker.getHashOfPaymentAccountPayload();
            byte[] hashOfTakersPaymentAccountPayload = ProcessModel.hashOfPaymentAccountPayload(processModel.getPaymentAccountPayload(trade));
            String makersPaymentMethodId = checkNotNull(maker.getPaymentMethodId());
            String takersPaymentMethodId = checkNotNull(processModel.getPaymentAccountPayload(trade)).getPaymentMethodId();

            Coin tradeAmount = checkNotNull(trade.getAmount());
            OfferPayload offerPayload = offer.getOfferPayload().orElseThrow();
            Contract contract = new Contract(
                    offerPayload,
                    tradeAmount.value,
                    trade.getPrice().getValue(),
                    takerFeeTxId,
                    buyerNodeAddress,
                    sellerNodeAddress,
                    trade.getMediatorNodeAddress(),
                    isBuyerMakerAndSellerTaker,
                    maker.getAccountId(),
                    processModel.getAccountId(),
                    null,
                    null,
                    maker.getPubKeyRing(),
                    processModel.getPubKeyRing(),
                    maker.getPayoutAddressString(),
                    takerPayoutAddressString,
                    maker.getMultiSigPubKey(),
                    takerMultiSigPubKey,
                    trade.getLockTime(),
                    trade.getRefundAgentNodeAddress(),
                    hashOfMakersPaymentAccountPayload,
                    hashOfTakersPaymentAccountPayload,
                    makersPaymentMethodId,
                    takersPaymentMethodId
            );
            String contractAsJson = JsonUtil.objectToJson(contract);

            if (!contractAsJson.equals(processModel.getTradePeer().getContractAsJson())) {
                contract.printDiff(processModel.getTradePeer().getContractAsJson());
                failed("Contracts are not matching");
            }

            String signature = Sig.sign(processModel.getKeyRing().getSignatureKeyPair().getPrivate(), contractAsJson);
            trade.setContract(contract);
            trade.setContractAsJson(contractAsJson);

            byte[] contractHash = Hash.getSha256Hash(checkNotNull(contractAsJson));
            trade.setContractHash(contractHash);

            trade.setTakerContractSignature(signature);

            processModel.getTradeManager().requestPersistence();
            try {
                checkNotNull(maker.getPubKeyRing(), "maker.getPubKeyRing() must nto be null");
                Sig.verify(maker.getPubKeyRing().getSignaturePubKey(),
                        contractAsJson,
                        maker.getContractSignature());
                complete();
            } catch (Throwable t) {
                failed("Contract signature verification failed. " + t.getMessage());
            }
        } catch (Throwable t) {
            failed(t);
        }
    }
}
