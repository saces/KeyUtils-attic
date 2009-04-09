/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyExplorer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;

import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.FetchWaiter;
import freenet.client.HighLevelSimpleClient;
import freenet.client.Metadata;
import freenet.client.MetadataParseException;
import freenet.clients.http.PageMaker;
import freenet.keys.BaseClientKey;
import freenet.keys.ClientKey;
import freenet.keys.FreenetURI;
import freenet.keys.USK;
import freenet.l10n.L10n.LANGUAGE;
import freenet.node.LowLevelGetException;
import freenet.node.RequestClient;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginFCP;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginL10n;
import freenet.pluginmanager.FredPluginRealVersioned;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;
import freenet.support.io.BucketTools;

/**
 * @author saces
 *
 */
public class KeyExplorer implements FredPlugin, FredPluginHTTP, FredPluginL10n, FredPluginFCP, FredPluginThreadless, FredPluginVersioned, FredPluginRealVersioned {

	private PluginRespirator m_pr;
	private PageMaker m_pm;

	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
		String uri = request.getParam("key");
		String type = request.getParam("type");
		if ("ZIPmanifest".equals(type)) {
			return makeManifestPage(uri, true, false);
		}
		if ("TARmanifest".equals(type)) {
			return makeManifestPage(uri, false, true);
		}
		if ("simplemanifest".equals(type)) {
			return makeManifestPage(uri, false, false);
		}
		return makeMainPage(uri);
	}

	public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {
		String uri = request.getPartAsString("key", 1024);
		return makeMainPage(uri);
	}

	public void handle(PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) {
		try {
			realHandle(replysender, params, data, accesstype);
		} catch (PluginNotFoundException pnfe) {
			Logger.error(this, "Connction to request sender lost.", pnfe);
		}
	}

	private void realHandle(PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) throws PluginNotFoundException {
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
		} 
		
		if ("Get".equals(command)) {

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
				GetResult getResult = simpleGet(m_pr, furi);

				SimpleFieldSet sfs = new SimpleFieldSet(true);
				sfs.putSingle("Identifier", identifier);
				sfs.put("IsMetadata", getResult.isMetaData());
				sfs.putSingle("Status", "DataFound");
				replysender.send(sfs, getResult.getData());
				return;

			} catch (MalformedURLException e) {
				sendError(replysender, 5, "Malformed freenet uri: "+e.getMessage());
				return;
			} catch (LowLevelGetException e) {
				sendError(replysender, 6, "Get failed: " + e.toString());
				return;
			}
		} else
			replysender.send(params);
	}

	public void runPlugin(PluginRespirator pr) {
		m_pr = pr;
		m_pm = pr.getPageMaker();
	}

	public void terminate() {
	}

	private static GetResult simpleGet(PluginRespirator pr, FreenetURI uri) throws MalformedURLException, LowLevelGetException {
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

	private void sendError(PluginReplySender replysender, int code, String description) throws PluginNotFoundException {
		SimpleFieldSet sfs = new SimpleFieldSet(true);
		sfs.putOverwrite("Status", "Error");
		sfs.put("Code", code);
		sfs.putOverwrite("Description", description);
		replysender.send(sfs);
	}

	private String makeMainPage(String key) {
		HTMLNode pageNode = m_pm.getPageNode("KeyExplorer", null);
		HTMLNode contentNode = m_pm.getContentNode(pageNode);

		String error = null;
		byte[] data = null;
		GetResult getresult = null;
		
		FreenetURI furi = null;

		try {
			if (key != null && (key.trim().length() > 0)) {
				furi = new FreenetURI(key);
				if ("USK".equals(furi.getKeyType())) {
					USK tempUSK = USK.create(furi);
					ClientKey tempKey = tempUSK.getSSK();
					furi = tempKey.getURI();
				} 
				getresult = simpleGet(m_pr, furi);
				data = BucketTools.toByteArray(getresult.getData());
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
			String title = "Key: " + furi.toString();
			if (getresult.isMetaData())
				title = title + "\u00a0(MetaData)";
			HTMLNode dataBox2 = m_pm.getInfobox(title);

			dataBox2.addChild("%", "<pre lang=\"en\" style=\"font-family: monospace;\">\n");
			dataBox2.addChild("#", hexDump(data));
			dataBox2.addChild("%", "\n</pre>");

			contentNode.addChild(dataBox2);

			error = null;

			if (getresult.isMetaData()) {

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
					
					if (md.isMultiLevelMetadata()) {
						metaBox.addChild("#", "Document type: MultiLevelMetadata");		
						metaBox.addChild("%", "<BR />");				
					}
					if (md.isSimpleManifest()) {
						metaBox.addChild("#", "Document type: SimpleManifest");
						metaBox.addChild("#", "\u00a0");
						metaBox.addChild(new HTMLNode("a", "href", "/plugins/plugins.KeyExplorer.KeyExplorer/?type=simplemanifest&key=" + furi,
						"reopen as manifest"));
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
						metaBox.addChild("#", "\u00a0");
						FreenetURI uri = md.getSingleTarget();
						if (uri != null) {
							String sfrUri = md.getSingleTarget().toString();
							metaBox.addChild("#", sfrUri);
							metaBox.addChild("#", "\u00a0");
							metaBox.addChild(new HTMLNode("a", "href", "/?key=" + sfrUri, "open"));
							metaBox.addChild("#", "\u00a0");
							metaBox.addChild(new HTMLNode("a", "href", "/plugins/plugins.KeyExplorer.KeyExplorer/?key=" + sfrUri,
									"explore"));
						} else {
							metaBox.addChild(new HTMLNode("a", "href", "/?key=" + furi, "reopen normal"));							
						}
						metaBox.addChild("%", "<BR />");
					}

					if (md.isArchiveInternalRedirect()) {
						metaBox.addChild("#", "Document type: ArchiveInternalRedirect");
						metaBox.addChild("%", "<BR />");
					}

					if (md.isArchiveManifest()) {
						metaBox.addChild("#", "Document type: ArchiveManifest");
						metaBox.addChild("#", "\u00a0");
						metaBox.addChild(new HTMLNode("a", "href", "/plugins/plugins.KeyExplorer.KeyExplorer/?type="+md.getArchiveType().name()+"manifest&key=" + furi,
						"reopen as manifest"));
						metaBox.addChild("%", "<BR />");
					}

					if (!md.isCompressed()) {
						metaBox.addChild("#", "Uncompressed");
					} else {
						metaBox.addChild("#", "Compressed (codec " + md.getCompressionCodec().name + ")");
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

	private String hexDump(byte[] data) {
		StringBuilder sb = new StringBuilder();
		Formatter formatter = new Formatter(sb, Locale.US);

		try {
			for (int offset = 0; offset < data.length; offset += 16) {
				formatter.format("%07x:",  offset);
				
				for (int i = 0; i < 16; i++) {
					if (i % 2 == 0)
						formatter.out().append(' ');
					if (i + offset >= data.length) {
						formatter.out().append("  ");
						continue;
					}
					formatter.format("%02x", data[i + offset]);
				}

				formatter.out().append("  ");
				for (int i = 0; i < 16; i++) {
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

	private String makeManifestPage(String key, boolean zip, boolean tar) {
		HTMLNode pageNode = m_pm.getPageNode("KeyExplorer", null);
		HTMLNode contentNode = m_pm.getContentNode(pageNode);

		String error = null;
		Metadata metadata = null;
		
		FreenetURI furi = null;

		try {
			furi = new FreenetURI(key);
			if ("USK".equals(furi.getKeyType())) {
				USK tempUSK = USK.create(furi);
				ClientKey tempKey = tempUSK.getSSK();
				furi = tempKey.getURI();
			} 
	
			if (zip)
				metadata = zipManifestGet(furi);
			else if (tar)
				metadata = tarManifestGet(furi);
			else
				metadata = simpleManifestGet(furi);
		} catch (MalformedURLException e) {
			error = "MalformedURL";
		} catch (FetchException e) {
			error = "get failed ("+e.mode+"): "+e.getMessage();
		} catch (IOException e) {
			error = "io error";
		} catch (MetadataParseException e) {
			error = "MetadataParseException";
		} catch (LowLevelGetException e) {
			error = "get failed ("+e.code+"): "+e.getMessage();
		}

		if (error != null) {
			contentNode.addChild(createErrorBox(error));
			contentNode.addChild(createUriBox());
			return pageNode.generate();
		}

		contentNode.addChild(createUriBox());
		String title = "Key: " + furi.toString()  + "\u00a0(Manifest)";
		HTMLNode listBox = m_pm.getInfobox(title);
		contentNode.addChild(listBox);
		
		HashMap<String, Metadata> docs = metadata.getDocuments();

		parseMetadata(listBox, docs, "", furi.toString());

		return pageNode.generate();

	}
	
	private void parseMetadata(HTMLNode htmlnode, HashMap<String, Metadata> docs, String prefix, String furi) {
		Set<String> s = docs.keySet();
		Iterator<String> i = s.iterator();
		while (i.hasNext()) {
			String name = i.next();
			Metadata md = docs.get(name);
			String fname = prefix + name;
			
			if (md.isArchiveInternalRedirect())
				htmlnode.addChild("#", "(container)\u00a0");
			if (md.isArchiveManifest())
				htmlnode.addChild("#", "(archive)\u00a0");
			if (md.isCompressed())
				htmlnode.addChild("#", "(compress)\u00a0");
			if (md.isMultiLevelMetadata())
				htmlnode.addChild("#", "(multilevel)\u00a0");
			if (md.isResolved())
				htmlnode.addChild("#", "(resolved)\u00a0");
			if (md.isSimpleManifest())
				htmlnode.addChild("#", "(simple-manifest)\u00a0");
			if (md.isSimpleSplitfile())
				htmlnode.addChild("#", "(simple-splitf)\u00a0");
			else if (md.isSplitfile())
				htmlnode.addChild("#", "(splitf)\u00a0");
			if (md.isSingleFileRedirect())
				htmlnode.addChild("#", "(extern)\u00a0");
			
			
			if (md.isArchiveInternalRedirect()) {
				htmlnode.addChild(new HTMLNode("a", "href", "/?key=" + furi + "/" + fname, fname));
				htmlnode.addChild("%", "<BR />");
	        } else if (md.isSingleFileRedirect()) {
	        	htmlnode.addChild(new HTMLNode("a", "href", "/?key=" + furi + "/" + fname, fname));
	        	htmlnode.addChild("#", "\u00a0");
				htmlnode.addChild(new HTMLNode("a", "href", "/plugins/plugins.KeyExplorer.KeyExplorer/?key=" + md.getSingleTarget().toString(), "explore"));
				htmlnode.addChild("%", "<BR />");
	        } else if (md.isSplitfile()) {
	        	htmlnode.addChild(new HTMLNode("a", "href", "/?key=" + furi + "/" + fname, fname));
				htmlnode.addChild("%", "<BR />");
	        } else {
	        	htmlnode.addChild("#", fname);
				htmlnode.addChild("%", "<BR />");
				parseMetadata(htmlnode, md.getDocuments(), fname + "/", furi);
	        }
       }
		
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

	public String getVersion() {
		return "0.3 r"+ Version.svnRevision;
	}

	public long getRealVersion() {
		return Version.version;
	}
	
	private Metadata simpleManifestGet(FreenetURI uri) throws MetadataParseException, LowLevelGetException, IOException {
		GetResult res = simpleGet(m_pr, uri);
		if (!res.isMetaData()) {
			throw new MetadataParseException("uri did not point to metadata " + uri);
		}
		return Metadata.construct(res.getData());
	}
	
	private Metadata zipManifestGet(FreenetURI uri) throws FetchException, MetadataParseException, IOException{
		HighLevelSimpleClient hlsc = m_pr.getHLSimpleClient();
		FetchContext fctx = hlsc.getFetchContext();
		fctx.returnZIPManifests = true;
		//fctx.r
		FetchWaiter fw = new FetchWaiter();
		hlsc.fetch(uri, -1, (RequestClient) hlsc, fw, fctx);
		FetchResult fr = fw.waitForCompletion();
		ZipInputStream zis = new ZipInputStream(fr.asBucket().getInputStream());
		ZipEntry entry;
		ByteArrayOutputStream bos;
		while(true) {
			entry = zis.getNextEntry();
			if(entry == null) break;
			if(entry.isDirectory()) continue;
			String name = entry.getName();
			if(".metadata".equals(name)) {
				byte[] buf = new byte[32768];
				bos = new ByteArrayOutputStream();
				// Read the element
				int readBytes;
				while((readBytes = zis.read(buf)) > 0) {
					bos.write(buf, 0, readBytes);
				}
				bos.close();
				return Metadata.construct(bos.toByteArray());
			}
		}
		throw new FetchException(200, "impossible? no metadata in archive " + uri);
	}
	
	private Metadata tarManifestGet(FreenetURI uri) throws FetchException, MetadataParseException, IOException{
		HighLevelSimpleClient hlsc = m_pr.getHLSimpleClient();
		FetchContext fctx = hlsc.getFetchContext();
		fctx.returnZIPManifests = true;
		//fctx.r
		FetchWaiter fw = new FetchWaiter();
		hlsc.fetch(uri, -1, (RequestClient) hlsc, fw, fctx);
		FetchResult fr = fw.waitForCompletion();
		TarInputStream zis = new TarInputStream(fr.asBucket().getInputStream());
		TarEntry entry;
		ByteArrayOutputStream bos;
		while(true) {
			entry = zis.getNextEntry();
			if(entry == null) break;
			if(entry.isDirectory()) continue;
			String name = entry.getName();
			if(".metadata".equals(name)) {
				byte[] buf = new byte[32768];
				bos = new ByteArrayOutputStream();
				// Read the element
				int readBytes;
				while((readBytes = zis.read(buf)) > 0) {
					bos.write(buf, 0, readBytes);
				}
				bos.close();
				return Metadata.construct(bos.toByteArray());
			}
		}
		throw new FetchException(200, "impossible? no metadata in archive " + uri);
	}


	public String getString(String key) {
		// TODO Auto-generated method stub
		return key;
	}

	public void setLanguage(LANGUAGE selectedLanguage) {
		// TODO Auto-generated method stub
	}
}
