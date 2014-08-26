/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.trade.createoffer;

import io.bitsquare.bank.BankAccountType;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.Country;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.utils.Fiat;

import java.util.Locale;

import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

public class CreateOfferPresenterTest {
    private static final Logger log = LoggerFactory.getLogger(CreateOfferPresenterTest.class);

    @Test
    public void testBindings() {
        CreateOfferModel model = new CreateOfferModel(null, null, null, null);

        BSFormatter.setLocale(Locale.US);
        BSFormatter.setFiatCurrencyCode("USD");
        
        CreateOfferPresenter presenter = new CreateOfferPresenter(model);
        presenter.onViewInitialized();

        model.collateralAsLong.set(100);
        presenter.price.set("500");
        presenter.amount.set("1");
        assertEquals("500.00", presenter.volume.get());
        assertEquals(Coin.COIN, model.amountAsCoin);
        assertEquals(Fiat.valueOf("USD", 500 * 10000), model.priceAsFiat);
        assertEquals(Fiat.valueOf("USD", 500 * 10000), model.tradeVolumeAsFiat);
        assertEquals(Coin.parseCoin("0.1011"), model.totalToPayAsCoin.get());

        presenter.price.set("500");
        presenter.volume.set("500");
        assertEquals("1.00", presenter.amount.get());
        assertEquals(Coin.COIN, model.amountAsCoin);
        assertEquals(Fiat.valueOf("USD", 500 * 10000), model.priceAsFiat);
        assertEquals(Fiat.valueOf("USD", 500 * 10000), model.tradeVolumeAsFiat);

        presenter.price.set("300");
        presenter.volume.set("1000");
        assertEquals("3.3333", presenter.amount.get());
        assertEquals(Coin.parseCoin("3.3333"), model.amountAsCoin);
        assertEquals(Fiat.valueOf("USD", 300 * 10000), model.priceAsFiat);
        assertEquals(Fiat.valueOf("USD", 9999900), model.tradeVolumeAsFiat);

        presenter.price.set("300");
        presenter.amount.set("3.3333");
        assertEquals("999.99", presenter.volume.get());
        assertEquals(Coin.parseCoin("3.3333"), model.amountAsCoin);
        assertEquals(Fiat.valueOf("USD", 300 * 10000), model.priceAsFiat);
        assertEquals(Fiat.valueOf("USD", 9999900), model.tradeVolumeAsFiat);

        presenter.price.set("300");
        presenter.amount.set("3.33333333");
        assertEquals("999.99", presenter.volume.get());
        assertEquals(Coin.parseCoin("3.3333"), model.amountAsCoin);
        assertEquals(Fiat.valueOf("USD", 300 * 10000), model.priceAsFiat);
        assertEquals(Fiat.valueOf("USD", 9999900), model.tradeVolumeAsFiat);


        model.collateralAsLong.set(100);
        assertEquals("Collateral (10.0 %):", presenter.collateralLabel.get());

        model.collateralAsLong.set(0);
        assertEquals("Collateral (0.0 %):", presenter.collateralLabel.get());


        model.bankAccountType.set(BankAccountType.SEPA.toString());
        assertEquals("Sepa", presenter.bankAccountType.get());

        model.bankAccountType.set(BankAccountType.WIRE.toString());
        assertEquals("Wire", presenter.bankAccountType.get());


        model.bankAccountCurrency.set("USD");
        assertEquals("USD", presenter.bankAccountCurrency.get());

        model.bankAccountCurrency.set("USD");
        assertEquals("USD", presenter.bankAccountCurrency.get());


        model.bankAccountCounty.set("Spain");
        assertEquals("Spain", presenter.bankAccountCounty.get());

        model.bankAccountCounty.set("Italy");
        assertEquals("Italy", presenter.bankAccountCounty.get());

        model.acceptedCountries.add(new Country(null, "Italy", null));
        assertEquals("Italy", presenter.acceptedCountries.get());

        model.acceptedCountries.add(new Country(null, "Spain", null));
        assertEquals("Italy, Spain", presenter.acceptedCountries.get());

    }


}
