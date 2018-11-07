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

import bisq.core.alert.Alert;
import bisq.core.arbitration.Arbitrator;
import bisq.core.arbitration.Mediator;
import bisq.core.filter.Filter;
import bisq.core.notifications.alerts.market.MarketAlertFilter;
import bisq.core.notifications.alerts.price.PriceAlertFilter;
import bisq.core.payment.PaymentAccount;
import bisq.core.proto.CoreProtoResolver;

import bisq.common.proto.ProtoUtil;
import bisq.common.proto.persistable.PersistableEnvelope;

import io.bisq.generated.protobuffer.PB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
@Data
@AllArgsConstructor
public class UserPayload implements PersistableEnvelope {
    @Nullable
    private String accountId;
    @Nullable
    private Set<PaymentAccount> paymentAccounts = new HashSet<>();
    @Nullable
    private PaymentAccount currentPaymentAccount;
    @Nullable
    private List<String> acceptedLanguageLocaleCodes = new ArrayList<>();
    @Nullable
    private Alert developersAlert;
    @Nullable
    private Alert displayedAlert;
    @Nullable
    private Filter developersFilter;
    @Nullable
    private Arbitrator registeredArbitrator;
    @Nullable
    private Mediator registeredMediator;
    @Nullable
    private List<Arbitrator> acceptedArbitrators = new ArrayList<>();
    @Nullable
    private List<Mediator> acceptedMediators = new ArrayList<>();
    @Nullable
    private PriceAlertFilter priceAlertFilter;
    @Nullable
    private List<MarketAlertFilter> marketAlertFilters = new ArrayList<>();

    public UserPayload() {
    }

    @Override
    public PB.PersistableEnvelope toProtoMessage() {
        PB.UserPayload.Builder builder = PB.UserPayload.newBuilder();
        Optional.ofNullable(accountId).ifPresent(e -> builder.setAccountId(accountId));
        Optional.ofNullable(paymentAccounts)
                .ifPresent(e -> builder.addAllPaymentAccounts(ProtoUtil.collectionToProto(paymentAccounts)));
        Optional.ofNullable(currentPaymentAccount)
                .ifPresent(e -> builder.setCurrentPaymentAccount(currentPaymentAccount.toProtoMessage()));
        Optional.ofNullable(acceptedLanguageLocaleCodes)
                .ifPresent(e -> builder.addAllAcceptedLanguageLocaleCodes(acceptedLanguageLocaleCodes));
        Optional.ofNullable(developersAlert)
                .ifPresent(developersAlert -> builder.setDevelopersAlert(developersAlert.toProtoMessage().getAlert()));
        Optional.ofNullable(displayedAlert)
                .ifPresent(displayedAlert -> builder.setDisplayedAlert(displayedAlert.toProtoMessage().getAlert()));
        Optional.ofNullable(developersFilter)
                .ifPresent(developersFilter -> builder.setDevelopersFilter(developersFilter.toProtoMessage().getFilter()));
        Optional.ofNullable(registeredArbitrator)
                .ifPresent(registeredArbitrator -> builder.setRegisteredArbitrator(registeredArbitrator.toProtoMessage().getArbitrator()));
        Optional.ofNullable(registeredMediator)
                .ifPresent(developersAlert -> builder.setDevelopersAlert(developersAlert.toProtoMessage().getAlert()));
        Optional.ofNullable(acceptedArbitrators)
                .ifPresent(e -> builder.addAllAcceptedArbitrators(ProtoUtil.collectionToProto(acceptedArbitrators,
                        message -> ((PB.StoragePayload) message).getArbitrator())));
        Optional.ofNullable(acceptedMediators)
                .ifPresent(e -> builder.addAllAcceptedMediators(ProtoUtil.collectionToProto(acceptedMediators,
                        message -> ((PB.StoragePayload) message).getMediator())));
        Optional.ofNullable(priceAlertFilter).ifPresent(priceAlertFilter -> builder.setPriceAlertFilter(priceAlertFilter.toProtoMessage()));
        Optional.ofNullable(marketAlertFilters)
                .ifPresent(e -> builder.addAllMarketAlertFilters(ProtoUtil.collectionToProto(marketAlertFilters)));
        return PB.PersistableEnvelope.newBuilder().setUserPayload(builder).build();
    }

    public static UserPayload fromProto(PB.UserPayload proto, CoreProtoResolver coreProtoResolver) {
        return new UserPayload(
                ProtoUtil.stringOrNullFromProto(proto.getAccountId()),
                proto.getPaymentAccountsList().isEmpty() ? new HashSet<>() : new HashSet<>(proto.getPaymentAccountsList().stream()
                        .map(e -> PaymentAccount.fromProto(e, coreProtoResolver))
                        .collect(Collectors.toSet())),
                proto.hasCurrentPaymentAccount() ? PaymentAccount.fromProto(proto.getCurrentPaymentAccount(), coreProtoResolver) : null,
                proto.getAcceptedLanguageLocaleCodesList().isEmpty() ? new ArrayList<>() : new ArrayList<>(proto.getAcceptedLanguageLocaleCodesList()),
                proto.hasDevelopersAlert() ? Alert.fromProto(proto.getDevelopersAlert()) : null,
                proto.hasDisplayedAlert() ? Alert.fromProto(proto.getDisplayedAlert()) : null,
                proto.hasDevelopersFilter() ? Filter.fromProto(proto.getDevelopersFilter()) : null,
                proto.hasRegisteredArbitrator() ? Arbitrator.fromProto(proto.getRegisteredArbitrator()) : null,
                proto.hasRegisteredMediator() ? Mediator.fromProto(proto.getRegisteredMediator()) : null,
                proto.getAcceptedArbitratorsList().isEmpty() ? new ArrayList<>() : new ArrayList<>(proto.getAcceptedArbitratorsList().stream()
                        .map(Arbitrator::fromProto)
                        .collect(Collectors.toList())),
                proto.getAcceptedMediatorsList().isEmpty() ? new ArrayList<>() : new ArrayList<>(proto.getAcceptedMediatorsList().stream()
                        .map(Mediator::fromProto)
                        .collect(Collectors.toList())),
                PriceAlertFilter.fromProto(proto.getPriceAlertFilter()),
                proto.getMarketAlertFiltersList().isEmpty() ? new ArrayList<>() : new ArrayList<>(proto.getMarketAlertFiltersList().stream()
                        .map(e -> MarketAlertFilter.fromProto(e, coreProtoResolver))
                        .collect(Collectors.toSet())));
    }
}
