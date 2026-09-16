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

package bisq.core.support.dispute.refund;

import bisq.core.btc.wallet.utils.DepositTransactionUtils;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.proto.CoreProtoResolver;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.util.JsonUtil;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.Hash;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.ScriptBuilder;

import java.security.KeyPair;
import java.util.List;

import static org.mockito.Mockito.mock;

/** Real contract, deposit and identity signatures shared by claim lifecycle and signing tests. */
final class RefundClaimTestData {
    static final long OPENING_DATE = 1_700_000_000_000L;
    static final String TRADE_ID = "refund-claim-test";
    static final NodeAddress BUYER_ADDRESS = new NodeAddress("a".repeat(56) + ".onion", 9999);
    static final NodeAddress SELLER_ADDRESS = new NodeAddress("b".repeat(56) + ".onion", 9999);
    static final NodeAddress AGENT_ADDRESS = new NodeAddress("c".repeat(56) + ".onion", 9999);
    final DeterministicKey buyerKey = HDKeyDerivation.createMasterPrivateKey(new byte[32]);
    final DeterministicKey sellerKey = HDKeyDerivation.createMasterPrivateKey(
            new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
    final KeyPair buyerIdentity = Sig.generateKeyPair();
    final KeyPair sellerIdentity = Sig.generateKeyPair();
    final PubKeyRing buyer = new PubKeyRing(buyerIdentity.getPublic(), Encryption.generateKeyPair().getPublic());
    final PubKeyRing seller = new PubKeyRing(sellerIdentity.getPublic(), Encryption.generateKeyPair().getPublic());
    final Contract contract;
    final Transaction deposit;
    private final PubKeyRing agent;

    RefundClaimTestData(PubKeyRing agent, boolean buyerIsMaker) {
        this.agent = agent;
        PubKeyRing maker = buyerIsMaker ? buyer : seller;
        OfferPayload offer = new OfferPayload(TRADE_ID, 0,
                buyerIsMaker ? BUYER_ADDRESS : SELLER_ADDRESS, maker,
                buyerIsMaker ? OfferDirection.BUY : OfferDirection.SELL,
                1_000_000, 0, false, 10_000, 10_000, "BTC", "USD",
                List.of(), List.of(), PaymentMethod.SEPA_ID, "makerAccountId",
                null, null, null, null, null, "1.0.0", 0, 0, 0, true,
                3_000, 4_000, 0, 0, false, false, 0, 0, false, null, null, 0);
        offer.setOfferFeePaymentTxId("ab".repeat(32));
        contract = new Contract(offer, 10_000, 1_000_000, "ef".repeat(32),
                BUYER_ADDRESS, SELLER_ADDRESS, AGENT_ADDRESS, buyerIsMaker,
                "makerAccountId", "takerAccountId", null, null,
                maker, buyerIsMaker ? seller : buyer,
                "makerPayoutAddress", "takerPayoutAddress",
                buyerIsMaker ? buyerKey.getPubKey() : sellerKey.getPubKey(),
                buyerIsMaker ? sellerKey.getPubKey() : buyerKey.getPubKey(),
                0, AGENT_ADDRESS, null, null, PaymentMethod.SEPA_ID, PaymentMethod.SEPA_ID, 0);
        Transaction makerFee = new Transaction(MainNetParams.get());
        makerFee.addOutput(Coin.valueOf(600_000), ScriptBuilder.createP2WPKHOutputScript(buyerKey));
        Transaction takerFee = new Transaction(MainNetParams.get());
        takerFee.addOutput(Coin.valueOf(600_000), ScriptBuilder.createP2WPKHOutputScript(sellerKey));
        deposit = new Transaction(MainNetParams.get());
        deposit.addInput(makerFee.getOutput(0));
        deposit.addInput(takerFee.getOutput(0));
        deposit.addOutput(Coin.valueOf(18_000), DepositTransactionUtils.get2of2MultiSigOutputScript(
                contract.getBuyerMultiSigPubKey(), contract.getSellerMultiSigPubKey()));
    }

    Dispute unsigned(boolean openerIsBuyer, long date) throws CryptoException {
        boolean buyerIsMaker = contract.isBuyerMakerAndSellerTaker();
        PubKeyRing opener = openerIsBuyer ? buyer : seller;
        String json = JsonUtil.objectToJson(contract);
        Dispute dispute = new Dispute(date, TRADE_ID, opener.hashCode(),
                openerIsBuyer, openerIsBuyer == buyerIsMaker, opener, 0, 0,
                contract, Hash.getSha256Hash(json), deposit.bitcoinSerialize(), null,
                deposit.getTxId().toString(), null, json,
                Sig.sign((buyerIsMaker ? buyerIdentity : sellerIdentity).getPrivate(), json),
                Sig.sign((buyerIsMaker ? sellerIdentity : buyerIdentity).getPrivate(), json),
                agent, false, SupportType.REFUND);
        dispute.setTradeTxFee(1_000);
        dispute.setDelayedPayoutTxId("cd".repeat(32));
        dispute.setBurningManSelectionHeight(1);
        return dispute;
    }

    Dispute signed(boolean openerIsBuyer, long date) throws CryptoException {
        Dispute dispute = unsigned(openerIsBuyer, date);
        dispute.setRefundClaimOpeningDate(date);
        dispute.setRefundClaimSignature(RefundClaimSignature.sign(dispute,
                openerIsBuyer ? buyerKey : sellerKey, null));
        return dispute;
    }

    static Dispute restore(protobuf.Dispute proto) {
        return Dispute.fromProto(proto, mock(CoreProtoResolver.class));
    }
}
