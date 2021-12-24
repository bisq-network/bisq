/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.common.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class PermutationTest {


    // @Test
    public void testGetPartialList() {
        String blindVote0 = "blindVote0";
        String blindVote1 = "blindVote1";
        String blindVote2 = "blindVote2";
        String blindVote3 = "blindVote3";
        String blindVote4 = "blindVote4";
        String blindVote5 = "blindVote5";

        List<String> list = new ArrayList<>(Arrays.asList(blindVote0, blindVote1, blindVote2, blindVote3, blindVote4, blindVote5));
        List<Integer> indicesToRemove = Arrays.asList(0, 3);
        List<String> expected = new ArrayList<>(Arrays.asList(blindVote1, blindVote2, blindVote4, blindVote5));
        List<String> result = PermutationUtil.getPartialList(list, indicesToRemove);
        assertTrue(expected.toString().equals(result.toString()));

        // remove nothing
        indicesToRemove = new ArrayList<>();
        expected = new ArrayList<>(list);
        result = PermutationUtil.getPartialList(list, indicesToRemove);
        assertTrue(expected.toString().equals(result.toString()));

        // remove first
        indicesToRemove = Collections.singletonList(0);
        expected = new ArrayList<>(list);
        expected.remove(0);
        result = PermutationUtil.getPartialList(list, indicesToRemove);
        assertTrue(expected.toString().equals(result.toString()));

        // remove last
        indicesToRemove = Collections.singletonList(5);
        expected = new ArrayList<>(list);
        expected.remove(5);
        result = PermutationUtil.getPartialList(list, indicesToRemove);
        assertTrue(expected.toString().equals(result.toString()));

        // remove all
        indicesToRemove = Arrays.asList(0, 1, 2, 3, 4, 5);
        expected = new ArrayList<>();
        result = PermutationUtil.getPartialList(list, indicesToRemove);
        assertTrue(expected.toString().equals(result.toString()));

        // wrong sorting of indices
        indicesToRemove = Arrays.asList(4, 0, 1);
        expected = expected = new ArrayList<>(Arrays.asList(blindVote2, blindVote3, blindVote5));
        result = PermutationUtil.getPartialList(list, indicesToRemove);
        assertTrue(expected.toString().equals(result.toString()));

        // wrong sorting of indices
        indicesToRemove = Arrays.asList(0, 0);
        expected = new ArrayList<>(Arrays.asList(blindVote1, blindVote2, blindVote3, blindVote4, blindVote5));
        result = PermutationUtil.getPartialList(list, indicesToRemove);
        assertTrue(expected.toString().equals(result.toString()));

        // don't remove as invalid index
        indicesToRemove = Collections.singletonList(9);
        expected = new ArrayList<>(list);
        result = PermutationUtil.getPartialList(list, indicesToRemove);
        assertTrue(expected.toString().equals(result.toString()));

        // don't remove as invalid index
        indicesToRemove = Collections.singletonList(-2);
        expected = new ArrayList<>(list);
        result = PermutationUtil.getPartialList(list, indicesToRemove);
        assertTrue(expected.toString().equals(result.toString()));
    }

    @Test
    public void testFindMatchingPermutation() {
        String a = "A";
        String b = "B";
        String c = "C";
        String d = "D";
        String e = "E";
        int limit = 1048575;
        List<String> result;
        List<String> list;
        List<String> expected;
        BiPredicate<String, List<String>> predicate = (target, variationList) -> variationList.toString().equals(target);

        list = Arrays.asList(a, b, c, d, e);

        expected = Arrays.asList(a);
        result = PermutationUtil.findMatchingPermutation(expected.toString(), list, predicate, limit);
        assertTrue(expected.toString().equals(result.toString()));


        expected = Arrays.asList(a, c, e);
        result = PermutationUtil.findMatchingPermutation(expected.toString(), list, predicate, limit);
        assertTrue(expected.toString().equals(result.toString()));
    }

    @Test
    public void testBreakAtLimit() {
        BiPredicate<String, List<String>> predicate =
                (target, variationList) -> variationList.toString().equals(target);
        var list = Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o");
        var expected = Arrays.asList("b", "g", "m");

        // Takes around 32508 tries starting from longer strings
        var limit = 100000;
        var result = PermutationUtil.findMatchingPermutation(expected.toString(), list, predicate, limit);
        assertTrue(expected.toString().equals(result.toString()));
        limit = 1000;
        result = PermutationUtil.findMatchingPermutation(expected.toString(), list, predicate, limit);
        assertTrue(result.isEmpty());
    }


    //  @Test
    public void testFindAllPermutations() {
        String blindVote0 = "blindVote0";
        String blindVote1 = "blindVote1";
        String blindVote2 = "blindVote2";
        String blindVote3 = "blindVote3";
        String blindVote4 = "blindVote4";

        // Up to about 1M iterations performance is acceptable (0.5 sec)
        // findAllPermutations took 580 ms for 20 items and 1048575 iterations
        // findAllPermutations took 10 ms for 15 items and 32767 iterations
        // findAllPermutations took 0 ms for 10 items and 1023 iterations
        // int limit = 1048575;
        int limit = 1048575000;
        List<String> list;
        List<List<String>> expected;
        List<List<String>> result;
        List<String> subList;


        list = new ArrayList<>();
       /* for (int i = 0; i < 4; i++) {
            list.add("blindVote" + i);
        }*/

        PermutationUtil.findAllPermutations(list, limit);


        list = new ArrayList<>();
        expected = new ArrayList<>();
        result = PermutationUtil.findAllPermutations(list, limit);
        assertTrue(expected.toString().equals(result.toString()));

        list = new ArrayList<>(Arrays.asList(blindVote0));
        expected = new ArrayList<>();
        expected.add(list);
        result = PermutationUtil.findAllPermutations(list, limit);
        assertTrue(expected.toString().equals(result.toString()));

        // 2 items -> 3 variations
        list = new ArrayList<>(Arrays.asList(blindVote0, blindVote1));
        expected = new ArrayList<>();
        expected.add(Arrays.asList(list.get(0)));

        expected.add(Arrays.asList(list.get(1)));

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(1));
        expected.add(subList);

        result = PermutationUtil.findAllPermutations(list, limit);
        assertTrue(expected.toString().equals(result.toString()));

        // 3 items -> 7 variations
        list = new ArrayList<>(Arrays.asList(blindVote0, blindVote1, blindVote2));
        expected = new ArrayList<>();
        expected.add(Arrays.asList(list.get(0)));

        expected.add(Arrays.asList(list.get(1)));

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(1));
        expected.add(subList);

        expected.add(Arrays.asList(list.get(2)));

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(2));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(1));
        subList.add(list.get(2));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(1));
        subList.add(list.get(2));
        expected.add(subList);

        result = PermutationUtil.findAllPermutations(list, limit);
        assertTrue(expected.toString().equals(result.toString()));

        // 4 items -> 15 variations
        list = new ArrayList<>(Arrays.asList(blindVote0, blindVote1, blindVote2, blindVote3));
        expected = new ArrayList<>();
        expected.add(Arrays.asList(list.get(0)));

        expected.add(Arrays.asList(list.get(1)));

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(1));
        expected.add(subList);

        expected.add(Arrays.asList(list.get(2)));

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(2));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(1));
        subList.add(list.get(2));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(1));
        subList.add(list.get(2));
        expected.add(subList);

        expected.add(Arrays.asList(list.get(3)));

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(3));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(1));
        subList.add(list.get(3));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(1));
        subList.add(list.get(3));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(2));
        subList.add(list.get(3));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(2));
        subList.add(list.get(3));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(1));
        subList.add(list.get(2));
        subList.add(list.get(3));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(1));
        subList.add(list.get(2));
        subList.add(list.get(3));
        expected.add(subList);

        result = PermutationUtil.findAllPermutations(list, limit);
        assertTrue(expected.toString().equals(result.toString()));


        // 5 items -> 31 variations
        list = new ArrayList<>(Arrays.asList(blindVote0, blindVote1, blindVote2, blindVote3, blindVote4));
        expected = new ArrayList<>();
        expected.add(Arrays.asList(list.get(0)));

        expected.add(Arrays.asList(list.get(1)));

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(1));
        expected.add(subList);

        expected.add(Arrays.asList(list.get(2)));

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(2));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(1));
        subList.add(list.get(2));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(1));
        subList.add(list.get(2));
        expected.add(subList);

        expected.add(Arrays.asList(list.get(3)));

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(3));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(1));
        subList.add(list.get(3));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(1));
        subList.add(list.get(3));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(2));
        subList.add(list.get(3));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(2));
        subList.add(list.get(3));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(1));
        subList.add(list.get(2));
        subList.add(list.get(3));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(1));
        subList.add(list.get(2));
        subList.add(list.get(3));
        expected.add(subList);

        expected.add(Arrays.asList(list.get(4)));

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(4));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(1));
        subList.add(list.get(4));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(1));
        subList.add(list.get(4));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(2));
        subList.add(list.get(4));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(2));
        subList.add(list.get(4));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(1));
        subList.add(list.get(2));
        subList.add(list.get(4));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(1));
        subList.add(list.get(2));
        subList.add(list.get(4));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(3));
        subList.add(list.get(4));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(3));
        subList.add(list.get(4));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(1));
        subList.add(list.get(3));
        subList.add(list.get(4));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(1));
        subList.add(list.get(3));
        subList.add(list.get(4));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(2));
        subList.add(list.get(3));
        subList.add(list.get(4));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(2));
        subList.add(list.get(3));
        subList.add(list.get(4));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(1));
        subList.add(list.get(2));
        subList.add(list.get(3));
        subList.add(list.get(4));
        expected.add(subList);

        subList = new ArrayList<>();
        subList.add(list.get(0));
        subList.add(list.get(1));
        subList.add(list.get(2));
        subList.add(list.get(3));
        subList.add(list.get(4));
        expected.add(subList);

        result = PermutationUtil.findAllPermutations(list, limit);
        assertTrue(expected.toString().equals(result.toString()));


    }


}
