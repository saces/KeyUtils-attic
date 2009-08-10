package plugins.fproxy.lib;

import java.io.IOException;
import java.net.URI;

import freenet.clients.http.LinkEnabledCallback;
import freenet.clients.http.RedirectException;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.api.HTTPRequest;

public abstract class WebInterfaceToadlet extends Toadlet implements LinkEnabledCallback {

	private final String _pluginURL;
	private final String _pageName;
	protected final PluginContext pluginContext;

	protected WebInterfaceToadlet(PluginContext pluginContext2, String pluginURL, String pageName) {
		super(pluginContext2.hlsc);
		pluginContext = pluginContext2;
		_pageName = pageName;
		_pluginURL = pluginURL;
	}

	@Override
	public String path() {
		return _pluginURL + "/" + _pageName;
	}

	public void handlePost(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {
		
		String pass = request.getPartAsString("formPassword", 32);
		if ((pass.length() == 0) || !pass.equals(pluginContext.clientCore.formPassword)) {
			writeHTMLReply(ctx, 403, "Forbidden", "Invalid form password.");
		}
	}

	public boolean isEnabled(ToadletContext ctx) {
		return true;
	}
}
