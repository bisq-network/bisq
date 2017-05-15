/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.user;

import io.bisq.common.proto.ProtoResolver;
import io.bisq.common.proto.ProtoUtil;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.core.alert.Alert;
import io.bisq.core.arbitration.Arbitrator;
import io.bisq.core.arbitration.Mediator;
import io.bisq.core.filter.Filter;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.generated.protobuffer.PB;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

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
                        storage -> ((PB.StoragePayload) storage).getArbitrator())));
        Optional.ofNullable(acceptedMediators)
                .ifPresent(e -> builder.addAllAcceptedMediators(ProtoUtil.collectionToProto(acceptedMediators,
                        storage -> ((PB.StoragePayload) storage).getMediator())));
        return PB.PersistableEnvelope.newBuilder().setUserPayload(builder).build();
    }

    public static UserPayload fromProto(PB.UserPayload proto, ProtoResolver resolver) {
        return new UserPayload(
                proto.getAccountId().isEmpty() ? null : proto.getAccountId(),
                proto.getPaymentAccountsList().isEmpty() ? null : proto.getPaymentAccountsList().stream()
                        .map(e -> PaymentAccount.fromProto(e, resolver))
                        .collect(Collectors.toSet()),
                proto.hasCurrentPaymentAccount() ? PaymentAccount.fromProto(proto.getCurrentPaymentAccount(), resolver) : null,
                proto.getAcceptedLanguageLocaleCodesList().isEmpty() ? null : new ArrayList<>(proto.getAcceptedLanguageLocaleCodesList()),
                proto.hasDevelopersAlert() ? Alert.fromProto(proto.getDevelopersAlert()) : null,
                proto.hasDisplayedAlert() ? Alert.fromProto(proto.getDisplayedAlert()) : null,
                proto.hasDevelopersFilter() ? Filter.fromProto(proto.getDevelopersFilter()) : null,
                proto.hasRegisteredArbitrator() ? Arbitrator.fromProto(proto.getRegisteredArbitrator()) : null,
                proto.hasRegisteredMediator() ? Mediator.fromProto(proto.getRegisteredMediator()) : null,
                proto.getAcceptedArbitratorsList().isEmpty() ? null : proto.getAcceptedArbitratorsList().stream()
                        .map(Arbitrator::fromProto)
                        .collect(Collectors.toList()),
                proto.getAcceptedMediatorsList().isEmpty() ? null : proto.getAcceptedMediatorsList().stream()
                        .map(Mediator::fromProto)
                        .collect(Collectors.toList())
        );
    }
/*
    public void apply(UserPayload persisted) {
        if (persisted != null) {
            accountId = persisted.getAccountId();
            paymentAccounts = persisted.getPaymentAccounts();
            currentPaymentAccount = persisted.getCurrentPaymentAccount();
            acceptedLanguageLocaleCodes = persisted.getAcceptedLanguageLocaleCodes();
            developersAlert = persisted.getDevelopersAlert();
            displayedAlert = persisted.getDisplayedAlert();
            developersFilter = persisted.getDevelopersFilter();
            registeredArbitrator = persisted.getRegisteredArbitrator();
            registeredMediator = persisted.getRegisteredMediator();
            acceptedArbitrators = persisted.getAcceptedArbitrators();
            acceptedMediators = persisted.getAcceptedMediators();
        }
    }*/
}
