/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyUtils;

import plugins.KeyUtils.toadlets.AboutToadlet;
import plugins.KeyUtils.toadlets.DownloadToadlet;
import plugins.KeyUtils.toadlets.ExtraToadlet;
import plugins.KeyUtils.toadlets.FBlobToadlet;
import plugins.KeyUtils.toadlets.KeyConverterToadlet;
import plugins.KeyUtils.toadlets.KeyExplorerToadlet;
import plugins.KeyUtils.toadlets.SiteExplorerToadlet;
import plugins.KeyUtils.toadlets.SplitExplorerToadlet;
import plugins.KeyUtils.toadlets.StaticToadlet;
import freenet.config.SubConfig;
import freenet.l10n.BaseL10n.LANGUAGE;
import freenet.l10n.PluginL10n;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginBaseL10n;
import freenet.pluginmanager.FredPluginConfigurable;
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
public class KeyUtilsPlugin implements FredPlugin, FredPluginL10n, FredPluginFCP, FredPluginThreadless, FredPluginVersioned, FredPluginRealVersioned, FredPluginConfigurable, FredPluginBaseL10n {

	private static volatile boolean logMINOR;
	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(KeyUtilsPlugin.class);
	}

	public static final String PLUGIN_URI = "/KeyUtils";
	private static final String PLUGIN_CATEGORY = "ConfigToadlet.plugins.KeyUtils.KeyUtilsPlugin.label";

	private WebInterface webInterface;
	private PluginContext pluginContext;
	private FCPHandler fcpHandler;
	private PluginL10n intl;

	@Override
	public void handle(PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) {
		try {
			fcpHandler.handle(replysender, params, data, accesstype);
		} catch (PluginNotFoundException pnfe) {
			Logger.error(this, "Connection to request sender Lost.", pnfe);
		}
	}

	@Override
	public void runPlugin(PluginRespirator pr) {

		if (logMINOR)
			Logger.minor(this, "Initialising Key Utils.");

		if (intl == null) {
			intl = new PluginL10n(this);
		}

		pluginContext = new PluginContext(pr);
		webInterface = new WebInterface(pluginContext);
		webInterface.addNavigationCategory(PLUGIN_URI+"/", PLUGIN_CATEGORY, "Plugin.Category.tooltip", this);

		fcpHandler = new FCPHandler(pluginContext);

		// Visible pages
		KeyExplorerToadlet keyToadlet = new KeyExplorerToadlet(pluginContext, intl);
		webInterface.registerVisible(keyToadlet, PLUGIN_CATEGORY, "Menu.KeyExplorer.title", "Menu.KeyExplorer.tooltip");
		SiteExplorerToadlet siteToadlet = new SiteExplorerToadlet(pluginContext, intl);
		webInterface.registerVisible(siteToadlet, PLUGIN_CATEGORY, "Menu.SiteExplorer.title", "Menu.SiteExplorer.tooltip");
		SplitExplorerToadlet splitToadlet = new SplitExplorerToadlet(pluginContext, intl);
		webInterface.registerVisible(splitToadlet, PLUGIN_CATEGORY, "Menu.SplitExplorer.title", "Menu.SplitExplorer.tooltip");
		ExtraToadlet extraToadlet = new ExtraToadlet(pluginContext, intl);
		webInterface.registerVisible(extraToadlet, PLUGIN_CATEGORY, "Menu.ExtraCalculator.title", "Menu.ExtraCalculator.tooltip");
		FBlobToadlet fblobToadlet = new FBlobToadlet(pluginContext, intl);
		webInterface.registerVisible(fblobToadlet, PLUGIN_CATEGORY, "Menu.FBlobViewer.title", "Menu.FBlobViewer.tooltip");
		KeyConverterToadlet keyConverterToadlet = new KeyConverterToadlet(pluginContext, intl);
		webInterface.registerVisible(keyConverterToadlet, PLUGIN_CATEGORY, "Menu.KeyConverter.title", "Menu.KeyConverter.tooltip");
		AboutToadlet aboutToadlet = new AboutToadlet(pluginContext, intl);
		webInterface.registerVisible(aboutToadlet, PLUGIN_CATEGORY, "Menu.About.title", "Menu.About.tooltip");

		// Invisible pages
		DownloadToadlet dlToadlet = new DownloadToadlet(pluginContext, keyToadlet, intl);
		webInterface.registerInvisible(dlToadlet);
		StaticToadlet cssToadlet = new StaticToadlet(pluginContext, KeyUtilsPlugin.PLUGIN_URI, "css", "/data/css/", "text/css", intl);
		webInterface.registerInvisible(cssToadlet);
		StaticToadlet picToadlet = new StaticToadlet(pluginContext, KeyUtilsPlugin.PLUGIN_URI, "images", "/data/images/", "image/png", intl);
		webInterface.registerInvisible(picToadlet);

		if (logMINOR)
			Logger.minor(this, "Initialising Key Utils done.");

	}

	@Override
	public void terminate() {
		// TODO kill all 'session handles'
		// TODO kill all requests
		webInterface.kill();
		webInterface = null;
		fcpHandler.kill();
		fcpHandler = null;
	}

	@Override
	public String getVersion() {
		return Version.longVersionString;
	}

	@Override
	public String getString(String key) {
		if (logDEBUG)
			Logger.debug(this, "GetKey : "+key);
		String s = intl.getBase().getString(key);
		return s != null ? s : key;
	}

	@Override
	public void setLanguage(LANGUAGE selectedLanguage) {
		if (logDEBUG)
			Logger.debug(this, "Setlang to: "+selectedLanguage.fullName);
		if (intl == null) {
			intl = new PluginL10n(this, selectedLanguage);
			return;
		}
		intl.getBase().setLanguage(selectedLanguage);
	}

	@Override
	public long getRealVersion() {
		return Version.version;
	}

	@Override
	public void setupConfig(SubConfig subconfig) {
		Configuration.initialize(subconfig);
	}

	@Override
	public String getL10nFilesBasePath() {
		return "plugins/KeyUtils/intl/";
	}

	@Override
	public String getL10nFilesMask() {
		return "${lang}.txt";
	}

	@Override
	public String getL10nOverrideFilesMask() {
		return "KeyUtils.${lang}.override.txt";
	}

	@Override
	public ClassLoader getPluginClassLoader() {
		return this.getClass().getClassLoader();
	}
}
