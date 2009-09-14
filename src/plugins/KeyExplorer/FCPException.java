package plugins.KeyExplorer;

/**
 * @author saces
 *
 */
public class FCPException extends Exception {

	private static final long serialVersionUID = 1L;

	public static final int NO_ERROR = 0;
	public static final int MISSING_IDENTIFIER = 1;
	public static final int MISSING_COMMAND = 2;
	public static final int INVALID_COMMAND = 3;
	public static final int DUPLICATE_SESSION = 4;
	public static final int DUPLICATE_SESSION_RUNNING = 5;
	public static final int INVALID_OLDURI = 6;
	public static final int MISSING_OLDURI = 7;
	public static final int INVALID_SESSION = 8;
	public static final int SESSION_CANTSTOP = 9;
	public static final int MISSING_INSERTURI = 10;
	public static final int INVALID_INSERTURI = 11;
	public static final int INTERNAL = 12;

	private final int _code;

	/**
	 * 
	 */
	public FCPException(int code, String message) {
		super(message);
		this._code = code;
	}

	public int getCode() {
		return _code;
	}
}
