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

package bisq.desktop.util;

import bisq.core.account.sign.SignedWitness;
import bisq.core.account.sign.SignedWitnessService;
import bisq.core.account.witness.AccountAgeWitness;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.crypto.LowRSigningKey;
import bisq.core.filter.DenyList;
import bisq.core.filter.FilterManager;
import bisq.core.filter.FilterPolicyService;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.Res;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.payload.SepaAccountPayload;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.crypto.Encryption;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;

import java.time.Clock;

import java.nio.charset.StandardCharsets;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// The description of an account in the details of a trade and in its contract
public class AccountWitnessDescriptionTest {
    private static final String BANNED = "Account was banned";

    private final FilterManager filterManager = mock(FilterManager.class);
    private final PubKeyRing owner = new PubKeyRing(Sig.generateKeyPair().getPublic(),
            Encryption.generateKeyPair().getPublic());
    private final PaymentAccountPayload paymentAccountPayload = new SepaAccountPayload(PaymentMethod.SEPA_ID, "id",
            CountryUtil.getAllSepaCountries());
    private AccountAgeWitnessService accountAgeWitnessService;

    @BeforeEach
    public void setup() {
        Res.setup();
        FilterPolicyService filterPolicyService = new FilterPolicyService(DenyList.fromProperties(new Properties()),
                filterManager);
        @SuppressWarnings("deprecation") ArbitratorManager arbitratorManager = mock(ArbitratorManager.class);
        when(arbitratorManager.isPublicKeyInList(any())).thenReturn(true);
        KeyRing keyRing = mock(KeyRing.class);
        SignedWitnessService signedWitnessService = new SignedWitnessService(keyRing, mock(P2PService.class),
                arbitratorManager, null, mock(AppendOnlyDataStoreService.class), filterPolicyService, false,
                Clock.systemUTC());
        accountAgeWitnessService = new AccountAgeWitnessService(keyRing, null, null, signedWitnessService, null, null,
                mock(AppendOnlyDataStoreService.class), Clock.systemUTC(), null, filterPolicyService);

        // An account which an arbitrator signed 100 days ago. Signing by an arbitrator before 09141eba92 put the key
        // of the counterparty into the owner key, so the signature counts also if the owner is banned.
        AccountAgeWitness witness = accountAgeWitnessService.getNewWitness(paymentAccountPayload, owner);
        accountAgeWitnessService.addToMap(witness);
        ECKey arbitratorKey = LowRSigningKey.from(new ECKey());
        signedWitnessService.addToMap(new SignedWitness(SignedWitness.VerificationMethod.ARBITRATOR,
                witness.getHash(),
                arbitratorKey.signMessage(Utilities.encodeToHex(witness.getHash())).getBytes(StandardCharsets.UTF_8),
                arbitratorKey.getPubKey(),
                Sig.getPublicKeyBytes(Sig.generateKeyPair().getPublic()),
                System.currentTimeMillis() - TimeUnit.DAYS.toMillis(100),
                Coin.COIN.value));
    }

    @Test
    public void aSignedAccountShowsTheTimeSinceSigning() {
        String description = DisplayUtils.getAccountWitnessDescription(accountAgeWitnessService,
                PaymentMethod.getPaymentMethod(PaymentMethod.SEPA_ID), paymentAccountPayload, owner);

        assertTrue(description.startsWith(Res.get("peerInfo.age.chargeBackRisk")), description);
    }

    @Test
    public void aBannedAccountShowsTheBanInsteadOfTheTimeSinceSigning() {
        when(filterManager.isWitnessSignerPubKeyBanned(Utilities.encodeToHex(owner.getSignaturePubKeyBytes())))
                .thenReturn(true);

        String description = DisplayUtils.getAccountWitnessDescription(accountAgeWitnessService,
                PaymentMethod.getPaymentMethod(PaymentMethod.SEPA_ID), paymentAccountPayload, owner);

        assertFalse(description.contains(Res.get("peerInfo.age.chargeBackRisk")), description);
        assertTrue(description.endsWith(", " + BANNED), description);
    }

    @Test
    public void aBannedAccountWithAWitnessWhichIsNotKnownShowsTheBan() {
        when(filterManager.isWitnessSignerPubKeyBanned(Utilities.encodeToHex(owner.getSignaturePubKeyBytes())))
                .thenReturn(true);
        PaymentAccountPayload unknownPaymentAccountPayload = new SepaAccountPayload(PaymentMethod.SEPA_ID, "id2",
                CountryUtil.getAllSepaCountries());

        String description = DisplayUtils.getAccountWitnessDescription(accountAgeWitnessService,
                PaymentMethod.getPaymentMethod(PaymentMethod.SEPA_ID), unknownPaymentAccountPayload, owner);

        assertEquals(Res.get("peerInfoIcon.tooltip.unknownAge") + ", " + BANNED, description);
    }
}
