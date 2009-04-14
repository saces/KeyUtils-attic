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
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;

import com.db4o.ObjectContainer;

import freenet.client.ArchiveContext;
import freenet.client.ClientMetadata;
import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.FetchWaiter;
import freenet.client.HighLevelSimpleClient;
import freenet.client.Metadata;
import freenet.client.MetadataParseException;
import freenet.client.ArchiveManager.ARCHIVE_TYPE;
import freenet.client.async.ClientContext;
import freenet.client.async.ClientGetState;
import freenet.client.async.GetCompletionCallback;
import freenet.client.async.KeyListenerConstructionException;
import freenet.client.async.SplitFileFetcher;
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
import freenet.pluginmanager.RedirectPluginHTTPException;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;
import freenet.support.compress.Compressor.COMPRESSOR_TYPE;
import freenet.support.io.BucketTools;

/**
 * @author saces
 * 
 */
public class KeyExplorer implements FredPlugin, FredPluginHTTP, FredPluginL10n, FredPluginFCP, FredPluginThreadless, FredPluginVersioned, FredPluginRealVersioned {

	private PluginRespirator m_pr;
	private PageMaker m_pm;

	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
		String uri;
		String type;
		boolean automf;
		boolean deep;
		int hexWidth;

		if (request.isParameterSet("key")) {
			uri = request.getParam("key");
			type = request.getParam("mftype");
			automf = request.getParam("automf").length() > 0;
			deep = request.getParam("deep").length() > 0;
			hexWidth = request.getIntParam("hexWidth", 32);
		} else {
			uri = null;
			type = null;
			automf = true;
			deep = true;
			hexWidth = 32;
		}

		List<String> errors = new LinkedList<String>();
		if (hexWidth < 1 || hexWidth > 1024) {
			errors.add("Hex display columns out of range. (1-1024). Set to 32 (default).");
			hexWidth = 32;
		}
		if ("ZIPmanifest".equals(type)) {
			return makeManifestPage(errors, uri, true, false, hexWidth, automf, deep);
		}
		if ("TARmanifest".equals(type)) {
			return makeManifestPage(errors, uri, false, true, hexWidth, automf, deep);
		}
		if ("simplemanifest".equals(type)) {
			return makeManifestPage(errors, uri, false, false, hexWidth, automf, deep);
		}
		return makeMainPage(errors, uri, hexWidth, automf, deep);
	}

	public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {
		String uri = request.getPartAsString("key", 1024);
		int hexWidth = request.getIntPart("hexWidth", 32);
		boolean automf = request.getPartAsString("automf", 128).length() > 0;
		boolean deep = request.getPartAsString("deep", 128).length() > 0;
		List<String> errors = new LinkedList<String>();
		if (hexWidth < 1 || hexWidth > 1024) {
			errors.add("Hex display columns out of range. (1-1024). Set to 32 (default).");
			hexWidth = 32;
		}
		return makeMainPage(errors, uri, hexWidth, automf, deep);
	}

	public void handle(PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) {
		try {
			realHandle(replysender, params, data, accesstype);
		} catch (PluginNotFoundException pnfe) {
			Logger.error(this, "Connction to request sender lost.", pnfe);
		}
	}

	/**
	 * @param accesstype  
	 */
	public void realHandle(PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) throws PluginNotFoundException {
			if (params == null) {
				sendError(replysender, 0, "Got void message");
				return;
			}
			
			if (data != null) {
				sendError(replysender, 0, "Got a diatribe piece of writing. Data not allowed!");
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
					FreenetURI furi = sanitizeURI(null, uri);
					GetResult getResult = simpleGet(m_pr, furi);
					SimpleFieldSet sfs = new SimpleFieldSet(true);
					sfs.putSingle("Identifier", identifier);
					sfs.put("IsMetadata", getResult.isMetaData());
					sfs.putSingle("Status", "DataFound");
					replysender.send(sfs, getResult.getData());
					return;
				} catch (MalformedURLException e) {
					sendError(replysender, 5, "Malformed freenet uri: " + e.getMessage());
					return;
				} catch (LowLevelGetException e) {
					sendError(replysender, 6, "Get failed: " + e.toString());
					return;
				}
			}

			if ("ListSiteManifest".equals(command)) {

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
					sendError(replysender, 5, "Malformed freenet uri: " + e.getMessage());
					return;
				} catch (LowLevelGetException e) {
					sendError(replysender, 6, "Get failed: " + e.toString());
					return;
				}
			}
			sendError(replysender, 1, "Unknown command: " + command);
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

	private FetchResult splitGet(Metadata metadata) throws MalformedURLException, LowLevelGetException, FetchException, MetadataParseException,
			KeyListenerConstructionException {

		final FetchWaiter fw = new FetchWaiter();

		GetCompletionCallback cb = new GetCompletionCallback() {

			public void onBlockSetFinished(ClientGetState state, ObjectContainer container, ClientContext context) {
				// TODO Auto-generated method stub
			}

			public void onExpectedMIME(String mime, ObjectContainer container) {
				// TODO Auto-generated method stub
			}

			public void onExpectedSize(long size, ObjectContainer container) {
				// TODO Auto-generated method stub
			}

			public void onFailure(FetchException e, ClientGetState state, ObjectContainer container, ClientContext context) {
				// TODO Auto-generated method stub
				fw.onFailure(e, null, container);
			}

			public void onFinalizedMetadata(ObjectContainer container) {
				// TODO Auto-generated method stub
			}

			public void onSuccess(FetchResult result, ClientGetState state, ObjectContainer container, ClientContext context) {
				// meta = Metadata.construct(result.asBucket());
				// System.out.println("HEHEHE!!!YEAH!!!");
				fw.onSuccess(result, null, container);
				// fresult = result;
			}

			public void onTransition(ClientGetState oldState, ClientGetState newState, ObjectContainer container) {
				// TODO Auto-generated method stub

			}
		};

		LinkedList<COMPRESSOR_TYPE> decompressors = new LinkedList<COMPRESSOR_TYPE>();
		FetchContext ctx = m_pr.getHLSimpleClient().getFetchContext();
		boolean deleteFetchContext = false;
		ClientMetadata clientMetadata = null;
		ArchiveContext actx = null;
		int recursionLevel = 0;
		Bucket returnBucket = null;
		long token = 0;
		if (metadata.isCompressed()) {
			COMPRESSOR_TYPE codec = metadata.getCompressionCodec();
			decompressors.add(codec);
		}
		VerySimpleGetter vsg = new VerySimpleGetter((short) 1, null, (RequestClient) m_pr.getHLSimpleClient());
		SplitFileFetcher sf = new SplitFileFetcher(metadata, cb, vsg, ctx, deleteFetchContext, decompressors, clientMetadata, actx, recursionLevel, returnBucket, token,
				null, m_pr.getNode().clientCore.clientContext);

		// VerySimpleGetter vsg = new VerySimpleGetter((short) 1, uri,
		// (RequestClient) m_pr.getHLSimpleClient());
		// VerySimpleGet vs = new VerySimpleGet(ck, 0,
		// m_pr.getHLSimpleClient().getFetchContext(), vsg);
		sf.schedule(null, m_pr.getNode().clientCore.clientContext);
		// fw.waitForCompletion();
		return fw.waitForCompletion();
	}

	private void sendError(PluginReplySender replysender, int code, String description) throws PluginNotFoundException {
		SimpleFieldSet sfs = new SimpleFieldSet(true);
		sfs.putOverwrite("Status", "Error");
		sfs.put("Code", code);
		sfs.putOverwrite("Description", description);
		replysender.send(sfs);
	}

	private String makeMainPage(List<String> errors, String key, int hexWidth, boolean automf, boolean deep) throws PluginHTTPException {
		HTMLNode pageNode = m_pm.getPageNode("KeyExplorer", null);
		HTMLNode contentNode = m_pm.getContentNode(pageNode);

		byte[] data = null;
		GetResult getresult = null;
		String extraParams = "&hexwidth=" + hexWidth;
		if (automf) {
			extraParams += "&automf=checked";
		}
		if (deep) {
			extraParams += "&deep=checked";
		}
		FreenetURI furi = null;
		FreenetURI retryUri = null;

		try {
			if (key != null && (key.trim().length() > 0)) {
				furi = sanitizeURI(errors, key);
				retryUri = furi;
				getresult = simpleGet(m_pr, furi);
				data = BucketTools.toByteArray(getresult.getData());
			}
		} catch (MalformedURLException e) {
			errors.add("MalformedURL: " + key);
		} catch (LowLevelGetException e) {
			errors.add("Get failed (" + e.code + "): " + e.getMessage());
		} catch (IOException e) {
			errors.add("IO Error: " + e.getMessage());
		} finally {
			if (getresult != null)
				getresult.free();
		}

		HTMLNode uriBox = createUriBox(((furi == null) ? null : furi.toString(false, false)), hexWidth, automf, deep, errors);

		if (errors.size() > 0) {
			contentNode.addChild(createErrorBox(errors, retryUri, null));
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
							throw new RedirectPluginHTTPException("Reopen as TAR manifest", "/plugins/plugins.KeyExplorer.KeyExplorer/?mftype=TARmanifest&key=" + furi
									+ extraParams);
						} else if (md.getArchiveType() == ARCHIVE_TYPE.ZIP) {
							throw new RedirectPluginHTTPException("Reopen as ZIP manifest", "/plugins/plugins.KeyExplorer.KeyExplorer/?mftype=ZIPmanifest&key=" + furi
									+ extraParams);
						} else {
							errors.add("Unknown Archive Type: " + md.getArchiveType().name());
						}
					}
					if (automf && md.isSimpleManifest()) {
						throw new RedirectPluginHTTPException("Reopen as simple manifest", "/plugins/plugins.KeyExplorer.KeyExplorer/?mftype=simplemanifest&key=" + furi
								+ extraParams);
					}
				}
			}

			String title = "Key: " + furi.toString(false, false);
			if (getresult.isMetaData())
				title = title + "\u00a0(MetaData)";
			HTMLNode dataBox2 = m_pm.getInfobox(title);

			dataBox2.addChild("%", "<pre lang=\"en\" style=\"font-family: monospace;\">\n");
			dataBox2.addChild("#", hexDump(data, hexWidth));
			dataBox2.addChild("%", "\n</pre>");

			contentNode.addChild(dataBox2);

			if (getresult.isMetaData()) {
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
						metaBox.addChild(new HTMLNode("a", "href", "/plugins/plugins.KeyExplorer.KeyExplorer/?mftype=simplemanifest&key=" + furi, "reopen as manifest"));
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
							String sfrUri = md.getSingleTarget().toString(false, false);
							metaBox.addChild("#", sfrUri);
							metaBox.addChild("#", "\u00a0");
							metaBox.addChild(new HTMLNode("a", "href", "/?key=" + sfrUri, "open"));
							metaBox.addChild("#", "\u00a0");
							metaBox.addChild(new HTMLNode("a", "href", "/plugins/plugins.KeyExplorer.KeyExplorer/?key=" + sfrUri, "explore"));
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
						metaBox.addChild(new HTMLNode("a", "href", "/plugins/plugins.KeyExplorer.KeyExplorer/?mftype=" + md.getArchiveType().name() + "manifest&key=" + furi,
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
			if (errors.size() > 0)
				contentNode.addChild(createErrorBox(errors, null, null));
		}
		return pageNode.generate();
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

	private String makeManifestPage(List<String> errors, String key, boolean zip, boolean tar, int hexWidth, boolean automf, boolean deep) {
		HTMLNode pageNode = m_pm.getPageNode("KeyExplorer", null);
		HTMLNode contentNode = m_pm.getContentNode(pageNode);

		Metadata metadata = null;

		FreenetURI furi = null;

		try {
			furi = sanitizeURI(errors, key);

			if (zip)
				metadata = zipManifestGet(furi);
			else if (tar)
				metadata = tarManifestGet(furi);
			else
				metadata = simpleManifestGet(m_pr, furi);
		} catch (MalformedURLException e) {
			errors.add("MalformedURL: " + key);
		} catch (FetchException e) {
			errors.add("Get failed (" + e.mode + "): " + e.getMessage());
		} catch (IOException e) {
			errors.add("IO Error: " + e.getMessage());
		} catch (MetadataParseException e) {
			errors.add("MetadataParseException");
		} catch (LowLevelGetException e) {
			errors.add("Get failed (" + e.code + "): " + e.getMessage());
		}

		if (errors.size() > 0) {
			contentNode.addChild(createErrorBox(errors, null, null));
			contentNode.addChild(createUriBox(((furi==null)?"":furi.toString(false, false)), hexWidth, automf, deep, errors));
			return pageNode.generate();
		}

		contentNode.addChild(createUriBox(furi.toString(false, false), hexWidth, automf, deep, errors));
		String title = "Key: " + furi.toString(false, false) + "\u00a0(Manifest)";
		HTMLNode listBox = m_pm.getInfobox(title);

		HashMap<String, Metadata> docs = metadata.getDocuments();

		// HTMLNode contentTable = contentNode.addChild("table", "class",
		// "column");
		HTMLNode contentTable = listBox.addChild("table");
		HTMLNode tableHead = contentTable.addChild("thead");
		HTMLNode tableRow = tableHead.addChild("tr");
		HTMLNode nextTableCell = tableRow.addChild("th");
		nextTableCell.addChild("#", "\u00a0");
		nextTableCell = tableRow.addChild("th");
		nextTableCell.addChild("#", "Type");
		nextTableCell = tableRow.addChild("th");
		nextTableCell.addChild("#", "Name");
		nextTableCell = tableRow.addChild("th");
		nextTableCell.addChild("#", "Size");
		nextTableCell = tableRow.addChild("th");
		nextTableCell.addChild("#", "Mime");
		nextTableCell = tableRow.addChild("th");
		nextTableCell.addChild("#", "Target");

		parseMetadata(contentTable, docs, "", furi.toString(false, false), errors);
		if (errors.size() > 0) {
			contentNode.addChild(createErrorBox(errors, null, null));
		}
		contentNode.addChild(listBox);
		return pageNode.generate();

	}

	private void addEmptyCell(HTMLNode tableRow) {
		HTMLNode tableCell = tableRow.addChild("td");
		tableCell.addChild("#", "\u00a0");
	}

	private void parseMetadata(HTMLNode htmlTable, HashMap<String, Metadata> docs, String prefix, String furi, List<String> errors) {
		Set<String> s = docs.keySet();
		Iterator<String> i = s.iterator();
		while (i.hasNext()) {
			HTMLNode htmlTableRow = htmlTable.addChild("tr");
			addEmptyCell(htmlTableRow);
			HTMLNode nextTableCell = htmlTableRow.addChild("td");
			String name = i.next();
			Metadata md = docs.get(name);
			String fname = prefix + name;

			if (md.isArchiveInternalRedirect())
				nextTableCell.addChild("#", "(container)\u00a0");
			if (md.isArchiveManifest())
				nextTableCell.addChild("#", "(archive)\u00a0");
			if (md.isCompressed())
				nextTableCell.addChild("#", "(compress)\u00a0");
			if (md.isMultiLevelMetadata())
				nextTableCell.addChild("#", "(multilevel)\u00a0");
			if (md.isResolved())
				nextTableCell.addChild("#", "(resolved)\u00a0");
			if (md.isSimpleManifest())
				nextTableCell.addChild("#", "(simple-manifest)\u00a0");
			if (md.isSimpleSplitfile())
				nextTableCell.addChild("#", "(simple-splitf)\u00a0");
			else if (md.isSplitfile())
				nextTableCell.addChild("#", "(splitf)\u00a0");
			if (md.isSingleFileRedirect())
				nextTableCell.addChild("#", "(extern)\u00a0");

			nextTableCell = htmlTableRow.addChild("td");

			if (md.isSimpleSplitfile()) {
				nextTableCell.addChild(new HTMLNode("a", "href", "/?key=" + furi + "/" + fname, fname));
				nextTableCell = htmlTableRow.addChild("td");
				if (md.isCompressed()) {
					nextTableCell.addChild("#", Long.toString(md.uncompressedDataLength()));
				} else {
					nextTableCell.addChild("#", Long.toString(md.dataLength()));
				}
				nextTableCell = htmlTableRow.addChild("td");
				nextTableCell.addChild("#", md.getMIMEType());
				addEmptyCell(htmlTableRow);
			} else if (md.isSplitfile()) {
				nextTableCell.addChild(new HTMLNode("a", "href", "/?key=" + furi + "/" + fname, fname));
				addEmptyCell(htmlTableRow);
				addEmptyCell(htmlTableRow);
				addEmptyCell(htmlTableRow);
			} else if (md.isArchiveInternalRedirect()) {
				nextTableCell.addChild(new HTMLNode("a", "href", "/" + furi + "/" + fname, fname));
				addEmptyCell(htmlTableRow);
				nextTableCell = htmlTableRow.addChild("td");
				nextTableCell.addChild("#", md.getMIMEType());
				addEmptyCell(htmlTableRow);
			} else if (md.isSingleFileRedirect()) {
				nextTableCell.addChild(new HTMLNode("a", "href", "/?key=" + furi + "/" + fname, fname));
				addEmptyCell(htmlTableRow);
				nextTableCell = htmlTableRow.addChild("td");
				nextTableCell.addChild("#", md.getMIMEType());
				nextTableCell = htmlTableRow.addChild("td");
				if (md.isArchiveManifest()) {
					String containerTarget = md.getSingleTarget().toString(false, false);
					nextTableCell.addChild(new HTMLNode("a", "href", "/plugins/plugins.KeyExplorer.KeyExplorer/?mftype=" + md.getArchiveType().name() + "manifest&key=" + containerTarget, containerTarget));
					Metadata subMd;
					if (md.getArchiveType() == ARCHIVE_TYPE.TAR) {
						try {
							subMd = tarManifestGet(md.getSingleTarget());
							parseMetadata(htmlTable, subMd.getDocuments(), fname + "/", furi, errors);
						} catch (FetchException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (MetadataParseException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				} else {
					nextTableCell.addChild(new HTMLNode("a", "href", "/plugins/plugins.KeyExplorer.KeyExplorer/?key=" + md.getSingleTarget().toString(false, false), md.getSingleTarget().toString(false, false)));
					// Multi level stuff

					FreenetURI mlUri = md.getSingleTarget();
					if ((mlUri.getExtra()[2] & 0x02) != 0) { // control document?
						//System.out.println("May ML target: " + mlUri.toString(false, false));
						try {
							Metadata subMd = simpleManifestGet(m_pr, mlUri);
							if (subMd.isMultiLevelMetadata()) {
								//System.out.println("is ML target: " + mlUri.toString(false, false));
								subMd.getResolvedURI();
								if (subMd.isSingleFileRedirect()) {
									//System.out.println("try get ML target: " + mlUri.toString(false, false));
									subMd = splitManifestGet(subMd);
									parseMetadata(htmlTable, subMd.getDocuments(), fname + "/", furi, errors);
									//System.out.println("brrrstlhipf!!!!!!!!!!");
								} else {
									//System.out.println("seems splitfile ML target: " + mlUri.toString(false, false));
									//System.out.println("try this: " + subMd.getSingleTarget());
								}
							} else {
								//System.out.println("no ML target: " + mlUri.toString(false, false));
							}
						} catch (MalformedURLException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (LowLevelGetException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (MetadataParseException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (FetchException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (KeyListenerConstructionException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					} else {
						//System.out.println("NO! ML target: " + mlUri.toString(false, false));
					}
				}
			} else {
				nextTableCell.addChild("#", fname);
				nextTableCell = htmlTableRow.addChild("td");
				nextTableCell.addChild("#", "(");
				nextTableCell.addChild("#", Integer.toString(md.getDocuments().size()));
				nextTableCell.addChild("#", " Items)");
				addEmptyCell(htmlTableRow);
				addEmptyCell(htmlTableRow);
				parseMetadata(htmlTable, md.getDocuments(), fname + "/", furi, errors);
			}
		}
	}

	private HTMLNode createUriBox(String uri, int hexWidth, boolean automf, boolean deep, List<String> errors) {
		HTMLNode browseBox = m_pm.getInfobox("Explore a freenet key");
		HTMLNode browseContent = m_pm.getContentNode(browseBox);

		if (hexWidth < 1 || hexWidth > 1024) {
			errors.add("Hex display columns out of range. (1-1024). Set to 32 (default).");
			hexWidth = 32;
		}
		browseContent.addChild("#", "Display the top level chunk as hexprint or list the content of a manifest");
		HTMLNode browseForm = m_pr.addFormChild(browseContent, "/plugins/plugins.KeyExplorer.KeyExplorer/", "uriForm");
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

	private HTMLNode createErrorBox(List<String> errors, FreenetURI retryUri, String extraParams) {

		HTMLNode errorBox = m_pm.getInfobox("infobox-alert", "ERROR");
		for (String error : errors) {
			errorBox.addChild("#", error);
			errorBox.addChild("%", "<BR />");
		}
		if (retryUri != null) {
			errorBox.addChild("#", "Retry: ");
			errorBox.addChild(new HTMLNode("a", "href", "/plugins/plugins.KeyExplorer.KeyExplorer?key="
					+ ((extraParams == null) ? retryUri : (retryUri + "?" + extraParams)), retryUri.toString(false, false)));
		}
		return errorBox;
	}

	public String getVersion() {
		return "0.4α " + Version.svnRevision;
	}

	// unroll until it hit a ARCHIVE_MANIFEST, return this
	private Metadata archiveManifestGet(FreenetURI uri) throws MetadataParseException, LowLevelGetException, IOException {
		GetResult res = simpleGet(m_pr, uri);
		if (!res.isMetaData()) {
			throw new MetadataParseException("Uri did not point to metadata " + uri);
		}
		Metadata md = Metadata.construct(res.getData());
		if (md.isArchiveManifest())
			return md;
		return archiveManifestGet(md);
	}

	private Metadata archiveManifestGet(Metadata md) throws MetadataParseException, LowLevelGetException, IOException {
		// GetResult res = simpleGet(uri);
		// if (!res.isMetaData()) {
		// throw new MetadataParseException("Uri did not point to metadata " +
		// uri);
		// }
		// Metadata md = Metadata.construct(res.getData());
		// if (md.isArchiveManifest())
		// return md;
		// return archiveManifestGet(md);
		return md;
	}

	public static Metadata simpleManifestGet(PluginRespirator pr, FreenetURI uri) throws MetadataParseException, LowLevelGetException, IOException {
		GetResult res = simpleGet(pr, uri);
		if (!res.isMetaData()) {
			throw new MetadataParseException("uri did not point to metadata " + uri);
		}
		return Metadata.construct(res.getData());
	}

	private Metadata splitManifestGet(Metadata metadata) throws MetadataParseException, LowLevelGetException, IOException, FetchException, KeyListenerConstructionException {
		FetchResult res = splitGet(metadata);
		return Metadata.construct(res.asBucket());
	}

	private Metadata zipManifestGet(FreenetURI uri) throws FetchException, MetadataParseException, IOException {
		HighLevelSimpleClient hlsc = m_pr.getHLSimpleClient();
		FetchContext fctx = hlsc.getFetchContext();
		fctx.returnZIPManifests = true;
		FetchWaiter fw = new FetchWaiter();
		hlsc.fetch(uri, -1, (RequestClient) hlsc, fw, fctx);
		FetchResult fr = fw.waitForCompletion();
		ZipInputStream zis = new ZipInputStream(fr.asBucket().getInputStream());
		ZipEntry entry;
		ByteArrayOutputStream bos;
		while (true) {
			entry = zis.getNextEntry();
			if (entry == null)
				break;
			if (entry.isDirectory())
				continue;
			String name = entry.getName();
			if (".metadata".equals(name)) {
				byte[] buf = new byte[32768];
				bos = new ByteArrayOutputStream();
				// Read the element
				int readBytes;
				while ((readBytes = zis.read(buf)) > 0) {
					bos.write(buf, 0, readBytes);
				}
				bos.close();
				return Metadata.construct(bos.toByteArray());
			}
		}
		throw new FetchException(200, "impossible? no metadata in archive " + uri);
	}

	private Metadata tarManifestGet(FreenetURI uri) throws FetchException, MetadataParseException, IOException {
		HighLevelSimpleClient hlsc = m_pr.getHLSimpleClient();
		FetchContext fctx = hlsc.getFetchContext();
		fctx.returnZIPManifests = true;
		FetchWaiter fw = new FetchWaiter();
		hlsc.fetch(uri, -1, (RequestClient) hlsc, fw, fctx);
		FetchResult fr = fw.waitForCompletion();
		TarInputStream zis = new TarInputStream(fr.asBucket().getInputStream());
		TarEntry entry;
		ByteArrayOutputStream bos;
		while (true) {
			entry = zis.getNextEntry();
			if (entry == null)
				break;
			if (entry.isDirectory())
				continue;
			String name = entry.getName();
			if (".metadata".equals(name)) {
				byte[] buf = new byte[32768];
				bos = new ByteArrayOutputStream();
				// Read the element
				int readBytes;
				while ((readBytes = zis.read(buf)) > 0) {
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

	public long getRealVersion() {
		return Version.version;
	}
	
	private FreenetURI sanitizeURI(List<String> errors, String key) throws MalformedURLException {
		if (key == null) throw new NullPointerException();
		
		FreenetURI tempURI = new FreenetURI(key);
		
		//get rid of metas, useles
		if (tempURI.hasMetaStrings()) {
			if (errors != null) {
				tempURI = tempURI.setMetaString(null);
				errors.add("URI did contain meta strings, removed it for you");
			} else {
				throw new MalformedURLException("URIs with meta strings not supported");
			}
		}
		
		// turn USK into SSK
		if (tempURI.isUSK()) {
			if (errors != null) {
				USK tempUSK = USK.create(tempURI);
				ClientKey tempKey = tempUSK.getSSK();
				tempURI = tempKey.getURI();
				errors.add("URI was an USK, converted it to SSK for you");
			} else {
				throw new MalformedURLException("USK not supported, use underlaying SSK insted.");
			}
		}
		
		return tempURI;
	}
}
