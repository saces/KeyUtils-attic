/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyExplorer.toadlets;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;

import plugins.KeyExplorer.KeyExplorer;
import plugins.KeyExplorer.KeyExplorerUtils;
import plugins.fproxy.lib.PluginContext;
import plugins.fproxy.lib.WebInterfaceToadlet;
import freenet.clients.http.InfoboxNode;
import freenet.clients.http.PageNode;
import freenet.clients.http.RedirectException;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.keys.FreenetURI;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * @author saces
 *
 */
public class SplitExplorerToadlet extends WebInterfaceToadlet {

	public SplitExplorerToadlet(PluginContext context) {
		super(context, KeyExplorer.PLUGIN_URI, "Split");
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {
		if (!normalizePath(request.getPath()).equals("/")) {
			sendErrorPage(ctx, 404, "Not found", "the path '"+uri+"' was not found");
			return;
		}
		
		String key;
		boolean ml;
		if (request.isParameterSet(Globals.PARAM_URI)) {
			key = request.getParam(Globals.PARAM_URI);
			ml = request.getParam(Globals.PARAM_MULTILEVEL).length() > 0;
		} else {
			key = null;
			ml = false;
		}

		List<String> errors = new LinkedList<String>();

		makeMainPage(ctx, errors, key);
	}

	private void makeMainPage(ToadletContext ctx, List<String> errors, String key) throws ToadletContextClosedException, IOException {
		PageNode page = pluginContext.pageMaker.getPageNode(KeyExplorer.PLUGIN_TITLE, ctx);
		HTMLNode outer = page.outer;
		HTMLNode contentNode = page.content;

		String extraParams = "";
		FreenetURI furi = null;
		FreenetURI retryUri = null;

		try {
			if (key != null && (key.trim().length() > 0)) {
				furi = KeyExplorerUtils.sanitizeURI(errors, key);
				retryUri = furi;
			}
		} catch (MalformedURLException e) {
			errors.add("MalformedURL: " + key);
		}

		HTMLNode uriBox = createUriBox(pluginContext, ((furi == null) ? null : furi.toString(false, false)), errors);

		if (errors.size() > 0) {
			contentNode.addChild(UIUtils.createErrorBox(pluginContext, errors, path(), retryUri, null));
			errors.clear();
		}

		contentNode.addChild(uriBox);

		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}

	private HTMLNode createUriBox(PluginContext pCtx, String uri, List<String> errors) {
		InfoboxNode box = pCtx.pageMaker.getInfobox("Explore a split file");
		HTMLNode browseBox = box.outer;
		HTMLNode browseContent = box.content;

		browseContent.addChild("#", "Display a split file structure");
		HTMLNode browseForm = pCtx.pluginRespirator.addFormChild(browseContent, "/plugins/plugins.KeyExplorer.KeyExplorer/", "uriForm");
		browseForm.addChild("#", "Freenetkey to explore: \u00a0 ");
		if (uri != null)
			browseForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", Globals.PARAM_URI, "70", uri });
		else
			browseForm.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", Globals.PARAM_URI, "70" });
		browseForm.addChild("#", "\u00a0");
		browseForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "debug", "Explore!" });
		browseForm.addChild("%", "<BR />");
		return browseBox;
	}
}
