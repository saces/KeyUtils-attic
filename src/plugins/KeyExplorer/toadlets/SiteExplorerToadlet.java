/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyExplorer.toadlets;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import plugins.KeyExplorer.KeyExplorer;
import plugins.KeyExplorer.KeyExplorerUtils;
import plugins.fproxy.lib.PluginContext;
import plugins.fproxy.lib.WebInterfaceToadlet;
import freenet.client.FetchException;
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

/**
 * @author saces
 *
 */
public class SiteExplorerToadlet extends WebInterfaceToadlet {

	public SiteExplorerToadlet(PluginContext context) {
		super(context, KeyExplorer.PLUGIN_URI, "Site");
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {

		System.out.println("Path-Test: " + normalizePath(request.getPath()) + " -> " + uri);
		
		
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

		List<String> errors = new LinkedList<String>();
		if (hexWidth < 1 || hexWidth > 1024) {
			errors.add("Hex display columns out of range. (1-1024). Set to 32 (default).");
			hexWidth = 32;
		}

		if ("ZIPmanifest".equals(type)) {
			makeManifestPage(ctx, errors, key, true, false, hexWidth, automf, deep, ml);
			return;
		}
		if ("TARmanifest".equals(type)) {
			makeManifestPage(ctx, errors, key, false, true, hexWidth, automf, deep, ml);
			return;
		}
		if ("simplemanifest".equals(type)) {
			makeManifestPage(ctx, errors, key, false, false, hexWidth, automf, deep, ml);
			return;
		}
		makeMainPage(ctx, errors, key, deep, ml);
	}

	private void makeMainPage(ToadletContext ctx, List<String> errors, String key, boolean deep, boolean ml) throws ToadletContextClosedException, IOException {
		PageNode page = pluginContext.pageMaker.getPageNode(KeyExplorer.PLUGIN_TITLE, ctx);
		HTMLNode outer = page.outer;
		HTMLNode contentNode = page.content;

		String extraParams = "";
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
			}
		} catch (MalformedURLException e) {
			errors.add("MalformedURL: " + key);
		}

		HTMLNode uriBox = createUriBox(pluginContext, ((furi == null) ? null : furi.toString(false, false)), deep, errors);

		if (errors.size() > 0) {
			contentNode.addChild(UIUtils.createErrorBox(pluginContext, errors, path(), retryUri, null));
			errors.clear();
		}

		contentNode.addChild(uriBox);

		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}

	private HTMLNode createUriBox(PluginContext pCtx, String uri, boolean deep, List<String> errors) {
		InfoboxNode box = pCtx.pageMaker.getInfobox("Explore a site");
		HTMLNode browseBox = box.outer;
		HTMLNode browseContent = box.content;

		browseContent.addChild("#", "List the content of a manifest");
		HTMLNode browseForm = pCtx.pluginRespirator.addFormChild(browseContent, path(), "uriForm");
		browseForm.addChild("#", "Freenetkey to explore: \u00a0 ");
		if (uri != null)
			browseForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", "key", "70", uri });
		else
			browseForm.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", "key", "70" });
		browseForm.addChild("#", "\u00a0");
		browseForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "debug", "Explore!" });
		browseForm.addChild("%", "<BR />");
		if (deep)
			browseForm.addChild("input", new String[] { "type", "name", "value", "checked" }, new String[] { "checkbox", "deep", "ok", "checked" });
		else
			browseForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "checkbox", "deep", "ok" });
		browseForm.addChild("#", "\u00a0parse manifest recursive (include multilevel metadata/subcontainers)");
		return browseBox;
	}

	private void makeManifestPage(ToadletContext ctx, List<String> errors, String key, boolean zip, boolean tar, int hexWidth, boolean automf, boolean deep, boolean ml) throws ToadletContextClosedException, IOException {
		PageNode page = pluginContext.pageMaker.getPageNode("KeyExplorer", ctx);
		HTMLNode pageNode = page.outer;
		HTMLNode contentNode = page.content;
	
		Metadata metadata = null;
	
		FreenetURI furi = null;
	
		try {
			furi = KeyExplorerUtils.sanitizeURI(errors, key);
	
			if (zip)
				metadata = KeyExplorerUtils.zipManifestGet(pluginContext.pluginRespirator, furi);
			else if (tar)
				metadata = KeyExplorerUtils.tarManifestGet(pluginContext.pluginRespirator, furi, ".metadata");
			else {
				metadata = KeyExplorerUtils.simpleManifestGet(pluginContext.pluginRespirator, furi);
				if (ml) {
					metadata = KeyExplorerUtils.splitManifestGet(pluginContext.pluginRespirator, metadata);
				}
			}
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
		} catch (KeyListenerConstructionException e) {
			Logger.error(this, "Hu?", e);
			errors.add("Internal Error: " + e.getMessage());
		}
	
		if (errors.size() > 0) {
			contentNode.addChild(UIUtils.createErrorBox(pluginContext, errors));
			contentNode.addChild(createUriBox(pluginContext, ((furi==null)?"":furi.toString(false, false)), automf, errors));
			writeHTMLReply(ctx, 200, "OK", pageNode.generate());
			return;
		}
	
		contentNode.addChild(createUriBox(pluginContext, furi.toString(false, false), automf, errors));
		String title = "Key: " + furi.toString(false, false) + "\u00a0(Manifest)";
		InfoboxNode listInfobox = pluginContext.pageMaker.getInfobox(title);
		HTMLNode listBox = listInfobox.content;
	
		// HTMLNode contentTable = contentNode.addChild("table", "class", "column");
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
	
		parseMetadataItem(contentTable, "", metadata, "", furi.toString(false, false), errors, deep, 0, -1);
		if (errors.size() > 0) {
			contentNode.addChild(UIUtils.createErrorBox(pluginContext, errors));
		}
		contentNode.addChild(listInfobox.outer);

		writeHTMLReply(ctx, 200, "OK", pageNode.generate());
	}

	private void parseMetadataItem(HTMLNode htmlTable, String name, Metadata md, String prefix, String furi, List<String> errors, boolean deep, int nestedLevel, int subLevel) {
	
		String fname = prefix + name;
	
		HTMLNode htmlTableRow = htmlTable.addChild("tr");
		htmlTableRow.addChild(makeNestedDeepCell(nestedLevel, subLevel));
		htmlTableRow.addChild(makeTypeCell(md));
	
		// the clear & easy first
		if (md.isSimpleManifest()) {
			// a subdir
			HashMap<String, Metadata> docs = md.getDocuments();
			Metadata defaultDoc = md.getDefaultDocument();
	
			htmlTableRow.addChild(makeNameCell(prefix, name));
			htmlTableRow.addChild(makeCell("(" + Integer.toString(docs.size())+" Items)"));
			htmlTableRow.addChild(makeEmptyCell());
			htmlTableRow.addChild(makeEmptyCell());
	
			if (defaultDoc != null) {
				parseMetadataItem(htmlTable, "/", defaultDoc, prefix+name, furi, errors, deep, nestedLevel, subLevel+1);
			}
	
			for (String iname: docs.keySet()) {
				Metadata doc = docs.get(iname);
				parseMetadataItem(htmlTable, iname, doc, prefix+name+'/', furi, errors, deep, nestedLevel, subLevel+1);
			}
			return;
		}
	
		if (md.isArchiveInternalRedirect()) {
			HTMLNode cell = htmlTableRow.addChild("td");
			cell.addChild(new HTMLNode("a", "href", "/" + furi + fname, fname));
			htmlTableRow.addChild(makeEmptyCell());
			htmlTableRow.addChild(makeMimeCell(md));
			htmlTableRow.addChild(makeCell(md.getArchiveInternalName()));
			return;
		}
	
		if (md.isSymbolicShortlink()) {
			HTMLNode cell = htmlTableRow.addChild("td");
			cell.addChild(new HTMLNode("a", "href", "/" + furi + fname, fname));
			htmlTableRow.addChild(makeEmptyCell());
			htmlTableRow.addChild(makeMimeCell(md));
			htmlTableRow.addChild(makeCell("->"+md.getSymbolicShortlinkTargetName()));
			return;
		}
	
		if (md.isMultiLevelMetadata()) {
			HTMLNode cell = htmlTableRow.addChild("td");
			cell.addChild(new HTMLNode("a", "href", "/" + furi + fname, fname));
			htmlTableRow.addChild(makeSizeCell(md));
			htmlTableRow.addChild(makeMimeCell(md));
			if (md.isSingleFileRedirect()) {
				htmlTableRow.addChild(makeCell(new HTMLNode("a", "href", "/plugins/plugins.KeyExplorer.KeyExplorer/?key=" + md.getSingleTarget().toString(false, false), md.getSingleTarget().toString(false, false))));
			} else {
				htmlTableRow.addChild(makeCell("Sorry, I won't deal with multilevel metadata here even though they are valid."));
			}
			return;
		}
	
		if (md.isSimpleRedirect()) {
			HTMLNode cell = htmlTableRow.addChild("td");
			cell.addChild(new HTMLNode("a", "href", "/" + furi + fname, fname));
			htmlTableRow.addChild(makeSizeCell(md));
			htmlTableRow.addChild(makeMimeCell(md));
			if (md.isSingleFileRedirect())
				htmlTableRow.addChild(makeCell(new HTMLNode("a", "href", "/plugins/plugins.KeyExplorer.KeyExplorer/?key=" + md.getSingleTarget().toString(false, false), md.getSingleTarget().toString(false, false))));
			else
				htmlTableRow.addChild(makeEmptyCell());
	
			// the row for the item itself is written, now look inside for multi level md
			if (deep && md.isNoMimeEnabled() && md.isSingleFileRedirect()) {
				// looks like possible ml
				FreenetURI mlUri = md.getSingleTarget();
				// is control document?
				if ((mlUri.getExtra()[2] & 0x02) != 0) {
					// control doc, look inside for ML
					Exception err;
					try {
						Metadata subMd = KeyExplorerUtils.simpleManifestGet(pluginContext.pluginRespirator, mlUri);
						if (subMd.isMultiLevelMetadata()) {
							// really multilevel, fetch it
							subMd = KeyExplorerUtils.splitManifestGet(pluginContext.pluginRespirator, subMd);
						}
						parseMetadataItem(htmlTable, "", subMd, prefix+name, furi, errors, deep, nestedLevel+1, -1);
						return;
					} catch (MetadataParseException e) {
						err = e;
					} catch (LowLevelGetException e) {
						err = e;
					} catch (IOException e) {
						err = e;
					} catch (FetchException e) {
						err = e;
					} catch (KeyListenerConstructionException e) {
						err = e;
					}
					htmlTable.addChild(makeErrorRow(err));
				}
			}
			return;
		}
	
		if (md.isArchiveMetadataRedirect()) {
			htmlTableRow.addChild(makeNameCell(prefix, name));
			htmlTableRow.addChild(makeSizeCell(md));
			htmlTableRow.addChild(makeMimeCell(md));
			htmlTableRow.addChild(makeCell(md.getArchiveInternalName()));
	
			if (deep) {
				//grab data;
				FreenetURI u;
				Metadata metadata;
				Exception err;
				try {
					u = new FreenetURI(furi);
					metadata = KeyExplorerUtils.tarManifestGet(pluginContext.pluginRespirator, u, md.getArchiveInternalName());
					//parse into
					parseMetadataItem(htmlTable, "", metadata, prefix+name, furi, errors, deep, nestedLevel+1, -1);
					return;
				} catch (MalformedURLException e) {
					err = e;
				} catch (FetchException e) {
					err = e;
				} catch (MetadataParseException e) {
					err = e;
				} catch (IOException e) {
					err = e;
				}
				htmlTable.addChild(makeErrorRow(err));
			}
			return;
		}
	
		if (md.isArchiveManifest()) {
			htmlTableRow.addChild(makeNameCell(prefix, name));
			htmlTableRow.addChild(makeSizeCell(md));
			htmlTableRow.addChild(makeMimeCell(md));
	
			if (md.isSingleFileRedirect()) {
				String containerTarget = md.getSingleTarget().toString(false, false);
				htmlTableRow.addChild(makeCell(new HTMLNode("a", "href", "/plugins/plugins.KeyExplorer.KeyExplorer/?mftype=" + md.getArchiveType().name() + "manifest&key=" + containerTarget, containerTarget)));
			} else {
				htmlTableRow.addChild(makeEmptyCell());
			}
			if (deep) {
				Metadata subMd;
				Exception err;
				// TODO turn "smash with stones" style into "rocket science"
				if (md.getArchiveType() == ARCHIVE_TYPE.TAR) {
					try {
						if (md.isSplitfile())
							subMd = KeyExplorerUtils.tarManifestGet(pluginContext.pluginRespirator, md, ".metadata");
						else
							subMd = KeyExplorerUtils.tarManifestGet(pluginContext.pluginRespirator, md.getSingleTarget(), ".metadata");
						parseMetadataItem(htmlTable, "", subMd, prefix+name, furi, errors, deep, nestedLevel+1, -1);
						return;
					} catch (FetchException e) {
						err = e;
					} catch (MetadataParseException e) {
						err = e;
					} catch (IOException e) {
						err = e;
					}
					htmlTable.addChild(makeErrorRow(err));
				}
			}
			return;
		}
	
		// in theory this is 'unreachable code'
		htmlTableRow.addChild(makeNameCell(prefix, name));
		htmlTableRow.addChild(makeSizeCell(md));
		htmlTableRow.addChild(makeMimeCell(md));
		htmlTableRow.addChild(makeCell("(Unknown dokument type)"));
	}

	private HTMLNode makeErrorRow(String msg) {
		HTMLNode row = new HTMLNode("tr");
		row.addChild(makeEmptyCell());
		row.addChild(makeEmptyCell());
		row.addChild(makeCell("<ERROR>"));
		row.addChild(makeEmptyCell());
		row.addChild(makeEmptyCell());
		if (msg != null)
			row.addChild(makeCell(msg));
		else
			row.addChild(makeEmptyCell());
		return row;
	}

	private HTMLNode makeEmptyCell() {
		HTMLNode cell = new HTMLNode("td");
		cell.addChild("#", "\u00a0");
		return cell;
	}

	private HTMLNode makeCell(String content) {
		HTMLNode cell = new HTMLNode("td");
		cell.addChild("#", content);
		return cell;
	}

	private HTMLNode makeErrorRow(Exception e) {
		return makeErrorRow(e.getLocalizedMessage());
	}

	private HTMLNode makeNameCell(String prefix, String name) {
		HTMLNode cell = new HTMLNode("td");
		if ((name == null)||(name.trim().length()==0))
			cell.addChild("#", "\u00a0");
		else
			if ((prefix != null))
				cell.addChild("#", prefix + name);
			else
				cell.addChild("#", name);
		return cell;
	}

	private HTMLNode makeMimeCell(Metadata md) {
		HTMLNode cell = new HTMLNode("td");
		if(md.isNoMimeEnabled())
			cell.addChild("#", "<NoMime>");
		else
			cell.addChild("#", md.getMIMEType());
		return cell;
	}

	private HTMLNode makeNestedDeepCell(int nestedLevel, int subLevel) {
		HTMLNode cell = new HTMLNode("td");
		cell.addChild("span", "title", "nested level", Integer.toString(nestedLevel));
		cell.addChild("#", "-");
		cell.addChild("span", "title", "sub level inside container/manifest", ((subLevel==-1)?"R":Integer.toString(subLevel)));
		return cell;
	}

	private HTMLNode makeTypeCell(Metadata md) {
		HTMLNode cell = new HTMLNode("td");
	
		if (md.isArchiveInternalRedirect() || md.isArchiveMetadataRedirect() || md.isSymbolicShortlink())
			cell.addChild("span", "title", "All data are in container/chunk", "[c]");
		else if (md.getSingleTarget() != null)
			cell.addChild("span", "title", "Pointer to external [meta+]data (FreenetURI)", "[e]");
		else if (md.isSimpleManifest())
			cell.addChild("span", "title", "A subdirectory inside container/chunk", "[s]");
		else
			cell.addChild("span", "title", "Metadata are in container, but points to external data (usually split files)", "[m]");
	
		cell.addChild("#", "\u00a0");
	
		if (md.isSimpleRedirect()) {
			cell.addChild("span", "title", "Simple redirect", "SRE");
		} else if (md.isSimpleManifest()) {
			cell.addChild("span", "title", "Simple manifest", "SMF");
		} else if (md.isArchiveInternalRedirect()) {
			cell.addChild("span", "title", "Archive internal redirect", "AIR");
		} else if (md.isArchiveMetadataRedirect()) {
			cell.addChild("span", "title", "Archive metadata redirect", "AMR");
		} else if (md.isArchiveManifest()) {
			cell.addChild("span", "title", "Archive redirect", "ARE");
		} else if (md.isMultiLevelMetadata()) {
			cell.addChild("span", "title", "Multi level metadata", "MLM");
		} else if (md.isSymbolicShortlink()) {
			cell.addChild("span", "title", "Symbolic short link", "SYS");
		} else {
			cell.addChild("span", "title", "Unknown document type", "?");
		}
	
		cell.addChild("#", "\u00a0");
	
		if (md.haveFlags()) {
			boolean isFirst = true;
	
			if (md.isSplitfile()) {
				cell.addChild("#", "(");
				cell.addChild("span", "title", "Split file", "SF");
				isFirst = false;
			}
			if (md.isCompressed()) {
				if (isFirst) {
					cell.addChild("#", "(");
					isFirst = false;
				}
				else
					cell.addChild("#", "\u00a0");
				cell.addChild("span", "title", "Compressed", "CP");
			}
			if (!isFirst)
				cell.addChild("#", ")\u00a0");
		}
		return cell;
	}

	private HTMLNode makeSizeCell(Metadata md) {
		long size;
		if (md.isCompressed())
			size = md.uncompressedDataLength();
		else if (md.isSplitfile())
			size = md.dataLength();
		else
			return makeEmptyCell();
		return makeCell(Long.toString(size)+"\u00a0B");
	}

	private HTMLNode makeCell(HTMLNode content) {
		HTMLNode cell = new HTMLNode("td");
		cell.addChild(content);
		return cell;
	}
}
