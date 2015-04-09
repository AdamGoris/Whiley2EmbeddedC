package wyec.lang;

import java.util.Arrays;

import wyec.util.IntegerRangeAnalysis;
import wyil.lang.Attribute;

/**
 * Maps each variable in a given block to a range type, or null if that
 * variable is undefined.
 * 
 * @author David J. Pearce
 *
 */
public class RangeFrame implements Attribute {
	/**
	 * 
	 */
	private final IntegerRangeAnalysis RangeFrame;
	private final IntegerRange[] ranges;

	public RangeFrame(IntegerRangeAnalysis integerRangeAnalysis, int numVars) {
		RangeFrame = integerRangeAnalysis;
		this.ranges = new IntegerRange[numVars];
	}

	public RangeFrame(IntegerRangeAnalysis integerRangeAnalysis, IntegerRange[] ranges) {
		RangeFrame = integerRangeAnalysis;
		this.ranges = Arrays.copyOf(ranges, ranges.length);
	}

	public void write(int var, IntegerRange range) {
		ranges[var] = range;
	}

	public IntegerRange read(int var) {
		return ranges[var];
	}

	public void havoc(int var) {
		if (ranges[var] != null) {
			ranges[var] = IntegerRange.TOP;
		}
	}

	/**
	 * Join two environments together by conservatively merging the
	 * information contained in both together.
	 * 
	 * @param other
	 * @return
	 */
	public RangeFrame join(RangeFrame other) {
		RangeFrame r = new RangeFrame(RangeFrame, ranges.length);
		for (int i = 0; i != ranges.length; ++i) {
			IntegerRange r1 = ranges[i];
			IntegerRange r2 = other.ranges[i];
			if (r1 != null && r2 != null) {
				r.ranges[i] = r1.union(r2);
			}
		}
		return r;
	}

	/**
	 * Return a clone of the given environment
	 */
	public RangeFrame clone() {
		return new RangeFrame(RangeFrame, ranges);
	}
	
	public String toString() {
		String r = "";
		for(int i=0;i!=ranges.length;++i) {
			if(i != 0) {
				r += ", ";
			}
			r += ranges[i];
		}
		return r;
	}
}