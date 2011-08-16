/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyUtils.toadlets;

import java.io.IOException;
import java.net.URI;
import plugins.KeyUtils.KeyUtilsPlugin;
import plugins.KeyUtils.Version;
import freenet.clients.http.InfoboxNode;
import freenet.clients.http.PageNode;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.l10n.PluginL10n;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;
import freenet.support.plugins.helpers1.PluginContext;
import freenet.support.plugins.helpers1.WebInterfaceToadlet;

/**
 * @author saces
 *
 */
public class AboutToadlet extends WebInterfaceToadlet {

	private final PluginL10n _intl;

	public AboutToadlet(PluginContext context, PluginL10n intl) {
		super(context, KeyUtilsPlugin.PLUGIN_URI, "About");
		_intl = intl;
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {

		if (!normalizePath(request.getPath()).equals("/")) {
			sendErrorPage(ctx, 404, "Not found", "the path '"+uri+"' was not found");
			return;
		}

		makeMainPage(ctx);
	}

	private PageNode getPageNode(ToadletContext ctx) {
		return pluginContext.pageMaker.getPageNode(i18n("About.PageTitle"), ctx);
	}

	private void makeMainPage(ToadletContext ctx) throws ToadletContextClosedException, IOException {
		PageNode page = getPageNode(ctx);
		HTMLNode outer = page.outer;
		HTMLNode contentNode = page.content;

		contentNode.addChild(createVersionBox(pluginContext));
		//TODO contentNode.addChild(createFeatureBox(pluginContext));
		contentNode.addChild(createReportBox(pluginContext));

		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}

	private HTMLNode createVersionBox(PluginContext pCtx) {
		InfoboxNode box = pCtx.pageMaker.getInfobox("KeyUtils version info");
		HTMLNode infoBox = box.outer;
		HTMLNode infoContent = box.content;

		infoContent.addChild("#", "This is KeyUtils "+Version.longVersionString+" ("+Version.version+')');
		return infoBox;
	}

	private HTMLNode createFeatureBox(PluginContext pCtx) {
		InfoboxNode box = pCtx.pageMaker.getInfobox("KeyUtils feature and function summary");
		HTMLNode infoBox = box.outer;
		HTMLNode infoContent = box.content;

		infoContent.addChild("#", "TODO: list and explain features");
		return infoBox;
	}

	private HTMLNode createReportBox(PluginContext pCtx) {
		InfoboxNode box = pCtx.pageMaker.getInfobox("Report bugs and wishes");
		HTMLNode infoBox = box.outer;
		HTMLNode infoContent = box.content;

		infoContent.addChild("#", "Report bugs at github (");
		HTMLNode buglink = new HTMLNode("a", "href", "/external-link/?_CHECKED_HTTP_=https://github.com/saces/KeyUtils/issues", "https://github.com/saces/KeyUtils/issues");
		infoContent.addChild(buglink);
		infoContent.addChild("#", ") or report them in sone ");
		infoContent.addChild(new HTMLNode("nobr", "@sone://MYLAnId-ZEyXhDGGbYOa1gOtkZZrFNTXjFl1dibLj9E"));
		return infoBox;
	}

	private String i18n(String key) {
		return _intl.getBase().getString(key);
	}
}
