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

package bisq.core.payment.validation;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.util.validation.InputValidator;

import bisq.asset.AddressValidationResult;
import bisq.asset.Asset;
import bisq.asset.AssetRegistry;

import bisq.common.app.DevEnv;
import bisq.common.config.Config;

import com.google.inject.Inject;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class AltCoinAddressValidator extends InputValidator {

    private final AssetRegistry assetRegistry;
    private String currencyCode;

    @Inject
    public AltCoinAddressValidator(AssetRegistry assetRegistry) {
        this.assetRegistry = assetRegistry;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    @Override
    public ValidationResult validate(String input) {
        ValidationResult validationResult = super.validate(input);
        if (!validationResult.isValid || currencyCode == null)
            return validationResult;

        Optional<Asset> optionalAsset = CurrencyUtil.findAsset(assetRegistry, currencyCode,
                Config.baseCurrencyNetwork(), DevEnv.isDaoTradingActivated());
        if (optionalAsset.isPresent()) {
            Asset asset = optionalAsset.get();
            AddressValidationResult result = asset.validateAddress(input);
            if (!result.isValid()) {
                return new ValidationResult(false, Res.get(result.getI18nKey(), asset.getTickerSymbol(),
                        result.getMessage()));
            }

            return new ValidationResult(true);
        } else {
            return new ValidationResult(false);
        }
    }
}
