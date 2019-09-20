package bisq.core.xmr.jsonrpc;

/**
 * Defines an HTTP exception for when HTTP responses are not in the 200s.
 * 
 * @author woodser
 */
public class HttpException extends RuntimeException {

  private static final long serialVersionUID = -4603832308887633042L;
  
  private int code;
  private String message;
  
  public HttpException(String message) {
    super(message);
    this.code = 500;
    this.message = message;
  }
  
  public HttpException(String message, Throwable e) {
    this(500, message, e);
  }
  
  public HttpException(int code, String message) {
    super(message);
    this.code = code;
    this.message = message;
  }

  public HttpException(int code, String message, Throwable e) {
    super(message, e);
    this.code = code;
    this.message = message;
  }

  public int getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }
}
