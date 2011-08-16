/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyUtils.toadlets;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.Date;

import freenet.client.DefaultMIMETypes;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.l10n.PluginL10n;
import freenet.support.Logger;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;
import freenet.support.plugins.helpers1.InvisibleWebInterfaceToadlet;
import freenet.support.plugins.helpers1.PluginContext;

/**
 * @author saces
 *
 */
public class StaticToadlet extends InvisibleWebInterfaceToadlet {

	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(StaticToadlet.class);
	}

	/** The path used as prefix when loading resources. */
	private final String resourcePathPrefix;

	/** The MIME type for the files this path contains. */
	private final String mimeType;

	private final PluginL10n _intl;

	public StaticToadlet(PluginContext context, String pluginUri, String path, String rezPath, String mime, PluginL10n intl) {
		super(context, pluginUri, path, null);
		resourcePathPrefix = rezPath;
		mimeType = mime;
		_intl = intl;
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {

		String path = normalizePath(request.getPath());
		if (logDEBUG) Logger.debug(this, "Requested ressource: "+path);
		int lastSlash = path.lastIndexOf('/');
		String filename = path.substring(lastSlash + 1);
		String fn = resourcePathPrefix + filename;
		if (logDEBUG) Logger.debug(this, "Resolved ressource path: "+path);

		InputStream fileInputStream = getClass().getResourceAsStream(fn);
		if (fileInputStream == null) {
			if (logDEBUG) Logger.debug(this, "Ressource not found.");
			ctx.sendReplyHeaders(404, "Not found.", null, null, 0);
			return;
		}

		Bucket data = ctx.getBucketFactory().makeBucket(fileInputStream.available());
		OutputStream os = data.getOutputStream();
		byte[] cbuf = new byte[4096];
		while(true) {
			int r = fileInputStream.read(cbuf);
			if(r == -1) break;
			os.write(cbuf, 0, r);
		}
		fileInputStream.close();
		os.close();

		URL url = getClass().getResource(resourcePathPrefix+path);
		Date mTime = getUrlMTime(url);

		String mime = (mimeType != null) ? (mimeType) : (DefaultMIMETypes.guessMIMEType(path, false));

		ctx.sendReplyHeaders(200, "OK", null, mime, data.size(), mTime);
		ctx.writeData(data);
	}

	/**
	 * Try to find the modification time for a URL, or return null if not possible
	 * We usually load our resources from the JAR, or possibly from a file in some setups, so we check the modification time of
	 * the JAR for resources in a jar and the mtime for files.
	 */
	private Date getUrlMTime(URL url) {
		if (url.getProtocol().equals("jar")) {
			File f = new File(url.getPath().substring(0, url.getPath().indexOf('!')));
			return new Date(f.lastModified());
		} else if (url.getProtocol().equals("file")) {
			File f = new File(url.getPath());
			return new Date(f.lastModified());
		} else {
			return null;
		}
	}

	private String i18n(String key) {
		return _intl.getBase().getString(key);
	}
}
