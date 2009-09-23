/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyExplorer.toadlets;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;

import com.db4o.ObjectContainer;

import plugins.KeyExplorer.KeyExplorer;
import plugins.KeyExplorer.KeyExplorerUtils;
import plugins.fproxy.lib.PluginContext;
import plugins.fproxy.lib.WebInterfaceToadlet;
import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchWaiter;
import freenet.client.Metadata;
import freenet.client.async.ClientContext;
import freenet.client.async.ClientGetter;
import freenet.client.async.SnoopMetadata;
import freenet.clients.http.InfoboxNode;
import freenet.clients.http.PageNode;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.keys.ClientCHK;
import freenet.keys.FreenetURI;
import freenet.node.RequestClient;
import freenet.node.RequestStarter;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

/**
 * @author saces
 *
 */
public class SplitExplorerToadlet extends WebInterfaceToadlet {

	private static class SnoopGetter implements SnoopMetadata {

		private Metadata firstSplit;

		SnoopGetter () {
		}

		public boolean snoopMetadata(Metadata meta, ObjectContainer container, ClientContext context) {
			if (meta.isSplitfile()) {
				firstSplit = meta;
				return true;
			}
			return false;
		}
	}

	public SplitExplorerToadlet(PluginContext context) {
		super(context, KeyExplorer.PLUGIN_URI, "Split");
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
//		if (!normalizePath(request.getPath()).equals("/")) {
//			sendErrorPage(ctx, 404, "Not found", "the path '"+uri+"' was not found");
//			return;
//		}
		
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

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {

		List<String> errors = new LinkedList<String>();

		if (!isFormPassword(request)) {
			errors.add("Invalid form password");
			makeMainPage(ctx, errors, null);
			return;
		}

		String key = request.getPartAsString(Globals.PARAM_URI, 1024).trim();
		boolean deep = request.getPartAsString(Globals.PARAM_RECURSIVE, 128).length() > 0;
		boolean ml = request.getPartAsString(Globals.PARAM_MULTILEVEL, 128).length() > 0;
		String mftype = request.getPartAsString(Globals.PARAM_MFTYPE, 128);

		FreenetURI furi = null;
		if (key.trim().length() == 0) {
			errors.add("URI is empty");
		} else {
			try {
				if (key.trim().length() > 0) {
					furi = new FreenetURI(key);
				}
			} catch (MalformedURLException e) {
				errors.add("MalformedURL: " + key);
			}
		}
		if (errors.size() > 0) {
			makeMainPage(ctx, errors, null);
			return;
		}
		makeSplitPage(ctx, errors, furi);
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
				furi = new FreenetURI(key);
				retryUri = furi;
			}
		} catch (MalformedURLException e) {
			errors.add("MalformedURL: " + key);
		}

		HTMLNode uriBox = createUriBox(pluginContext, ((furi == null) ? null : furi.toString(false, false)));

		if (errors.size() > 0) {
			contentNode.addChild(UIUtils.createErrorBox(pluginContext, errors, path(), retryUri, null));
			errors.clear();
		}

		contentNode.addChild(uriBox);

		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}

	private void makeSplitPage(ToadletContext ctx, List<String> errors, FreenetURI furi) throws ToadletContextClosedException, IOException {
		PageNode page = pluginContext.pageMaker.getPageNode(KeyExplorer.PLUGIN_TITLE, ctx);
		HTMLNode outer = page.outer;
		HTMLNode contentNode = page.content;

		String extraParams = "";
		FreenetURI retryUri = null;
		Metadata md = null;
		try {
			md = splitGet(pluginContext.pluginRespirator, furi);
		} catch (FetchException e) {
			Logger.error(this, "debug", e);
			errors.add(e.getLocalizedMessage());
		}

		HTMLNode uriBox = createUriBox(pluginContext, ((furi == null) ? null : furi.toString(false, false)));

		if (errors.size() > 0) {
			contentNode.addChild(UIUtils.createErrorBox(pluginContext, errors, path(), retryUri, null));
			contentNode.addChild(uriBox);
			writeHTMLReply(ctx, 200, "OK", outer.generate());
			return;
		}

		HTMLNode splitBox = createSplitBox(pluginContext, md, furi.toString(false, false));

		contentNode.addChild(uriBox);
		contentNode.addChild(splitBox);

		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}

	private HTMLNode createSplitBox(PluginContext pCtx, Metadata md, String uri) {
		InfoboxNode box = pCtx.pageMaker.getInfobox("Split file: "+uri);
		HTMLNode browseBox = box.outer;
		HTMLNode browseContent = box.content;
		browseContent.addChild(createSplitHeaderBox(pCtx, md));
		browseContent.addChild(createSplitDataBox(pCtx, md));
		browseContent.addChild(createSplitCheckBox(pCtx, md));
		return browseBox;
	}

	private HTMLNode createSplitHeaderBox(PluginContext pCtx, Metadata md) {
		InfoboxNode box = pCtx.pageMaker.getInfobox("Split file info");
		HTMLNode browseBox = box.outer;
		HTMLNode browseContent = box.content;
		browseContent.addChild("#", "Split file type: ");
		short type = md.getSplitfileType();
		switch (type) {
		case Metadata.SPLITFILE_NONREDUNDANT: browseContent.addChild("#", "Non redundant"); break;
		case Metadata.SPLITFILE_ONION_STANDARD: browseContent.addChild("#", "FEC Onion standard"); break;
		default: browseContent.addChild("#", "<unknown>");
		}
		browseContent.addChild("#", "\u00a0("+type+")");
		return browseBox;
	}

	private HTMLNode createSplitDataBox(PluginContext pCtx, Metadata md) {
		InfoboxNode box = pCtx.pageMaker.getInfobox("Split file data blocks");
		HTMLNode browseBox = box.outer;
		HTMLNode browseContent = box.content;
		ClientCHK[] datakeys = md.getSplitfileDataKeys();
		for (ClientCHK key: datakeys) {
			browseContent.addChild("#", key.getURI().toString(false, false));
			browseContent.addChild("br");
		}
		return browseBox;
	}

	private HTMLNode createSplitCheckBox(PluginContext pCtx, Metadata md) {
		InfoboxNode box = pCtx.pageMaker.getInfobox("Split file check blocks");
		HTMLNode browseBox = box.outer;
		HTMLNode browseContent = box.content;
		ClientCHK[] checkkeys = md.getSplitfileCheckKeys();
		for (ClientCHK key: checkkeys) {
			browseContent.addChild("#", key.getURI().toString(false, false));
			browseContent.addChild("br");
		}
		return browseBox;
	}

	private HTMLNode createUriBox(PluginContext pCtx, String uri) {
		InfoboxNode box = pCtx.pageMaker.getInfobox("Explore a split file");
		HTMLNode browseBox = box.outer;
		HTMLNode browseContent = box.content;

		browseContent.addChild("#", "Display a split file structure");
		HTMLNode browseForm = pCtx.pluginRespirator.addFormChild(browseContent, path(), "uriForm");
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

	public static Metadata splitGet(PluginRespirator pr, FreenetURI uri) throws FetchException {
		SnoopGetter snooper = new SnoopGetter();
		FetchContext context = pr.getHLSimpleClient().getFetchContext();
		FetchWaiter fw = new FetchWaiter();
		ClientGetter get = new ClientGetter(fw, uri, context, RequestStarter.INTERACTIVE_PRIORITY_CLASS, (RequestClient)pr.getHLSimpleClient(), null, null);
		get.setMetaSnoop(snooper);

		try {
			get.start(null, pr.getNode().clientCore.clientContext);
			fw.waitForCompletion();
		} catch (FetchException e) {
			if (snooper.firstSplit == null) {
				// really an error
				Logger.error(SplitExplorerToadlet.class, "pfehler", e);
				throw e;
			}
		}

		return snooper.firstSplit;
	}
}
