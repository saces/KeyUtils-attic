/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyExplorer;

import freenet.client.async.ClientGetState;
import freenet.client.async.ClientRequestScheduler;
import freenet.client.async.ClientRequester;
import freenet.keys.FreenetURI;
import freenet.support.Logger;

/**
 * @author saces
 *
 */
public class VerySimpleGetter extends ClientRequester {

	private FreenetURI uri;

	/**
	 * @param priorityclass 
	 * @param chkscheduler 
	 * @param sskscheduler 
	 * @param client2 
	 * 
	 */
	public VerySimpleGetter(short priorityclass, ClientRequestScheduler chkscheduler, ClientRequestScheduler sskscheduler, FreenetURI uri2, Object client2) {
		super(priorityclass, chkscheduler, sskscheduler, client2);
		uri = uri2;
	}
	
	@Override
	public FreenetURI getURI() {
		return uri;
	}

	@Override
	public boolean isFinished() {
		Logger.error(this, "TODO?", new Error("TODO?"));
		return false;
	}

	@Override
	public void notifyClients() {
		Logger.error(this, "TODO?", new Error("TODO?"));
	}

	@Override
	public void onTransition(ClientGetState oldState, ClientGetState newState) {
		Logger.error(this, "TODO?", new Error("TODO?"));
	}

}
