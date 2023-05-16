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

package bisq.core.dao.burningman.accounting;

import bisq.core.dao.DaoSetupService;
import bisq.core.dao.burningman.BurningManPresentationService;
import bisq.core.dao.burningman.accounting.balance.BalanceEntry;
import bisq.core.dao.burningman.accounting.balance.BalanceModel;
import bisq.core.dao.burningman.accounting.balance.BaseBalanceEntry;
import bisq.core.dao.burningman.accounting.balance.ReceivedBtcBalanceEntry;
import bisq.core.dao.burningman.accounting.blockchain.AccountingBlock;
import bisq.core.dao.burningman.accounting.blockchain.AccountingTx;
import bisq.core.dao.burningman.accounting.exceptions.BlockHashNotConnectingException;
import bisq.core.dao.burningman.accounting.exceptions.BlockHeightNotConnectingException;
import bisq.core.dao.burningman.accounting.storage.BurningManAccountingStoreService;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.monetary.Price;
import bisq.core.trade.statistics.TradeStatistics3;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.core.util.AveragePriceUtil;

import bisq.common.UserThread;
import bisq.common.config.Config;
import bisq.common.util.DateUtil;
import bisq.common.util.MathUtils;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Singleton;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import javafx.collections.SetChangeListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides APIs for the accounting related aspects of burningmen.
 * Combines the received funds from BTC trade fees and DPT payouts and the burned BSQ.
 */
@Slf4j
@Singleton
public class BurningManAccountingService implements DaoSetupService, DaoStateListener {
    // now 763195 -> 107159 blocks takes about 14h
    // First tx at BM address 656036 Sun Nov 08 19:02:18 EST 2020
    // 2 months ago 754555
    public static final int EARLIEST_BLOCK_HEIGHT = Config.baseCurrencyNetwork().isRegtest() ? 111 : 656035;
    public static final int EARLIEST_DATE_YEAR = 2020;
    public static final int EARLIEST_DATE_MONTH = 10;
    public static final int HIST_BSQ_PRICE_LAST_DATE_YEAR = 2022;
    public static final int HIST_BSQ_PRICE_LAST_DATE_MONTH = 10;

    private final BurningManAccountingStoreService burningManAccountingStoreService;
    private final BurningManPresentationService burningManPresentationService;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final Preferences preferences;

    private final Map<Date, Price> averageBsqPriceByMonth = new HashMap<>(getHistoricalAverageBsqPriceByMonth());
    private boolean averagePricesValid;
    @Getter
    private final Map<String, BalanceModel> balanceModelByBurningManName = new HashMap<>();
    @Getter
    private final BooleanProperty isProcessing = new SimpleBooleanProperty();

    // cache
    private final List<ReceivedBtcBalanceEntry> receivedBtcBalanceEntryListExcludingLegacyBM = new ArrayList<>();

    @Inject
    public BurningManAccountingService(DaoStateService daoStateService,
                                       BurningManAccountingStoreService burningManAccountingStoreService,
                                       BurningManPresentationService burningManPresentationService,
                                       TradeStatisticsManager tradeStatisticsManager,
                                       Preferences preferences) {
        this.burningManAccountingStoreService = burningManAccountingStoreService;
        this.burningManPresentationService = burningManPresentationService;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.preferences = preferences;

        daoStateService.addDaoStateListener(this);
        daoStateService.getLastBlock().ifPresent(this::applyBlock);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
        tradeStatisticsManager.getObservableTradeStatisticsSet().addListener(
                (SetChangeListener<TradeStatistics3>) observable -> averagePricesValid = false);
    }

    @Override
    public void start() {
        UserThread.execute(() -> isProcessing.set(true));

        updateBalanceModelByAddress();
        CompletableFuture.runAsync(() -> {
            Map<String, BalanceModel> map = new HashMap<>();
            // addAccountingBlockToBalanceModel takes about 500ms for 100k items, so we run it in a non UI thread.
            burningManAccountingStoreService.forEachBlock(block -> addAccountingBlockToBalanceModel(map, block));
            UserThread.execute(() -> balanceModelByBurningManName.putAll(map));
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        applyBlock(block);
    }

    private void applyBlock(Block block) {
        receivedBtcBalanceEntryListExcludingLegacyBM.clear();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onInitialBlockRequestsComplete() {
        updateBalanceModelByAddress();
        burningManAccountingStoreService.forEachBlock(this::addAccountingBlockToBalanceModel);
        UserThread.execute(() -> isProcessing.set(false));
    }

    public void onNewBlockReceived(AccountingBlock accountingBlock) {
        updateBalanceModelByAddress();
        addAccountingBlockToBalanceModel(accountingBlock);
    }

    public void addBlock(AccountingBlock block) throws BlockHashNotConnectingException, BlockHeightNotConnectingException {
        burningManAccountingStoreService.addIfNewBlock(block);
    }

    public int getBlockHeightOfLastBlock() {
        return getLastBlock().map(AccountingBlock::getHeight).orElse(BurningManAccountingService.EARLIEST_BLOCK_HEIGHT - 1);
    }

    public Optional<AccountingBlock> getLastBlock() {
        return burningManAccountingStoreService.getLastBlock();
    }

    public Optional<AccountingBlock> getBlockAtHeight(int height) {
        return burningManAccountingStoreService.getBlockAtHeight(height);
    }

    public Map<Date, Price> getAverageBsqPriceByMonth() {
        if (!averagePricesValid) {
            // Fill the map from now back to the last entry of the historical data (April 2019-Nov. 2022).
            averageBsqPriceByMonth.putAll(getAverageBsqPriceByMonth(new Date(), HIST_BSQ_PRICE_LAST_DATE_YEAR, HIST_BSQ_PRICE_LAST_DATE_MONTH));
            averagePricesValid = true;
        }
        return averageBsqPriceByMonth;
    }

    public long getTotalAmountOfDistributedBtc() {
        return getReceivedBtcBalanceEntryListExcludingLegacyBM().stream()
                .mapToLong(BaseBalanceEntry::getAmount)
                .sum();
    }

    public long getTotalAmountOfDistributedBtcFees() {
        return getReceivedBtcBalanceEntryListExcludingLegacyBM().stream()
                .filter(e -> e.getType() == BalanceEntry.Type.BTC_TRADE_FEE_TX)
                .mapToLong(BaseBalanceEntry::getAmount)
                .sum();
    }

    public long getTotalAmountOfDistributedBtcFeesAsBsq() {
        Map<Date, Price> averageBsqPriceByMonth = getAverageBsqPriceByMonth();
        return getReceivedBtcBalanceEntryListExcludingLegacyBM().stream()
                .filter(e -> e.getType() == BalanceEntry.Type.BTC_TRADE_FEE_TX)
                .flatMapToLong(balanceEntry -> receivedBtcAsBsq(balanceEntry, averageBsqPriceByMonth).stream())
                .sum();
    }

    public long getTotalAmountOfDistributedDPT() {
        return getReceivedBtcBalanceEntryListExcludingLegacyBM().stream()
                .filter(e -> e.getType() == BalanceEntry.Type.DPT_TX)
                .mapToLong(BaseBalanceEntry::getAmount)
                .sum();
    }

    public long getTotalAmountOfDistributedDPTAsBsq() {
        Map<Date, Price> averageBsqPriceByMonth = getAverageBsqPriceByMonth();
        return getReceivedBtcBalanceEntryListExcludingLegacyBM().stream()
                .filter(e -> e.getType() == BalanceEntry.Type.DPT_TX)
                .flatMapToLong(balanceEntry -> receivedBtcAsBsq(balanceEntry, averageBsqPriceByMonth).stream())
                .sum();
    }

    public long getTotalAmountOfDistributedBsq() {
        Map<Date, Price> averageBsqPriceByMonth = getAverageBsqPriceByMonth();
        return getReceivedBtcBalanceEntryListExcludingLegacyBM().stream()
                .flatMapToLong(balanceEntry -> receivedBtcAsBsq(balanceEntry, averageBsqPriceByMonth).stream())
                .sum();
    }

    private static OptionalLong receivedBtcAsBsq(ReceivedBtcBalanceEntry balanceEntry,
                                                 Map<Date, Price> averageBsqPriceByMonth) {
        Date month = balanceEntry.getMonth();
        long receivedBtc = balanceEntry.getAmount();
        Price price = averageBsqPriceByMonth.get(month);
        if (price == null) {
            return OptionalLong.empty();
        }
        long volume = price.getVolumeByAmount(Coin.valueOf(receivedBtc)).getValue();
        return OptionalLong.of(MathUtils.roundDoubleToLong(MathUtils.scaleDownByPowerOf10(volume, 6)));
    }

    private List<ReceivedBtcBalanceEntry> getReceivedBtcBalanceEntryListExcludingLegacyBM() {
        if (!receivedBtcBalanceEntryListExcludingLegacyBM.isEmpty()) {
            return receivedBtcBalanceEntryListExcludingLegacyBM;
        }

        receivedBtcBalanceEntryListExcludingLegacyBM.addAll(balanceModelByBurningManName.entrySet().stream()
                .filter(e -> !e.getKey().equals(BurningManPresentationService.LEGACY_BURNING_MAN_DPT_NAME) &&
                        !e.getKey().equals(BurningManPresentationService.LEGACY_BURNING_MAN_BTC_FEES_NAME))
                .map(Map.Entry::getValue)
                .flatMap(balanceModel -> balanceModel.getReceivedBtcBalanceEntries().stream())
                .collect(Collectors.toList()));
        return receivedBtcBalanceEntryListExcludingLegacyBM;
    }

    public Stream<ReceivedBtcBalanceEntry> getDistributedBtcBalanceByMonth(Date month) {
        return balanceModelByBurningManName.entrySet().stream()
                .filter(e -> !e.getKey().equals(BurningManPresentationService.LEGACY_BURNING_MAN_DPT_NAME) &&
                        !e.getKey().equals(BurningManPresentationService.LEGACY_BURNING_MAN_BTC_FEES_NAME))
                .map(Map.Entry::getValue)
                .flatMap(balanceModel -> balanceModel.getReceivedBtcBalanceEntriesByMonth(month).stream());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Delegates
    ///////////////////////////////////////////////////////////////////////////////////////////

    public List<AccountingBlock> getBlocksAtLeastWithHeight(int minHeight) {
        return burningManAccountingStoreService.getBlocksAtLeastWithHeight(minHeight);
    }

    public Map<String, String> getBurningManNameByAddress() {
        return burningManPresentationService.getBurningManNameByAddress();
    }

    public String getGenesisTxId() {
        return burningManPresentationService.getGenesisTxId();
    }

    public void purgeLastTenBlocks() {
        burningManAccountingStoreService.purgeLastTenBlocks();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateBalanceModelByAddress() {
        burningManPresentationService.getBurningManCandidatesByName().keySet()
                .forEach(key -> balanceModelByBurningManName.putIfAbsent(key, new BalanceModel()));
    }

    private void addAccountingBlockToBalanceModel(AccountingBlock accountingBlock) {
        addAccountingBlockToBalanceModel(balanceModelByBurningManName, accountingBlock);
    }

    private void addAccountingBlockToBalanceModel(Map<String, BalanceModel> balanceModelByBurningManName,
                                                  AccountingBlock accountingBlock) {
        accountingBlock.getTxs().forEach(tx ->
                tx.getOutputs().forEach(txOutput -> {
                    String name = txOutput.getName();
                    balanceModelByBurningManName.putIfAbsent(name, new BalanceModel());
                    balanceModelByBurningManName.get(name).addReceivedBtcBalanceEntry(new ReceivedBtcBalanceEntry(tx.getTruncatedTxId(),
                            txOutput.getValue(),
                            new Date(accountingBlock.getDate()),
                            toBalanceEntryType(tx.getType())));
                }));
    }

    @SuppressWarnings("SameParameterValue")
    private Map<Date, Price> getAverageBsqPriceByMonth(Date from, int backToYear, int backToMonth) {
        Map<Date, Price> averageBsqPriceByMonth = new HashMap<>();
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(from);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        do {
            for (; month >= 0; month--) {
                if (year == backToYear && month == backToMonth) {
                    break;
                }
                Date date = DateUtil.getStartOfMonth(year, month);
                Price averageBsqPrice = AveragePriceUtil.getAveragePriceTuple(preferences, tradeStatisticsManager, 30, date).second;
                averageBsqPriceByMonth.put(date, averageBsqPrice);
            }
            year--;
            month = 11;
        } while (year >= backToYear);
        return averageBsqPriceByMonth;
    }

    private static BalanceEntry.Type toBalanceEntryType(AccountingTx.Type type) {
        return type == AccountingTx.Type.BTC_TRADE_FEE_TX ?
                BalanceEntry.Type.BTC_TRADE_FEE_TX :
                BalanceEntry.Type.DPT_TX;
    }

    @SuppressWarnings("CommentedOutCode")
    private static Map<Date, Price> getHistoricalAverageBsqPriceByMonth() {
        // We use the average 30 day BSQ price from the first day of a month back 30 days. So for 1.Nov 2022 we take the average during October 2022.
        // Filling the map takes a bit of computation time (about 5 sec), so we use for historical data a pre-calculated list.
        // Average price from 1. May 2019 (April average) - 1. Nov 2022 (Oct average)
        String historical = "1648789200000=2735, 1630472400000=3376, 1612155600000=6235, 1559365200000=13139, 1659330000000=3609, 1633064400000=3196, 1583038800000=7578, 1622523600000=3918, 1625115600000=3791, 1667278800000=3794, 1561957200000=10882, 1593579600000=6153, 1577854800000=9034, 1596258000000=6514, 1604206800000=5642, 1643691600000=3021, 1606798800000=4946, 1569906000000=10445, 1567314000000=9885, 1614574800000=5052, 1656651600000=3311, 1638334800000=3015, 1564635600000=8788, 1635742800000=3065, 1654059600000=3207, 1646110800000=2824, 1609477200000=4199, 1664600400000=3820, 1662008400000=3756, 1556686800000=24094, 1588309200000=7986, 1585717200000=7994, 1627794000000=3465, 1580533200000=5094, 1590987600000=7411, 1619845200000=3956, 1617253200000=4024, 1575176400000=9571, 1572584400000=9058, 1641013200000=3052, 1601528400000=5648, 1651381200000=2908, 1598936400000=6032";

        // Create historical data as string
        /* log.info("averageBsqPriceByMonth=" + getAverageBsqPriceByMonth(new Date(), 2019, 3).entrySet().stream()
                .map(e -> e.getKey().getTime() + "=" + e.getValue().getValue())
                .collect(Collectors.toList()));
        */
        return Arrays.stream(historical.split(", "))
                .map(chunk -> chunk.split("="))
                .collect(Collectors.toMap(tuple -> new Date(Long.parseLong(tuple[0])),
                        tuple -> Price.valueOf("BSQ", Long.parseLong(tuple[1]))));
    }
}
