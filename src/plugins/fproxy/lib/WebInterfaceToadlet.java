package plugins.fproxy.lib;

import freenet.clients.http.LinkEnabledCallback;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;

public abstract class WebInterfaceToadlet extends Toadlet implements LinkEnabledCallback {

	private final String _pluginURL;
	private final String _pageName;
	protected final PluginContext pluginContext;

	private final String _path;

	protected WebInterfaceToadlet(PluginContext pluginContext2, String pluginURL, String pageName) {
		super(pluginContext2.hlsc);
		pluginContext = pluginContext2;
		_pageName = pageName;
		_pluginURL = pluginURL;
		_path = _pluginURL + "/" + _pageName;
	}

	@Override
	public String path() {
		return _path;
	}

	public boolean isEnabled(ToadletContext ctx) {
		return true;
	}

	protected String normalizePath(String path) {
		String result = path.substring(_path.length());
		if (result.length() == 0) {
			return "/";
		}
		return result;
	}
}
