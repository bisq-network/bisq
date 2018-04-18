package io.bisq.api.service;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletsManager;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.spongycastle.crypto.params.KeyParameter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class AuthFilter implements Filter {


    private final BtcWalletService btcWalletService;
    private final WalletsManager walletsManager;

    public AuthFilter(BtcWalletService btcWalletService, WalletsManager walletsManager) {
        this.btcWalletService = btcWalletService;
        this.walletsManager = walletsManager;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        final HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
        if (!btcWalletService.isWalletReady()) {
            httpServletResponse.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        if (!btcWalletService.isWalletReady() || !btcWalletService.isEncrypted()) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        KeyCrypterScrypt keyCrypterScrypt = walletsManager.getKeyCrypterScrypt();
        final String authorizationHeader = ((HttpServletRequest) servletRequest).getHeader("authorization");
        final String token = null == authorizationHeader ? "" : authorizationHeader.substring("Bearer ".length());
        KeyParameter aesKey = keyCrypterScrypt.deriveKey(token);

        final boolean result = walletsManager.checkAESKey(aesKey);
        if (result)
            filterChain.doFilter(servletRequest, servletResponse);
        else {
            httpServletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    @Override
    public void destroy() {

    }
}
