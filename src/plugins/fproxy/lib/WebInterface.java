package plugins.fproxy.lib;

import java.util.Vector;

import freenet.clients.http.PageMaker;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContainer;
import freenet.pluginmanager.FredPluginL10n;

public class WebInterface {

	private final Vector<WebInterfaceToadlet> _toadlets;
	private final ToadletContainer _container;
	private final PageMaker _pageMaker;

	public WebInterface(final PluginContext context) {
		_toadlets = new Vector<WebInterfaceToadlet>();
		_container = context.pluginRespirator.getToadletContainer();
		_pageMaker = context.pageMaker;
	}

	public void addNavigationCategory(String uri, String category, String title, FredPluginL10n plugin) {
		_pageMaker.addNavigationCategory(uri, category, title, plugin);
	}

	public void removeNavigationCategory(String category) {
		_pageMaker.removeNavigationCategory(category);
	}

	public void kill() {
		for (WebInterfaceToadlet toadlet : _toadlets) {
			_container.unregister(toadlet);
		}
		_toadlets.clear();
	}

	public void registerVisible(Toadlet toadlet, String category, String urlPrefix, String name, String title) {
		_container.register(toadlet, category, urlPrefix, true, name, title, false, null);
	}

	public void registerInvisible(Toadlet toadlet, String urlPrefix) {
		_container.register(toadlet , null, urlPrefix, true, false);
	}
}
