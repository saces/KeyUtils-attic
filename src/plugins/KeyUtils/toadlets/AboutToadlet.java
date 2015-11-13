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
		contentNode.addChild(createDonateBox(pluginContext));

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

		if (pCtx.pluginRespirator.isOfficialPlugin()) {
			infoContent.addChild("#", "Report bugs at the Freenet butracker (");
			HTMLNode buglink = new HTMLNode("a", "href", "/external-link/?_CHECKED_HTTP_=https://bugs.freenetproject.org/view_all_bug_page.php?project_id=16", "https://bugs.freenetproject.org");
			infoContent.addChild(buglink);
			infoContent.addChild("#", ")");
		} else {
			HTMLNode p = new HTMLNode("p");
			p.addChild("#", "If you got this plugin from an official source (from freenetproject.org) report bugs at the Freenet butracker (");
			HTMLNode buglink = new HTMLNode("a", "href", "/external-link/?_CHECKED_HTTP_=https://bugs.freenetproject.org/view_all_bug_page.php?project_id=16", "https://bugs.freenetproject.org");
			p.addChild(buglink);
			p.addChild("#", ")");
			infoContent.addChild(p);
			p = new HTMLNode("p");
			p.addChild("#", "Report bugs at github (");
			buglink = new HTMLNode("a", "href", "/external-link/?_CHECKED_HTTP_=https://github.com/saces/KeyUtils/issues", "https://github.com/saces/KeyUtils/issues");
			p.addChild(buglink);
			p.addChild("#", ") or report them in sone ");
			p.addChild(new HTMLNode("nobr", "@sone://MYLAnId-ZEyXhDGGbYOa1gOtkZZrFNTXjFl1dibLj9E"));
			infoContent.addChild(p);
		}
		return infoBox;
	}

	private HTMLNode createDonateBox(PluginContext pCtx) {
		InfoboxNode box = pCtx.pageMaker.getInfobox("Donate");
		HTMLNode donateBox = box.outer;
		HTMLNode donateContent = box.content;

		donateContent.addChild("#", "Donate");

		HTMLNode flattr = new HTMLNode("p");
		HTMLNode flattrlink = flattr.addChild(new HTMLNode("a", "href", "/external-link/?_CHECKED_HTTP_=https://flattr.com/thing/374147/KeyUtils"));
		flattrlink.addChild(new HTMLNode("img", new String[] {"src", "align"}, new String[] {"images/flattr-badge-large.png", "middle"}));
		HTMLNode small = new HTMLNode("small");
		small.addChild("#", "\u00a0https://flattr.com/thing/374147/KeyUtils");
		flattr.addChild(small);
		donateContent.addChild(flattr);

		HTMLNode btc = new HTMLNode("p");
		btc.addChild(new HTMLNode("img", new String[] {"src", "align"}, new String[] {"images/th_Bitcoinorg_100x35_new.png", "middle"}));
		btc.addChild("#", "\u00a0144vvEyH6zWYtPYMiuaKvUtVu4B3uSeToW");
		donateContent.addChild(btc);
		return donateBox;
	}

	private String i18n(String key) {
		return _intl.getBase().getString(key);
	}
}
