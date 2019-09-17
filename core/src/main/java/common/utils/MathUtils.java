package common.utils;

import static org.junit.Assert.assertTrue;

import java.util.Random;

/**
 * Collection of math utilities.
 */
public class MathUtils {
  
  private static Random random = new Random();

  /**
   * Returns a random integer between the given integers, inclusive.
   * 
   * @param start is the start of the range, inclusive
   * @param end is the end of the range, inclusive
   * @return int is a random integer between start and end, inclusive
   */
  public static int random(int start, int end) {
    assertTrue(start <= end);
    return random.nextInt(end - start) + start;
  }
}
