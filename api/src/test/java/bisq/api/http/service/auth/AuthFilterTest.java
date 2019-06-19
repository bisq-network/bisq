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

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;



import com.github.javafaker.Faker;

public class AuthFilterTest {

    private TokenRegistry tokenRegistryMock;
    private ApiPasswordManager apiPasswordManagerMock;
    private AuthFilter authFilter;
    private HttpServletRequest servletRequestMock;
    private HttpServletResponse servletResponseMock;
    private FilterChain filterChainMock;

    @Before
    public void setUp() {
        tokenRegistryMock = mock(TokenRegistry.class);
        apiPasswordManagerMock = mock(ApiPasswordManager.class);
        authFilter = new AuthFilter(apiPasswordManagerMock, tokenRegistryMock);

        servletRequestMock = mock(HttpServletRequest.class);
        servletResponseMock = mock(HttpServletResponse.class);
        filterChainMock = mock(FilterChain.class);
    }

    @Test
    public void doFilter_passwordNotSet_passThrough() throws Exception {
        //        Given
        when(servletRequestMock.getPathInfo()).thenReturn("/api/version");

        //        When
        authFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);

        //        Then
        verify(filterChainMock).doFilter(servletRequestMock, servletResponseMock);
    }

    @Test
    public void doFilter_invalidAuthorizationToken_forbid() throws Exception {
        //        Given
        when(apiPasswordManagerMock.isPasswordSet()).thenReturn(true);
        when(servletRequestMock.getPathInfo()).thenReturn("/api/version");
        String invalidToken = Faker.instance().crypto().md5();
        when(servletRequestMock.getHeader("authorization")).thenReturn(invalidToken);

        //        When
        authFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);

        //        Then
        verify(filterChainMock, never()).doFilter(any(), any());
        verify(servletResponseMock).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    public void doFilter_missingAuthorizationToken_forbid() throws Exception {
        //        Given
        when(apiPasswordManagerMock.isPasswordSet()).thenReturn(true);
        when(servletRequestMock.getPathInfo()).thenReturn("/api/version");

        //        When
        authFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);

        //        Then
        verify(filterChainMock, never()).doFilter(any(), any());
        verify(servletResponseMock).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    public void doFilter_tokenInvalidButAuthenticationPath_passThrough() throws Exception {
        //        Given
        when(apiPasswordManagerMock.isPasswordSet()).thenReturn(true);
        when(servletRequestMock.getPathInfo()).thenReturn("/api/user/authenticate");
        String invalidToken = Faker.instance().crypto().md5();
        when(servletRequestMock.getHeader("authorization")).thenReturn(invalidToken);

        //        When
        authFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);

        //        Then
        verify(filterChainMock).doFilter(servletRequestMock, servletResponseMock);
    }

    @Test
    public void doFilter_tokenInvalidButPasswordChangePath_passThrough() throws Exception {
        //        Given
        when(apiPasswordManagerMock.isPasswordSet()).thenReturn(true);
        when(servletRequestMock.getPathInfo()).thenReturn("/api/user/password");
        String invalidToken = Faker.instance().crypto().md5();
        when(servletRequestMock.getHeader("authorization")).thenReturn(invalidToken);

        //        When
        authFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);

        //        Then
        verify(filterChainMock).doFilter(servletRequestMock, servletResponseMock);
    }

    @Test
    public void doFilter_tokenInvalidButNonApiPath_passThrough() throws Exception {
        //        Given
        when(apiPasswordManagerMock.isPasswordSet()).thenReturn(true);
        when(servletRequestMock.getPathInfo()).thenReturn("/docs");
        String invalidToken = Faker.instance().crypto().md5();
        when(servletRequestMock.getHeader("authorization")).thenReturn(invalidToken);

        //        When
        authFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);

        //        Then
        verify(filterChainMock).doFilter(servletRequestMock, servletResponseMock);
    }

    @Test
    public void doFilter_tokenValid_passThrough() throws Exception {
        //        Given
        when(apiPasswordManagerMock.isPasswordSet()).thenReturn(true);
        when(servletRequestMock.getPathInfo()).thenReturn("/api/version");
        String token = Faker.instance().crypto().md5();
        when(tokenRegistryMock.isValidToken(token)).thenReturn(true);
        when(servletRequestMock.getHeader("authorization")).thenReturn(token);

        //        When
        authFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);

        //        Then
        verify(filterChainMock).doFilter(servletRequestMock, servletResponseMock);
    }

    @Test
    public void destroy_always_doesNothing() {
        //        Given

        //        When
        authFilter.destroy();

        //        Then
    }

    @Test
    public void destroy_init_doesNothing() {
        //        Given

        //        When
        authFilter.init(null);

        //        Then
    }
}
