/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyExplorer.toadlets;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import plugins.KeyExplorer.GetResult;
import plugins.KeyExplorer.KeyExplorer;
import plugins.KeyExplorer.KeyExplorerUtils;
import plugins.fproxy.lib.PluginContext;
import plugins.fproxy.lib.WebInterfaceToadlet;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.Metadata;
import freenet.client.MetadataParseException;
import freenet.client.ArchiveManager.ARCHIVE_TYPE;
import freenet.client.async.KeyListenerConstructionException;
import freenet.clients.http.InfoboxNode;
import freenet.clients.http.PageNode;
import freenet.clients.http.RedirectException;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.keys.FreenetURI;
import freenet.node.LowLevelGetException;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;
import freenet.support.io.BucketTools;

/**
 * @author saces
 *
 */
public class KeyExplorerToadlet extends WebInterfaceToadlet {

	public KeyExplorerToadlet(PluginContext context) {
		super(context, KeyExplorer.PLUGIN_URI, "");
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException, URISyntaxException {
		System.out.println("Path-Test: " + normalizePath(request.getPath()) + " -> " + uri);
		if (!request.getPath().toString().equals(path())) {
			sendErrorPage(ctx, 404, "Not found", "the path '"+uri+"' was not found");
			return;
		}
		String key;
		String type;
		boolean automf;
		boolean deep;
		boolean ml;
		int hexWidth;
		String action;
		if (request.isParameterSet("key")) {
			key = request.getParam("key");
			type = request.getParam("mftype");
			automf = request.getParam("automf").length() > 0;
			deep = request.getParam("deep").length() > 0;
			ml = request.getParam("ml").length() > 0;
			hexWidth = request.getIntParam("hexWidth", 32);
			action = request.getParam("action");
		} else {
			key = null;
			type = null;
			automf = true;
			deep = true;
			ml = true;
			hexWidth = 32;
			action = "";
		}
		
		String extraParams = "&hexwidth=" + hexWidth;
		if (automf) {
			extraParams += "&automf=checked";
		}
		if (deep) {
			extraParams += "&deep=checked";
		}
		if (ml) {
			extraParams += "&ml=checked";
		}

		List<String> errors = new LinkedList<String>();
		if (hexWidth < 1 || hexWidth > 1024) {
			errors.add("Hex display columns out of range. (1-1024). Set to 32 (default).");
			hexWidth = 32;
		}

		if ("ZIPmanifest".equals(type)) {
			throw new RedirectException("/KeyExplorer/Site/?mftype=ZIPmanifest&key=" + key + extraParams);
		}
		if ("TARmanifest".equals(type)) {
			throw new RedirectException("/KeyExplorer/Site/?mftype=TARmanifest&key=" + key + extraParams);
		}
		if ("simplemanifest".equals(type)) {
			throw new RedirectException("/KeyExplorer/Site/?mftype=simplemanifest&key=" + key + extraParams);
		}
		makeMainPage(ctx, errors, key, hexWidth, automf, deep, ml);
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException, URISyntaxException {
		System.out.println("Path-Test: " + normalizePath(request.getPath()) + " -> " + uri);
		
		String key = request.getPartAsString("key", 1024);
		int hexWidth = request.getIntPart("hexWidth", 32);
		boolean automf = request.getPartAsString("automf", 128).length() > 0;
		boolean deep = request.getPartAsString("deep", 128).length() > 0;
		boolean ml = request.getPartAsString("ml", 128).length() > 0;
		List<String> errors = new LinkedList<String>();
		if (hexWidth < 1 || hexWidth > 1024) {
			errors.add("Hex display columns out of range. (1-1024). Set to 32 (default).");
			hexWidth = 32;
		}
		makeMainPage(ctx, errors, key, hexWidth, automf, deep, ml);
	}

	private void makeMainPage(ToadletContext ctx, List<String> errors, String key, int hexWidth, boolean automf, boolean deep, boolean ml) throws ToadletContextClosedException, IOException, RedirectException, URISyntaxException {
		PageNode page = pluginContext.pageMaker.getPageNode("KeyExplorer", ctx);
		HTMLNode pageNode = page.outer;
		HTMLNode contentNode = page.content;

		byte[] data = null;
		GetResult getresult = null;
		String extraParams = "&hexwidth=" + hexWidth;
		if (automf) {
			extraParams += "&automf=checked";
		}
		if (deep) {
			extraParams += "&deep=checked";
		}
		if (ml) {
			extraParams += "&ml=checked";
		}
		FreenetURI furi = null;
		FreenetURI retryUri = null;

		try {
			if (key != null && (key.trim().length() > 0)) {
				furi = KeyExplorerUtils.sanitizeURI(errors, key);
				retryUri = furi;
				if (ml) { // multilevel is requestet
					Metadata tempMD = KeyExplorerUtils.simpleManifestGet(pluginContext.pluginRespirator, furi);
					FetchResult tempResult = KeyExplorerUtils.splitGet(pluginContext.pluginRespirator, tempMD);
					getresult = new GetResult(tempResult.asBucket(), true);
					data = tempResult.asByteArray();
				} else { // normal get
					getresult = KeyExplorerUtils.simpleGet(pluginContext.pluginRespirator, furi);
					data = BucketTools.toByteArray(getresult.getData());
				}
			}
		} catch (MalformedURLException e) {
			errors.add("MalformedURL: " + key);
		} catch (LowLevelGetException e) {
			errors.add("Get failed (" + e.code + "): " + e.getMessage());
		} catch (IOException e) {
			Logger.error(this, "500", e);
			errors.add("IO Error: " + e.getMessage());
		} catch (MetadataParseException e) {
			errors.add("Metadata Parse Error: " + e.getMessage());
		} catch (FetchException e) {
			errors.add("Get failed (" + e.mode + "): " + e.getMessage());
		} catch (KeyListenerConstructionException e) {
			Logger.error(this, "Hu?", e);
			errors.add("Internal Error: " + e.getMessage());
		} finally {
			if (getresult != null)
				getresult.free();
		}

		HTMLNode uriBox = createUriBox(pluginContext, ((furi == null) ? null : furi.toString(false, false)), hexWidth, automf, deep, errors);

		if (errors.size() > 0) {
			contentNode.addChild(UIUtils.createErrorBox(pluginContext, errors, extraParams, retryUri, null));
			errors.clear();
		}

		contentNode.addChild(uriBox);

		if (data != null) {
			Metadata md = null;

			if (getresult.isMetaData()) {
				try {
					md = Metadata.construct(data);
				} catch (MetadataParseException e) {
					errors.add("Metadata parse error: " + e.getMessage());
				}
				if (md != null) {
					if (automf && md.isArchiveManifest()) {
						if (md.getArchiveType() == ARCHIVE_TYPE.TAR) {
							throw new RedirectException("/KeyExplorer/Site/?mftype=TARmanifest&key=" + furi + extraParams);
						} else if (md.getArchiveType() == ARCHIVE_TYPE.ZIP) {
							throw new RedirectException("/KeyExplorer/Site/?mftype=ZIPmanifest&key=" + furi + extraParams);
						} else {
							errors.add("Unknown Archive Type: " + md.getArchiveType().name());
						}
					}
					if (automf && md.isSimpleManifest()) {
						throw new RedirectException("/KeyExplorer/Site/?mftype=simplemanifest&key=" + furi + extraParams);
					}
				}
			}

			String title = "Key: " + furi.toString(false, false);
			if (getresult.isMetaData())
				title = title + "\u00a0(MetaData)";
			HTMLNode dataBox2 = pluginContext.pageMaker.getInfobox("#", title, contentNode);

			dataBox2.addChild("%", "<pre lang=\"en\" style=\"font-family: monospace;\">\n");
			dataBox2.addChild("#", hexDump(data, hexWidth));
			dataBox2.addChild("%", "\n</pre>");

			if (getresult.isMetaData()) {
				if (md != null) {
					HTMLNode metaBox = pluginContext.pageMaker.getInfobox("#", "Decomposed metadata", contentNode);

					metaBox.addChild("#", "Document type:\u00a0");
					if (md.isSimpleRedirect()) {
						metaBox.addChild("#", "SimpleRedirect");
					} else if (md.isSimpleManifest()) {
						metaBox.addChild("#", "SimpleManifest");
					} else if (md.isArchiveInternalRedirect()) {
						metaBox.addChild("#", "ArchiveInternalRedirect");
					} else if (md.isArchiveMetadataRedirect()) {
						metaBox.addChild("#", "ArchiveMetadataRedirect");
					} else if (md.isArchiveManifest()) {
						metaBox.addChild("#", "ArchiveManifest");
					} else if (md.isMultiLevelMetadata()) {
						metaBox.addChild("#", "MultiLevelMetadata");
					} else if (md.isSymbolicShortlink()) {
						metaBox.addChild("#", "SymbolicShortlink");
					} else {
						metaBox.addChild("#", "<Unknown document type>");
					}
					metaBox.addChild("%", "<BR />");

					if (md.haveFlags()) {
						metaBox.addChild("#", "Flags:\u00a0");
						boolean isFirst = true;

						if (md.isSplitfile()) {
							metaBox.addChild("#", "SplitFile");
							isFirst = false;
						}
						if (md.isCompressed()) {
							if (isFirst)
								isFirst = false;
							else
								metaBox.addChild("#", "\u00a0");
							metaBox.addChild("#", "Compressed ("+ md.getCompressionCodec().name + ")");
						}
						if (isFirst)
							metaBox.addChild("#", "<No flag set>");
					}
					metaBox.addChild("%", "<BR />");

					if (md.isCompressed()) {
						metaBox.addChild("#", "Decompressed size: " + md.uncompressedDataLength() + " bytes.");
					} else {
						metaBox.addChild("#", "Uncompressed");
					}

					metaBox.addChild("%", "<BR />");

					if (md.isSplitfile()) {
						metaBox.addChild("#", "Splitfile size\u00a0=\u00a0" + md.dataLength() + " bytes.");
						metaBox.addChild("%", "<BR />");
					}

					metaBox.addChild("#", "Options:");
					metaBox.addChild("%", "<BR />");

					if (md.isSimpleManifest()) {
						metaBox.addChild(new HTMLNode("a", "href", "/KeyExplorer/Site/?mftype=simplemanifest&key=" + furi + extraParams, "reopen as manifest"));
						metaBox.addChild("%", "<BR />");
					}
					if (md.isArchiveManifest()) {
						metaBox.addChild(new HTMLNode("a", "href", "/KeyExplorer/Site/?mftype=" + md.getArchiveType().name() + "manifest&key=" + furi + extraParams,
								"reopen as manifest"));
						metaBox.addChild("%", "<BR />");
					}
					if (md.isMultiLevelMetadata()) {
						if (ml)
							metaBox.addChild(new HTMLNode("a", "href", "/KeyExplorer/?key=" + furi + extraParams, "explore multilevel"));
						else
							metaBox.addChild(new HTMLNode("a", "href", "/KeyExplorer/?ml=checked&key=" + furi + extraParams, "explore multilevel"));
						metaBox.addChild("%", "<BR />");
					}

					FreenetURI uri = md.getSingleTarget();
					if (uri != null) {
						String sfrUri = uri.toString(false, false);
						metaBox.addChild("#", sfrUri);
						metaBox.addChild("#", "\u00a0");
						metaBox.addChild(new HTMLNode("a", "href", "/?key=" + sfrUri, "open"));
						metaBox.addChild("#", "\u00a0");
						metaBox.addChild(new HTMLNode("a", "href", "/KeyExplorer/?key=" + sfrUri + extraParams, "explore"));
					} else {
						metaBox.addChild(new HTMLNode("a", "href", "/?key=" + furi, "reopen normal"));
					}
					metaBox.addChild("%", "<BR />");

					if ((uri == null) && md.isSplitfile() ) {
						metaBox.addChild(new HTMLNode("a", "href", "/KeyExplorer/?action=splitdownload&key=" + furi.toString(false, false), "split-download"));
						metaBox.addChild("%", "<BR />");
					}
				}
			}
			if (errors.size() > 0)
				contentNode.addChild(UIUtils.createErrorBox(pluginContext, errors));
		}
		writeHTMLReply(ctx, 200, "OK", pageNode.generate());
	}
	
	private HTMLNode createUriBox(PluginContext pCtx, String uri, int hexWidth, boolean automf, boolean deep, List<String> errors) {
		InfoboxNode box = pCtx.pageMaker.getInfobox("Explore a freenet key");
		HTMLNode browseBox = box.outer;
		HTMLNode browseContent = box.content;

		if (hexWidth < 1 || hexWidth > 1024) {
			errors.add("Hex display columns out of range. (1-1024). Set to 32 (default).");
			hexWidth = 32;
		}
		browseContent.addChild("#", "Display the top level chunk as hexprint or list the content of a manifest");
		HTMLNode browseForm = pCtx.pluginRespirator.addFormChild(browseContent, path(), "uriForm");
		browseForm.addChild("#", "Freenetkey to explore: \u00a0 ");
		if (uri != null)
			browseForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", "key", "70", uri });
		else
			browseForm.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", "key", "70" });
		browseForm.addChild("#", "\u00a0");
		browseForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "debug", "Explore!" });
		browseForm.addChild("%", "<BR />");
		if (automf)
			browseForm.addChild("input", new String[] { "type", "name", "value", "checked" }, new String[] { "checkbox", "automf", "ok", "checked" });
		else
			browseForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "checkbox", "automf", "ok" });
		browseForm.addChild("#", "\u00a0auto open as manifest if possible\u00a0");
		if (deep)
			browseForm.addChild("input", new String[] { "type", "name", "value", "checked" }, new String[] { "checkbox", "deep", "ok", "checked" });
		else
			browseForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "checkbox", "deep", "ok" });
		browseForm.addChild("#", "\u00a0parse manifest recursive (include multilevel metadata/subcontainers)\u00a0\u00a0");
		browseForm.addChild("#", "Hex display columns:\u00a0");
		browseForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", "hexWidth", "3", Integer.toString(hexWidth) });
		return browseBox;
	}

	private String hexDump(byte[] data, int width) {
		StringBuilder sb = new StringBuilder();
		Formatter formatter = new Formatter(sb, Locale.US);

		try {
			for (int offset = 0; offset < data.length; offset += width) {
				formatter.format("%07X:", offset);

				for (int i = 0; i < width; i++) {
					if (i % 2 == 0)
						formatter.out().append(' ');
					if (i + offset >= data.length) {
						formatter.out().append("  ");
						continue;
					}
					formatter.format("%02X", data[i + offset]);
				}

				formatter.out().append("  ");
				for (int i = 0; i < width; i++) {
					if (i + offset >= data.length)
						break;

					if (data[i + offset] >= 32 && data[i + offset] < 127) {
						formatter.out().append((char) data[i + offset]);
					} else
						formatter.out().append('.');

				}
				formatter.out().append('\n');
			}
		} catch (IOException e) {
			// impossible
		}

		formatter.flush();
		return sb.toString();
	}

}
