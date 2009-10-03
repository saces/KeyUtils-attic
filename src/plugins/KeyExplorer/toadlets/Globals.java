/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyExplorer.toadlets;

/**
 * constants shared between toadlets
 * 
 * @author saces
 *
 */
public class Globals {

	static final String MFTYPE_TAR = "TARmanifest";
	static final String MFTYPE_ZIP = "ZIPmanifest";
	static final String MFTYPE_SIMPLE = "simplemanifest";
	static final String MFTYPE_AUTO = "auto";

	static final String PARAM_URI = "key";
	static final String PARAM_MFTYPE = "mftype";
	static final String PARAM_RECURSIVE = "deep";
	static final String PARAM_MULTILEVEL = "ml";
	static final String PARAM_LEVEL = "level";
}
