package plugins.KeyExplorer;

import java.net.MalformedURLException;

import plugins.fproxy.lib.AbstractFCPHandler;
import plugins.fproxy.lib.PluginContext;

import freenet.client.FetchException;
import freenet.keys.FreenetURI;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;

public class FCPHandler extends AbstractFCPHandler {

	FCPHandler(PluginContext pluginContext2) {
		super(pluginContext2);
	}

	/**
	 * @param accesstype
	 */
	public void handle(PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) throws PluginNotFoundException {
			if (params == null) {
				sendError(replysender, 0, "<void>", "Got void message");
				return;
			}

			if (data != null) {
				sendError(replysender, 0, "<void>", "Got a diatribe piece of writing. Data not allowed!");
				return;
			}

			String command = params.get("Command");

			if (command == null || command.trim().length() == 0) {
				sendError(replysender, 1, "", "Invalid Command name");
				return;
			}

			
			if ("Ping".equals(command)) {
				SimpleFieldSet sfs = new SimpleFieldSet(true);
				sfs.put("Pong", System.currentTimeMillis());
				replysender.send(sfs);
				return;
			}

			final String identifier = params.get("Identifier");
			if (identifier == null || identifier.trim().length() == 0) {
				sendError(replysender, 3, "<missing>", "Missing identifier");
				return;
			}

			if ("Get".equals(command)) {

				final String uri = params.get("URI");
				if (uri == null || uri.trim().length() == 0) {
					sendError(replysender, 4, identifier, "missing freenet uri");
					return;
				}

				try {
					FreenetURI furi = KeyExplorerUtils.sanitizeURI(null, uri);
					GetResult getResult = KeyExplorerUtils.simpleGet(pluginContext.pluginRespirator, furi);
					SimpleFieldSet sfs = new SimpleFieldSet(true);
					sfs.putSingle("Identifier", identifier);
					sfs.put("IsMetadata", getResult.isMetaData());
					sfs.putSingle("Status", "DataFound");
					replysender.send(sfs, getResult.getData());
					return;
				} catch (MalformedURLException e) {
					sendError(replysender, 5, identifier, "Malformed freenet uri: " + e.getMessage());
					return;
				} catch (FetchException e) {
					sendError(replysender, 6, identifier, "Get failed: " + e.toString());
					return;
				}
			}

			if ("ListSiteManifest".equals(command)) {

				final String uri = params.get("URI");
				if (uri == null || uri.trim().length() == 0) {
					sendError(replysender, 4, identifier, "missing freenet uri");
					return;
				}

				try {
					FreenetURI furi = new FreenetURI(uri);
					GetResult getResult = KeyExplorerUtils.simpleGet(pluginContext.pluginRespirator, furi);
					SimpleFieldSet sfs = new SimpleFieldSet(true);
					sfs.putSingle("Identifier", identifier);
					sfs.put("IsMetadata", getResult.isMetaData());
					sfs.putSingle("Status", "DataFound");
					replysender.send(sfs, getResult.getData());
					return;
				} catch (MalformedURLException e) {
					sendError(replysender, 5, identifier, "Malformed freenet uri: " + e.getMessage());
					return;
				} catch (FetchException e) {
					sendError(replysender, 6, identifier, "Get failed: " + e.toString());
					return;
				}
			}
			sendError(replysender, 1, identifier, "Unknown command: " + command);
	}

	public void kill() {
		// TODO Auto-generated method stub
		
	}


}
