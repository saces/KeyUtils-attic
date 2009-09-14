package plugins.fproxy.lib;

import freenet.clients.http.Toadlet;

public class InvisibleWebInterfaceToadlet extends WebInterfaceToadlet {

	private final Toadlet _showAsToadlet;

	protected InvisibleWebInterfaceToadlet(PluginContext pluginContext2,
			String pluginURL2, String pageName2, Toadlet showAsToadlet) {
		super(pluginContext2, pluginURL2, pageName2);
		_showAsToadlet = showAsToadlet;
	}

	@Override
	public Toadlet showAsToadlet() {
		return _showAsToadlet;
	}
}
