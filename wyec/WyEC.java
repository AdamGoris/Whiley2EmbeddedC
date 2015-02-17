package wyec;

import java.io.IOException;

import wyec.util.IntegerRangeAnalysis;
import wyil.io.WyilFileReader;

public class WyEC {
	public static void main(String[] args) {
		try {
			WyilFileReader r = new WyilFileReader(args[0]);
			IntegerRangeAnalysis ira = new IntegerRangeAnalysis();
			ira.apply(r.read());
		} catch(IOException e) {
			System.out.println(e.getMessage());
		}
	}
}
