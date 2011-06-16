/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyUtils.toadlets;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;

import plugins.KeyUtils.FBlobUtils;
import plugins.KeyUtils.FBlobUtils.FBlobParserCallback;
import plugins.KeyUtils.KeyUtilsPlugin;

import freenet.client.async.BinaryBlobFormatException;
import freenet.clients.http.PageNode;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.keys.KeyBlock;
import freenet.keys.NodeCHK;
import freenet.keys.NodeSSK;
import freenet.support.Base64;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;
import freenet.support.api.HTTPUploadedFile;
import freenet.support.plugins.helpers1.PluginContext;
import freenet.support.plugins.helpers1.WebInterfaceToadlet;

/**
 * @author saces
 *
 */
public class FBlobToadlet extends WebInterfaceToadlet {

	public FBlobToadlet(PluginContext context) {
		super(context, KeyUtilsPlugin.PLUGIN_URI, "FBlob");
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		List<String> errors = new LinkedList<String>();
		makeMainPage(ctx, errors, null);
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		List<String> errors = new LinkedList<String>();

		if (!isFormPassword(request)) {
			errors.add("Invalid form password");
			makeMainPage(ctx, errors, null);
			return;
		}

		final HTTPUploadedFile file = request.getUploadedFile(Globals.PARAM_FILENAME);
		if (file == null || file.getFilename().trim().length() == 0) {
			errors.add("Common.NoFileSelected");
			makeMainPage(ctx, errors, null);
			return;
		}
		makeMainPage(ctx, errors, file);
	}

	private void makeMainPage(ToadletContext ctx, List<String> errors, HTTPUploadedFile file) throws ToadletContextClosedException, IOException {
		PageNode page = pluginContext.pageMaker.getPageNode(KeyUtilsPlugin.PLUGIN_TITLE, ctx);
		HTMLNode outer = page.outer;
		HTMLNode contentNode = page.content;

		if (errors.size() > 0) {
			contentNode.addChild(createErrorBox(errors));
			errors.clear();
		}

		HTMLNode fileBox = pluginContext.pageMaker.getInfobox("infobox-information", "Show the content of a FBlob file", contentNode);
		fileBox.addChild("#", "Select a FBlob file to display:");
		HTMLNode fileForm = pluginContext.pluginRespirator.addFormChild(fileBox, path(), "uriForm");
		fileForm.addChild("#", "File: \u00a0 ");
		fileForm.addChild("input", new String[] { "type", "name", "size" }, new String[] { "file", Globals.PARAM_FILENAME, "70" });
		fileForm.addChild("#", "\u00a0");
		fileForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "debug", "View" });

		if (file != null) {
			HTMLNode contentBox = pluginContext.pageMaker.getInfobox("infobox-information", "Content of '"+file.getFilename()+"'", contentNode);
			HTMLNode contentTable = contentBox.addChild("table");
			HTMLNode tableHead = contentTable.addChild("thead");
			HTMLNode tableRow = tableHead.addChild("tr");
			HTMLNode nextTableCell = tableRow.addChild("th");
			nextTableCell.addChild("#", "\u00a0");
			nextTableCell = tableRow.addChild("th");
			nextTableCell.addChild("#", "Type");
			nextTableCell = tableRow.addChild("th");
			nextTableCell.addChild("#", "Key");
			nextTableCell = tableRow.addChild("th");
			nextTableCell.addChild("#", "Internal Name");
			parseFblob(file, contentTable, errors);
		}
		if (errors.size() > 0) {
			contentNode.addChild(createErrorBox(errors));
		}
		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}

	private static class ParserCallBack implements FBlobParserCallback {
		private final HTMLNode n;
		private int counter;
		ParserCallBack(HTMLNode node) {
			n = node;
			counter = 0;
		}
		@Override
		public void onKeyBlock(KeyBlock block) {
			HTMLNode row = n.addChild("tr");
			HTMLNode nextCell = row.addChild("td");
			nextCell.addChild("#", Integer.toString(counter++));
			nextCell = row.addChild("td");
			byte buf = (byte) (block.getKey().getType() >> 8);
			String type = null;
			if (buf == NodeCHK.BASE_TYPE) {
				type = "CHK";
				nextCell.addChild("#", type);
			} else if (buf == NodeSSK.BASE_TYPE) {
				type = "SSK";
				nextCell.addChild("#", type);
			} else {
				nextCell.addChild("#", "Unknown: "+buf);
			}
			nextCell = row.addChild("td");
			if (type != null) {
				String key = type + '@' + Base64.encode(block.getRoutingKey());
				nextCell.addChild("#", key);
			} else {
				nextCell.addChild("#", "\u00a0");
			}
			nextCell = row.addChild("td");
			nextCell.addChild("#", block.getKey().toString());
		}
	}

	private void parseFblob(HTTPUploadedFile file, HTMLNode contentBox, List<String> errors) {
		FBlobParserCallback cb = new ParserCallBack(contentBox);
		DataInputStream dis;
		try {
			dis = new DataInputStream(file.getData().getInputStream());
		} catch (IOException e) {
			Logger.error(this, "Hu? Unable to aquire uploaded data", e);
			errors.add("Hu? Unable to aquire uploaded data");
			return;
		}
		try {
			FBlobUtils.parseFBlob(dis, cb);
		} catch (IOException e) {
			errors.add("Hu? IO Error: "+e.getLocalizedMessage());
		} catch (BinaryBlobFormatException e) {
			errors.add("Blob Format Error: "+e.getLocalizedMessage());
		}
	}
}
