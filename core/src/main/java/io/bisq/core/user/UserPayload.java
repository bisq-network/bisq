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

import com.google.protobuf.Message;
import io.bisq.common.persistable.PersistableCollectionUtil;
import io.bisq.common.persistable.PersistableEnvelope;
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
    // Persisted fields
    private String accountID;
    private Set<PaymentAccount> paymentAccounts = new HashSet<>();
    @Nullable
    private PaymentAccount currentPaymentAccount;
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

    private List<Arbitrator> acceptedArbitrators = new ArrayList<>();
    private List<Mediator> acceptedMediators = new ArrayList<>();

    public UserPayload() {
    }

    @Override
    public PB.DiscEnvelope toProtoMessage() {
        PB.UserPayload.Builder builder = PB.UserPayload.newBuilder()
                .setAccountId(accountID)
                .addAllPaymentAccounts(PersistableCollectionUtil.collectionToProto(paymentAccounts))
                .addAllAcceptedLanguageLocaleCodes(acceptedLanguageLocaleCodes)
                .addAllAcceptedArbitrators(PersistableCollectionUtil.collectionToProto(acceptedArbitrators, (Message storage) -> ((PB.StoragePayload) storage).getArbitrator()))
                .addAllAcceptedMediators(PersistableCollectionUtil.collectionToProto(acceptedMediators, (Message storage) -> ((PB.StoragePayload) storage).getMediator()));

        Optional.ofNullable(currentPaymentAccount)
                .ifPresent(paymentAccount -> builder.setCurrentPaymentAccount(paymentAccount.toProtoMessage()));
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
        return PB.DiscEnvelope.newBuilder().setUserPayload(builder).build();
    }

    public static UserPayload fromProto(PB.UserPayload user) {
        Set<PaymentAccount> collect = user.getPaymentAccountsList().stream().map(paymentAccount -> PaymentAccount.fromProto(paymentAccount)).collect(Collectors.toSet());
        UserPayload vo = new UserPayload(user.getAccountId(),
                collect,
                user.hasCurrentPaymentAccount() ? PaymentAccount.fromProto(user.getCurrentPaymentAccount()) : null,
                user.getAcceptedLanguageLocaleCodesList(),
                user.hasDevelopersAlert() ? Alert.fromProto(user.getDevelopersAlert()) : null,
                user.hasDisplayedAlert() ? Alert.fromProto(user.getDisplayedAlert()) : null,
                user.hasDevelopersFilter() ? Filter.fromProto(user.getDevelopersFilter()) : null,
                user.hasRegisteredArbitrator() ? Arbitrator.fromProto(user.getRegisteredArbitrator()) : null,
                user.hasRegisteredMediator() ? Mediator.fromProto(user.getRegisteredMediator()) : null,
                user.getAcceptedArbitratorsList().stream().map(Arbitrator::fromProto).collect(Collectors.toList()),
                user.getAcceptedMediatorsList().stream().map(Mediator::fromProto).collect(Collectors.toList())
        );
        return vo;
    }
}
