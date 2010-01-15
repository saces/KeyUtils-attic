/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyUtils;

import plugins.KeyUtils.toadlets.DownloadToadlet;
import plugins.KeyUtils.toadlets.ExtraToadlet;
import plugins.KeyUtils.toadlets.KeyExplorerToadlet;
import plugins.KeyUtils.toadlets.SiteExplorerToadlet;
import plugins.KeyUtils.toadlets.SplitExplorerToadlet;
import freenet.l10n.BaseL10n.LANGUAGE;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginFCP;
import freenet.pluginmanager.FredPluginL10n;
import freenet.pluginmanager.FredPluginRealVersioned;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.plugins.helpers1.PluginContext;
import freenet.support.plugins.helpers1.WebInterface;

/**
 * @author saces
 *
 */
public class KeyUtilsPlugin implements FredPlugin, /*FredPluginHTTP,*/ FredPluginL10n, FredPluginFCP, FredPluginThreadless, FredPluginVersioned, FredPluginRealVersioned {

	private static volatile boolean logMINOR;
	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(KeyUtilsPlugin.class);
	}

	public static final String PLUGIN_URI = "/KeyExplorer";
	private static final String PLUGIN_CATEGORY = "Key Tools";
	public static final String PLUGIN_TITLE = "KeyExplorer Plugin";

	private WebInterface webInterface;
	private PluginContext pluginContext;
	private FCPHandler fcpHandler;

	public void handle(PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) {
		try {
			fcpHandler.handle(replysender, params, data, accesstype);
		} catch (PluginNotFoundException pnfe) {
			Logger.error(this, "Connction to request sender Lost.", pnfe);
		}
	}

	public void runPlugin(PluginRespirator pr) {

		if (logMINOR)
			Logger.minor(this, "Initialising KeyExplorer.");

		pluginContext = new PluginContext(pr);
		webInterface = new WebInterface(pluginContext);
		webInterface.addNavigationCategory(PLUGIN_URI+"/", PLUGIN_CATEGORY, "Toolbox for debugging Freenet URIs and more...", this);

		fcpHandler = new FCPHandler(pluginContext);

		// Visible pages
		KeyExplorerToadlet keyToadlet = new KeyExplorerToadlet(pluginContext);
		webInterface.registerVisible(keyToadlet, PLUGIN_CATEGORY, "KeyExplorer", "Explore a Freenet URI");
		SiteExplorerToadlet siteToadlet = new SiteExplorerToadlet(pluginContext);
		webInterface.registerVisible(siteToadlet, PLUGIN_CATEGORY, "SiteExplorer", "Explore a site manifest");
		SplitExplorerToadlet splitToadlet = new SplitExplorerToadlet(pluginContext);
		webInterface.registerVisible(splitToadlet, PLUGIN_CATEGORY, "SplitExplorer", "Explore a split file");
		ExtraToadlet extraToadlet = new ExtraToadlet(pluginContext);
		webInterface.registerVisible(extraToadlet, PLUGIN_CATEGORY, "Extra Calculator", "Compose and decompose Freenet URI extra data");

		// Invisible pages
		DownloadToadlet dlToadlet = new DownloadToadlet(pluginContext, keyToadlet);
		webInterface.registerInvisible(dlToadlet);

		if (logMINOR)
			Logger.minor(this, "Initialising KeyExplorer done.");

	}

	public void terminate() {
		// TODO kill all 'session handles'
		// TODO kill all requests
		webInterface.kill();
		webInterface = null;
		fcpHandler.kill();
		fcpHandler = null;
	}

	public String getVersion() {
		return Version.longVersionString;
	}

	public String getString(String key) {
		// disable, it is just to loud in log for now
//		if (logDEBUG)
//			Logger.debug(this, "GetKey : "+key);
		return key;
	}

	public void setLanguage(LANGUAGE selectedLanguage) {
		if (logDEBUG)
			Logger.debug(this, "Setlang to: "+selectedLanguage.fullName);
	}

	public long getRealVersion() {
		return Version.version;
	}
}
