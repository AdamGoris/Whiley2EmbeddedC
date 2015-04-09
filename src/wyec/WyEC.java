package wyec;

import java.io.IOException;

import wyec.io.EmbeddedCFileWriter;
import wyec.util.IntegerRangeAnalysis;
import wyil.io.WyilFileReader;
import wyil.lang.WyilFile;

public class WyEC {
	public static void main(String[] args) {
		try {
			WyilFileReader r = new WyilFileReader(args[0]);
			WyilFile wyilFile = r.read();
			IntegerRangeAnalysis ira = new IntegerRangeAnalysis();			
			ira.apply(wyilFile);
			new EmbeddedCFileWriter(System.out).write(wyilFile);
		} catch(IOException e) {
			System.out.println(e.getMessage());
		}
	}
}
