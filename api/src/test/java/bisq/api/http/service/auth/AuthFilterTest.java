package bisq.api.http.service.auth;

import bisq.api.http.exceptions.UnauthorizedException;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Base64;

import javax.annotation.Nonnull;

import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;



import com.github.javafaker.Faker;
import org.mockito.AdditionalMatchers;

public class AuthFilterTest {

    private ApiPasswordManager apiPasswordManagerMock;
    private AuthFilter authFilter;
    private HttpServletRequest servletRequestMock;
    private HttpServletResponse servletResponseMock;
    private FilterChain filterChainMock;

    @Before
    public void setUp() {
        apiPasswordManagerMock = mock(ApiPasswordManager.class);
        authFilter = new AuthFilter(apiPasswordManagerMock);

        servletRequestMock = mock(HttpServletRequest.class);
        servletResponseMock = mock(HttpServletResponse.class);
        filterChainMock = mock(FilterChain.class);
    }

    @Test
    public void doFilter_passwordNotSet_passThrough() throws Exception {
        //        Given
        when(servletRequestMock.getPathInfo()).thenReturn("/api/version");
        doThrow(UnauthorizedException.class).when(apiPasswordManagerMock).authenticate(any());

        //        When
        authFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);

        //        Then
        verify(filterChainMock).doFilter(servletRequestMock, servletResponseMock);
    }

    @Test
    public void doFilter_invalidAuthorizationHeader_forbid() throws Exception {
        //        Given
        when(apiPasswordManagerMock.isPasswordSet()).thenReturn(true);
        doThrow(UnauthorizedException.class).when(apiPasswordManagerMock).authenticate(any());
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
    public void doFilter_missingAuthorizationHeader_forbid() throws Exception {
        //        Given
        when(apiPasswordManagerMock.isPasswordSet()).thenReturn(true);
        doThrow(UnauthorizedException.class).when(apiPasswordManagerMock).authenticate(any());
        when(servletRequestMock.getPathInfo()).thenReturn("/api/version");

        //        When
        authFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);

        //        Then
        verify(filterChainMock, never()).doFilter(any(), any());
        verify(servletResponseMock).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    public void doFilter_authHeaderInvalidButPasswordChangePath_passThrough() throws Exception {
        //        Given
        when(apiPasswordManagerMock.isPasswordSet()).thenReturn(true);
        doThrow(UnauthorizedException.class).when(apiPasswordManagerMock).authenticate(any());
        when(servletRequestMock.getPathInfo()).thenReturn("/api/user/password");
        String invalidToken = Faker.instance().crypto().md5();
        when(servletRequestMock.getHeader("authorization")).thenReturn(invalidToken);

        //        When
        authFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);

        //        Then
        verify(filterChainMock).doFilter(servletRequestMock, servletResponseMock);
    }

    @Test
    public void doFilter_authHeaderInvalidButNonApiPath_passThrough() throws Exception {
        //        Given
        when(apiPasswordManagerMock.isPasswordSet()).thenReturn(true);
        doThrow(UnauthorizedException.class).when(apiPasswordManagerMock).authenticate(any());
        when(servletRequestMock.getPathInfo()).thenReturn("/docs");
        String invalidToken = Faker.instance().crypto().md5();
        when(servletRequestMock.getHeader("authorization")).thenReturn(invalidToken);

        //        When
        authFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);

        //        Then
        verify(filterChainMock).doFilter(servletRequestMock, servletResponseMock);
    }

    @Test
    public void doFilter_authHeaderValid_passThrough() throws Exception {
        //        Given
        String password = Faker.instance().crypto().md5();
        when(apiPasswordManagerMock.isPasswordSet()).thenReturn(true);
        doThrow(UnauthorizedException.class).when(apiPasswordManagerMock).authenticate(AdditionalMatchers.not(eq(password)));
        when(servletRequestMock.getPathInfo()).thenReturn("/api/version");
        String header = getAuthHeaderForPassword(password);
        when(servletRequestMock.getHeader("authorization")).thenReturn(header);

        //        When
        authFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);

        //        Then
        verify(filterChainMock).doFilter(servletRequestMock, servletResponseMock);
    }

    @Test
    public void doFilter_authHeaderMissingColon_forbid() throws Exception {
        //        Given
        String password = Faker.instance().crypto().md5();
        when(apiPasswordManagerMock.isPasswordSet()).thenReturn(true);
        doThrow(UnauthorizedException.class).when(apiPasswordManagerMock).authenticate(AdditionalMatchers.not(eq(password)));
        when(servletRequestMock.getPathInfo()).thenReturn("/api/version");
        String header = "Basic " + Base64.getEncoder().encodeToString(password.getBytes());
        when(servletRequestMock.getHeader("authorization")).thenReturn(header);

        //        When
        authFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);

        //        Then
        verify(filterChainMock, never()).doFilter(any(), any());
        verify(servletResponseMock).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    public void doFilter_authHeaderNotBase64_forbid() throws Exception {
        //        Given
        when(apiPasswordManagerMock.isPasswordSet()).thenReturn(true);
        doThrow(UnauthorizedException.class).when(apiPasswordManagerMock).authenticate(any());
        when(servletRequestMock.getPathInfo()).thenReturn("/api/version");
        String header = "Basic abcde";
        when(servletRequestMock.getHeader("authorization")).thenReturn(header);

        //        When
        authFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);

        //        Then
        verify(filterChainMock, never()).doFilter(any(), any());
        verify(servletResponseMock).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
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

    @Nonnull
    private String getAuthHeaderForPassword(@Nonnull String password) {
        return "Basic " + Base64.getEncoder().encodeToString((":" + password).getBytes());
    }
}
