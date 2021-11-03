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

package bisq.core.trade.protocol.bisq_v1.tasks.maker;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.trade.model.bisq_v1.BuyerAsMakerTrade;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.util.JsonUtil;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.Hash;
import bisq.common.crypto.Sig;
import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class MakerCreateAndSignContract extends TradeTask {
    public MakerCreateAndSignContract(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            String takerFeeTxId = checkNotNull(processModel.getTakeOfferFeeTxId());

            TradingPeer taker = processModel.getTradePeer();
            boolean isBuyerMakerAndSellerTaker = trade instanceof BuyerAsMakerTrade;
            NodeAddress buyerNodeAddress = isBuyerMakerAndSellerTaker ?
                    processModel.getMyNodeAddress() : processModel.getTempTradingPeerNodeAddress();
            NodeAddress sellerNodeAddress = isBuyerMakerAndSellerTaker ?
                    processModel.getTempTradingPeerNodeAddress() : processModel.getMyNodeAddress();
            BtcWalletService walletService = processModel.getBtcWalletService();
            Offer offer = processModel.getOffer();
            String id = offer.getId();
            AddressEntry makerAddressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.MULTI_SIG);
            byte[] makerMultiSigPubKey = makerAddressEntry.getPubKey();

            AddressEntry takerAddressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.TRADE_PAYOUT);

            byte[] hashOfMakersPaymentAccountPayload = ProcessModel.hashOfPaymentAccountPayload(processModel.getPaymentAccountPayload(trade));
            byte[] hashOfTakersPaymentAccountPayload = taker.getHashOfPaymentAccountPayload();
            String makersPaymentMethodId = checkNotNull(processModel.getPaymentAccountPayload(trade)).getPaymentMethodId();
            String takersPaymentMethodId = checkNotNull(taker.getPaymentMethodId());
            OfferPayload offerPayload = offer.getOfferPayload().orElseThrow();

            Contract contract = new Contract(
                    offerPayload,
                    checkNotNull(trade.getAmount()).value,
                    trade.getPrice().getValue(),
                    takerFeeTxId,
                    buyerNodeAddress,
                    sellerNodeAddress,
                    trade.getMediatorNodeAddress(),
                    isBuyerMakerAndSellerTaker,
                    processModel.getAccountId(),
                    checkNotNull(taker.getAccountId()),
                    null,
                    null,
                    processModel.getPubKeyRing(),
                    checkNotNull(taker.getPubKeyRing()),
                    takerAddressEntry.getAddressString(),
                    checkNotNull(taker.getPayoutAddressString()),
                    makerMultiSigPubKey,
                    checkNotNull(taker.getMultiSigPubKey()),
                    trade.getLockTime(),
                    trade.getRefundAgentNodeAddress(),
                    hashOfMakersPaymentAccountPayload,
                    hashOfTakersPaymentAccountPayload,
                    makersPaymentMethodId,
                    takersPaymentMethodId
            );
            String contractAsJson = JsonUtil.objectToJson(contract);
            String signature = Sig.sign(processModel.getKeyRing().getSignatureKeyPair().getPrivate(), contractAsJson);

            trade.setContract(contract);
            trade.setContractAsJson(contractAsJson);
            trade.setMakerContractSignature(signature);

            byte[] contractHash = Hash.getSha256Hash(checkNotNull(trade.getContractAsJson()));
            trade.setContractHash(contractHash);

            processModel.setMyMultiSigPubKey(makerMultiSigPubKey);

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
