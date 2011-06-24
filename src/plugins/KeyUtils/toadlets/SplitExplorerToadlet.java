/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyUtils.toadlets;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;

import plugins.KeyUtils.KeyUtilsPlugin;

import com.db4o.ObjectContainer;

import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchWaiter;
import freenet.client.Metadata;
import freenet.client.async.ClientContext;
import freenet.client.async.ClientGetter;
import freenet.client.async.SnoopMetadata;
import freenet.client.async.SplitFileSegmentKeys;
import freenet.clients.http.InfoboxNode;
import freenet.clients.http.PageNode;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.crypt.HashResult;
import freenet.keys.ClientCHK;
import freenet.keys.FreenetURI;
import freenet.node.RequestClient;
import freenet.node.RequestStarter;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Fields;
import freenet.support.HexUtil;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;
import freenet.support.plugins.helpers1.PluginContext;
import freenet.support.plugins.helpers1.WebInterfaceToadlet;

/**
 * @author saces
 *
 */
public class SplitExplorerToadlet extends WebInterfaceToadlet {

	private static abstract class AbstractSnoop implements SnoopMetadata {
		abstract Metadata getResult();
	}

	private static class SnoopFirst extends AbstractSnoop {

		private Metadata firstSplit;

		SnoopFirst () {
		}

		@Override
		public boolean snoopMetadata(Metadata meta, ObjectContainer container, ClientContext context) {
			if (meta.isSplitfile()) {
				firstSplit = meta;
				return true;
			}
			return false;
		}

		@Override
		Metadata getResult() {
			return firstSplit;
		}
	}

	private static class SnoopLast extends AbstractSnoop {

		private Metadata lastSplit;

		SnoopLast () {
		}

		@Override
		public boolean snoopMetadata(Metadata meta, ObjectContainer container, ClientContext context) {
			if (meta.isSplitfile()) {
				lastSplit = (Metadata) meta.clone();
			}
			return false;
		}

		@Override
		Metadata getResult() {
			return lastSplit;
		}
	}

	private static class SnoopLevel implements SnoopMetadata {

		private final int _level;
		private Metadata lastSplit;
		private int lastLevel;

		SnoopLevel (int level) {
			_level = level;
			lastLevel = 0;
		}

		@Override
		public boolean snoopMetadata(Metadata meta, ObjectContainer container, ClientContext context) {
			if (meta.isSplitfile()) {
				lastSplit = meta;
				lastLevel++;
				if (lastLevel > _level) return true;
			}
			return false;
		}
	}

	public SplitExplorerToadlet(PluginContext context) {
		super(context, KeyUtilsPlugin.PLUGIN_URI, "Split");
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
//		if (!normalizePath(request.getPath()).equals("/")) {
//			sendErrorPage(ctx, 404, "Not found", "the path '"+uri+"' was not found");
//			return;
//		}
		
		String key;
		boolean ml;
		int level;
		if (request.isParameterSet(Globals.PARAM_URI)) {
			key = request.getParam(Globals.PARAM_URI);
			ml = request.getParam(Globals.PARAM_MULTILEVEL).length() > 0;
			level = request.getIntParam(Globals.PARAM_LEVEL, 0);
		} else {
			key = null;
			ml = false;
			level = 0;
		}

		List<String> errors = new LinkedList<String>();

		makeMainPage(ctx, errors, key, level);
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {

		List<String> errors = new LinkedList<String>();

		if (!isFormPassword(request)) {
			errors.add("Invalid form password");
			makeMainPage(ctx, errors);
			return;
		}

		String key = request.getPartAsString(Globals.PARAM_URI, 1024).trim();
		boolean deep = request.getPartAsString(Globals.PARAM_RECURSIVE, 128).length() > 0;
		boolean ml = request.getPartAsString(Globals.PARAM_MULTILEVEL, 128).length() > 0;
		String mftype = request.getPartAsString(Globals.PARAM_MFTYPE, 128);
		int level = request.getIntPart(Globals.PARAM_LEVEL, 0);

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
			makeMainPage(ctx, errors);
			return;
		}
		makeSplitPage(ctx, errors, furi, level);
	}

	private void makeMainPage(ToadletContext ctx, List<String> errors) throws ToadletContextClosedException, IOException {
		makeMainPage(ctx, errors, null, 0);
	}

	private void makeMainPage(ToadletContext ctx, List<String> errors, String key, int level) throws ToadletContextClosedException, IOException {
		PageNode page = pluginContext.pageMaker.getPageNode(KeyUtilsPlugin.PLUGIN_TITLE, ctx);
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

		HTMLNode uriBox = createUriBox(pluginContext, ((furi == null) ? null : furi.toString(false, false)), level);

		if (errors.size() > 0) {
			contentNode.addChild(createErrorBox(errors, path(), retryUri, null));
			errors.clear();
		}

		contentNode.addChild(uriBox);

		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}

	private void makeSplitPage(ToadletContext ctx, List<String> errors, FreenetURI furi, int level) throws ToadletContextClosedException, IOException {
		PageNode page = pluginContext.pageMaker.getPageNode(KeyUtilsPlugin.PLUGIN_TITLE, ctx);
		HTMLNode outer = page.outer;
		HTMLNode contentNode = page.content;

		String extraParams = "";
		FreenetURI retryUri = null;
		Metadata md = null;
		if (level < 1) {
			try {
				md = splitGet(pluginContext.pluginRespirator, furi, (level < 0));
			} catch (FetchException e) {
				Logger.error(this, "debug", e);
				errors.add(e.getLocalizedMessage());
			}
		} else {
			try {
				md = splitGet(pluginContext.pluginRespirator, furi, level);
			} catch (FetchException e) {
				Logger.error(this, "debug", e);
				errors.add(e.getLocalizedMessage());
			}
		}
		HTMLNode uriBox = createUriBox(pluginContext, ((furi == null) ? null : furi.toString(false, false)), level);

		if (errors.size() > 0) {
			contentNode.addChild(createErrorBox(errors, path(), retryUri, null));
			contentNode.addChild(uriBox);
			writeHTMLReply(ctx, 200, "OK", outer.generate());
			return;
		}

		HTMLNode splitBox;
		if (md.getParsedVersion() == 0) {
			splitBox = createSplitBoxV1(pluginContext, md, furi.toString(false, false));
		} else {
			// version 1+
			splitBox = createSplitBoxV1(pluginContext, md, furi.toString(false, false));
		}
		contentNode.addChild(uriBox);
		contentNode.addChild(splitBox);

		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}

	private HTMLNode createSplitBoxV0(PluginContext pCtx, Metadata md, String uri) {
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
			browseContent.addChild(createSegmentedBoxV0(pCtx, "data", md.getSplitfileDataKeys(), dataBlocksPerSegment));
			browseContent.addChild(createSegmentedBoxV0(pCtx, "check", md.getSplitfileCheckKeys(), checkBlocksPerSegment));
		} else if (type == Metadata.SPLITFILE_NONREDUNDANT) {
			browseContent.addChild(createSegmentedBoxV0(pCtx, "data", md.getSplitfileDataKeys(), -1));
		}
		return browseBox;
	}

	private HTMLNode createSegmentedBoxV0(PluginContext pCtx, String title, ClientCHK[] keys, int blockspersegment) {
		InfoboxNode box = pCtx.pageMaker.getInfobox("Split file "+title+" blocks: " + keys.length);
		HTMLNode browseBox = box.outer;
		HTMLNode browseContent = box.content;

		int segmentcounter = 1;
		for (int offset = 0; offset < keys.length; offset += blockspersegment) {
			InfoboxNode segmentInfo = pCtx.pageMaker.getInfobox("Segment #"+segmentcounter++);
			HTMLNode segmentBox = segmentInfo.outer;
			HTMLNode segmentContent = segmentInfo.content;
			segmentContent.addChild("%", "<div lang=\"en\" style=\"font-family: monospace;\">\n");
			for (int i = 0; i < blockspersegment; i++) {
				if (i + offset >= keys.length)
					break;
				FreenetURI key = keys[i+offset].getURI();
				segmentContent.addChild("#", key.toString(false, false));
				segmentContent.addChild("br");
			}
			segmentContent.addChild("%", "\n</div>");
			browseContent.addChild(segmentBox);
		}
		return browseBox;
	}

	private HTMLNode createSplitBoxV1(PluginContext pCtx, Metadata md, String uri) {
		InfoboxNode box = pCtx.pageMaker.getInfobox("Split file: "+uri);
		HTMLNode browseBox = box.outer;
		HTMLNode browseContent = box.content;

		InfoboxNode iBox = pCtx.pageMaker.getInfobox("Split file info");
		HTMLNode infoBox = iBox.outer;
		HTMLNode infoContent = iBox.content;

		if (!md.isSplitfile()) {
			infoContent.addChild("#", "Error: Not a splitfile manifest?!@#?. may a bug, please report.");
			return browseBox;
		}

		infoContent.addChild("#", "Metadata version "+Short.toString(md.getParsedVersion()));
		infoContent.addChild("br");

		infoContent.addChild("#", "Split file type: ");

		short type = md.getSplitfileType();

		switch (type) {
		case Metadata.SPLITFILE_NONREDUNDANT: infoContent.addChild("#", "Non redundant"); break;
		case Metadata.SPLITFILE_ONION_STANDARD: infoContent.addChild("#", "FEC Onion standard"); break;
		default: infoContent.addChild("#", "<unknown>");
		}
		infoContent.addChild("#", "\u00a0("+type+")");
		infoContent.addChild("br");

		infoContent.addChild("#", "Compatiblity: "+md.getTopCompatibilityMode().name()+" (min: "+md.getMinCompatMode().name() +" max: "+md.getMaxCompatMode()+")");
		infoContent.addChild("br");

		// FIXME BEGIN refactor: duplicated with KeyExplorerToadlet
		byte[] splitfileCryptoKey = md.getCustomSplitfileKey();
		if (splitfileCryptoKey != null) {
			infoContent.addChild("#", "Splitfile CryptoKey: " + HexUtil.bytesToHex(splitfileCryptoKey));
			infoContent.addChild("br");
		}

		if (md.hasTopData()) {
			infoContent.addChild("#", "Top Block Data:");
			infoContent.addChild("br");
			infoContent.addChild("#", "\u00a0\u00a0DontCompress: " + Boolean.toString(md.topDontCompress));
			infoContent.addChild("br");
			infoContent.addChild("#", "\u00a0\u00a0Compressed size: " + Long.toString(md.topCompressedSize) + " bytes.");
			infoContent.addChild("br");
			infoContent.addChild("#", "\u00a0\u00a0Decompressed Size: " + Long.toString(md.topSize) + " bytes.");
			infoContent.addChild("br");
			infoContent.addChild("#", "\u00a0\u00a0Blocks: " + Integer.toString(md.topBlocksRequired) + " required, " + Integer.toString(md.topBlocksTotal) + " total.");
			infoContent.addChild("br");
		}

		final HashResult[] hashes = md.getHashes();
		if (hashes != null && hashes.length > 0) {
			infoContent.addChild("#", "Hashes:");
			infoContent.addChild("br");
			for (final HashResult hash : hashes) {
				infoContent.addChild("#", "\u00a0\u00a0" + hash.type.name() + ": " + HexUtil.bytesToHex(hash.result));
				infoContent.addChild("br");
			}
		}

		final String dataLengthPrefix;
		if (md.isCompressed()) {
			infoContent.addChild("#", "Decompressed size: " + md.uncompressedDataLength() + " bytes.");
			infoContent.addChild("br");
			dataLengthPrefix = "Compressed ("+ md.getCompressionCodec().name + ")";
		} else {
			dataLengthPrefix = "Uncompressed";
		}

		infoContent.addChild("#", dataLengthPrefix + " data size: " + md.dataLength() + " bytes.");
		infoContent.addChild("br");
		// FIXME END

		browseContent.addChild(infoBox);

		SplitFileSegmentKeys[] segments;
		try {
			segments = md .grabSegmentKeys(null);
		} catch (FetchException e) {
			Logger.error(this, "Internal failures: "+e.getMessage(), e);
			infoContent.addChild("#", "Error: Internal failure while decoding data. Try again (refresh the page).");
			return browseBox;
		}

		if (segments == null) {
			infoContent.addChild("br");
			Logger.error(this, "Segements is null!?", new Error("Debug"));
			infoContent.addChild("#", "Error: Segements is null!? Should not happen!?!");
			return browseBox;
		}

		infoContent.addChild("#", "Segment count: " + segments.length);
		infoContent.addChild("br");

		if (type == Metadata.SPLITFILE_ONION_STANDARD) {
			infoContent.addChild("#", "Data blocks per segment: " + md.getDataBlocksPerSegment());
			infoContent.addChild("br");
			infoContent.addChild("#", "Check blocks per segment: " + md.getCheckBlocksPerSegment());
			infoContent.addChild("br");
			for (int i=0;i<segments.length;i++) {
				browseContent.addChild(createSegmentedBoxV1(pCtx, i, segments[i]));
			}
		} else if (type == Metadata.SPLITFILE_NONREDUNDANT) {
			infoContent.addChild("#", "Data blocks per segment: " + md.getDataBlocksPerSegment());
			infoContent.addChild("br");
			for (int i=0;i<segments.length;i++) {
				browseContent.addChild(createSegmentedBoxV1(pCtx, i, segments[i]));
			}
		}
		return browseBox;
	}

	private HTMLNode createSegmentedBoxV1(PluginContext pCtx, int index, SplitFileSegmentKeys segment) {
		InfoboxNode box = pCtx.pageMaker.getInfobox("Segment #"+index);
		HTMLNode browseBox = box.outer;
		HTMLNode browseContent = box.content;

		if (segment.dataBlocks > 0) {
			InfoboxNode segmentInfo = pCtx.pageMaker.getInfobox("Data Blocks: "+segment.dataBlocks);
			HTMLNode segmentBox = segmentInfo.outer;
			HTMLNode segmentContent = segmentInfo.content;
			segmentContent.addChild("%", "<div lang=\"en\" style=\"font-family: monospace;\">\n");
			for (int i = 0; i < segment.dataBlocks; i++) {
				ClientCHK key = segment.getKey(i, null, false);
				segmentContent.addChild("#", i+"\t"+key.getURI().toString(false, false));
				segmentContent.addChild("br");
			}
			segmentContent.addChild("%", "\n</div>");
			browseContent.addChild(segmentBox);
		}
		if (segment.checkBlocks > 0) {
			InfoboxNode segmentInfo = pCtx.pageMaker.getInfobox("Check Blocks: "+segment.checkBlocks);
			HTMLNode segmentBox = segmentInfo.outer;
			HTMLNode segmentContent = segmentInfo.content;
			segmentContent.addChild("%", "<div lang=\"en\" style=\"font-family: monospace;\">\n");
			for (int i = 0; i < (segment.checkBlocks); i++) {
				ClientCHK key = segment.getKey(i+segment.dataBlocks, null, false);
				segmentContent.addChild("#", i+"\t"+key.getURI().toString(false, false));
				segmentContent.addChild("br");
			}
			segmentContent.addChild("%", "\n</div>");
			browseContent.addChild(segmentBox);
		}

		return browseBox;
	}

	private HTMLNode createUriBox(PluginContext pCtx, String uri, int level) {
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
		browseForm.addChild("br");
		browseForm.addChild("#", "Level:\u00a0");
		if (uri != null)
			browseForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", Globals.PARAM_LEVEL, "2", Integer.toString(level) });
		else
			browseForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", Globals.PARAM_LEVEL, "2", "0" });
		browseForm.addChild("#", "\u00a00=first, -1=last, n=jump over n split levels");
		return browseBox;
	}

	private Metadata splitGet(PluginRespirator pr, FreenetURI uri, boolean last) throws FetchException {
		AbstractSnoop snooper;
		if (last)
			snooper = new SnoopLast();
		else
			snooper = new SnoopFirst();
		FetchContext context = pr.getHLSimpleClient().getFetchContext();
		FetchWaiter fw = new FetchWaiter();
		ClientGetter get = new ClientGetter(fw, uri, context, RequestStarter.INTERACTIVE_PRIORITY_CLASS, (RequestClient)pr.getHLSimpleClient(), null, null, null);
		get.setMetaSnoop(snooper);

		try {
			get.start(null, pr.getNode().clientCore.clientContext);
			fw.waitForCompletion();
		} catch (FetchException e) {
			if (snooper.getResult() == null) {
				// really an error
				throw e;
			}
		}
		Metadata result = snooper.getResult();
		if (result == null) {
			throw new FetchException(FetchException.INVALID_METADATA, "URI does not point to a split file");
		}
		return result;
	}

	private Metadata splitGet(PluginRespirator pr, FreenetURI uri, int level) throws FetchException {
		SnoopLevel snooper = new SnoopLevel(level);
		FetchContext context = pr.getHLSimpleClient().getFetchContext();
		FetchWaiter fw = new FetchWaiter();
		ClientGetter get = new ClientGetter(fw, uri, context, RequestStarter.INTERACTIVE_PRIORITY_CLASS, (RequestClient)pr.getHLSimpleClient(), null, null, null);
		get.setMetaSnoop(snooper);

		try {
			get.start(null, pr.getNode().clientCore.clientContext);
			fw.waitForCompletion();
		} catch (FetchException e) {
			if (snooper.lastSplit == null) {
				// really an error
				throw e;
			}
		}
		Metadata result = snooper.lastSplit;
		if (result == null) {
			throw new FetchException(FetchException.INVALID_METADATA, "URI does not point to a split file");
		}
		return snooper.lastSplit;
	}
}
