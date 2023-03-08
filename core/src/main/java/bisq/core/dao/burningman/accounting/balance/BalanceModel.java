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

package bisq.core.dao.burningman.accounting.balance;

import bisq.core.dao.burningman.model.BurnOutputModel;
import bisq.core.dao.burningman.model.BurningManCandidate;

import bisq.common.util.DateUtil;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import static bisq.core.dao.burningman.accounting.BurningManAccountingService.EARLIEST_DATE_MONTH;
import static bisq.core.dao.burningman.accounting.BurningManAccountingService.EARLIEST_DATE_YEAR;

@Slf4j
@EqualsAndHashCode
public class BalanceModel {
    private final Set<ReceivedBtcBalanceEntry> receivedBtcBalanceEntries = new HashSet<>();
    private final Map<Date, Set<ReceivedBtcBalanceEntry>> receivedBtcBalanceEntriesByMonth = new HashMap<>();

    public BalanceModel() {
    }

    public void addReceivedBtcBalanceEntry(ReceivedBtcBalanceEntry balanceEntry) {
        receivedBtcBalanceEntries.add(balanceEntry);

        Date month = balanceEntry.getMonth();
        receivedBtcBalanceEntriesByMonth.putIfAbsent(month, new HashSet<>());
        receivedBtcBalanceEntriesByMonth.get(month).add(balanceEntry);
    }

    public Set<ReceivedBtcBalanceEntry> getReceivedBtcBalanceEntries() {
        return receivedBtcBalanceEntries;
    }

    public Set<ReceivedBtcBalanceEntry> getReceivedBtcBalanceEntriesByMonth(Date month) {
        return receivedBtcBalanceEntriesByMonth.entrySet().stream()
                        .filter(e -> e.getKey().equals(month))
                        .map(Map.Entry::getValue)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toSet());
    }

    public Stream<BurnedBsqBalanceEntry> getBurnedBsqBalanceEntries(Set<BurnOutputModel> burnOutputModels) {
        return burnOutputModels.stream()
                .map(burnOutputModel -> new BurnedBsqBalanceEntry(burnOutputModel.getTxId(),
                        burnOutputModel.getAmount(),
                        new Date(burnOutputModel.getDate())));
    }

    public List<MonthlyBalanceEntry> getMonthlyBalanceEntries(BurningManCandidate burningManCandidate,
                                                              Predicate<BaseBalanceEntry> predicate) {
        Map<Date, Set<BurnOutputModel>> burnOutputModelsByMonth = burningManCandidate.getBurnOutputModelsByMonth();
        Set<Date> months = getMonths(new Date(), EARLIEST_DATE_YEAR, EARLIEST_DATE_MONTH);
        return months.stream()
                .map(date -> {
                    long sumBurnedBsq = 0;
                    Set<BalanceEntry.Type> types = new HashSet<>();
                    if (burnOutputModelsByMonth.containsKey(date)) {
                        Set<BurnOutputModel> burnOutputModels = burnOutputModelsByMonth.get(date);
                        Set<MonthlyBurnedBsqBalanceEntry> monthlyBurnedBsqBalanceEntries = burnOutputModels.stream()
                                .map(burnOutputModel -> new MonthlyBurnedBsqBalanceEntry(burnOutputModel.getTxId(),
                                        burnOutputModel.getAmount(),
                                        date))
                                .collect(Collectors.toSet());
                        sumBurnedBsq = monthlyBurnedBsqBalanceEntries.stream()
                                .filter(predicate)
                                .peek(e -> types.add(e.getType()))
                                .mapToLong(MonthlyBurnedBsqBalanceEntry::getAmount)
                                .sum();
                    }
                    long sumReceivedBtc = 0;
                    if (receivedBtcBalanceEntriesByMonth.containsKey(date)) {
                        sumReceivedBtc = receivedBtcBalanceEntriesByMonth.get(date).stream()
                                .filter(predicate)
                                .peek(e -> types.add(e.getType()))
                                .mapToLong(BaseBalanceEntry::getAmount)
                                .sum();
                    }
                    return new MonthlyBalanceEntry(sumReceivedBtc, sumBurnedBsq, date, types);
                })
                .filter(balanceEntry -> balanceEntry.getBurnedBsq() > 0 || balanceEntry.getReceivedBtc() > 0)
                .collect(Collectors.toList());
    }

    private Set<Date> getMonths(Date from, int toYear, int toMonth) {
        Set<Date> map = new HashSet<>();
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(from);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        do {
            for (; month >= 0; month--) {
                if (year == toYear && month == toMonth) {
                    break;
                }
                map.add(DateUtil.getStartOfMonth(year, month));
            }
            year--;
            month = 11;
        } while (year >= toYear);
        return map;
    }
}
