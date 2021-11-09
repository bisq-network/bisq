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

package bisq.cli.table.builder;

import bisq.proto.grpc.OfferInfo;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import static bisq.cli.table.builder.TableBuilderConstants.*;
import static bisq.cli.table.builder.TableType.OFFER_TBL;
import static bisq.cli.table.column.AltcoinColumn.DISPLAY_MODE.ALTCOIN_OFFER_VOLUME;
import static bisq.cli.table.column.AltcoinColumn.DISPLAY_MODE.ALTCOIN_TRIGGER_PRICE;
import static bisq.cli.table.column.Column.JUSTIFICATION.LEFT;
import static bisq.cli.table.column.Column.JUSTIFICATION.NONE;
import static bisq.cli.table.column.Column.JUSTIFICATION.RIGHT;
import static bisq.cli.table.column.FiatColumn.DISPLAY_MODE.TRIGGER_PRICE;
import static bisq.cli.table.column.FiatColumn.DISPLAY_MODE.VOLUME;
import static bisq.cli.table.column.ZippedStringColumns.DUPLICATION_MODE.EXCLUDE_DUPLICATES;
import static java.lang.String.format;
import static protobuf.OfferDirection.BUY;
import static protobuf.OfferDirection.SELL;



import bisq.cli.table.Table;
import bisq.cli.table.column.AltcoinColumn;
import bisq.cli.table.column.Column;
import bisq.cli.table.column.FiatColumn;
import bisq.cli.table.column.Iso8601DateTimeColumn;
import bisq.cli.table.column.SatoshiColumn;
import bisq.cli.table.column.StringColumn;
import bisq.cli.table.column.ZippedStringColumns;

/**
 * Builds a {@code bisq.cli.table.Table} from a List of
 * {@code bisq.proto.grpc.OfferInfo} objects.
 */
class OfferTableBuilder extends AbstractTableBuilder {

    // Columns common to both fiat and cryptocurrency offers.
    private final Column<String> colOfferId = new StringColumn(COL_HEADER_UUID, LEFT);
    private final Column<String> colDirection = new StringColumn(COL_HEADER_DIRECTION, LEFT);
    private final Column<Long> colAmount = new SatoshiColumn("Temp Amount", NONE);
    private final Column<Long> colMinAmount = new SatoshiColumn("Temp Min Amount", NONE);
    private final Column<String> colPaymentMethod = new StringColumn(COL_HEADER_PAYMENT_METHOD, LEFT);
    private final Column<Long> colCreateDate = new Iso8601DateTimeColumn(COL_HEADER_CREATION_DATE);

    OfferTableBuilder(List<?> protos) {
        super(OFFER_TBL, protos);
    }

    public Table build() {
        List<OfferInfo> offers = protos.stream().map(p -> (OfferInfo) p).collect(Collectors.toList());
        return isShowingFiatOffers.get()
                ? buildFiatOfferTable(offers)
                : buildCryptoCurrencyOfferTable(offers);
    }

    @SuppressWarnings("ConstantConditions")
    public Table buildFiatOfferTable(List<OfferInfo> offers) {
        @Nullable
        Column<String> colEnabled = enabledColumn.get(); // Not boolean: YES, NO, or PENDING
        Column<Long> colFiatPrice = new FiatColumn(format(COL_HEADER_DETAILED_PRICE, fiatTradeCurrency.get()));
        Column<Long> colFiatVolume = new FiatColumn(format("Temp Volume (%s)", fiatTradeCurrency.get()), NONE, VOLUME);
        Column<Long> colMinFiatVolume = new FiatColumn(format("Temp Min Volume (%s)", fiatTradeCurrency.get()), NONE, VOLUME);
        @Nullable
        Column<Long> colTriggerPrice = fiatTriggerPriceColumn.get();

        // Populate columns with offer info.

        //noinspection SimplifyStreamApiCallChains
        offers.stream().forEachOrdered(o -> {
            if (colEnabled != null)
                colEnabled.addRow(toEnabled.apply(o));

            colDirection.addRow(o.getDirection());
            colFiatPrice.addRow(o.getPrice());
            colMinAmount.addRow(o.getMinAmount());
            colAmount.addRow(o.getAmount());
            colMinFiatVolume.addRow(o.getMinVolume());
            colFiatVolume.addRow(o.getVolume());

            if (colTriggerPrice != null)
                colTriggerPrice.addRow(o.getTriggerPrice());

            colPaymentMethod.addRow(o.getPaymentMethodShortName());
            colCreateDate.addRow(o.getDate());
            colOfferId.addRow(o.getId());
        });

        ZippedStringColumns amountRange = zippedAmountRangeColumns.get();
        ZippedStringColumns volumeRange =
                new ZippedStringColumns(format(COL_HEADER_VOLUME_RANGE, fiatTradeCurrency.get()),
                        RIGHT,
                        " - ",
                        colMinFiatVolume.asStringColumn(),
                        colFiatVolume.asStringColumn());

        // Define and return the table instance with populated columns.

        if (isShowingMyOffers.get()) {
            return new Table(colEnabled.asStringColumn(),
                    colDirection,
                    colFiatPrice.asStringColumn(),
                    amountRange.asStringColumn(EXCLUDE_DUPLICATES),
                    volumeRange.asStringColumn(EXCLUDE_DUPLICATES),
                    colTriggerPrice.asStringColumn(),
                    colPaymentMethod,
                    colCreateDate.asStringColumn(),
                    colOfferId);
        } else {
            return new Table(colDirection,
                    colFiatPrice.asStringColumn(),
                    amountRange.asStringColumn(EXCLUDE_DUPLICATES),
                    volumeRange.asStringColumn(EXCLUDE_DUPLICATES),
                    colPaymentMethod,
                    colCreateDate.asStringColumn(),
                    colOfferId);
        }
    }

    @SuppressWarnings("ConstantConditions")
    public Table buildCryptoCurrencyOfferTable(List<OfferInfo> offers) {
        @Nullable
        Column<String> colEnabled = enabledColumn.get(); // Not boolean: YES, NO, or PENDING
        Column<Long> colBtcPrice = new SatoshiColumn(format(COL_HEADER_DETAILED_PRICE_OF_ALTCOIN, altcoinTradeCurrency.get()));
        Column<Long> colBtcVolume = new AltcoinColumn(format("Temp Volume (%s)", altcoinTradeCurrency.get()),
                NONE,
                ALTCOIN_OFFER_VOLUME);
        Column<Long> colMinBtcVolume = new AltcoinColumn(format("Temp Min Volume (%s)", altcoinTradeCurrency.get()),
                NONE,
                ALTCOIN_OFFER_VOLUME);
        @Nullable
        Column<Long> colTriggerPrice = altcoinTriggerPriceColumn.get();

        // Populate columns with offer info.

        //noinspection SimplifyStreamApiCallChains
        offers.stream().forEachOrdered(o -> {
            if (colEnabled != null)
                colEnabled.addRow(toEnabled.apply(o));

            colDirection.addRow(directionFormat.apply(o));
            colBtcPrice.addRow(o.getPrice());
            colAmount.addRow(o.getAmount());
            colMinAmount.addRow(o.getMinAmount());
            colBtcVolume.addRow(o.getMinVolume());
            colMinBtcVolume.addRow(o.getVolume());

            if (colTriggerPrice != null)
                colTriggerPrice.addRow(o.getTriggerPrice());

            colPaymentMethod.addRow(o.getPaymentMethodShortName());
            colCreateDate.addRow(o.getDate());
            colOfferId.addRow(o.getId());
        });

        ZippedStringColumns amountRange = zippedAmountRangeColumns.get();
        ZippedStringColumns volumeRange =
                new ZippedStringColumns(format(COL_HEADER_VOLUME_RANGE, altcoinTradeCurrency.get()),
                        RIGHT,
                        " - ",
                        colBtcVolume.asStringColumn(),
                        colMinBtcVolume.asStringColumn());

        // Define and return the table instance with populated columns.

        if (isShowingMyOffers.get()) {
            if (isShowingBsqOffers.get()) {
                return new Table(colEnabled.asStringColumn(),
                        colDirection,
                        colBtcPrice.asStringColumn(),
                        amountRange.asStringColumn(EXCLUDE_DUPLICATES),
                        volumeRange.asStringColumn(EXCLUDE_DUPLICATES),
                        colPaymentMethod,
                        colCreateDate.asStringColumn(),
                        colOfferId);
            } else {
                return new Table(colEnabled.asStringColumn(),
                        colDirection,
                        colBtcPrice.asStringColumn(),
                        amountRange.asStringColumn(EXCLUDE_DUPLICATES),
                        volumeRange.asStringColumn(EXCLUDE_DUPLICATES),
                        colTriggerPrice.asStringColumn(),
                        colPaymentMethod,
                        colCreateDate.asStringColumn(),
                        colOfferId);
            }
        } else {
            return new Table(colDirection,
                    colBtcPrice.asStringColumn(),
                    amountRange.asStringColumn(EXCLUDE_DUPLICATES),
                    volumeRange.asStringColumn(EXCLUDE_DUPLICATES),
                    colPaymentMethod,
                    colCreateDate.asStringColumn(),
                    colOfferId);
        }
    }

    private final Supplier<OfferInfo> firstOfferInList = () -> (OfferInfo) protos.get(0);
    private final Supplier<Boolean> isShowingMyOffers = () -> firstOfferInList.get().getIsMyOffer();
    private final Supplier<Boolean> isShowingFiatOffers = () -> isFiatOffer.test(firstOfferInList.get());
    private final Supplier<String> fiatTradeCurrency = () -> firstOfferInList.get().getCounterCurrencyCode();
    private final Supplier<String> altcoinTradeCurrency = () -> firstOfferInList.get().getBaseCurrencyCode();
    private final Supplier<Boolean> isShowingBsqOffers = () ->
            !isFiatOffer.test(firstOfferInList.get()) && altcoinTradeCurrency.get().equals("BSQ");

    @Nullable  // Not a boolean column: YES, NO, or PENDING.
    private final Supplier<StringColumn> enabledColumn = () ->
            isShowingMyOffers.get()
                    ? new StringColumn(COL_HEADER_ENABLED, LEFT)
                    : null;
    @Nullable
    private final Supplier<FiatColumn> fiatTriggerPriceColumn = () ->
            isShowingMyOffers.get()
                    ? new FiatColumn(format(COL_HEADER_TRIGGER_PRICE, fiatTradeCurrency.get()), RIGHT, TRIGGER_PRICE)
                    : null;
    @Nullable
    private final Supplier<AltcoinColumn> altcoinTriggerPriceColumn = () ->
            isShowingMyOffers.get() && !isShowingBsqOffers.get()
                    ? new AltcoinColumn(format(COL_HEADER_TRIGGER_PRICE, altcoinTradeCurrency.get()), RIGHT, ALTCOIN_TRIGGER_PRICE)
                    : null;

    private final Function<OfferInfo, String> toEnabled = (o) -> {
        if (o.getIsMyOffer() && o.getIsMyPendingOffer())
            return "PENDING";
        else
            return o.getIsActivated() ? "YES" : "NO";
    };

    private final Function<String, String> toMirroredDirection = (d) ->
            d.equalsIgnoreCase(BUY.name()) ? SELL.name() : BUY.name();

    private final Function<OfferInfo, String> directionFormat = (o) -> {
        if (isFiatOffer.test(o)) {
            return o.getBaseCurrencyCode();
        } else {
            // Return "Sell BSQ (Buy BTC)", or "Buy BSQ (Sell BTC)".
            String direction = o.getDirection();
            String mirroredDirection = toMirroredDirection.apply(direction);
            Function<String, String> mixedCase = (word) -> word.charAt(0) + word.substring(1).toLowerCase();
            return format("%s %s (%s %s)",
                    mixedCase.apply(mirroredDirection),
                    o.getBaseCurrencyCode(),
                    mixedCase.apply(direction),
                    o.getCounterCurrencyCode());
        }
    };

    private final Supplier<ZippedStringColumns> zippedAmountRangeColumns = () -> {
        if (colMinAmount.isEmpty() || colAmount.isEmpty())
            throw new IllegalStateException("amount columns must have data");

        return new ZippedStringColumns(COL_HEADER_AMOUNT_RANGE,
                RIGHT,
                " - ",
                colMinAmount.asStringColumn(),
                colAmount.asStringColumn());
    };
}
