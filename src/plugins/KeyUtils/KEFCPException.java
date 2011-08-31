/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
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
