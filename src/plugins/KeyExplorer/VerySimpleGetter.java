/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyExplorer;

import com.db4o.ObjectContainer;

import freenet.client.async.ClientContext;
import freenet.client.async.ClientGetState;
import freenet.client.async.ClientRequester;
import freenet.keys.FreenetURI;
import freenet.node.RequestClient;
import freenet.support.LogThresholdCallback;
import freenet.support.Logger;

/**
 * @author saces
 *
 */
public class VerySimpleGetter extends ClientRequester {
	
	private static volatile boolean logDEBUG;
	
	static {
		Logger.registerLogThresholdCallback(new LogThresholdCallback() {
			
			@Override
			public void shouldUpdate() {
				logDEBUG = Logger.shouldLog(Logger.DEBUG, this);
			}
		});
	}

	private FreenetURI uri;

	/**
	 * @param priorityclass 
	 * @param chkscheduler 
	 * @param sskscheduler 
	 * @param client2 
	 * 
	 */
	public VerySimpleGetter(short priorityclass, FreenetURI uri2, RequestClient client2) {
		super(priorityclass, client2);
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
	public void cancel(ObjectContainer container, ClientContext context) {
		Logger.error(this, "TODO?", new Error("TODO?"));
	}

	@Override
	public void notifyClients(ObjectContainer container, ClientContext context) {
		// progress, ignore Logger.error(this, "TODO?", new Error("TODO?"));
	}

	@Override
	public void onTransition(ClientGetState oldState, ClientGetState newState, ObjectContainer container) {
		Logger.error(this, "TODO?", new Error("TODO?"));
	}

	@Override
	protected void innerToNetwork(ObjectContainer container, ClientContext context) {
		if (logDEBUG) Logger.debug(this, "Request goes out to network now.");
	}

}
