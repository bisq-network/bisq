package common.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Base filter.
 */
public interface Filter<T> {

  /**
   * Indicates if the given item meets the criteria of this filter.
   * 
   * @param item is the item to test
   * @return true if the item meets the criteria of this filter, false otherwise
   */
  public boolean meetsCriteria(T item);
  
  /**
   * Returns a new list comprised of elements from the given list that meet the
   * filter's criteria.
   * 
   * @param items are the items to filter
   * @return the items that meet this filter's criteria
   */
  public static <T> List<T> apply(Filter<T> filter, List<? extends T> items) {
    List<T> filtered = new ArrayList<T>();
    for (T item : items) if (filter.meetsCriteria(item)) filtered.add(item);
    return filtered;
  }
  
  /**
   * Returns a new set comprised of elements from the given set that meet the
   * filter's criteria.
   * 
   * @param items are the items to filter
   * @return the items that meet this filter's criteria
   */
  public static <T> Set<T> apply(Filter<? extends T> filter, Set<? extends T> items) {
    throw new RuntimeException("Not implemented");
  }
}
