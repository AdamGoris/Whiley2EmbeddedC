package wyec;

import java.io.IOException;

import wyc.lang.WhileyFile;
import wyec.io.EmbeddedCFilePrinter;
import wyfs.lang.Content;
import wyfs.lang.Content.Type;
import wyfs.lang.Path;
import wyfs.lang.Path.Entry;
import wyfs.util.DirectoryRoot;
import wyfs.util.Trie;
import wyfs.util.VirtualRoot;
import wyil.io.WyilFileReader;
import wyil.lang.WyilFile;

public class WyEC {
	/**
	 * Default implementation of a content registry. This associates whiley and
	 * wyil files with their respective content types.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Registry implements Content.Registry {

		public void associate(Entry e) {
			String suffix = e.suffix();

			if (suffix.equals("wyil")) {
				e.associate(WyilFile.ContentType, null);
			}
		}

		public String suffix(Type<?> t) {
			return t.getSuffix();
		}
	}

	public static void main(String[] args) {
		try {
			Content.Registry registry = new Registry();
			DirectoryRoot root = new DirectoryRoot(".", registry);
			Path.Entry<WyilFile> srcFile = root.create(Trie.ROOT.append(args[0]), WyilFile.ContentType);
			WyilFileReader r = new WyilFileReader(srcFile);
			WyilFile wyilFile = r.read();
			new EmbeddedCFilePrinter(System.out).apply(wyilFile);
		} catch(IOException e) {
			System.out.println(e.getMessage());
		}
	}
}
