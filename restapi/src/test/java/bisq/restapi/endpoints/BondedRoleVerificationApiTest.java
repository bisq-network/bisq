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

package bisq.restapi.endpoints;

import bisq.core.dao.governance.bond.role.BondedRoleRegistration;
import bisq.core.dao.governance.bond.role.BondedRolesRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;



import bisq.restapi.RestApi;
import bisq.restapi.RestApiMain;
import bisq.restapi.dto.BondedRoleVerificationDto;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class BondedRoleVerificationApiTest {
    private static final String BOND_USER_NAME = "alice";
    private static final String ROLE_TYPE = "NETLAYER_MAINTAINER";
    private static final String PROFILE_ID = "profileId";
    private static final String PROPOSAL_TX_ID = "proposalTxId";
    private static final String LOCKUP_TX_ID = "lockupTxId";
    private static final String PROTOCOL_VERSION = String.valueOf(BondedRoleRegistration.CURRENT_PROTOCOL_VERSION);
    private static final BondedRoleRegistration REGISTRATION = BondedRoleRegistration.current(
            BOND_USER_NAME, ROLE_TYPE, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID, "AA==");

    private RestApi restApi;
    private BondedRolesRepository bondedRolesRepository;
    private BondedRoleVerificationApi api;

    @BeforeEach
    public void setup() {
        restApi = mock(RestApi.class);
        bondedRolesRepository = mock(BondedRolesRepository.class);
        when(restApi.getBondedRolesRepository()).thenReturn(bondedRolesRepository);
        RestApiMain application = mock(RestApiMain.class);
        when(application.getRestApi()).thenReturn(restApi);
        api = new BondedRoleVerificationApi(application);
    }

    @Test
    public void malformedHexSignatureReturnsVerificationFailure() {
        BondedRoleVerificationDto result = api.getBondedRoleVerification(
                PROTOCOL_VERSION,
                BOND_USER_NAME, ROLE_TYPE, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID, "not-hex");

        assertEquals("Bonded role invalid. Invalid signature encoding.", result.getErrorMessage());
        verifyNoInteractions(bondedRolesRepository);
    }

    @Test
    public void malformedProtocolVersionReturnsVerificationFailure() {
        BondedRoleVerificationDto result = api.getBondedRoleVerification(
                "v1",
                BOND_USER_NAME, ROLE_TYPE, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID, "00");

        assertEquals("Bonded role invalid. Invalid protocol version.", result.getErrorMessage());
        verifyNoInteractions(bondedRolesRepository);
    }

    @Test
    public void validHexSignatureIsConvertedAndVerified() {
        BondedRoleVerificationDto result = api.getBondedRoleVerification(
                PROTOCOL_VERSION,
                BOND_USER_NAME, ROLE_TYPE, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID, "00");

        assertNull(result.getErrorMessage());
        verify(bondedRolesRepository).verifyBondedRole(REGISTRATION);
    }

    @Test
    public void repositoryRejectionReturnsVerificationFailure() {
        doThrow(new IllegalArgumentException("Invalid signature"))
                .when(bondedRolesRepository)
                .verifyBondedRole(REGISTRATION);

        BondedRoleVerificationDto result = api.getBondedRoleVerification(
                PROTOCOL_VERSION,
                BOND_USER_NAME, ROLE_TYPE, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID, "00");

        assertEquals("Bonded role invalid. Invalid signature", result.getErrorMessage());
    }

    @Test
    public void daoStateWhichIsNotReadyIsRejected() {
        doThrow(new IllegalArgumentException("DAO not ready and in sync yet"))
                .when(restApi).checkDaoReadyAndInSync();

        WebApplicationException exception = assertThrows(WebApplicationException.class,
                () -> api.getBondedRoleVerification(
                        PROTOCOL_VERSION,
                        BOND_USER_NAME, ROLE_TYPE, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID, "00"));

        assertEquals(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), exception.getResponse().getStatus());
        BondedRoleVerificationDto body = (BondedRoleVerificationDto) exception.getResponse().getEntity();
        assertEquals("DAO state is not ready and in sync.", body.getErrorMessage());
        verifyNoInteractions(bondedRolesRepository);
    }

    @Test
    public void legacyRequestReturnsStructuredUpgradeRequiredResponse() {
        Response response = api.rejectLegacyBondedRoleVerification();

        assertEquals(426, response.getStatus());
        BondedRoleVerificationDto body = (BondedRoleVerificationDto) response.getEntity();
        assertEquals("Bonded role invalid. The unbound registration protocol is no longer supported.",
                body.getErrorMessage());
        verifyNoInteractions(bondedRolesRepository);
    }
}
