/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyUtils;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Set;

import freenet.client.async.BinaryBlob;
import freenet.client.async.BinaryBlobFormatException;
import freenet.client.async.BlockSet;
import freenet.keys.ClientKey;
import freenet.keys.ClientKeyBlock;
import freenet.keys.Key;
import freenet.keys.KeyBlock;

public class FBlobUtils {

	public interface FBlobParserCallback {
		void onKeyBlock(KeyBlock block);
	}

	private static class BlockSetWrapper implements BlockSet {

		private final FBlobParserCallback cb;

		BlockSetWrapper(FBlobParserCallback callBack) {
			cb = callBack;
		}

		@Override
		public KeyBlock get(Key key) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void add(KeyBlock block) {
			cb.onKeyBlock(block);
		}

		@Override
		public Set<Key> keys() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ClientKeyBlock get(ClientKey key) {
			throw new UnsupportedOperationException();
		}
	}

	public static void parseFBlob(DataInputStream fblob, FBlobParserCallback cb) throws IOException, BinaryBlobFormatException {
		BlockSetWrapper bsw = new BlockSetWrapper(cb);
		BinaryBlob.readBinaryBlob(fblob, bsw, true);
	}
}
