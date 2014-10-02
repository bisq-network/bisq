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

package io.bitsquare.gui.main.trade.createoffer;

import io.bitsquare.bank.BankAccountType;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BtcValidator;
import io.bitsquare.gui.util.validation.FiatValidator;
import io.bitsquare.locale.Country;
import io.bitsquare.user.User;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.utils.Fiat;

import java.util.Locale;

import org.junit.Before;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

public class CreateOfferPMTest {
    private static final Logger log = LoggerFactory.getLogger(CreateOfferPMTest.class);

    private CreateOfferModel model;
    private CreateOfferPM presenter;

    @Before
    public void setup() {
        BSFormatter formatter = new BSFormatter(new User());
        formatter.setLocale(Locale.US);
        formatter.setFiatCurrencyCode("USD");
        model = new CreateOfferModel(null, null, null, null, null, formatter);

        presenter = new CreateOfferPM(model, new FiatValidator(null), new BtcValidator(), formatter);
        presenter.initialize();
    }


    @Test
    public void testBindings() {
        model.collateralAsLong.set(100);
        presenter.price.set("500");
        presenter.amount.set("1");
        assertEquals("500.00", presenter.volume.get());
        assertEquals(Coin.COIN, model.amountAsCoin.get());
        assertEquals(Fiat.valueOf("USD", 500 * 10000), model.priceAsFiat.get());
        assertEquals(Fiat.valueOf("USD", 500 * 10000), model.volumeAsFiat.get());

        presenter.price.set("500");
        presenter.volume.set("500");
        assertEquals("1.00", presenter.amount.get());
        assertEquals(Coin.COIN, model.amountAsCoin.get());
        assertEquals(Fiat.valueOf("USD", 500 * 10000), model.priceAsFiat.get());
        assertEquals(Fiat.valueOf("USD", 500 * 10000), model.volumeAsFiat.get());

        presenter.price.set("300");
        presenter.volume.set("1000");
        assertEquals("3.3333", presenter.amount.get());
        assertEquals(Coin.parseCoin("3.3333"), model.amountAsCoin.get());
        assertEquals(Fiat.valueOf("USD", 300 * 10000), model.priceAsFiat.get());
        assertEquals(Fiat.valueOf("USD", 9999900), model.volumeAsFiat.get());

        presenter.price.set("300");
        presenter.amount.set("3.3333");
        assertEquals("999.99", presenter.volume.get());
        assertEquals(Coin.parseCoin("3.3333"), model.amountAsCoin.get());
        assertEquals(Fiat.valueOf("USD", 300 * 10000), model.priceAsFiat.get());
        assertEquals(Fiat.valueOf("USD", 9999900), model.volumeAsFiat.get());

        presenter.price.set("300");
        presenter.amount.set("3.33333333");
        assertEquals("999.99", presenter.volume.get());
        assertEquals(Coin.parseCoin("3.3333"), model.amountAsCoin.get());
        assertEquals(Fiat.valueOf("USD", 300 * 10000), model.priceAsFiat.get());
        assertEquals(Fiat.valueOf("USD", 9999900), model.volumeAsFiat.get());


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
