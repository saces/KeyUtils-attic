/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
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

	public VerySimpleGet(ClientKey key2, int maxRetries2, FetchContext ctx2,
			ClientRequester parent2) {
		super(key2, maxRetries2, ctx2, parent2);
	}

	public void onFailure(LowLevelGetException e, Object token,
			RequestScheduler sched) {
		boolean logMINOR = Logger.shouldLog(Logger.MINOR, this);
		if (logMINOR)
			Logger.minor(this, "onFailure( " + e + " , ...)", e);

		if (!(isFatalError(e.code))) {
			if (retry(sched, getContext().executor)) {
				if (logMINOR)
					Logger.minor(this, "Retrying");
				return;
			}
		}
		error = e;
		finished = true;
		synchronized (waiter) {
			waiter.notifyAll();
		}
	}

	public void onSuccess(ClientKeyBlock block, boolean fromStore,
			Object token, RequestScheduler scheduler) {
		data = extract(block);
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

	private Bucket extract(ClientKeyBlock block) {
		Bucket tempdata;
		try {
			// FIXME What is the maximim size of an decompressed 32K chunk?
			tempdata = block.decode(getContext().bucketFactory, 1024 * 1024,
					false);
		} catch (KeyDecodeException e1) {
			if (Logger.shouldLog(Logger.MINOR, this))
				Logger.minor(this, "Decode failure: " + e1, e1);
			onFailure(new LowLevelGetException(
					LowLevelGetException.DECODE_FAILED), null, null);
			return null;
		} catch (TooBigException e) {
			Logger.error(this, "Should never happens: " + e, e);
			onFailure(new LowLevelGetException(
					LowLevelGetException.INTERNAL_ERROR), null, null);
			return null;
		} catch (IOException e) {
			Logger.error(this, "Could not capture data - disk full?: " + e, e);
			onFailure(new LowLevelGetException(
					LowLevelGetException.DECODE_FAILED), null, null);
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
}
