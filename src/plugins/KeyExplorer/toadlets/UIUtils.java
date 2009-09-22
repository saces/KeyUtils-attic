package plugins.KeyExplorer.toadlets;

import java.util.List;

import plugins.fproxy.lib.PluginContext;

import freenet.clients.http.InfoboxNode;
import freenet.keys.FreenetURI;
import freenet.support.HTMLNode;

public class UIUtils {

	public static HTMLNode createErrorBox(PluginContext pCtx, List<String> errors) {
		return createErrorBox(pCtx, errors, null, null, null);
	}

	static HTMLNode createErrorBox(PluginContext pCtx, List<String> errors, String path, FreenetURI retryUri, String extraParams) {

		InfoboxNode box = pCtx.pageMaker.getInfobox("infobox-alert", "ERROR");
		HTMLNode errorBox = box.content;
		for (String error : errors) {
			errorBox.addChild("#", error);
			errorBox.addChild("%", "<BR />");
		}
		if (retryUri != null) {
			errorBox.addChild("#", "Retry: ");
			errorBox.addChild(new HTMLNode("a", "href", path + "?key="
					+ ((extraParams == null) ? retryUri : (retryUri + "?" + extraParams)), retryUri.toString(false, false)));
		}
		return box.outer;
	}

}
