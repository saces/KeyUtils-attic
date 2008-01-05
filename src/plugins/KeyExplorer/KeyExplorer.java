package plugins.KeyExplorer;

import java.io.IOException;
import java.net.MalformedURLException;

import freenet.client.Metadata;
import freenet.client.MetadataParseException;
import freenet.clients.http.PageMaker;
import freenet.config.Config;
import freenet.config.SubConfig;
import freenet.keys.BaseClientKey;
import freenet.keys.ClientKey;
import freenet.keys.ClientKeyBlock;
import freenet.keys.FreenetURI;
import freenet.keys.KeyDecodeException;
import freenet.node.LowLevelGetException;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLNode;
import freenet.support.HexUtil;
import freenet.support.api.HTTPRequest;
import freenet.support.io.ArrayBucket;
import freenet.support.io.ArrayBucketFactory;
import freenet.support.io.BucketTools;

public class KeyExplorer implements FredPlugin, FredPluginHTTP, FredPluginThreadless {

	private PluginRespirator m_pr;
	private PageMaker m_pm;

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

	public void runPlugin(PluginRespirator pr) {
		Config nc = pr.getNode().config;
		SubConfig fc = nc.get("fproxy");
		String cssName = fc.getString("css");

		m_pm = new PageMaker(cssName);
		m_pr = pr;
	}

	public void terminate() {
	}

	private String makeMainPage() {
		return makeMainPage(null);
	}

	private String makeMainPage(String key) {
		HTMLNode pageNode = m_pm.getPageNode("KeyExplorer", null);
		HTMLNode contentNode = m_pm.getContentNode(pageNode);

		String error = null;
		byte[] data = null;
		ClientKeyBlock ckb = null;

		try {
			if (key != null && (key.trim().length() > 0)) {
				FreenetURI furi = new FreenetURI(key);
				ClientKey ck = (ClientKey) BaseClientKey.getBaseKey(furi);
				ckb = m_pr.getNode().clientCore.realGetKey(ck, true, true, false);
				ArrayBucket a = (ArrayBucket) ckb.decode(new ArrayBucketFactory(), 32 * 1024, false);
				data = BucketTools.toByteArray(a);
			}
		} catch (MalformedURLException e) {
			error = "MalformedURL";
		} catch (LowLevelGetException e) {
			error = "get failed";
		} catch (KeyDecodeException e) {
			error = "decode error";
		} catch (IOException e) {
			error = "io error";
		}

		if (error != null) {
			contentNode.addChild(createErrorBox(error));
		}

		contentNode.addChild(createUriBox());

		if (data != null) {
			String title = "Key: " + key;
			if (ckb.isMetadata())
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
					//int j = data[i];
					//sb.append((char) j);
					asciibuf[offset] =(char) data[i];
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
				int n = (15-offset)*3;
				for (int m = 0; m < n; m++) 
					sb.append(' ');
				sb.append(' ');
				sb.append(asciibuf);
			}

			dataBox2.addChild("#", sb.toString());
			dataBox2.addChild("%", "\n</PRE>");

			contentNode.addChild(dataBox2);

			error = null;

			if (ckb.isMetadata()) {

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
							metaBox.addChild(new HTMLNode("a", "href", "/?key="+sfrUri, "open"));
							metaBox.addChild("#", "\u00a0");
							metaBox.addChild(new HTMLNode("a", "href", "/plugins/plugins.KeyExplorer.KeyExplorer/?key="+sfrUri, "explore"));
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
