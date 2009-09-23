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
import freenet.support.Fields;
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

		// TODO sanitize uri USK->SSK
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

		InfoboxNode iBox = pCtx.pageMaker.getInfobox("Split file info");
		HTMLNode infoBox = iBox.outer;
		HTMLNode infoContent = iBox.content;
		infoContent.addChild("#", "Split file type: ");

		short type = md.getSplitfileType();
		int dataBlocksPerSegment = -1;
		int checkBlocksPerSegment = -1;

		switch (type) {
		case Metadata.SPLITFILE_NONREDUNDANT: infoContent.addChild("#", "Non redundant"); break;
		case Metadata.SPLITFILE_ONION_STANDARD: infoContent.addChild("#", "FEC Onion standard"); break;
		default: infoContent.addChild("#", "<unknown>");
		}
		infoContent.addChild("#", "\u00a0("+type+")");
		infoContent.addChild("br");

		browseContent.addChild(infoBox);

		if (type == Metadata.SPLITFILE_ONION_STANDARD) {
			byte[] params = md.splitfileParams();
			if((params == null) || (params.length < 8))
				infoContent.addChild("#", "Error: No splitfile params!");
			else {
				dataBlocksPerSegment = Fields.bytesToInt(params, 0);
				checkBlocksPerSegment = Fields.bytesToInt(params, 4);
				infoContent.addChild("#", "Data blocks per segment: " + dataBlocksPerSegment);
				infoContent.addChild("br");
				infoContent.addChild("#", "Check blocks per segment: " + checkBlocksPerSegment);
				infoContent.addChild("br");
				int sfDB = md.getSplitfileDataKeys().length;
				infoContent.addChild("#", "Segment count: " + ((sfDB / dataBlocksPerSegment) + (sfDB % dataBlocksPerSegment == 0 ? 0 : 1)));
			}
			browseContent.addChild(createSegmentedBox(pCtx, "data", md.getSplitfileDataKeys(), dataBlocksPerSegment));
			browseContent.addChild(createSegmentedBox(pCtx, "check", md.getSplitfileCheckKeys(), checkBlocksPerSegment));
		} else if (type == Metadata.SPLITFILE_NONREDUNDANT) {
			browseContent.addChild(createSegmentedBox(pCtx, "data", md.getSplitfileDataKeys(), -1));
		}
		return browseBox;
	}

	private HTMLNode createSegmentedBox(PluginContext pCtx, String title, ClientCHK[] keys, int blockspersegment) {
		InfoboxNode box = pCtx.pageMaker.getInfobox("Split file "+title+" blocks: " + keys.length);
		HTMLNode browseBox = box.outer;
		HTMLNode browseContent = box.content;

		int segmentcounter = 1;
		for (int offset = 0; offset < keys.length; offset += blockspersegment) {
			InfoboxNode segmentInfo = pCtx.pageMaker.getInfobox("Segment #"+segmentcounter);
			HTMLNode segmentBox = segmentInfo.outer;
			HTMLNode segmentContent = segmentInfo.content;

			int count = Math.min(blockspersegment, keys.length);
			for (int i = 0; i < count ; i++) {
				FreenetURI key = keys[i+offset].getURI();
				segmentContent.addChild("#", key.toString(false, false));
				segmentContent.addChild("br");
			}

			browseContent.addChild(segmentBox);
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
