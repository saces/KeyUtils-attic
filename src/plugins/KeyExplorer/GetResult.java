/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyExplorer;

import freenet.support.api.Bucket;

public class GetResult {
	private final Bucket data;
	private final boolean isMetaData;

	public GetResult(Bucket data2, boolean isMetaData2) {
		data = data2;
		isMetaData = isMetaData2;
	}

	public boolean isMetaData() {
		return isMetaData;
	}

	public Bucket getData() {
		return data;
	}
	
	public void free() {
		if (data != null) data.free();
	}


}
