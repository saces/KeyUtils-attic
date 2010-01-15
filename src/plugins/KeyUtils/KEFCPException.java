package plugins.KeyUtils;

import freenet.support.plugins.helpers1.AbstractFCPHandler.FCPException;

/**
 * @author saces
 *
 */
public class KEFCPException extends FCPException {

	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public KEFCPException(int code, String message) {
		super(code, message);
	}
}
