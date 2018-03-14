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

package bisq.desktop.main.offer.offerbook;

import bisq.core.offer.OfferMaker;
import bisq.core.offer.OfferPayload;

import com.natpryce.makeiteasy.Instantiator;
import com.natpryce.makeiteasy.MakeItEasy;
import com.natpryce.makeiteasy.Maker;
import com.natpryce.makeiteasy.Property;

import static bisq.core.offer.OfferMaker.btcUsdOffer;
import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;

public class OfferBookListItemMaker {

    public static final Property<OfferBookListItem, Long> price = new Property<>();
    public static final Property<OfferBookListItem, Long> amount = new Property<>();
    public static final Property<OfferBookListItem, Long> minAmount = new Property<>();
    public static final Property<OfferBookListItem, OfferPayload.Direction> direction = new Property<>();
    public static final Property<OfferBookListItem, Boolean> useMarketBasedPrice = new Property<>();
    public static final Property<OfferBookListItem, Double> marketPriceMargin = new Property<>();

    public static final Instantiator<OfferBookListItem> OfferBookListItem = lookup ->
            new OfferBookListItem(make(btcUsdOffer.but(
                    MakeItEasy.with(OfferMaker.price, lookup.valueOf(price, 100000L)),
                    with(OfferMaker.amount, lookup.valueOf(amount, 100000L)),
                    with(OfferMaker.minAmount, lookup.valueOf(amount, 100000L)),
                    with(OfferMaker.direction, lookup.valueOf(direction, OfferPayload.Direction.BUY)),
                    with(OfferMaker.useMarketBasedPrice, lookup.valueOf(useMarketBasedPrice, false)),
                    with(OfferMaker.marketPriceMargin, lookup.valueOf(marketPriceMargin, 0.0)))));

    public static final Instantiator<OfferBookListItem> OfferBookListItemWithRange = lookup ->
            new OfferBookListItem(make(btcUsdOffer.but(
                    MakeItEasy.with(OfferMaker.price, lookup.valueOf(price, 100000L)),
                    with(OfferMaker.minAmount, lookup.valueOf(minAmount, 100000L)),
                    with(OfferMaker.amount, lookup.valueOf(amount, 200000L)))));

    public static final Maker<OfferBookListItem> btcItem = a(OfferBookListItem);
    public static final Maker<OfferBookListItem> btcSellItem = a(OfferBookListItem, with(direction, OfferPayload.Direction.SELL));

    public static final Maker<OfferBookListItem> btcItemWithRange = a(OfferBookListItemWithRange);
}
