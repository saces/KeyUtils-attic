/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyExplorer;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Set;

import freenet.client.Metadata;
import freenet.client.MetadataParseException;
import freenet.client.async.ManifestElement;
import freenet.keys.BaseClientKey;
import freenet.keys.ClientKey;
import freenet.keys.FreenetURI;
import freenet.node.LowLevelGetException;
import freenet.node.RequestClient;
import freenet.pluginmanager.PluginRespirator;

public class KeyExplorerUtils {

	public static Metadata simpleManifestGet(PluginRespirator pr, FreenetURI uri) throws MetadataParseException, LowLevelGetException, IOException {
		GetResult res = simpleGet(pr, uri);
		if (!res.isMetaData()) {
			throw new MetadataParseException("uri did not point to metadata " + uri);
		}
		return Metadata.construct(res.getData());
	}

	public static GetResult simpleGet(PluginRespirator pr, FreenetURI uri) throws MalformedURLException, LowLevelGetException {
		ClientKey ck;
		try {
			ck = (ClientKey) BaseClientKey.getBaseKey(uri);
		} catch (ClassCastException cce) {
			throw new MalformedURLException("Not a supported freenet uri: " + uri);
		}
		VerySimpleGetter vsg = new VerySimpleGetter((short) 1, uri, (RequestClient) pr.getHLSimpleClient());
		VerySimpleGet vs = new VerySimpleGet(ck, 0, pr.getHLSimpleClient().getFetchContext(), vsg);
		vs.schedule(null, pr.getNode().clientCore.clientContext);
		return new GetResult(vs.waitForCompletion(), vs.isMetadata());
	}

	public static HashMap<String, Object> parseMetadata(Metadata oldMetadata, FreenetURI oldUri) throws MalformedURLException {
		return parseMetadata(oldMetadata.getDocuments(), oldUri, "");
	}

	private static HashMap<String, Object> parseMetadata(HashMap<String, Metadata> oldMetadata, FreenetURI oldUri, String prefix) throws MalformedURLException {
		HashMap<String, Object> newMetadata = new HashMap<String, Object>();
		Set<String> set = oldMetadata.keySet();
		for(String name:set) {
			Metadata md = oldMetadata.get(name);
			if (md.isArchiveInternalRedirect()) {
				String fname = prefix + name;
				FreenetURI newUri = new FreenetURI(oldUri.toString(false, false) + "/"+ fname);
				//System.err.println("NewURI: "+newUri.toString(false, false));
				newMetadata.put(name, new ManifestElement(name, newUri, null));
			} else if (md.isSingleFileRedirect()) {
				newMetadata.put(name, new ManifestElement(name, md.getSingleTarget(), null));
			} else if (md.isSplitfile()) {
				newMetadata.put(name, new ManifestElement(name, md.getSingleTarget(), null));
			} else {
				newMetadata.put(name, parseMetadata(md.getDocuments(), oldUri, prefix + name + "/"));
			}
		}
		return newMetadata;
	}

}
