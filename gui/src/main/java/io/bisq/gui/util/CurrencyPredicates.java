package io.bisq.gui.util;

import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.TradeCurrency;

class CurrencyPredicates {
    boolean isCryptoCurrency(TradeCurrency currency) {
        return CurrencyUtil.isCryptoCurrency(currency.getCode());
    }

    boolean isFiatCurrency(TradeCurrency currency) {
        return CurrencyUtil.isFiatCurrency(currency.getCode());
    }
}
