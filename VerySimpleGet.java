/**
 * 
 */
package plugins.KeyExplorer;

import java.io.IOException;

import freenet.client.FetchContext;
import freenet.client.async.BaseSingleFileFetcher;
import freenet.client.async.ClientRequester;
import freenet.keys.ClientKey;
import freenet.keys.ClientKeyBlock;
import freenet.keys.KeyDecodeException;
import freenet.keys.TooBigException;
import freenet.node.LowLevelGetException;
import freenet.node.RequestScheduler;
import freenet.support.Logger;
import freenet.support.api.Bucket;

/**
 * @author saces
 *
 */
public class VerySimpleGet extends BaseSingleFileFetcher {
	
	private boolean finished = false;
	private LowLevelGetException error;
	private Bucket data;
	private final Object waiter = new Object();
	private boolean ismetadata;

	public VerySimpleGet(ClientKey key2, int maxRetries2, FetchContext ctx2, ClientRequester parent2) {
		super(key2, maxRetries2, ctx2, parent2);
	}

	public void onFailure(LowLevelGetException e, Object token, RequestScheduler sheduler) {
		error = e;
		finished = true;
		synchronized(waiter)
		{
			waiter.notifyAll();
		}
	}

	public void onSuccess(ClientKeyBlock block, boolean fromStore, Object token, RequestScheduler sheduler) {
		data = extract(block);
		ismetadata = block.isMetadata();
		if(data == null) return; // failed
		finished = true;
		synchronized(waiter)
		{
			waiter.notifyAll();
		}
	}
	
	public Bucket waitForCompletion() throws LowLevelGetException {
		while(!finished) {
			try {
				synchronized(waiter)
				{
					waiter.wait();
				}
			} catch (InterruptedException e) {
				// Ignore
			}
		}
		if(error != null) throw error;
		return data;
	}
	
	private Bucket extract(ClientKeyBlock block) {
		Bucket tempdata;
		try {
			// FIXME What is the maximim size of an decompressed 32K chunk?
			tempdata = block.decode(getContext().bucketFactory, 1024*1024, false);
		} catch (KeyDecodeException e1) {
			if(Logger.shouldLog(Logger.MINOR, this))
				Logger.minor(this, "Decode failure: "+e1, e1);
			onFailure(new LowLevelGetException(LowLevelGetException.DECODE_FAILED), null, null);
			return null;
		} catch (TooBigException e) {
			Logger.error(this, "Should never happens: "+e, e);
			onFailure(new LowLevelGetException(LowLevelGetException.INTERNAL_ERROR), null, null);
			return null;
		} catch (IOException e) {
			Logger.error(this, "Could not capture data - disk full?: "+e, e);
			onFailure(new LowLevelGetException(LowLevelGetException.DECODE_FAILED), null, null);
			return null;
		}
		return tempdata;
	}

	public boolean isMetadata() {
		return ismetadata;
	}
}
