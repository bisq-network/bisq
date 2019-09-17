package common.types;

/**
 * Generic parameterized pair.
 * 
 * @author woodser
 *
 * @param <F> the type of the first element
 * @param <S> the type of the second element
 */
public class Pair<F, S> {

  private F first;
  private S second;
  
  public Pair(F first, S second) {
    super();
    this.first = first;
    this.second = second;
  }

  public F getFirst() {
    return first;
  }
  
  public void setFirst(F first) {
    this.first = first;
  }
  
  public S getSecond() {
    return second;
  }
  
  public void setSecond(S second) {
    this.second = second;
  }
}
