/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyUtils.toadlets;

import plugins.KeyUtils.KeyUtilsPlugin;
import freenet.l10n.PluginL10n;
import freenet.support.HTMLNode;

/**
 * functions shared between toadlets
 * 
 * @author saces
 *
 */
public class Utils {

	static HTMLNode makeDonateFooter(PluginL10n intl) {
		HTMLNode n = new HTMLNode("div", "style", "text-align: center; margin-top: 1em; border-top: solid 1px #ccc; padding-top: 2em;");
		n.addChild("a", new String[] {"href", "title"}, new String[] {KeyUtilsPlugin.PLUGIN_URI+"/About#Donate", i18n(intl, "Donate.tooltip")}, i18n(intl, "Donate.title"));
		return n;
	}

	private static String i18n(PluginL10n intl, String key) {
		return intl.getBase().getString(key);
	}

}
