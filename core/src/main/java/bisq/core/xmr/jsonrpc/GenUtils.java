package bisq.core.xmr.jsonrpc;

import java.util.ArrayList;
import java.util.List;

/**
 * Collection of general purpose utilities.
 */
public class GenUtils {
  
  /**
   * Converts a templated array to a list.
   * 
   * @param arr is an array of type T to convert to a list
   * @return List<T> is the array converted to a list
   */
  public static <T> List<T> arrayToList(T[] arr) {
    List<T> list = new ArrayList<T>(arr.length);
    for (T elem : arr) list.add(elem);
    return list;
  }
  
  /**
   * Converts a list of integers to an int array.
   * 
   * @param list is the list ot convert
   * @return the int array
   */
  public static int[] listToIntArray(List<Integer> list) {
    if (list == null) return null;
    int[] ints = new int[list.size()];
    for (int i = 0; i < list.size(); i++) ints[i] = list.get(i);
    return ints;
  }
  
  /**
   * Returns a string indentation of the given length;
   * 
   * @param length is the length of the indentation
   * @returns {string} is an indentation string of the given length
   */
  public static String getIndent(int length) {
    String str = "";
    for (int i = 0; i < length; i++) str += "  "; // two spaces
    return str;
  }
}
