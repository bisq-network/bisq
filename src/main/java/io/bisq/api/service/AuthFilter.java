package io.bisq.api.service;

import bisq.core.btc.wallet.BtcWalletService;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class AuthFilter implements Filter {


    private final BtcWalletService btcWalletService;
    private final TokenRegistry tokenRegistry;

    public AuthFilter(BtcWalletService btcWalletService, TokenRegistry tokenRegistry) {
        this.btcWalletService = btcWalletService;
        this.tokenRegistry = tokenRegistry;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        final HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        final HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
        final String pathInfo = httpServletRequest.getPathInfo();
        if(!pathInfo.startsWith("/api") || pathInfo.endsWith("/user/auth")) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        if (!btcWalletService.isWalletReady()) {
            httpServletResponse.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        if (!btcWalletService.isEncrypted()) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        final String authorizationHeader = httpServletRequest.getHeader("authorization");
        if (null == authorizationHeader) {
            httpServletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        final String token = authorizationHeader.substring("Bearer ".length());
        if (tokenRegistry.isValidToken(token))
            filterChain.doFilter(servletRequest, servletResponse);
        else
            httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    @Override
    public void destroy() {

    }
}
