/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyExplorer.toadlets;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;

import freenet.clients.http.InfoboxNode;
import freenet.clients.http.PageNode;
import freenet.clients.http.RedirectException;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.keys.FreenetURI;
import freenet.keys.Key;
import freenet.keys.KeyBlock;
import freenet.support.Base64;
import freenet.support.HTMLNode;
import freenet.support.HexUtil;
import freenet.support.api.HTTPRequest;
import plugins.KeyExplorer.KeyExplorer;
import plugins.fproxy.lib.PluginContext;
import plugins.fproxy.lib.WebInterfaceToadlet;

/**
 * @author saces
 *
 */
public class ExtraToadlet extends WebInterfaceToadlet {
	
	private static final String PATH_DECOMPOSE = "/decompose";
	private static final String PATH_COMPOSE_SSK = "/composeSSK";
	private static final String PATH_COMPOSE_CHK = "/composeCHK";
	private static final String FIELDNAME_URI = "key";
	private static final String FIELDNAME_CRYPTO_ALGO = "crypto_algo";
	private static final String FIELDNAME_COMPRESS_ALGO = "compress_algo";
	private static final String FIELDNAME_CONTROL_DOCUMENT = "control_doc";
	private static final String FIELDNAME_HASH_ALGO = "hash_algo";
	private static final String FIELDNAME_SSK_TYPE = "ssk_type";
	private static final String FIELDNAME_SSK_PRIVATE = "ssk_private";

	public ExtraToadlet(PluginContext context) {
		super(context, KeyExplorer.PLUGIN_URI, "Extra");
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {

		if (!normalizePath(request.getPath()).equals("/")) {
			sendErrorPage(ctx, 404, "Not found", "the path '"+uri+"' was not found");
			return;
		}

		String key;
		if (request.isParameterSet(FIELDNAME_URI)) {
			key = request.getParam("key");
		} else {
			key = null;
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

		String path = normalizePath(request.getPath());

		if (path.startsWith(PATH_DECOMPOSE)) {
			String key = request.getPartAsString(FIELDNAME_URI, 1024);

			if (key == null || (key.trim().length() == 0)) {
				errors.add("Are you jokingly? URI is empty");
				makeMainPage(ctx, errors, key);
				return;
			}

			FreenetURI furi = null;

			try {
				if (key != null && (key.trim().length() > 0)) {
					furi = new FreenetURI(key);
					if (furi.isKSK()) {
						errors.add("Keytype 'KSK' not supported jet: " + key);
						makeMainPage(ctx, errors, key);
						return;
					}
				}
			} catch (MalformedURLException e) {
				errors.add("MalformedURL: " + key);
				makeMainPage(ctx, errors, key);
				return;
			}

			byte[] extra = furi.getExtra();

			// make result box
			InfoboxNode box = pluginContext.pageMaker.getInfobox("infobox-warning", "Decompose an Freenet URI's EXTRA: "+furi.toString(false, false));
			HTMLNode resultBox = box.outer;
			HTMLNode resultContent = box.content;
			resultContent.addChild("#", "Key type: ");
			if (furi.isCHK()) {
				resultContent.addChild("#", "CHK");
				resultContent.addChild("%", "<BR />");

				// byte 0 reserved for now

				resultContent.addChild("#", "Crypto algorithm: "+ (extra[1]));
				if (extra[1] == Key.ALGO_AES_PCFB_256_SHA256)
					resultContent.addChild("#", " (AES_PCFB_256_SHA256)");
				resultContent.addChild("%", "<BR />");

				if ((extra[2] & 0x02) == 0)
					resultContent.addChild("#", "payload");
				else
					resultContent.addChild("#", "Control document");
				resultContent.addChild("%", "<BR />");

				short compressAlgorithm = (short)(((extra[3] & 0xff) << 8) + (extra[4] & 0xff));
				resultContent.addChild("#", "Compress algorithm: "+ compressAlgorithm);
				switch(compressAlgorithm) {
				case -1:
					resultContent.addChild("#", " (uncompressed)");
					break;
				case 0:
					resultContent.addChild("#", " (GZip)");
					break;
				case 1:
					resultContent.addChild("#", " (BZip2)");
					break;
				case 2:
					resultContent.addChild("#", " (LZMA)");
				}
			} else {
				resultContent.addChild("#", "SSK");
				resultContent.addChild("%", "<BR />");

				resultContent.addChild("#", "SSK version: "+ (extra[0]));
				resultContent.addChild("%", "<BR />");

				if (extra[1] == 0)
					resultContent.addChild("#", "fetch (public) URI");
				else
					resultContent.addChild("#", "insert (private) URI");
				resultContent.addChild("%", "<BR />");

				resultContent.addChild("#", "Crypto algorithm: "+ (extra[2]));
				if (extra[2] == Key.ALGO_AES_PCFB_256_SHA256)
					resultContent.addChild("#", " (AES_PCFB_256_SHA256)");
				resultContent.addChild("%", "<BR />");

				short hashAlgorithm = (short)(((extra[3] & 0xff) << 8) + (extra[4] & 0xff));
				resultContent.addChild("#", "Hash algorithm: "+ hashAlgorithm);
				if (hashAlgorithm == KeyBlock.HASH_SHA256)
					resultContent.addChild("#", " (SHA256)");
			}
			makeMainPage(ctx, errors, key, resultBox);
			return;
		}

		if (path.startsWith(PATH_COMPOSE_CHK)) {
			String s_crypto = request.getPartAsString(FIELDNAME_CRYPTO_ALGO, 1024);
			String s_compress = request.getPartAsString(FIELDNAME_COMPRESS_ALGO, 1024);
			String s_control = request.getPartAsString(FIELDNAME_CONTROL_DOCUMENT, 1024);

			// make result box
			InfoboxNode box = pluginContext.pageMaker.getInfobox("infobox-warning", "Composed an Freenet URI's EXTRA (CHK): ");
			HTMLNode resultBox = box.outer;
			HTMLNode resultContent = box.content;
			resultContent.addChild("#", "Your parameters: Crypto='");
			resultContent.addChild("#", s_crypto);
			resultContent.addChild("#", "', Compress='");
			resultContent.addChild("#", s_compress);
			resultContent.addChild("#", "', Control='");
			resultContent.addChild("#", s_control);
			resultContent.addChild("#", "'");
			resultContent.addChild("%", "<BR />");

			byte[] extra = {0, 0, 0, 0, 0};
			try {
				extra[1] = Byte.parseByte(s_crypto);
			} catch (NumberFormatException nfe) {
				errors.add("Field 'crypto' must be a number -1 - 255)");
			}
			extra[2] = (byte) (s_control.length()>0 ? 2 : 0);

			try {
				short compressionAlgo = Short.parseShort(s_compress);
				extra[3] = (byte) (compressionAlgo >> 8);
				extra[4] = (byte) compressionAlgo;
			} catch (NumberFormatException nfe) {
				errors.add("Field 'compress' must be a number -1 - 255)");
			}

			resultContent.addChild("#", "Your EXTRA (hex): ");
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < extra.length; i++) {
				HexUtil.bytesToHexAppend(extra, i, 1, sb);
				sb.append("\u00A0");
			}
			resultContent.addChild("#", sb.toString());
			resultContent.addChild("%", "<BR />");

			resultContent.addChild("#", "Your EXTRA (base64): ");
			resultContent.addChild("#", Base64.encode(extra));
			
			makeMainPage(ctx, errors, null, resultBox);
			return;
		}

		if (path.startsWith(PATH_COMPOSE_SSK)) {
			String s_crypto = request.getPartAsString(FIELDNAME_CRYPTO_ALGO, 1024);
			String s_hash = request.getPartAsString(FIELDNAME_HASH_ALGO, 1024);
			String s_type = request.getPartAsString(FIELDNAME_SSK_TYPE, 1024);
			String s_private = request.getPartAsString(FIELDNAME_SSK_PRIVATE, 1024);

			// make result box
			InfoboxNode box = pluginContext.pageMaker.getInfobox("infobox-warning", "Composed an Freenet URI's EXTRA (SSK): ");
			HTMLNode resultBox = box.outer;
			HTMLNode resultContent = box.content;
			resultContent.addChild("#", "Your parameters: Type='");
			resultContent.addChild("#", s_type);
			resultContent.addChild("#", "', Private='");
			resultContent.addChild("#", s_private);
			resultContent.addChild("#", "', Crypto='");
			resultContent.addChild("#", s_crypto);
			resultContent.addChild("#", "', Hash='");
			resultContent.addChild("#", s_hash);
			resultContent.addChild("#", "'");
			resultContent.addChild("%", "<BR />");

			byte[] extra = {0, 0, 0, 0, 0};
			try {
				extra[0] = Byte.parseByte(s_type);
			} catch (NumberFormatException nfe) {
				errors.add("Field 'type' must be a number 1 - 255)");
			}
			extra[1] = (byte) (s_private.length()>0 ? 2 : 0);
			try {
				extra[2] = Byte.parseByte(s_crypto);
			} catch (NumberFormatException nfe) {
				errors.add("Field 'crypto' must be a number -1 - 255)");
			}
			try {
				short hashAlgo = Short.parseShort(s_hash);
				extra[3] = (byte) (hashAlgo >> 8);
				extra[4] = (byte) hashAlgo;
			} catch (NumberFormatException nfe) {
				errors.add("Field 'hash' must be a number -1 - 255)");
			}

			resultContent.addChild("#", "Your EXTRA (hex): ");
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < extra.length; i++) {
				HexUtil.bytesToHexAppend(extra, i, 1, sb);
				sb.append("\u00A0");
			}
			resultContent.addChild("#", sb.toString());
			resultContent.addChild("%", "<BR />");

			resultContent.addChild("#", "Your EXTRA (base64): ");
			resultContent.addChild("#", Base64.encode(extra));
			
			makeMainPage(ctx, errors, null, resultBox);
			return;
		}

		String key = request.getPartAsString(FIELDNAME_URI, 1024);
		int hexWidth = request.getIntPart("hexWidth", 32);
		boolean automf = request.getPartAsString("automf", 128).length() > 0;
		boolean deep = request.getPartAsString("deep", 128).length() > 0;
		boolean ml = request.getPartAsString("ml", 128).length() > 0;
		if (hexWidth < 1 || hexWidth > 1024) {
			errors.add("Hex display columns out of range. (1-1024). Set to 32 (default).");
			hexWidth = 32;
		}
		makeMainPage(ctx, errors, key);
	}

	private PageNode getPageNode(ToadletContext ctx) {
		return pluginContext.pageMaker.getPageNode(KeyExplorer.PLUGIN_TITLE, ctx);
	}

	private void makeMainPage(ToadletContext ctx, List<String> errors, String key) throws ToadletContextClosedException, IOException {
		makeMainPage(ctx, errors, key, null);
	}

	private void makeMainPage(ToadletContext ctx, List<String> errors, String key, HTMLNode resultBox) throws ToadletContextClosedException, IOException {
		PageNode page = getPageNode(ctx);
		HTMLNode outer = page.outer;
		HTMLNode contentNode = page.content;

		HTMLNode uriBox = createUriBox(pluginContext, key, errors);

		HTMLNode composeBox = createComposeBox(pluginContext, errors);

		if (errors.size() > 0) {
			contentNode.addChild(UIUtils.createErrorBox(pluginContext, errors, path(), null, null));
			errors.clear();
		}

		if (resultBox != null) {
			contentNode.addChild(resultBox);
		}

		contentNode.addChild(uriBox);
		contentNode.addChild(composeBox);

		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}

	private HTMLNode createUriBox(PluginContext pCtx, String uri, List<String> errors) {
		InfoboxNode box = pCtx.pageMaker.getInfobox("Decompose an Freenet URI's EXTRA");
		HTMLNode browseBox = box.outer;
		HTMLNode browseContent = box.content;

		browseContent.addChild("#", "Decompose EXTRA from a Freenet URI:");
		HTMLNode browseForm = pCtx.pluginRespirator.addFormChild(browseContent, path() + PATH_DECOMPOSE, "uriForm");
		if (uri != null)
			browseForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", FIELDNAME_URI, "70", uri });
		else
			browseForm.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", FIELDNAME_URI, "70" });
		browseForm.addChild("#", "\u00a0");
		browseForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "debug", "Decompose!" });
		browseForm.addChild("%", "<BR />");
		return browseBox;
	}

	private HTMLNode createComposeBox(PluginContext pCtx, List<String> errors) {
		InfoboxNode box = pCtx.pageMaker.getInfobox("Compose an Freenet URI's EXTRA");
		HTMLNode browseBox = box.outer;
		HTMLNode browseContent = box.content;

		HTMLNode table = browseContent.addChild("table", "class", "column");
		HTMLNode tableRow = table.addChild("tr");
		HTMLNode CHK_Cell = new HTMLNode("td");
		HTMLNode SSK_Cell = new HTMLNode("td");

		CHK_Cell.addChild(createComposeCHKBox(pCtx, errors));
		SSK_Cell.addChild(createComposeSSKBox(pCtx, errors));

		tableRow.addChild(CHK_Cell);
		tableRow.addChild(SSK_Cell);
		return browseBox;
	}

	private HTMLNode createComposeCHKBox(PluginContext pCtx, List<String> errors) {
		InfoboxNode box = pCtx.pageMaker.getInfobox("CHK EXTRA");
		HTMLNode browseBox = box.outer;
		HTMLNode browseContent = box.content;

		HTMLNode browseForm = pCtx.pluginRespirator.addFormChild(browseContent, path()+PATH_COMPOSE_CHK, "chkExtraForm");

		HTMLNode tableRow;
		HTMLNode cell;

		HTMLNode table = browseForm.addChild("table", "class", "column");

		tableRow = table.addChild("tr");

		cell = tableRow.addChild("td");
		cell.addChild("#", "Crypto algorithm\u00a0");

		cell = tableRow.addChild("td");
		cell.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", "crypto_algo", "7" });
		cell.addChild("#", "\u00a0");

		HTMLNode cryptoselect = new HTMLNode("select", new String[] {"name", "onchange"}, new String[] {"crypto_selector", "document.forms[\"chkExtraForm\"].elements[\"crypto_algo\"].value = this.options[this.options.selectedIndex].value"});
		cryptoselect.addChild("option", "value", "", "-- select --");
		cryptoselect.addChild("option", "value", "2", "AES_PCFB_256_SHA256 (2)");

		cell.addChild(cryptoselect);

		tableRow = table.addChild("tr");

		cell = tableRow.addChild("td");
		cell.addChild("#", "\u00a0");

		cell = tableRow.addChild("td");
		cell.addChild("input", new String[] { "type", "name", "value" }, new String[] { "checkbox", "control_doc", "ok" });
		cell.addChild("#", "\u00a0Control document");

		tableRow = table.addChild("tr");

		cell = tableRow.addChild("td");
		cell.addChild("#", "Compress algorithm\u00a0");

		cell = tableRow.addChild("td");
		cell.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", "compress_algo", "7" });
		cell.addChild("#", "\u00a0");

		HTMLNode compressselect = new HTMLNode("select", new String[] {"name", "onchange"}, new String[] {"compress_selector", "document.forms[\"chkExtraForm\"].elements[\"compress_algo\"].value = this.options[this.options.selectedIndex].value"});
		compressselect.addChild("option", "value", "", "-- select --");
		compressselect.addChild("option", "value", "-1", "uncompressed (-1)");
		compressselect.addChild("option", "value", "0", "GZip (0)");
		compressselect.addChild("option", "value", "1", "BZip2 (1)");
		compressselect.addChild("option", "value", "2", "LZMA (2)");

		cell.addChild(compressselect);

		browseForm.addChild("%", "<BR />");
		browseForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "debug", "Compose CHK!" });
		return browseBox;
	}

	private HTMLNode createComposeSSKBox(PluginContext pCtx, List<String> errors) {
		InfoboxNode box = pCtx.pageMaker.getInfobox("SSK EXTRA");
		HTMLNode browseBox = box.outer;
		HTMLNode browseContent = box.content;

		HTMLNode browseForm = pCtx.pluginRespirator.addFormChild(browseContent, path()+PATH_COMPOSE_SSK, "sskExtraForm");

		HTMLNode tableRow;
		HTMLNode cell;

		HTMLNode table = browseForm.addChild("table", "class", "column");

		tableRow = table.addChild("tr");

		cell = tableRow.addChild("td");
		cell.addChild("#", "SSK Type\u00a0");

		cell = tableRow.addChild("td");
		cell.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", "ssk_type", "7" });
		cell.addChild("#", "\u00a0");

		HTMLNode ssktype = new HTMLNode("select", new String[] {"name", "onchange"}, new String[] {"ssktype_selector", "document.forms[\"sskExtraForm\"].elements[\"ssk_type\"].value = this.options[this.options.selectedIndex].value"});
		ssktype.addChild("option", "value", "", "-- select --");
		ssktype.addChild("option", "value", "1", "1");

		cell.addChild(ssktype);

		tableRow = table.addChild("tr");

		cell = tableRow.addChild("td");
		cell.addChild("#", "\u00a0");

		cell = tableRow.addChild("td");
		cell.addChild("input", new String[] { "type", "name", "value" }, new String[] { "checkbox", "ssk_private", "ok" });
		cell.addChild("#", "\u00a0Private");

		tableRow = table.addChild("tr");

		cell = tableRow.addChild("td");
		cell.addChild("#", "Crypto algorithm\u00a0");

		cell = tableRow.addChild("td");
		cell.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", "crypto_algo", "7" });
		cell.addChild("#", "\u00a0");

		HTMLNode cryptoselect = new HTMLNode("select", new String[] {"name", "onchange"}, new String[] {"crypto_selector", "document.forms[\"sskExtraForm\"].elements[\"crypto_algo\"].value = this.options[this.options.selectedIndex].value"});
		cryptoselect.addChild("option", "value", "", "-- select --");
		cryptoselect.addChild("option", "value", "2", "AES_PCFB_256_SHA256 (2)");

		cell.addChild(cryptoselect);

		tableRow = table.addChild("tr");

		cell = tableRow.addChild("td");
		cell.addChild("#", "Hash algorithm\u00a0");

		cell = tableRow.addChild("td");
		cell.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", "hash_algo", "7" });
		cell.addChild("#", "\u00a0");

		HTMLNode hashselect = new HTMLNode("select", new String[] {"name", "onchange"}, new String[] {"hash_selector", "document.forms[\"sskExtraForm\"].elements[\"hash_algo\"].value = this.options[this.options.selectedIndex].value"});
		hashselect.addChild("option", "value", "", "-- select --");
		hashselect.addChild("option", "value", "1", "SHA256 (1)");

		cell.addChild(hashselect);

		browseForm.addChild("%", "<BR />");
		browseForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "debug", "Compose SSK!" });
		return browseBox;
	}
}
