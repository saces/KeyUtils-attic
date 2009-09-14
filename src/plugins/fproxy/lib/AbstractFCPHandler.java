package plugins.fproxy.lib;

import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.support.SimpleFieldSet;

public abstract class AbstractFCPHandler {

	protected final PluginContext pluginContext;

	protected AbstractFCPHandler(PluginContext pluginContext2) {
		this.pluginContext = pluginContext2;
	}

	public void sendError(PluginReplySender replysender, int code, String identifier, String description) throws PluginNotFoundException {
		SimpleFieldSet sfs = new SimpleFieldSet(true);
		sfs.putOverwrite("Status", "Error");
		sfs.put("Code", code);
		sfs.putSingle("Identifier", identifier);
		sfs.putOverwrite("Description", description);
		replysender.send(sfs);
	}
}
