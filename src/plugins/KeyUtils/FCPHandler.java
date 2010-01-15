package plugins.KeyUtils;

import java.net.MalformedURLException;

import freenet.client.FetchException;
import freenet.keys.FreenetURI;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.plugins.helpers1.AbstractFCPHandler;
import freenet.support.plugins.helpers1.PluginContext;
import freenet.support.plugins.helpers1.URISanitizer;

public class FCPHandler extends AbstractFCPHandler {

	FCPHandler(PluginContext pluginContext2) {
		super(pluginContext2);
	}

	/**
	 * @param accesstype
	 * @throws PluginNotFoundException 
	 */
	@Override
	public void handle(PluginReplySender replysender, String command, String identifier, SimpleFieldSet params, Bucket data, int accesstype) throws PluginNotFoundException {
		if (params == null) {
			sendError(replysender, 0, "<void>", "Got void message");
			return;
		}

		if (data != null) {
			sendError(replysender, 0, "<void>", "Got a diatribe piece of writing. Data not allowed!");
			return;
		}

		if ("Get".equals(command)) {
			final String uri = params.get("URI");
			if (uri == null || uri.trim().length() == 0) {
				sendError(replysender, 4, identifier, "missing freenet uri");
				return;
			}
			try {
				FreenetURI furi = URISanitizer.sanitizeURI(uri, URISanitizer.Options.NOMETASTRINGS, URISanitizer.Options.SSKFORUSK);
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
	}
}
