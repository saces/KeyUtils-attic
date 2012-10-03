/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyUtils.toadlets;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;

import plugins.KeyUtils.KeyExplorerUtils;
import plugins.KeyUtils.KeyUtilsPlugin;
import freenet.client.FetchException;
import freenet.client.HighLevelSimpleClientImpl;
import freenet.client.InsertBlock;
import freenet.client.InsertException;
import freenet.client.Metadata;
import freenet.client.Metadata.SimpleManifestComposer;
import freenet.client.MetadataParseException;
import freenet.client.MetadataUnresolvedException;
import freenet.clients.http.InfoboxNode;
import freenet.clients.http.PageNode;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.keys.ClientCHK;
import freenet.keys.FreenetURI;
import freenet.l10n.PluginL10n;
import freenet.node.RequestStarter;
import freenet.support.HTMLNode;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;
import freenet.support.io.FileUtil;
import freenet.support.io.FileUtil.OperatingSystem;
import freenet.support.plugins.helpers1.PluginContext;
import freenet.support.plugins.helpers1.WebInterfaceToadlet;

/**
 * @author Sadao
 *
 */
public class KeyConverterToadlet extends WebInterfaceToadlet {

	private static final String PARAM_GETKEYONLY = "getkeyonly";

	private static final int KEY_MAX_LENGTH = 1024;
	private static final int FILENAME_MAX_LENGTH = 255;
	private static final int MAX_RECURSION = 10;

	private final PluginL10n _intl;

	public KeyConverterToadlet(PluginContext context, PluginL10n intl) {
		super(context, KeyUtilsPlugin.PLUGIN_URI, "KeyConverter");
		_intl = intl;
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		makeMainPage(ctx, new LinkedList<String>(), "", "", false, false);
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		List<String> errors = new LinkedList<String>();
		if (!isFormPassword(request))
			errors.add("Invalid form password");
		String origFileKey = request.getPartAsStringFailsafe(Globals.PARAM_URI, KEY_MAX_LENGTH + 1);
		String newFilename = request.getPartAsStringFailsafe(Globals.PARAM_FILENAME, FILENAME_MAX_LENGTH + 1);
		boolean getKeyOnly = request.isPartSet(PARAM_GETKEYONLY);
		makeMainPage(ctx, errors, origFileKey, newFilename, getKeyOnly, true);
	}

	private void makeMainPage(ToadletContext ctx, List<String> errors, String origFileKey, String newFilename, boolean getKeyOnly, boolean doIt) throws ToadletContextClosedException, IOException {
		assert errors != null && origFileKey != null && newFilename != null;

		PageNode page = pluginContext.pageMaker.getPageNode(i18n("KeyKonverter.PageTitle"), ctx);
		HTMLNode content = page.content;

		if (!errors.isEmpty()) {
			content.addChild(createErrorBox(errors));
			content.addChild(createFormBox(origFileKey, newFilename, getKeyOnly));
			writeHTMLReply(ctx, 200, "OK", page.outer.generate());
			return;
		}

		if (!doIt) {
			content.addChild(createFormBox(origFileKey, newFilename, getKeyOnly));
			writeHTMLReply(ctx, 200, "OK", page.outer.generate());
			return;
		}

		FreenetURI newFileKey = convert(errors, origFileKey, newFilename, getKeyOnly);
		if (!errors.isEmpty()) {
			content.addChild(createErrorBox(errors));
			content.addChild(createFormBox(origFileKey, newFilename, getKeyOnly));
			writeHTMLReply(ctx, 200, "OK", page.outer.generate());
			return;
		}

		content.addChild(createResultBox(newFileKey, getKeyOnly));
		writeHTMLReply(ctx, 200, "OK", page.outer.generate());
	}

	private FreenetURI convert(List<String> errors, String origFileKey, String newFilename, boolean getKeyOnly) {
		assert errors != null && origFileKey != null && newFilename != null;

		if (origFileKey.length() > KEY_MAX_LENGTH)
			errors.add("Original file key is too long");
		if (origFileKey.isEmpty())
			errors.add("Original file key is not specified");

		if (newFilename.length() > FILENAME_MAX_LENGTH)
			errors.add("New filename is too long");
		if (newFilename.isEmpty())
			errors.add("New filename is not specified");

		if (!errors.isEmpty())
			return null;

		FreenetURI uri = null;
		try {
			uri = new FreenetURI(origFileKey);
		} catch (MalformedURLException e) {
			errors.add("Original file key has wrong format");
		}

		if (!newFilename.equals(FileUtil.sanitizeFileName(newFilename, OperatingSystem.All, "")))
			errors.add("New filename is not valid");

		if (!errors.isEmpty())
			return null;

		// A loop to follow redirects
		Metadata metadata = null;
		byte[] cryptoKey = null;
		int recursionLevel = 0;
		while (true) {
			if (++recursionLevel > MAX_RECURSION) {
				errors.add("Too much recursion");
				return null;
			}

			// Fetch the metadata block
			try {
				metadata = KeyExplorerUtils.simpleManifestGet(pluginContext.pluginRespirator, uri);
			} catch (FetchException e) {
				errors.add("Fetch error (" + e.mode + "): " + e.getMessage());
			} catch (MetadataParseException e) {
				errors.add("Metadata parse error: " + e.getMessage());
			} catch (IOException e) {
				errors.add("IO error: " + e.getMessage());
			}

			if (!errors.isEmpty())
				return null;

			// Remove a manifest wrapper, if any. We will create a new one later.
			if (metadata.isSimpleManifest()) {
				Metadata manifest = metadata;
				if (manifest.countDocuments() != 1) {
					errors.add("Manifest is not a single-file manifest");
					return null;
				}
				metadata = manifest.getDefaultDocument();
				if (metadata == null)
					metadata = manifest.getDocuments().values().iterator().next();
			}

			if (metadata.isSimpleRedirect()) {
				if (!metadata.isSplitfile()) {
					uri = metadata.getSingleTarget();

					// Follow redirects to all keys that point to the metadata.
					// We assume that all keys other than CHK always point to
					// the metadata. For CHK keys we check the extra bytes.
					if (!uri.isCHK())
						continue;
					ClientCHK targetCHK = null;
					try {
						targetCHK = new ClientCHK(uri);
					} catch (MalformedURLException e) {
						errors.add("Redirection key has wrong format: " + uri);
						return null;
					}
					if (targetCHK.isMetadata())
						continue;

					// The metadata points to a single data block here. There
					// is no easy way to determine whether a file was inserted
					// with the default crypto key derived from a block hash
					// or it was inserted with a random crypto key. Therefore
					// for such small files we always insert the top block with
					// the explicitly specified crypto key, equal to the crypto
					// key of a redirection URI.
					cryptoKey = uri.getCryptoKey();
				}
			} else if (!metadata.isMultiLevelMetadata()) {
				errors.add("Unsupported metadata type");
				return null;
			}
			break;
		}
		assert metadata != null;

		// If a crypto key is specified in the metadata, use it to insert the top block
		if (metadata.getCustomSplitfileKey() != null)
			cryptoKey = metadata.getCustomSplitfileKey();

		// Create a new manifest
		SimpleManifestComposer smc = new SimpleManifestComposer();
		smc.addItem(newFilename, metadata);
		metadata = smc.getMetadata();

		// and insert it
		FreenetURI newFileKey = null;
		try {
			Bucket metadataBucket = metadata.toBucket(pluginContext.clientCore.tempBucketFactory);
			InsertBlock block = new InsertBlock(metadataBucket, null, FreenetURI.EMPTY_CHK_URI);
			assert pluginContext.hlsc instanceof HighLevelSimpleClientImpl;
			HighLevelSimpleClientImpl hlsc = (HighLevelSimpleClientImpl)pluginContext.hlsc;
			newFileKey = hlsc.insert(block, getKeyOnly, newFilename, true, RequestStarter.INTERACTIVE_PRIORITY_CLASS,
					hlsc.getInsertContext(true), cryptoKey);
		} catch (IOException e) {
			errors.add("IO error: " + e.getMessage());
		} catch (MetadataUnresolvedException e) {
			errors.add("Metadata unresolved error: " + e.getMessage());
		} catch (InsertException e) {
			errors.add("Insert error: " + e.getMessage());
		}

		if (!errors.isEmpty())
			return null;

		return newFileKey;
	}

	private HTMLNode createFormBox(String origFileKey, String newFilename, boolean getKeyOnly) {
		assert origFileKey != null && newFilename != null;

		InfoboxNode box = pluginContext.pageMaker.getInfobox("Convert a file key of any type to CHK with optional filename changing");
		HTMLNode content = box.content;

		content.addChild("#", "Fetches the top-level metadata block of the specified file key, creates a new single-file manifest for it using the specified filename, inserts the manifest back into Freenet as CHK and returns a new file key. Any redundant redirects are removed. The file can be reinserted to the gotten CHK key in the usual way if the correct crypto key is specified as an insert option.");
		content.addChild("br");
		content.addChild("br");

		HTMLNode form = pluginContext.pluginRespirator.addFormChild(content, path(), "form");
		form.addChild("#", "Original file key:");
		form.addChild("br");
		form.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", Globals.PARAM_URI, "70", origFileKey });
		form.addChild("br");
		form.addChild("#", "New filename:");
		form.addChild("br");
		form.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", Globals.PARAM_FILENAME, "70", newFilename });
		form.addChild("br");
		if (getKeyOnly)
			form.addChild("input", new String[] { "type", "name", "value", "checked" }, new String[] { "checkbox", PARAM_GETKEYONLY, "ok", "checked" });
		else
			form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "checkbox", PARAM_GETKEYONLY, "ok" });
		form.addChild("#", "Get new file key only (don't insert anything)");
		form.addChild("br");
		form.addChild("br");
		form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "debug", "Convert" });

		return box.outer;
	}

	private HTMLNode createResultBox(FreenetURI newFileKey, boolean getKeyOnly) {
		assert newFileKey != null && newFileKey.hasMetaStrings();

		InfoboxNode box = pluginContext.pageMaker.getInfobox("infobox-information", getKeyOnly ? "The file key has NOT been converted (only a new key was calculated)." : "The file key has been successfully converted.");
		HTMLNode content = box.content;

		content.addChild("#", "New file key:");
		content.addChild("br");

		// Workaround to avoid URL-encoding of the filename
		FreenetURI key = newFileKey.setMetaString(null);
		String filename = newFileKey.getMetaString();
		content.addChild("#", key.toString() + "/" + filename.replace("%", "%25"));

		content.addChild("br");
		content.addChild("br");
		content.addChild(new HTMLNode("a", "href", path(), "Convert another key"));

		return box.outer;
	}

	private String i18n(String key) {
		return _intl.getBase().getString(key);
	}
}