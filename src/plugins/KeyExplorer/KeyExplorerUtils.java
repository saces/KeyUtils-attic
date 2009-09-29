/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyExplorer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;

import com.db4o.ObjectContainer;

import freenet.client.ArchiveContext;
import freenet.client.ClientMetadata;
import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.FetchWaiter;
import freenet.client.HighLevelSimpleClient;
import freenet.client.Metadata;
import freenet.client.MetadataParseException;
import freenet.client.async.ClientContext;
import freenet.client.async.ClientGetState;
import freenet.client.async.ClientGetter;
import freenet.client.async.ManifestElement;
import freenet.client.async.GetCompletionCallback;
import freenet.client.async.KeyListenerConstructionException;
import freenet.client.async.SnoopBucket;
import freenet.client.async.SplitFileFetcher;
import freenet.keys.FreenetURI;
import freenet.node.RequestClient;
import freenet.node.RequestStarter;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;
import freenet.support.api.Bucket;
import freenet.support.api.BucketFactory;
import freenet.support.compress.Compressor.COMPRESSOR_TYPE;
import freenet.support.io.BucketTools;

public class KeyExplorerUtils {

	private static class SnoopGetter implements SnoopBucket {

		private GetResult result;
		private final BucketFactory _bf;
		
		SnoopGetter (BucketFactory bf) {
			_bf = bf;
		}

		public boolean snoopBucket(Bucket data, boolean isMetadata,
				ObjectContainer container, ClientContext context) {
			Bucket temp;
			try {
				temp = _bf.makeBucket(data.size());
				BucketTools.copy(data, temp);
			} catch (IOException e) {
				Logger.error(this, "Bucket error, disk full?", e);
				return true;
			}
			result = new GetResult(temp, isMetadata);
			return true;
		}
	}

	public static Metadata simpleManifestGet(PluginRespirator pr, FreenetURI uri) throws MetadataParseException, FetchException, IOException {
		GetResult res = simpleGet(pr, uri);
		if (!res.isMetaData()) {
			throw new MetadataParseException("uri did not point to metadata " + uri);
		}
		return Metadata.construct(res.getData());
	}

	public static GetResult simpleGet(PluginRespirator pr, FreenetURI uri) throws FetchException {
		SnoopGetter snooper = new SnoopGetter(pr.getNode().clientCore.tempBucketFactory);
		FetchContext context = pr.getHLSimpleClient().getFetchContext();
		FetchWaiter fw = new FetchWaiter();
		ClientGetter get = new ClientGetter(fw, uri, context, RequestStarter.INTERACTIVE_PRIORITY_CLASS, (RequestClient)pr.getHLSimpleClient(), null, null);
		get.setBucketSnoop(snooper);

		try {
			get.start(null, pr.getNode().clientCore.clientContext);
			fw.waitForCompletion();
		} catch (FetchException e) {
			if (snooper.result == null) {
				// really an error
				Logger.error(KeyExplorerUtils.class, "pfehler", e);
				throw e;
			}
		}

		return snooper.result;
	}

	public static FetchResult splitGet(PluginRespirator pr, Metadata metadata) throws FetchException, MetadataParseException,
			KeyListenerConstructionException {

		final FetchWaiter fw = new FetchWaiter();

		GetCompletionCallback cb = new GetCompletionCallback() {

			public void onBlockSetFinished(ClientGetState state, ObjectContainer container, ClientContext context) {
				// TODO Auto-generated method stub
			}

			public void onExpectedMIME(String mime, ObjectContainer container, ClientContext context) {
				// TODO Auto-generated method stub
			}

			public void onExpectedSize(long size, ObjectContainer container, ClientContext context) {
				// TODO Auto-generated method stub
			}

			public void onFailure(FetchException e, ClientGetState state, ObjectContainer container, ClientContext context) {
				// TODO Auto-generated method stub
				fw.onFailure(e, null, container);
			}

			public void onFinalizedMetadata(ObjectContainer container) {
				// TODO Auto-generated method stub
			}

			public void onSuccess(FetchResult result, ClientGetState state, ObjectContainer container, ClientContext context) {
				// meta = Metadata.construct(result.asBucket());
				// System.out.println("HEHEHE!!!YEAH!!!");
				fw.onSuccess(result, null, container);
				// fresult = result;
			}

			public void onTransition(ClientGetState oldState, ClientGetState newState, ObjectContainer container) {
				// TODO Auto-generated method stub

			}
		};

		List<COMPRESSOR_TYPE> decompressors = new LinkedList<COMPRESSOR_TYPE>();
		FetchContext ctx = pr.getHLSimpleClient().getFetchContext();
		boolean deleteFetchContext = false;
		ClientMetadata clientMetadata = null;
		ArchiveContext actx = null;
		int recursionLevel = 0;
		Bucket returnBucket = null;
		long token = 0;
		if (metadata.isCompressed()) {
			COMPRESSOR_TYPE codec = metadata.getCompressionCodec();
			decompressors.add(codec);
		}
		VerySimpleGetter vsg = new VerySimpleGetter((short) 1, null, (RequestClient) pr.getHLSimpleClient());
		SplitFileFetcher sf = new SplitFileFetcher(metadata, cb, vsg, ctx, deleteFetchContext, decompressors, clientMetadata, actx, recursionLevel, returnBucket, token,
				null, pr.getNode().clientCore.clientContext);

		// VerySimpleGetter vsg = new VerySimpleGetter((short) 1, uri,
		// (RequestClient) pr.getHLSimpleClient());
		// VerySimpleGet vs = new VerySimpleGet(ck, 0,
		// pr.getHLSimpleClient().getFetchContext(), vsg);
		sf.schedule(null, pr.getNode().clientCore.clientContext);
		// fw.waitForCompletion();
		return fw.waitForCompletion();
	}

	public static Metadata splitManifestGet(PluginRespirator pr, Metadata metadata) throws MetadataParseException, IOException, FetchException, KeyListenerConstructionException {
		FetchResult res = splitGet(pr, metadata);
		return Metadata.construct(res.asBucket());
	}

	public static Metadata zipManifestGet(PluginRespirator pr, FreenetURI uri) throws FetchException, MetadataParseException, IOException {
		HighLevelSimpleClient hlsc = pr.getHLSimpleClient();
		FetchContext fctx = hlsc.getFetchContext();
		fctx.returnZIPManifests = true;
		FetchWaiter fw = new FetchWaiter();
		hlsc.fetch(uri, -1, (RequestClient) hlsc, fw, fctx);
		FetchResult fr = fw.waitForCompletion();
		ZipInputStream zis = new ZipInputStream(fr.asBucket().getInputStream());
		ZipEntry entry;
		ByteArrayOutputStream bos;
		while (true) {
			entry = zis.getNextEntry();
			if (entry == null)
				break;
			if (entry.isDirectory())
				continue;
			String name = entry.getName();
			if (".metadata".equals(name)) {
				byte[] buf = new byte[32768];
				bos = new ByteArrayOutputStream();
				// Read the element
				int readBytes;
				while ((readBytes = zis.read(buf)) > 0) {
					bos.write(buf, 0, readBytes);
				}
				bos.close();
				return Metadata.construct(bos.toByteArray());
			}
		}
		throw new FetchException(200, "impossible? no metadata in archive " + uri);
	}

	public static Metadata tarManifestGet(PluginRespirator pr, Metadata md, String metaName) throws FetchException, MetadataParseException, IOException {
		FetchResult fr;
		try {
			fr = splitGet(pr, md);
		} catch (KeyListenerConstructionException e) {
			throw new FetchException(FetchException.INTERNAL_ERROR, e);
		}
		return internalTarManifestGet(fr.asBucket(), metaName);
	}

	public static Metadata tarManifestGet(PluginRespirator pr, FreenetURI uri, String metaName) throws FetchException, MetadataParseException, IOException {
		HighLevelSimpleClient hlsc = pr.getHLSimpleClient();
		FetchContext fctx = hlsc.getFetchContext();
		fctx.returnZIPManifests = true;
		FetchWaiter fw = new FetchWaiter();
		hlsc.fetch(uri, -1, (RequestClient) hlsc, fw, fctx);
		FetchResult fr = fw.waitForCompletion();
		return internalTarManifestGet(fr.asBucket(), metaName);
	}

	public static Metadata internalTarManifestGet(Bucket data, String metaName) throws IOException, MetadataParseException, FetchException {
		TarInputStream zis = new TarInputStream(data.getInputStream());
		TarEntry entry;
		ByteArrayOutputStream bos;
		while (true) {
			entry = zis.getNextEntry();
			if (entry == null)
				break;
			if (entry.isDirectory())
				continue;
			String name = entry.getName();
			if (metaName.equals(name)) {
				byte[] buf = new byte[32768];
				bos = new ByteArrayOutputStream();
				// Read the element
				int readBytes;
				while ((readBytes = zis.read(buf)) > 0) {
					bos.write(buf, 0, readBytes);
				}
				bos.close();
				return Metadata.construct(bos.toByteArray());
			}
		}
		throw new FetchException(200, "impossible? no metadata in archive ");
	}

	public static FreenetURI sanitizeURI(List<String> errors, String key) throws MalformedURLException {
		if (key == null) throw new NullPointerException();
		FreenetURI uri = new FreenetURI(key);
		return sanitizeURI(errors, uri);
	}

	public static FreenetURI sanitizeURI(List<String> errors, FreenetURI key) throws MalformedURLException {
		if (key == null) throw new NullPointerException();

		FreenetURI tempURI = key;

		//get rid of metas, useles
		if (tempURI.hasMetaStrings()) {
			if (errors != null) {
				tempURI = tempURI.setMetaString(null);
				errors.add("URI did contain meta strings, removed it for you");
			} else {
				throw new MalformedURLException("URIs with meta strings not supported");
			}
		}

		// turn USK into SSK
		if (tempURI.isUSK()) {
			if (errors != null) {
				tempURI = tempURI.sskForUSK();
				errors.add("URI was an USK, converted it to SSK for you");
			} else {
				throw new MalformedURLException("USK not supported, use underlying SSK instead.");
			}
		}

		return tempURI;
	}

	public static HashMap<String, Object> parseMetadata(Metadata oldMetadata, FreenetURI oldUri) throws MalformedURLException {
		return parseMetadata(oldMetadata.getDocuments(), oldUri, "");
	}

	private static HashMap<String, Object> parseMetadata(HashMap<String, Metadata> oldMetadata, FreenetURI oldUri, String prefix) throws MalformedURLException {
		HashMap<String, Object> newMetadata = new HashMap<String, Object>();
		for(Entry<String, Metadata> entry:oldMetadata.entrySet()) {
			Metadata md = entry.getValue();
			String name = entry.getKey();
			if (md.isArchiveInternalRedirect()) {
				String fname = prefix + name;
				FreenetURI newUri = new FreenetURI(oldUri.toString(false, false) + "/"+ fname);
				//System.err.println("NewURI: "+newUri.toString(false, false));
				newMetadata.put(name, new ManifestElement(name, newUri, null));
			} else if (md.isSingleFileRedirect()) {
				newMetadata.put(name, new ManifestElement(name, md.getSingleTarget(), null));
			} else if (md.isSplitfile()) {
				newMetadata.put(name, new ManifestElement(name, md.getSingleTarget(), null));
			} else {
				newMetadata.put(name, parseMetadata(md.getDocuments(), oldUri, prefix + name + "/"));
			}
		}
		return newMetadata;
	}

	private byte[] doDownload(PluginRespirator pluginRespirator, List<String> errors, String key) {
	
		if (errors.size() > 0) {
			return null;
		}
		if (key == null || (key.trim().length() == 0)) {
			errors.add("Are you jokingly? Empty URI");
			return null;
		}
		try {
			FreenetURI furi = sanitizeURI(errors, key);
			GetResult getresult = simpleGet(pluginRespirator, furi);
			if (getresult.isMetaData()) {
				return unrollMetadata(pluginRespirator, errors, Metadata.construct(getresult.getData()));
			} else {
				return BucketTools.toByteArray(getresult.getData());
			}
		} catch (MalformedURLException e) {
			errors.add(e.getMessage());
			e.printStackTrace();
		} catch (MetadataParseException e) {
			errors.add(e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			errors.add(e.getMessage());
			e.printStackTrace();
		} catch (FetchException e) {
			errors.add(e.getMessage());
			e.printStackTrace();
		} catch (KeyListenerConstructionException e) {
			errors.add(e.getMessage());
			e.printStackTrace();
		}
		return null;
	}

	public static byte[] unrollMetadata(PluginRespirator pluginRespirator, List<String> errors, Metadata md) throws MalformedURLException, IOException, FetchException, MetadataParseException, KeyListenerConstructionException {
	
		if (!md.isSplitfile()) {
			errors.add("Unsupported Metadata: Not a Splitfile");
			return null;
		}
		byte[] result = null;
		result = BucketTools.toByteArray(splitGet(pluginRespirator, md).asBucket());
		return result;
	}

}
