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

package bisq.core.api.model;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.StableCoinAccountPayload;
import bisq.core.trade.model.bisq_v1.BuyerAsMakerTrade;
import bisq.core.trade.model.bisq_v1.BuyerAsTakerTrade;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.tasks.SepaInstantTakerContractFixture;
import bisq.core.util.JsonUtil;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static bisq.core.api.model.PaymentAccountPayloadInfo.toPaymentAccountPayloadInfo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TradeInfoSellerPaymentDetailsTest {
    @BeforeAll
    static void setUpResources() {
        Res.setup();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mismatchedSellerDetailsAreHiddenInTheReply(boolean buyerIsMaker) {
        SepaInstantTakerContractFixture fixture = new SepaInstantTakerContractFixture(buyerIsMaker);
        PaymentAccountPayload sellerAccount = buyerIsMaker ?
                fixture.takerPaymentAccountPayload : fixture.makerPaymentAccountPayload;
        StableCoinAccountPayload wrongRail = new StableCoinAccountPayload(sellerAccount.getPaymentMethodId(),
                sellerAccount.getId());
        wrongRail.setAddress("seller-address-on-another-rail");
        PaymentAccountPayload buyerAccount = buyerIsMaker ?
                fixture.makerPaymentAccountPayload : fixture.takerPaymentAccountPayload;
        fixture.contract.setPaymentAccountPayloads(wrongRail, buyerAccount,
                buyerIsMaker ? fixture.makerPubKeyRing : fixture.takerPubKeyRing);
        Trade trade = trade(fixture.contract);
        assertTrue(trade.getContractAsJson().contains(wrongRail.getAddress()));

        var reply = TradeInfo.toTradeInfo(trade, "Buyer", buyerIsMaker, "Pending").toProtoMessage();

        assertEquals("", reply.getContractAsJson());
        var maker = reply.getContract().getMakerPaymentAccountPayload();
        var taker = reply.getContract().getTakerPaymentAccountPayload();
        assertEquals(toPaymentAccountPayloadInfo(buyerAccount).toProtoMessage(), buyerIsMaker ? maker : taker);
        assertEquals(bisq.proto.grpc.PaymentAccountPayloadInfo.getDefaultInstance(), buyerIsMaker ? taker : maker);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void matchingAccountsKeepBothPayloadsAndTheOriginalContractJson(boolean buyerIsMaker) {
        SepaInstantTakerContractFixture fixture = new SepaInstantTakerContractFixture(buyerIsMaker);
        fixture.contract.setPaymentAccountPayloads(fixture.takerPaymentAccountPayload, fixture.makerPaymentAccountPayload,
                fixture.makerPubKeyRing);
        Trade trade = trade(fixture.contract);

        var reply = TradeInfo.toTradeInfo(trade, "Buyer", buyerIsMaker, "Pending").toProtoMessage();

        assertEquals(trade.getContractAsJson(), reply.getContractAsJson());
        assertEquals(toPaymentAccountPayloadInfo(fixture.makerPaymentAccountPayload).toProtoMessage(),
                reply.getContract().getMakerPaymentAccountPayload());
        assertEquals(toPaymentAccountPayloadInfo(fixture.takerPaymentAccountPayload).toProtoMessage(),
                reply.getContract().getTakerPaymentAccountPayload());
    }

    private static Trade trade(Contract contract) {
        Offer offer = new Offer(contract.getOfferPayload());
        BtcWalletService wallet = mock(BtcWalletService.class);
        ProcessModel process = mock(ProcessModel.class);
        Coin fee = Coin.valueOf(1000);
        Trade trade = contract.isBuyerMakerAndSellerTaker()
                ? new BuyerAsMakerTrade(offer, fee, fee, true, null, contract.getMediatorNodeAddress(),
                        contract.getRefundAgentNodeAddress(), wallet, process, "trade")
                : new BuyerAsTakerTrade(offer, offer.getAmount(), fee, fee, true, offer.getPrice().getValue(),
                        offer.getMakerNodeAddress(), null, contract.getMediatorNodeAddress(), contract.getRefundAgentNodeAddress(),
                        wallet, process, "trade");
        trade.setContract(contract);
        trade.setContractAsJson(JsonUtil.objectToJson(contract));
        return trade;
    }
}
