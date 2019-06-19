package bisq.api.http.service.auth;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

public class AuthFilter implements Filter {
    private final TokenRegistry tokenRegistry;
    private final ApiPasswordManager apiPasswordManager;


    public AuthFilter(ApiPasswordManager apiPasswordManager, TokenRegistry tokenRegistry) {
        this.apiPasswordManager = apiPasswordManager;
        this.tokenRegistry = tokenRegistry;
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
        if (!pathInfo.startsWith("/api") || pathInfo.endsWith("/user/authenticate") || pathInfo.endsWith("/user/password")) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        if (!apiPasswordManager.isPasswordSet()) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        String authorizationHeader = httpServletRequest.getHeader("authorization");
        if (authorizationHeader == null) {
            respondWithUnauthorizedStatus(httpServletResponse);
            return;
        }
        if (tokenRegistry.isValidToken(authorizationHeader))
            filterChain.doFilter(servletRequest, servletResponse);
        else
            respondWithUnauthorizedStatus(httpServletResponse);
    }

    @Override
    public void destroy() {
    }

    private void respondWithUnauthorizedStatus(HttpServletResponse httpServletResponse) {
        httpServletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
