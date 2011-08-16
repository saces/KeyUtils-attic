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
	 * version.
	 * 
	 * I have set this to 3000 to allow encoding marketing versions into real versions.
	 * Long provides plenty of leeway! Toad. */
	public static final long version = 5011;

	public static final String longVersionString = "0.5.2 " + gitRevision;

}
