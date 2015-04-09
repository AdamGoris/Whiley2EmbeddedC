package wyec.io;

import java.io.PrintStream;

import wyil.lang.Code;
import wyil.lang.CodeBlock;
import wyil.lang.Codes;
import wyil.lang.Type;
import wyil.lang.WyilFile;

public class EmbeddedCFileWriter {
	private PrintStream out;
	
	public EmbeddedCFileWriter(PrintStream out) {
		this.out = out;
	}
	
	public void write(WyilFile module) {
		for(WyilFile.Block b : module.blocks()) {
			if(b instanceof WyilFile.FunctionOrMethod) {
				write((WyilFile.FunctionOrMethod)b);
			}
		}
	}
	
	public void write(WyilFile.FunctionOrMethod fm) {
		Type.FunctionOrMethod fmt = fm.type();
		write(fmt.ret());
		out.print(" ");
		out.print(fm.name());
		out.print("(");
		out.println(") {");
		write(fm.body());
		out.println("}");
	}
	
	public void write(Type t) {
		if(t instanceof Type.Bool) {
			out.print("int");
		} else if(t instanceof Type.Int) {
			out.print("int");
		}
	}
	
	public void write(CodeBlock block) {
		for(int i=0;i!=block.size();++i) {
			out.print("    ");
			write(block.get(i));
		}
	}
	
	public void write(Code c) {
		if(c instanceof Codes.Assign) {
			write((Codes.Assign) c);
		} else if(c instanceof Codes.BinaryOperator) {
			write((Codes.BinaryOperator) c);
		} else if(c instanceof Codes.Return) {
			write((Codes.Return) c);
		}
	}
	
	public void write(Codes.Assign c) {		
		out.print(reg(c.target()) + " = " + reg(c.operand(0)) + ";");
	}
	
	public void write(Codes.BinaryOperator c) {
		out.print(reg(c.target()) + " = " + reg(c.operand(0)) + " " + binOp(c.kind) + " " + reg(c.operand(1)) + ";");
	}

	public void write(Codes.Return c) {
		out.print("return");
		if(c.operand != Codes.NULL_REG) {
			out.print(" " + reg(c.operand));
		}
		out.println(";");
	}
	
	private String binOp(Codes.BinaryOperatorKind kind) {
		return kind.toString();
	}
	
	private String reg(int register) {
		// FIXME: in future, could use declare variable names.
		return "r" + register;
	}
}
