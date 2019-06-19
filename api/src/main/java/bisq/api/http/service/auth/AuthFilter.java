package bisq.api.http.service.auth;

import bisq.api.http.exceptions.UnauthorizedException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

import java.util.Base64;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class AuthFilter implements Filter {
    private final ApiPasswordManager apiPasswordManager;


    public AuthFilter(ApiPasswordManager apiPasswordManager) {
        this.apiPasswordManager = apiPasswordManager;
    }

    @NotNull
    private static String decodeBase64Safely(String encodedCredentials) {
        try {
            return new String(Base64.getDecoder().decode(encodedCredentials));
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
        String pathInfo = httpServletRequest.getPathInfo();
        if (!pathInfo.startsWith("/api") || pathInfo.endsWith("/user/password")) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        if (!apiPasswordManager.isPasswordSet()) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        String authorizationHeader = httpServletRequest.getHeader("authorization");
        try {
            String password = getPassword(authorizationHeader);
            apiPasswordManager.authenticate(password);
            filterChain.doFilter(servletRequest, servletResponse);
        } catch (UnauthorizedException e) {
            respondWithUnauthorizedStatus(httpServletResponse);
        }
    }

    @Override
    public void destroy() {
    }

    private void respondWithUnauthorizedStatus(HttpServletResponse httpServletResponse) {
        httpServletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Nullable
    private String getPassword(@Nullable String authorizationHeader) {
        String headerValuePrefix = "basic ";
        if (authorizationHeader == null || !authorizationHeader.toLowerCase().startsWith(headerValuePrefix)) {
            return null;
        }
        String encodedCredentials = authorizationHeader.substring(headerValuePrefix.length());
        String usernameColonPassword = decodeBase64Safely(encodedCredentials);
        int indexOfColon = usernameColonPassword.indexOf(":");
        if (0 > indexOfColon) {
            return null;
        }
        return usernameColonPassword.substring(indexOfColon + 1);
    }
}
