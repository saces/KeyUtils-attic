/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyExplorer;

import java.io.IOException;

import com.db4o.ObjectContainer;

import freenet.client.FetchContext;
import freenet.client.async.BaseSingleFileFetcher;
import freenet.client.async.ClientContext;
import freenet.client.async.ClientRequester;
import freenet.client.async.KeyListenerConstructionException;
import freenet.keys.ClientKey;
import freenet.keys.ClientKeyBlock;
import freenet.keys.KeyDecodeException;
import freenet.keys.TooBigException;
import freenet.node.LowLevelGetException;
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

	public VerySimpleGet(ClientKey key2, int maxRetries2, FetchContext ctx2,
			ClientRequester parent2) {
		super(key2, maxRetries2, ctx2, parent2, true);
	}

	@Override
	public void onFailure(LowLevelGetException e, Object token, ObjectContainer container, ClientContext context) {
		boolean logMINOR = Logger.shouldLog(Logger.MINOR, this);
		if (logMINOR)
			Logger.minor(this, "onFailure( " + e + " , ...)", e);

		if (!(isFatalError(e.code))) {
			if (retry(container, context)) {
				if (logMINOR)
					Logger.minor(this, "Retrying", new Error("DEBUG"));
				return;
			}
		}
		error = e;
		finished = true;
		synchronized (waiter) {
			waiter.notifyAll();
		}
	}

	@Override
	public void onSuccess(ClientKeyBlock block, boolean fromStore, Object token, ObjectContainer container, ClientContext context) {
		data = extract(block, context);
		ismetadata = block.isMetadata();
		if (data == null)
			return; // failed
		finished = true;
		synchronized (waiter) {
			waiter.notifyAll();
		}
	}

	public Bucket waitForCompletion() throws LowLevelGetException {
		while (!finished) {
			try {
				synchronized (waiter) {
					waiter.wait();
				}
			} catch (InterruptedException e) {
				// Ignore
			}
		}
		if (error != null)
			throw error;
		return data;
	}

	private Bucket extract(ClientKeyBlock block, ClientContext context) {
		Bucket tempdata;
		try {
			// TODO this: make max size a parameter (4MB hardcoded for now)
			// TODO KeyExplorer: ask/warn the user before displaying browser bombs.
			// FIXME
			//   What is the maximum size of an decompressed 32K chunk?
			//   Hah, 2GB is the max insertsize for a compressed single chunk,
			//   its a memory/browser bomb!
			//   The hexdump can be ~4x in size! (8GB+a few K for surrounding html)
			tempdata = block.decode(context.tempBucketFactory, 4 * 1024 * 1024,
					false);
		} catch (KeyDecodeException e1) {
			if (Logger.shouldLog(Logger.MINOR, this))
				Logger.minor(this, "Decode failure: " + e1, e1);
			onFailure(new LowLevelGetException(
					LowLevelGetException.DECODE_FAILED), null, null, context);
			return null;
		} catch (TooBigException e) {
			Logger.error(this, "Should never happens: " + e, e);
			onFailure(new LowLevelGetException(
					LowLevelGetException.INTERNAL_ERROR), null, null, context);
			return null;
		} catch (IOException e) {
			Logger.error(this, "Could not capture data - disk full?: " + e, e);
			onFailure(new LowLevelGetException(
					LowLevelGetException.DECODE_FAILED), null, null, context);
			return null;
		}
		return tempdata;
	}

	public boolean isMetadata() {
		return ismetadata;
	}

	public static boolean isFatalError(int mode) {
		switch (mode) {
		// Low level errors, can be retried
		case LowLevelGetException.DATA_NOT_FOUND:
		case LowLevelGetException.ROUTE_NOT_FOUND:
		case LowLevelGetException.REJECTED_OVERLOAD:
		case LowLevelGetException.TRANSFER_FAILED:
		case LowLevelGetException.RECENTLY_FAILED: // wait a bit, but fine
			return false;
		case LowLevelGetException.INTERNAL_ERROR:
			// Maybe fatal
			return false;

			// Wierd ones
		case LowLevelGetException.CANCELLED:
			return true;

		default:
			Logger.error(VerySimpleGet.class,
					"Do not know if error code ("+mode+") is fatal: " + LowLevelGetException.getMessage(mode));
			return false; // assume it isn't
		}
	}

	public void onFailed(KeyListenerConstructionException e, ObjectContainer container, ClientContext context) {
		Logger.error(this, "TODO?: " + e, e);
	}
}
