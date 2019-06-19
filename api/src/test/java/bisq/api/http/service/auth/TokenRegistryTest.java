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

package bisq.api.http.service.auth;

import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TokenRegistryTest {

    private TokenRegistry tokenRegistry;

    @Before
    public void setUp() {
        tokenRegistry = new TokenRegistry();
    }

    @Test
    public void generateToken_always_returnsNewValidToken() {
        //        Given

        //        When
        String token1 = tokenRegistry.generateToken();
        String token2 = tokenRegistry.generateToken();

        //        Then
        assertNotNull(token1);
        assertNotNull(token2);
        assertNotEquals(token1, token2);
        assertTrue(tokenRegistry.isValidToken(token1));
        assertTrue(tokenRegistry.isValidToken(token2));
    }

    @Test
    public void generateToken_always_returnsNewUniqueToken() {
        //        Given
        TokenRegistry.RandomStringGenerator randomStringGeneratorMock = mock(TokenRegistry.RandomStringGenerator.class);
        tokenRegistry = new TokenRegistry(randomStringGeneratorMock, System::currentTimeMillis);
        when(randomStringGeneratorMock.generateRandomString())
                .thenReturn("a")
                .thenReturn("a")
                .thenReturn("b");

        //        When
        String token1 = tokenRegistry.generateToken();
        String token2 = tokenRegistry.generateToken();

        //        Then
        assertEquals("a", token1);
        assertEquals("b", token2);
        assertTrue(tokenRegistry.isValidToken(token1));
        assertTrue(tokenRegistry.isValidToken(token2));
    }

    @Test
    public void generateToken_always_removesExpiredTokens() {
        //        Given
        TokenRegistry.TimeProvider timeProviderMock = mock(TokenRegistry.TimeProvider.class);
        tokenRegistry = new TokenRegistry(() -> UUID.randomUUID().toString(), timeProviderMock);
        when(timeProviderMock.getTime()).thenReturn(0L);
        String token1 = tokenRegistry.generateToken();
        when(timeProviderMock.getTime()).thenReturn(TokenRegistry.TTL + 1);

        //        When
        String token2 = tokenRegistry.generateToken();
        when(timeProviderMock.getTime()).thenReturn(TokenRegistry.TTL + 1 + TokenRegistry.TTL);

        //        Then
        assertTrue(tokenRegistry.isValidToken(token2));
        assertFalse(tokenRegistry.isValidToken(token1));
    }

    @Test
    public void isValidToken_invalidToken_returnsFalse() {
        //        Given

        //        When
        boolean result = tokenRegistry.isValidToken(UUID.randomUUID().toString());

        //        Then
        assertFalse(result);
    }

    @Test
    public void isValidToken_expiredToken_returnsFalse() {
        //        Given
        TokenRegistry.TimeProvider timeProviderMock = mock(TokenRegistry.TimeProvider.class);
        tokenRegistry = new TokenRegistry(() -> UUID.randomUUID().toString(), timeProviderMock);
        when(timeProviderMock.getTime()).thenReturn(0L);
        String token = tokenRegistry.generateToken();
        when(timeProviderMock.getTime()).thenReturn(TokenRegistry.TTL + 1);

        //        When
        boolean result = tokenRegistry.isValidToken(token);

        //        Then
        assertFalse(result);
    }

    @Test
    public void clear_always_removesAllTokens() {
        //        Given
        String token1 = tokenRegistry.generateToken();
        String token2 = tokenRegistry.generateToken();

        //        When
        tokenRegistry.clear();

        //        Then
        assertFalse(tokenRegistry.isValidToken(token1));
        assertFalse(tokenRegistry.isValidToken(token2));
    }
}
