package plugins.KeyExplorer;

import plugins.fproxy.lib.AbstractFCPHandler.FCPException;

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
