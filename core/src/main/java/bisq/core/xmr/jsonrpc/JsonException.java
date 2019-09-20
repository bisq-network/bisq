package bisq.core.xmr.jsonrpc;

/**
 * Represents an exception handling JSON.
 * 
 * @author woodser
 */
public class JsonException extends RuntimeException {

  private static final long serialVersionUID = -5238056297221576735L;

  public JsonException(String msg) {
    super(msg);
  }
  
  public JsonException(String msg, Throwable e) {
    super(msg, e);
  }
  
  public JsonException(Throwable e) {
    super(e);
  }
}
