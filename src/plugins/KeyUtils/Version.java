/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyUtils;

/**
 * @author saces
 *
 */
public class Version {

	/** SVN revision number. Only set if the plugin is compiled properly e.g. by emu. */
	public static final String gitRevision = "@custom@";

	/** Version number of the plugin for getRealVersion(). Increment this on making
	 * a major change, a significant bugfix etc. These numbers are used in auto-update 
	 * etc, at a minimum any build inserted into auto-update should have a unique 
	 * version. */
	public static final long version = 5025;

	public static final String longVersionString = "0.5.3 " + gitRevision;

	/**
	 * just prints the version number to standard out. intended to be used
	 * by build scripts those depends on keyutils
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println(version);
	}
}
