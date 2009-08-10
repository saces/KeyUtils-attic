package plugins.fproxy.lib;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.PageMaker;
import freenet.node.NodeClientCore;
import freenet.pluginmanager.PluginRespirator;

public class PluginContext {

	public final PluginRespirator pluginRespirator;
	public final NodeClientCore clientCore;
	public final PageMaker pageMaker;
	public final HighLevelSimpleClient hlsc;

	public PluginContext(PluginRespirator pluginRespirator2) {
		this.pluginRespirator = pluginRespirator2;
		this.clientCore = pluginRespirator.getNode().clientCore;
		this.pageMaker = pluginRespirator.getPageMaker();
		this.hlsc = pluginRespirator.getHLSimpleClient();
	}
}
