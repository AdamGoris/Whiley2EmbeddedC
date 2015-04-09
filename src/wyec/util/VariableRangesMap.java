package wyec.util;

import wyec.lang.RangeFrame;
import wyil.lang.Attribute;
import wyil.util.AbstractAttributeMap;

/**
 * Provides an encoding of range type information associated with a given
 * attribute block.
 * 
 * @author David J. Pearce
 *
 */
public class VariableRangesMap extends AbstractAttributeMap<RangeFrame> implements Attribute.Map<RangeFrame>{

	@Override
	public Class<? extends Attribute> type() {
		return RangeFrame.class;
	}

}
