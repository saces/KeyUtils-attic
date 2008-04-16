package plugins.KeyExplorer;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;

import freenet.client.Metadata;
import freenet.client.MetadataParseException;
import freenet.clients.http.PageMaker;
import freenet.config.Config;
import freenet.config.SubConfig;
import freenet.keys.BaseClientKey;
import freenet.keys.ClientKey;
import freenet.keys.FreenetURI;
import freenet.node.LowLevelGetException;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginFCP;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginReplySender;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLNode;
import freenet.support.HexUtil;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;
import freenet.support.io.BucketTools;

public class KeyExplorer implements FredPlugin, FredPluginHTTP, FredPluginFCP, FredPluginThreadless {

	private PluginRespirator m_pr;
	private PageMaker m_pm;

	private HashMap getters = new HashMap();

	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
		String uri = request.getParam("key");
		return makeMainPage(uri);
	}

	public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {
		String uri = request.getPartAsString("key", 1024);
		return makeMainPage(uri);
	}

	public String handleHTTPPut(HTTPRequest request) throws PluginHTTPException {
		return makeMainPage();
	}

	public void handle(PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) {

		if (params == null) {
			sendError(replysender, 0, "Got void message");
			return;
		}
		
		String command = params.get("Command");

		if (command == null || command.trim().length() == 0) {
			sendError(replysender, 1, "Invalid Command name");
			return;
		}

		if ("Ping".equals(command)) {
			SimpleFieldSet sfs = new SimpleFieldSet(true);
			sfs.put("Pong", System.currentTimeMillis());
			replysender.send(sfs);
			return;
		} else if ("Get".equals(command)) {

			final String identifier = params.get("Identifier");
			if (identifier == null || identifier.trim().length() == 0) {
				sendError(replysender, 3, "Missing identifier");
				return;
			}

			final String uri = params.get("URI");
			if (uri == null || uri.trim().length() == 0) {
				sendError(replysender, 4, "missing freenet uri");
				return;
			}

			try {
				FreenetURI furi = new FreenetURI(uri);
				GetResult getresult = simpleGet(furi);

				SimpleFieldSet sfs = new SimpleFieldSet(true);
				sfs.putSingle("Identifier", identifier);
				sfs.put("IsMetadata", getresult.isMetaData);
				sfs.putSingle("Status", "DataFound");
				replysender.send(sfs, getresult.data);
				return;

			} catch (MalformedURLException e) {
				sendError(replysender, 5, "malformed freenet uri");
				return;
			} catch (LowLevelGetException e) {
				sendError(replysender, 6, "Get failed: " + e.toString());
				return;
			}
		} else
			replysender.send(params);
	}

	public void runPlugin(PluginRespirator pr) {
		Config nc = pr.getNode().config;
		SubConfig fc = nc.get("fproxy");
		String cssName = fc.getString("css");

		m_pm = new PageMaker(cssName);
		m_pr = pr;
	}

	public void terminate() {
	}

	private class GetResult {
		final Bucket data;
		final boolean isMetaData;

		GetResult(Bucket data2, boolean isMetaData2) {
			data = data2;
			isMetaData = isMetaData2;
		}
	}

	private GetResult simpleGet(FreenetURI uri) throws MalformedURLException, LowLevelGetException {
		ClientKey ck = (ClientKey) BaseClientKey.getBaseKey(uri);
		VerySimpleGetter vsg = new VerySimpleGetter((short) 1, m_pr.getNode().clientCore.requestStarters.chkFetchScheduler, m_pr
				.getNode().clientCore.requestStarters.sskFetchScheduler, uri, null);
		VerySimpleGet vs = new VerySimpleGet(ck, 3, m_pr.getHLSimpleClient().getFetchContext(), vsg);
		vs.schedule();
		return new GetResult(vs.waitForCompletion(), vs.isMetadata());
	}

	private void sendError(PluginReplySender replysender, int code, String description) {
		SimpleFieldSet sfs = new SimpleFieldSet(true);
		sfs.putOverwrite("Status", "Error");
		sfs.put("Code", code);
		sfs.putOverwrite("Description", description);
		replysender.send(sfs);
	}

	private String makeMainPage() {
		return makeMainPage(null);
	}

	private String makeMainPage(String key) {
		HTMLNode pageNode = m_pm.getPageNode("KeyExplorer", null);
		HTMLNode contentNode = m_pm.getContentNode(pageNode);

		String error = null;
		byte[] data = null;
		GetResult getresult = null;

		try {
			if (key != null && (key.trim().length() > 0)) {
				FreenetURI furi = new FreenetURI(key);
				getresult = simpleGet(furi);
				data = BucketTools.toByteArray(getresult.data);
			}
		} catch (MalformedURLException e) {
			error = "MalformedURL";
		} catch (LowLevelGetException e) {
			error = "get failed";
		} catch (IOException e) {
			error = "io error";
		}

		if (error != null) {
			contentNode.addChild(createErrorBox(error));
		}

		contentNode.addChild(createUriBox());

		if (data != null) {
			String title = "Key: " + key;
			if (getresult.isMetaData)
				title = title + "\u00a0(MetaData)";
			HTMLNode dataBox2 = m_pm.getInfobox(title);

			char[] asciibuf = new char[16];

			for (int j = 0; j < 16; j++)
				asciibuf[j] = ' ';

			dataBox2.addChild("%", "<PRE>\n");
			StringBuffer sb = new StringBuffer();
			int offset = 0;

			for (int i = 0; i < data.length; i++) {
				offset = (i) % 16;
				HexUtil.bytesToHexAppend(data, i, 1, sb);
				sb.append(' ');
				if ((data[i] > 31) && (data[i] < 127)) {
					// int j = data[i];
					// sb.append((char) j);
					asciibuf[offset] = (char) data[i];
				}

				if ((i > 1) && ((i + 1) % 16 == 0)) {
					sb.append(' ');
					sb.append(asciibuf);
					sb.append('\n');
					for (int k = 0; k < 16; k++)
						asciibuf[k] = ' ';
				}
			}
			if (offset > 0) {
				int n = (15 - offset) * 3;
				for (int m = 0; m < n; m++)
					sb.append(' ');
				sb.append(' ');
				sb.append(asciibuf);
			}

			dataBox2.addChild("#", sb.toString());
			dataBox2.addChild("%", "\n</PRE>");

			contentNode.addChild(dataBox2);

			error = null;

			if (getresult.isMetaData) {

				Metadata md = null;
				try {
					md = Metadata.construct(data);
				} catch (MetadataParseException e) {
					error = "Metadata parse error";
				}

				if (error != null)
					contentNode.addChild(createErrorBox(error));

				if (md != null) {
					HTMLNode metaBox = m_pm.getInfobox("Decomposed metadata");

					metaBox.addChild("#", "Document type:");
					metaBox.addChild("%", "<BR />");

					if (md.isSimpleManifest()) {
						metaBox.addChild("#", "Document type: SimpleManifest");
						metaBox.addChild("%", "<BR />");
					}

					if (md.isSplitfile()) {
						metaBox.addChild("#", "Document type: Splitfile");
						metaBox.addChild("%", "<BR />");
					}

					if (md.isSimpleSplitfile()) {
						metaBox.addChild("#", "Document type: SimpleSplitfile");
						metaBox.addChild("%", "<BR />");
					}

					if (md.isSingleFileRedirect()) {
						metaBox.addChild("#", "Document type: SingleFileRedirect");
						metaBox.addChild("%", "<BR />");
						FreenetURI uri = md.getSingleTarget();
						// TODO saces need a lesson in manifest structure?
						if (uri != null) {
							String sfrUri = md.getSingleTarget().toString();
							metaBox.addChild("#", sfrUri);
							metaBox.addChild("#", "\u00a0");
							metaBox.addChild(new HTMLNode("a", "href", "/?key=" + sfrUri, "open"));
							metaBox.addChild("#", "\u00a0");
							metaBox.addChild(new HTMLNode("a", "href", "/plugins/plugins.KeyExplorer.KeyExplorer/?key=" + sfrUri,
									"explore"));
							metaBox.addChild("%", "<BR />");
						}
					}

					if (md.isArchiveInternalRedirect()) {
						metaBox.addChild("#", "Document type: ArchiveInternalRedirect");
						metaBox.addChild("%", "<BR />");
					}

					if (md.isArchiveManifest()) {
						metaBox.addChild("#", "Document type: ArchiveManifest");
						metaBox.addChild("%", "<BR />");
					}

					if (!md.isCompressed()) {
						metaBox.addChild("#", "Uncompressed");
					} else {
						metaBox.addChild("#", "Compressed (codec " + md.getCompressionCodec() + ")");
						metaBox.addChild("%", "<BR />");
						metaBox.addChild("#", "Decompressed size: " + md.uncompressedDataLength() + " bytes");
					}
					metaBox.addChild("%", "<BR />");

					metaBox.addChild("#", "Data size\u00a0=\u00a0" + md.dataLength());
					metaBox.addChild("%", "<BR />");

					if (md.isResolved()) {
						metaBox.addChild("#", "Resolved URI:\u00a0=\u00a0" + md.getResolvedURI());
						metaBox.addChild("%", "<BR />");
					}
					contentNode.addChild(metaBox);
				}

			}

		}

		return pageNode.generate();
	}

	private HTMLNode createUriBox() {
		HTMLNode browseBox = m_pm.getInfobox("Explore a freenet key");
		HTMLNode browseContent = m_pm.getContentNode(browseBox);
		browseContent.addChild("#", "Display the top level chunk as hexprint");
		HTMLNode browseForm = m_pr.addFormChild(browseContent, "/plugins/plugins.KeyExplorer.KeyExplorer/", "uriForm");
		browseForm.addChild("#", "Freenetkey to explore: \u00a0 ");
		browseForm.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", "key", "70" });
		browseForm.addChild("#", "\u00a0");
		browseForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "debug", "Explore!" });
		return browseBox;
	}

	private HTMLNode createErrorBox(String errmsg) {
		HTMLNode errorBox = m_pm.getInfobox("infobox-alert", "ERROR");
		errorBox.addChild("#", errmsg);
		return errorBox;
	}
}
