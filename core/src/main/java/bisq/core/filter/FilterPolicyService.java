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

package bisq.core.filter;

import bisq.core.offer.Offer;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Utils;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

@Singleton
public class FilterPolicyService {
    private final DenyList denyList;
    private final FilterManager filterManager;

    @Inject
    public FilterPolicyService(DenyList denyList, FilterManager filterManager) {
        this.denyList = denyList;
        this.filterManager = filterManager;
    }

    public boolean isOfferIdBanned(String offerId) {
        return filterManager.isOfferIdBanned(offerId);
    }

    public boolean isCurrencyBanned(String currencyCode) {
        return denyList.getBannedCurrencies().contains(currencyCode) ||
                filterManager.isCurrencyBanned(currencyCode);
    }

    public boolean isPaymentMethodBanned(PaymentMethod paymentMethod) {
        return paymentMethod != null &&
                (denyList.getBannedPaymentMethods().contains(paymentMethod.getId()) ||
                        filterManager.isPaymentMethodBanned(paymentMethod));
    }

    public boolean isNodeAddressBanned(NodeAddress nodeAddress) {
        return nodeAddress != null &&
                (denyList.getNodeAddressesBannedFromTrading().contains(nodeAddress.getFullAddress()) ||
                        filterManager.isNodeAddressBanned(nodeAddress));
    }

    public boolean isNodeAddressBannedFromNetwork(NodeAddress nodeAddress) {
        return nodeAddress != null &&
                (denyList.getNodeAddressesBannedFromNetwork().contains(nodeAddress.getFullAddress()) ||
                        filterManager.isNodeAddressBannedFromNetwork(nodeAddress));
    }

    public boolean isAutoConfExplorerBanned(String address) {
        return denyList.getBannedAutoConfExplorers().contains(address) ||
                filterManager.isAutoConfExplorerBanned(address);
    }

    public boolean isWitnessSignerPubKeyBanned(String witnessSignerPubKeyAsHex) {
        return denyList.getBannedAccountWitnessSignerPubKeys().contains(witnessSignerPubKeyAsHex) ||
                filterManager.isWitnessSignerPubKeyBanned(witnessSignerPubKeyAsHex);
    }

    public boolean arePeersPaymentAccountDataBanned(@Nullable PaymentAccountPayload paymentAccountPayload) {
        return isPaymentAccountDataHashBanned(paymentAccountPayload) ||
                filterManager.arePeersPaymentAccountDataBanned(paymentAccountPayload);
    }

    public boolean isPaymentAccountDataHashBanned(@Nullable PaymentAccountPayload paymentAccountPayload) {
        return paymentAccountPayload != null &&
                isPaymentAccountDataHashBanned(Utilities.bytesAsHexString(paymentAccountPayload.getHashForContract()));
    }

    public boolean isPaymentAccountDataHashBanned(String paymentAccountDataHash) {
        return denyList.isPaymentAccountDataHashBanned(paymentAccountDataHash);
    }

    public boolean isDelayedPayoutPaymentAccount(PaymentAccountPayload paymentAccountPayload) {
        return filterManager.isDelayedPayoutPaymentAccount(paymentAccountPayload);
    }

    public boolean isBsqSwapDisabled() {
        return filterManager.isBsqSwapDisabled();
    }

    public boolean isApiDisabled() {
        Filter filter = filterManager.getFilter();
        return filter != null && filter.isDisableApi();
    }

    public boolean requireUpdateToNewVersionForTrading() {
        return denyList.hasRequiredVersionForTrading() &&
                Version.isNewVersion(denyList.getRequiredVersionForTrading()) ||
                filterManager.requireUpdateToNewVersionForTrading();
    }

    public boolean isPriceInBounds(Offer offer) {
        return filterManager.isPriceInBounds(offer);
    }

    public boolean isProofOfWorkValid(Offer offer) {
        return filterManager.isProofOfWorkValid(offer);
    }

    public List<Integer> getEnabledPowVersions() {
        return filterManager.getEnabledPowVersions();
    }

    public double getPowDifficulty() {
        Filter filter = filterManager.getFilter();
        return filter != null ? filter.getPowDifficulty() : 0.0;
    }

    public List<String> getBannedArbitrators() {
        Filter filter = filterManager.getFilter();
        return filter != null ? filter.getArbitrators() : List.of();
    }

    public List<String> getBannedMediators() {
        Filter filter = filterManager.getFilter();
        return merge(denyList.getBannedMediators(), filter != null ? filter.getMediators() : List.of());
    }

    public List<String> getBannedRefundAgents() {
        Filter filter = filterManager.getFilter();
        return merge(denyList.getBannedRefundAgents(), filter != null ? filter.getRefundAgents() : List.of());
    }

    private <T> List<T> merge(List<T> first, List<T> second) {
        Set<T> merged = new LinkedHashSet<>(first);
        merged.addAll(second);
        return List.copyOf(merged);
    }
}
