package wyec.io;

import java.util.*;
import java.io.PrintStream;

import wyil.attributes.VariableDeclarations;
import wyil.lang.Code;
import wyil.lang.CodeBlock;
import wyil.lang.Codes;
import wyil.lang.Type;
import wyil.lang.WyilFile;
import wyil.lang.CodeBlock.Index;
import wyec.lang.*;
import wyec.util.VariableRangesMap;

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
		VariableRangesMap ranges = fm.attribute(VariableRangesMap.class);
		Type.FunctionOrMethod fmt = fm.type();
		write(fmt.ret(),null);
		out.print(" ");
		out.print(fm.name());
		out.print("(");
		List<Type> fmt_params = fmt.params();
		for(int i=0;i!=fmt_params.size();++i) {
			if(i != 0) {
				out.print(", ");
			}
			write(fmt_params.get(i),determineRange(i,ranges,fm.body()));
			out.print(" r" + i);
		}
		out.println(") {");
		write(fm.body());
		out.println("}");
	}
	
	public void write(Type t, Range r) {
		if(t instanceof Type.Bool) {
			out.print("int");
		} else if(t instanceof Type.Int) {			
			// Check whether can further constrain type
			if(r instanceof IntegerRange) {
				IntegerRange ir = (IntegerRange) r;
				out.print(determineBounds(ir));
			} else {
				out.print("int");
			}
		}
	}
	
	public IntegerRange determineRange(int var, VariableRangesMap ranges,
			CodeBlock root) {
		IntegerRange ir = null;
		for (int i = 0; i != root.size(); ++i) {
			CodeBlock.Index idx = new CodeBlock.Index(null, i);
			RangeFrame rf = ranges.get(idx);
			if (rf != null) {
				Range r = rf.read(var);
				if (r instanceof IntegerRange) {
					IntegerRange rr = (IntegerRange) r;
					ir = ir == null ? rr : ir.union(rr);
				}
			}
		}
		return ir;
	}
	
	private static final IntegerRange i8 = new IntegerRange(-128,127);
	private static final IntegerRange i16 = new IntegerRange(-32768,32767);
	private static final IntegerRange i32 = new IntegerRange(Integer.MIN_VALUE,Integer.MAX_VALUE);	
	private static final IntegerRange u8 = new IntegerRange(0,255);
	private static final IntegerRange u16 = new IntegerRange(0,65535);
	private static final IntegerRange u32 = new IntegerRange(0,4294967296L);
	
	public String determineBounds(IntegerRange r) {
		if(i8.contains(r)) {
			return "int8_t";
		} else if(u8.contains(r)) {
			return "uint8_t";
		} else if(i16.contains(r)) {
			return "int16_t";
		} else if(u16.contains(r)) {
			return "uint16_t";
		} else if(i32.contains(r)) {
			return "int32_t";
		} else if(u32.contains(r)) {
			return "uint32_t";
		} else {
			return "int";
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
