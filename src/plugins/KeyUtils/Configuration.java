/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.KeyUtils;

import freenet.config.InvalidConfigValueException;
import freenet.config.NodeNeedRestartException;
import freenet.config.SubConfig;
import freenet.support.api.BooleanCallback;
import freenet.support.api.ShortCallback;

public class Configuration {

	static class AutoMFOption extends BooleanCallback {
		@Override
		public Boolean get() {
			return autoMF;
		}

		@Override
		public void set(Boolean val) throws InvalidConfigValueException,
				NodeNeedRestartException {
			if (!val.equals(get())) {
				autoMF = val;
			}
		}
	}

	static class DeepOption extends BooleanCallback {
		@Override
		public Boolean get() {
			return deep;
		}

		@Override
		public void set(Boolean val) throws InvalidConfigValueException,
				NodeNeedRestartException {
			if (!val.equals(get())) {
				deep = val;
			}
		}
	}

	static class MultilevelOption extends BooleanCallback {
		@Override
		public Boolean get() {
			return ml;
		}

		@Override
		public void set(Boolean val) throws InvalidConfigValueException,
				NodeNeedRestartException {
			if (!val.equals(get())) {
				ml = val;
			}
		}
	}

	static class HexWidthOption extends ShortCallback {
		@Override
		public Short get() {
			return hexWidth;
		}

		@Override
		public void set(Short val) throws InvalidConfigValueException,
				NodeNeedRestartException {
			if (!val.equals(get())) {
				hexWidth = val;
			}
		}
	}

	private static boolean autoMF;
	private static boolean deep;
	private static boolean ml;
	private static short hexWidth;

	public static final String OPTION_AUTOMF = "autoMF";
	public static final String OPTION_DEEP = "deep";
	public static final String OPTION_HEXWIDTH = "hexWidth";
	public static final String OPTION_MULTILEVEL = "ml";

	static void initialize(SubConfig subconfig) {
		short sortOrder = 0;
		subconfig.register(OPTION_AUTOMF, false, sortOrder, true, true, "Config.autoMF", "Config.autoMFLong", new AutoMFOption());
		autoMF = subconfig.getBoolean(OPTION_AUTOMF);
		subconfig.register(OPTION_DEEP, false, sortOrder++, true, false, "Config.recursive", "Config.recursiveLong", new DeepOption());
		deep = subconfig.getBoolean(OPTION_DEEP);
		subconfig.register(OPTION_MULTILEVEL, false, sortOrder++, true, false, "Config.multilevel", "Config.multilevelLong", new MultilevelOption());
		deep = subconfig.getBoolean(OPTION_MULTILEVEL);
		subconfig.register(OPTION_HEXWIDTH, (short)32, sortOrder++, true, false, "Config.hexWidth", "Config.hexWidthLong", new HexWidthOption(), false);
		hexWidth = subconfig.getShort(OPTION_HEXWIDTH);
	}

	public static int getHexWidth() {
		return hexWidth;
	}

	public static boolean getAutoMF() {
		return autoMF;
	}

	public static boolean getDeep() {
		return deep;
	}

	public static boolean getMultilevel() {
		return ml;
	}
}
