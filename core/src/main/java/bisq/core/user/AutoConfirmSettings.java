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

package bisq.core.user;

import bisq.common.proto.persistable.PersistablePayload;

import com.google.protobuf.Message;

import org.bitcoinj.core.Coin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public final class AutoConfirmSettings implements PersistablePayload {
    public interface Listener {
        void onChange();
    }

    private boolean enabled;
    private int requiredConfirmations;
    private long tradeLimit;
    private List<String> serviceAddresses;
    private String currencyCode;
    private List<Listener> listeners = new CopyOnWriteArrayList<>();

    @SuppressWarnings("SameParameterValue")
    static Optional<AutoConfirmSettings> getDefault(List<String> serviceAddresses, String currencyCode) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (currencyCode) {
            case "XMR":
                return Optional.of(new AutoConfirmSettings(
                        false,
                        5,
                        Coin.COIN.value,
                        serviceAddresses,
                        "XMR"));
            default:
                log.error("No AutoConfirmSettings supported yet for currency {}", currencyCode);
                return Optional.empty();
        }
    }

    public AutoConfirmSettings(boolean enabled,
                               int requiredConfirmations,
                               long tradeLimit,
                               List<String> serviceAddresses,
                               String currencyCode) {
        this.enabled = enabled;
        this.requiredConfirmations = requiredConfirmations;
        this.tradeLimit = tradeLimit;
        this.serviceAddresses = serviceAddresses;
        this.currencyCode = currencyCode;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Message toProtoMessage() {
        return protobuf.AutoConfirmSettings.newBuilder()
                .setEnabled(enabled)
                .setRequiredConfirmations(requiredConfirmations)
                .setTradeLimit(tradeLimit)
                .addAllServiceAddresses(serviceAddresses)
                .setCurrencyCode(currencyCode)
                .build();
    }

    public static AutoConfirmSettings fromProto(protobuf.AutoConfirmSettings proto) {
        List<String> serviceAddresses = proto.getServiceAddressesList().isEmpty() ?
                new ArrayList<>() : new ArrayList<>(proto.getServiceAddressesList());
        return new AutoConfirmSettings(
                proto.getEnabled(),
                proto.getRequiredConfirmations(),
                proto.getTradeLimit(),
                serviceAddresses,
                proto.getCurrencyCode());
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        listeners.forEach(Listener::onChange);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        notifyListeners();
    }

    public void setRequiredConfirmations(int requiredConfirmations) {
        this.requiredConfirmations = requiredConfirmations;
        notifyListeners();
    }

    public void setTradeLimit(long tradeLimit) {
        this.tradeLimit = tradeLimit;
        notifyListeners();
    }

    public void setServiceAddresses(List<String> serviceAddresses) {
        this.serviceAddresses = serviceAddresses;
        notifyListeners();
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
        notifyListeners();
    }
}
