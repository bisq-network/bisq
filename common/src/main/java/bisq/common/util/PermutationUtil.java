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

package bisq.common.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PermutationUtil {

    /**
     * @param list                  Original list
     * @param indicesToRemove       List of indices to remove
     * @param <T>                   Type of List items
     * @return Partial list where items at indices of indicesToRemove have been removed
     */
    public static <T> List<T> getPartialList(List<T> list, List<Integer> indicesToRemove) {
        List<T> altered = new ArrayList<>(list);

        // Eliminate duplicates
        indicesToRemove = new ArrayList<>(new HashSet<>(indicesToRemove));

        // Sort
        Collections.sort(indicesToRemove);

        // Reverse list.
        // We need to remove from highest index downwards to not change order of remaining indices
        Collections.reverse(indicesToRemove);

        indicesToRemove.forEach(index -> {
            if (altered.size() > index && index >= 0)
                altered.remove((int) index);
        });
        return altered;
    }

    public static <T, R> List<T> findMatchingPermutation(R targetValue,
                                                         List<T> list,
                                                         BiPredicate<R, List<T>> predicate,
                                                         int maxIterations) {
        if (predicate.test(targetValue, list)) {
            return list;
        } else {
            return findMatchingPermutation(targetValue,
                    list,
                    new ArrayList<>(),
                    predicate,
                    new AtomicInteger(maxIterations));
        }
    }

    private static <T, R> List<T> findMatchingPermutation(R targetValue,
                                                          List<T> list,
                                                          List<List<T>> lists,
                                                          BiPredicate<R, List<T>> predicate,
                                                          AtomicInteger maxIterations) {
        for (int level = 0; level < list.size(); level++) {
            // Test one level at a time
            var result = checkLevel(targetValue, list, predicate, level, 0, maxIterations);
            if (!result.isEmpty()) {
                return result;
            }
        }

        return new ArrayList<>();
    }

    @NonNull
    private static <T, R> List<T> checkLevel(R targetValue,
                                             List<T> previousLevel,
                                             BiPredicate<R, List<T>> predicate,
                                             int level,
                                             int permutationIndex,
                                             AtomicInteger maxIterations) {
        if (previousLevel.size() == 1) {
            return new ArrayList<>();
        }
        for (int i = permutationIndex; i < previousLevel.size(); i++) {
            if (maxIterations.get() <= 0) {
                return new ArrayList<>();
            }
            List<T> newList = new ArrayList<>(previousLevel);
            newList.remove(i);
            if (level == 0) {
                maxIterations.decrementAndGet();
                // Check all permutations on this level
                if (predicate.test(targetValue, newList)) {
                    return newList;
                }
            } else {
                // Test next level
                var result = checkLevel(targetValue, newList, predicate, level - 1, i, maxIterations);
                if (!result.isEmpty()) {
                    return result;
                }
            }
        }
        return new ArrayList<>();
    }

    //TODO optimize algorithm so that it starts from all objects and goes down instead starting with from the bottom.
    // That should help that we are not hitting the iteration limit so easily.

    /**
     * Returns a list of all possible permutations of a give sorted list ignoring duplicates.
     * E.g. List [A,B,C] results in this list of permutations: [[A], [B], [A,B], [C], [A,C], [B,C], [A,B,C]]
     * Number of variations and iterations grows with 2^n - 1 where n is the number of items in the list.
     * With 20 items we reach about 1 million iterations and it takes about 0.5 sec.
     * To avoid performance issues we added the maxIterations parameter to stop once the number of iterations has
     * reached the maxIterations and return in such a case the list of permutations we have been able to create.
     * Depending on the type of object which is stored in the list the memory usage should be considered as well for
     * choosing the right maxIterations value.
     *
     * @param list              List from which we create permutations
     * @param maxIterations     Max. number of iterations including inner iterations
     * @param <T>               Type of list items
     * @return List of possible permutations of the original list
     */
    public static <T> List<List<T>> findAllPermutations(List<T> list, int maxIterations) {
        List<List<T>> result = new ArrayList<>();
        int counter = 0;
        long ts = System.currentTimeMillis();
        for (T item : list) {
            counter++;
            if (counter > maxIterations) {
                log.warn("We reached maxIterations of our allowed iterations and return current state of the result. " +
                        "counter={}", counter);
                return result;
            }

            List<List<T>> subLists = new ArrayList<>();
            for (int n = 0; n < result.size(); n++) {
                counter++;
                if (counter > maxIterations) {
                    log.warn("We reached maxIterations of our allowed iterations and return current state of the result. " +
                            "counter={}", counter);
                    return result;
                }
                List<T> subList = new ArrayList<>(result.get(n));
                subList.add(item);
                subLists.add(subList);
            }

            // add single item
            result.add(new ArrayList<>(Collections.singletonList(item)));

            // add subLists
            result.addAll(subLists);
        }

        log.info("findAllPermutations took {} ms for {} items and {} iterations. Heap size used: {} MB",
                (System.currentTimeMillis() - ts), list.size(), counter, Profiler.getUsedMemoryInMB());
        return result;
    }
}
