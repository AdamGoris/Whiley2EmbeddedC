// Copyright (c) 2011, David J. Pearce (djp@ecs.vuw.ac.nz)
// All rights reserved.
//
// This software may be modified and distributed under the terms
// of the BSD license.  See the LICENSE file for details.

package wyec.io;

import java.io.*;
import java.util.*;

import wybs.lang.Build;
import wybs.lang.NameID;
import wyfs.util.Trie;
import wyil.lang.*;
import wyil.lang.Constant;
import wyil.lang.Bytecode.AliasDeclaration;
import wyil.lang.Bytecode.Expr;
import wyil.lang.Bytecode.VariableAccess;
import wyil.lang.Bytecode.VariableDeclaration;
import wyil.lang.SyntaxTree.Location;
import wyil.lang.Type;
import wyil.lang.WyilFile.*;

/**
 * Writes WYIL bytecodes in a textual from to a given file.
 *
 * <b>NOTE:</b> currently, this class is somewhat broken since it does not
 * provide any way to specify the output directory. Rather, it simply puts the
 * WYIL file in the same place as the Whiley file.
 *
 * @author David J. Pearce
 *
 */
public final class EmbeddedCFilePrinter {
	private PrintWriter out;
	private boolean verbose = false;

	public EmbeddedCFilePrinter(Build.Task builder) {

	}

	public EmbeddedCFilePrinter(PrintWriter writer) {
		this.out = writer;
	}

	public EmbeddedCFilePrinter(OutputStream stream) {
		this.out = new PrintWriter(new OutputStreamWriter(stream));
	}

	// ======================================================================
	// Configuration Methods
	// ======================================================================

	public void setVerbose(boolean flag) {
		this.verbose = flag;
	}

	// ======================================================================
	// Apply Method
	// ======================================================================

	public void apply(WyilFile module) throws IOException {
		writeIncludes();
		out.println();

		for(FunctionOrMethod md : module.functionOrMethods()) {
			write(md);
			out.println();
		}
		out.flush();
	}

	private void writeIncludes() {
		out.println("#include <whiley.h>");

	}

	private void write(FunctionOrMethod method) {
		//
		if(verbose) {
			writeLocationsAsComments(method.getTree());
		}
		//
		Type.FunctionOrMethod ft = method.type();

		writeReturns(ft.returns());
		out.print(" ");
		out.print(method.name());
		writeParameters(method);
		//
		if (method.getBody() != null) {
			out.println(" {");
			writeBlock(0, method.getBody());
			out.println("}");
		}
	}

	private void writeLocationsAsComments(SyntaxTree tree) {
		List<Location<?>> locations = tree.getLocations();
		for(int i=0;i!=locations.size();++i) {
			Location<?> loc = locations.get(i);
			String id = String.format("%1$" + 3 + "s", "#" + i);
			String type = String.format("%1$-" + 8 + "s", Arrays.toString(loc.getTypes()));
			out.println("// " + id + " " + type + " " + loc.getBytecode());
		}
	}


	private void writeReturns(Type[] returns) {
		if(returns.length > 1) {
			throw new RuntimeException("Missing support for multiple returns");
		} else if(returns.length == 0) {
			out.print("void");
		} else {
			writeType(returns[0]);
		}
	}
	private void writeParameters(WyilFile.FunctionOrMethod fm) {
		Type.FunctionOrMethod ft = fm.type();
		SyntaxTree tree = fm.getTree();
		Type[] parameters = ft.params();
		out.print("(");
		for (int i = 0; i != parameters.length; ++i) {
			if (i != 0) {
				out.print(", ");
			}
			writeType(parameters[i]);
			out.print(" ");
			Location<VariableDeclaration> d = (Location<VariableDeclaration>) tree.getLocation(i);
			out.print(d.getBytecode().getName());
		}
		out.print(")");
	}

	private void writeBlock(int indent, Location<Bytecode.Block> block) {
		for (int i = 0; i != block.numberOfOperands(); ++i) {
			writeStatement(indent, block.getOperand(i));
		}
	}

	@SuppressWarnings("unchecked")
	private void writeStatement(int indent, Location<?> c) {
		tabIndent(indent+1);
		switch(c.getOpcode()) {
		case Bytecode.OPCODE_aliasdecl:
			writeAliasDeclaration(indent, (Location<Bytecode.AliasDeclaration>) c);
			break;
		case Bytecode.OPCODE_assert:
			writeAssert(indent, (Location<Bytecode.Assert>) c);
			break;
		case Bytecode.OPCODE_assume:
			writeAssume(indent, (Location<Bytecode.Assume>) c);
			break;
		case Bytecode.OPCODE_assign:
			writeAssign(indent, (Location<Bytecode.Assign>) c);
			break;
		case Bytecode.OPCODE_break:
			writeBreak(indent, (Location<Bytecode.Break>) c);
			break;
		case Bytecode.OPCODE_continue:
			writeContinue(indent, (Location<Bytecode.Continue>) c);
			break;
		case Bytecode.OPCODE_debug:
			writeDebug(indent, (Location<Bytecode.Debug>) c);
			break;
		case Bytecode.OPCODE_dowhile:
			writeDoWhile(indent, (Location<Bytecode.DoWhile>) c);
			break;
		case Bytecode.OPCODE_fail:
			writeFail(indent, (Location<Bytecode.Fail>) c);
			break;
		case Bytecode.OPCODE_if:
		case Bytecode.OPCODE_ifelse:
			writeIf(indent, (Location<Bytecode.If>) c);
			break;
		case Bytecode.OPCODE_indirectinvoke:
			writeIndirectInvoke(indent, (Location<Bytecode.IndirectInvoke>) c);
			break;
		case Bytecode.OPCODE_invoke:
			writeInvoke(indent, (Location<Bytecode.Invoke>) c);
			break;
		case Bytecode.OPCODE_namedblock:
			writeNamedBlock(indent, (Location<Bytecode.NamedBlock>) c);
			break;
		case Bytecode.OPCODE_while:
			writeWhile(indent, (Location<Bytecode.While>) c);
			break;
		case Bytecode.OPCODE_return:
			writeReturn(indent, (Location<Bytecode.Return>) c);
			break;
		case Bytecode.OPCODE_skip:
			writeSkip(indent, (Location<Bytecode.Skip>) c);
			break;
		case Bytecode.OPCODE_switch:
			writeSwitch(indent, (Location<Bytecode.Switch>) c);
			break;
		case Bytecode.OPCODE_vardecl:
		case Bytecode.OPCODE_vardeclinit:
			writeVariableDeclaration(indent, (Location<Bytecode.VariableDeclaration>) c);
			break;
		default:
			throw new IllegalArgumentException("unknown bytecode encountered");
		}
	}

	private void writeAliasDeclaration(int indent, Location<AliasDeclaration> loc) {
		out.print("alias ");
		out.print(loc.getType());
		out.print(" ");
		Location<VariableDeclaration> aliased = getVariableDeclaration(loc);
		out.print(aliased.getBytecode().getName());
		out.println(";");
	}
	private void writeAssert(int indent, Location<Bytecode.Assert> c) {
		out.print("assert ");
		writeExpression(c.getOperand(0));
		out.println(";");
	}

	private void writeAssume(int indent, Location<Bytecode.Assume> c) {
		out.print("assume ");
		writeExpression(c.getOperand(0));
		out.println(";");
	}

	private void writeAssign(int indent, Location<Bytecode.Assign> stmt) {
		Location<?>[] lhs = stmt.getOperandGroup(SyntaxTree.LEFTHANDSIDE);
		Location<?>[] rhs = stmt.getOperandGroup(SyntaxTree.RIGHTHANDSIDE);
		if(lhs.length > 0) {
			for(int i=0;i!=lhs.length;++i) {
				if(i!=0) { out.print(", "); }
				writeExpression(lhs[i]);
			}
			out.print(" = ");
		}
		writeExpressions(rhs);
		out.println(";");
	}

	private void writeBreak(int indent, Location<Bytecode.Break> b) {
		out.println("break;");
	}

	private void writeContinue(int indent, Location<Bytecode.Continue> b) {
		out.println("continue;");
	}

	private void writeDebug(int indent, Location<Bytecode.Debug> b) {

	}

	private void writeDoWhile(int indent, Location<Bytecode.DoWhile> b) {
		Location<?>[] loopInvariant = b.getOperandGroup(0);
		Location<?>[] modifiedOperands = b.getOperandGroup(1);
		out.println("do:");
		//
		writeBlock(indent+1,b.getBlock(0));
		tabIndent(indent+1);
		out.print("while ");
		writeExpression(b.getOperand(0));
		out.print(" modifies ");
		writeExpressions(modifiedOperands);
		for(Location<?> invariant : loopInvariant) {
			out.println();
			tabIndent(indent+1);
			out.print("where ");
			writeExpression(invariant);
		}
		// FIXME: add invariants
		out.println();
	}

	private void writeFail(int indent, Location<Bytecode.Fail> c) {

	}

	private void writeIf(int indent, Location<Bytecode.If> b) {
		out.print("if(");
		writeExpression(b.getOperand(0));
		out.println(") {");
		writeBlock(indent+1,b.getBlock(0));
		//
		if(b.numberOfBlocks() > 1) {
			tabIndent(indent+1);
			out.println("} else {");
			writeBlock(indent+1,b.getBlock(1));
		}
		//
		tabIndent(indent+1);
		out.println("}");
	}

	private void writeIndirectInvoke(int indent, Location<Bytecode.IndirectInvoke> stmt) {
		Location<?>[] operands = stmt.getOperands();
		writeExpression(operands[0]);
		out.print("(");
		for(int i=1;i!=operands.length;++i) {
			if(i!=1) {
				out.print(", ");
			}
			writeExpression(operands[i]);
		}
		out.println(")");
	}
	private void writeInvoke(int indent, Location<Bytecode.Invoke> stmt) {
		out.print(stmt.getBytecode().name() + "(");
		Location<?>[] operands = stmt.getOperands();
		for(int i=0;i!=operands.length;++i) {
			if(i!=0) {
				out.print(", ");
			}
			writeExpression(operands[i]);
		}
		out.println(")");
	}

	private void writeNamedBlock(int indent, Location<Bytecode.NamedBlock> b) {
		out.print(b.getBytecode().getName());
		out.println(":");
		writeBlock(indent+1,b.getBlock(0));
	}

	private void writeWhile(int indent, Location<Bytecode.While> b) {
		out.print("while(");
		writeExpression(b.getOperand(0));
		out.println(") {");
		writeBlock(indent+1,b.getBlock(0));
		tabIndent(indent+1);out.println("}");
	}

	private void writeReturn(int indent, Location<Bytecode.Return> b) {
		Location<?>[] operands = b.getOperands();
		out.print("return");
		if(operands.length > 0) {
			out.print(" ");
			writeExpressions(operands);
		}
		out.println(";");
	}

	private void writeSkip(int indent, Location<Bytecode.Skip> b) {
		out.println("// skip");
	}

	private void writeSwitch(int indent, Location<Bytecode.Switch> b) {
		out.print("switch ");
		writeExpression(b.getOperand(0));
		out.println(":");
		for (int i = 0; i != b.numberOfBlocks(); ++i) {
			// FIXME: ugly
			Bytecode.Case cAse = b.getBytecode().cases()[i];
			Constant[] values = cAse.values();
			tabIndent(indent + 2);
			if (values.length == 0) {
				out.println("default:");
			} else {
				out.print("case ");
				for (int j = 0; j != values.length; ++j) {
					if (j != 0) {
						out.print(", ");
					}
					out.print(values[j]);
				}
				out.println(":");
			}
			writeBlock(indent + 2, b.getBlock(i));
		}
	}

	private void writeVariableAccess(Location<VariableAccess> loc) {
		Location<VariableDeclaration> vd = getVariableDeclaration(loc.getOperand(0));
		out.print(vd.getBytecode().getName());
	}

	private void writeVariableDeclaration(int indent, Location<VariableDeclaration> loc) {
		Location<?>[] operands = loc.getOperands();
		writeType(loc.getType());
		out.print(" ");
		out.print(loc.getBytecode().getName());
		if (operands.length > 0) {
			out.print(" = ");
			writeExpression(operands[0]);
		}
		out.println(";");
	}

	/**
	 * Write a bracketed operand if necessary. Any operand whose human-readable
	 * representation can contain whitespace must have brackets around it.
	 *
	 * @param operand
	 * @param enclosing
	 * @param out
	 */
	private void writeBracketedExpression(Location<?> expr) {
		boolean needsBrackets = needsBrackets(expr.getBytecode());
		if (needsBrackets) {
			out.print("(");
		}
		writeExpression(expr);
		if (needsBrackets) {
			out.print(")");
		}
	}

	private void writeExpressions(Location<?>[] exprs) {
		for (int i = 0; i != exprs.length; ++i) {
			if (i != 0) {
				out.print(", ");
			}
			writeExpression(exprs[i]);
		}
	}

	@SuppressWarnings("unchecked")
	private void writeExpression(Location<?> expr) {
		switch (expr.getOpcode()) {
		case Bytecode.OPCODE_arraylength:
			writeArrayLength((Location<Bytecode.Operator>) expr);
			break;
		case Bytecode.OPCODE_arrayindex:
			writeArrayIndex((Location<Bytecode.Operator>) expr);
			break;
		case Bytecode.OPCODE_array:
			writeArrayInitialiser((Location<Bytecode.Operator>) expr);
			break;
		case Bytecode.OPCODE_arraygen:
			writeArrayGenerator((Location<Bytecode.Operator>) expr);
			break;
		case Bytecode.OPCODE_convert:
			writeConvert((Location<Bytecode.Convert>) expr);
			break;
		case Bytecode.OPCODE_const:
			writeConst((Location<Bytecode.Const>) expr);
			break;
		case Bytecode.OPCODE_fieldload:
			writeFieldLoad((Location<Bytecode.FieldLoad>) expr);
			break;
		case Bytecode.OPCODE_indirectinvoke:
			writeIndirectInvoke((Location<Bytecode.IndirectInvoke>) expr);
			break;
		case Bytecode.OPCODE_invoke:
			writeInvoke((Location<Bytecode.Invoke>) expr);
			break;
		case Bytecode.OPCODE_lambda:
			writeLambda((Location<Bytecode.Lambda>) expr);
			break;
		case Bytecode.OPCODE_record:
			writeRecordConstructor((Location<Bytecode.Operator>) expr);
			break;
		case Bytecode.OPCODE_newobject:
			writeNewObject((Location<Bytecode.Operator>) expr);
			break;
		case Bytecode.OPCODE_dereference:
		case Bytecode.OPCODE_logicalnot:
		case Bytecode.OPCODE_neg:
		case Bytecode.OPCODE_bitwiseinvert:
			writePrefixLocations((Location<Bytecode.Operator>) expr);
			break;
		case Bytecode.OPCODE_all:
		case Bytecode.OPCODE_some:
			writeQuantifier((Location<Bytecode.Quantifier>) expr);
			break;
		case Bytecode.OPCODE_add:
		case Bytecode.OPCODE_sub:
		case Bytecode.OPCODE_mul:
		case Bytecode.OPCODE_div:
		case Bytecode.OPCODE_rem:
		case Bytecode.OPCODE_eq:
		case Bytecode.OPCODE_ne:
		case Bytecode.OPCODE_lt:
		case Bytecode.OPCODE_le:
		case Bytecode.OPCODE_gt:
		case Bytecode.OPCODE_ge:
		case Bytecode.OPCODE_logicaland:
		case Bytecode.OPCODE_logicalor:
		case Bytecode.OPCODE_bitwiseor:
		case Bytecode.OPCODE_bitwisexor:
		case Bytecode.OPCODE_bitwiseand:
		case Bytecode.OPCODE_shl:
		case Bytecode.OPCODE_shr:
		case Bytecode.OPCODE_is:
			writeInfixLocations((Location<Bytecode.Operator>) expr);
			break;
		case Bytecode.OPCODE_varaccess:
			writeVariableAccess((Location<VariableAccess>) expr);
			break;
		default:
			throw new IllegalArgumentException("unknown bytecode encountered: " + expr.getBytecode());
		}
	}


	private void writeArrayLength(Location<Bytecode.Operator> expr) {
		out.print("|");
		writeExpression(expr.getOperand(0));
		out.print("|");
	}

	private void writeArrayIndex(Location<Bytecode.Operator> expr) {
		writeExpression(expr.getOperand(0));
		out.print("[");
		writeExpression(expr.getOperand(1));
		out.print("]");
	}

	private void writeArrayInitialiser(Location<Bytecode.Operator> expr) {
		Location<?>[] operands = expr.getOperands();
		out.print("[");
		for(int i=0;i!=operands.length;++i) {
			if(i != 0) {
				out.print(", ");
			}
			writeExpression(operands[i]);
		}
		out.print("]");
	}

	private void writeArrayGenerator(Location<Bytecode.Operator> expr) {
		out.print("[");
		writeExpression(expr.getOperand(0));
		out.print(" ; ");
		writeExpression(expr.getOperand(1));
		out.print("]");
	}
	private void writeConvert(Location<Bytecode.Convert> expr) {
		out.print("(" + expr.getType() + ") ");
		writeExpression(expr.getOperand(0));
	}
	private void writeConst(Location<Bytecode.Const> expr) {
		out.print(expr.getBytecode().constant());
	}
	private void writeFieldLoad(Location<Bytecode.FieldLoad> expr) {
		writeBracketedExpression(expr.getOperand(0));
		out.print("." + expr.getBytecode().fieldName());
	}
	private void writeIndirectInvoke(Location<Bytecode.IndirectInvoke> expr) {
		Location<?>[] operands = expr.getOperands();
		writeExpression(operands[0]);
		out.print("(");
		for(int i=1;i!=operands.length;++i) {
			if(i!=1) {
				out.print(", ");
			}
			writeExpression(operands[i]);
		}
		out.print(")");
	}
	private void writeInvoke(Location<Bytecode.Invoke> expr) {
		out.print(expr.getBytecode().name() + "(");
		Location<?>[] operands = expr.getOperands();
		for(int i=0;i!=operands.length;++i) {
			if(i!=0) {
				out.print(", ");
			}
			writeExpression(operands[i]);
		}
		out.print(")");
	}

	@SuppressWarnings("unchecked")
	private void writeLambda(Location<Bytecode.Lambda> expr) {
		out.print("&[");
		Location<?>[] environment = expr.getOperandGroup(SyntaxTree.ENVIRONMENT);
		for (int i = 0; i != environment.length; ++i) {
			Location<VariableDeclaration> var = (Location<VariableDeclaration>) environment[i];
			if (i != 0) {
				out.print(", ");
			}
			out.print(var.getType());
			out.print(" ");
			out.print(var.getBytecode().getName());
		}
		out.print("](");
		Location<?>[] parameters = expr.getOperandGroup(SyntaxTree.PARAMETERS);
		for (int i = 0; i != parameters.length; ++i) {
			Location<VariableDeclaration> var = (Location<VariableDeclaration>) parameters[i];
			if (i != 0) {
				out.print(", ");
			}
			out.print(var.getType());
			out.print(" ");
			out.print(var.getBytecode().getName());
		}
		out.print(" -> ");
		writeExpression(expr.getOperand(0));
		out.print(")");
	}

	private void writeRecordConstructor(Location<Bytecode.Operator> expr) {
		Type.EffectiveRecord t = (Type.EffectiveRecord) expr.getType();
		String[] fields = t.getFieldNames();
		Location<?>[] operands = expr.getOperands();
		out.print("{");
		for (int i = 0; i != operands.length; ++i) {
			if (i != 0) {
				out.print(", ");
			}
			out.print(fields[i]);
			out.print(" ");
			writeExpression(operands[i]);
		}
		out.print("}");
	}

	private void writeNewObject(Location<Bytecode.Operator> expr) {
		out.print("new ");
		writeExpression(expr.getOperand(0));
	}

	private void writePrefixLocations(Location<Bytecode.Operator> expr) {
		// Prefix operators
		out.print(opcode(expr.getBytecode().kind()));
		writeBracketedExpression(expr.getOperand(0));
	}

	private void writeInfixLocations(Location<Bytecode.Operator> c) {
		writeBracketedExpression(c.getOperand(0));
		out.print(" ");
		out.print(opcode(c.getBytecode().kind()));
		out.print(" ");
		writeBracketedExpression(c.getOperand(1));

	}

	@SuppressWarnings("unchecked")
	private void writeQuantifier(Location<Bytecode.Quantifier> c) {
		out.print(quantifierKind(c));
		out.print(" { ");
		for (int i = 0; i != c.numberOfOperandGroups(); ++i) {
			Location<?>[] range = c.getOperandGroup(i);
			if (i != 0) {
				out.print(", ");
			}
			Location<VariableDeclaration>  v = (Location<VariableDeclaration>) range[SyntaxTree.VARIABLE];
			out.print(v.getBytecode().getName());
			out.print(" in ");
			writeExpression(range[SyntaxTree.START]);
			out.print("..");
			writeExpression(range[SyntaxTree.END]);
		}
		out.print(" | ");
		writeExpression(c.getOperand(SyntaxTree.CONDITION));
		out.print(" } ");
	}

	private String quantifierKind(Location<Bytecode.Quantifier> c) {
		switch(c.getOpcode()) {
		case Bytecode.OPCODE_some:
			return "some";
		case Bytecode.OPCODE_all:
			return "all";
		}
		throw new IllegalArgumentException();
	}

	private boolean needsBrackets(Bytecode e) {
		switch(e.getOpcode()) {
		case Bytecode.OPCODE_convert:
		case Bytecode.OPCODE_add:
		case Bytecode.OPCODE_sub:
		case Bytecode.OPCODE_mul:
		case Bytecode.OPCODE_div:
		case Bytecode.OPCODE_rem:
		case Bytecode.OPCODE_eq:
		case Bytecode.OPCODE_ne:
		case Bytecode.OPCODE_lt:
		case Bytecode.OPCODE_le:
		case Bytecode.OPCODE_gt:
		case Bytecode.OPCODE_ge:
		case Bytecode.OPCODE_logicaland:
		case Bytecode.OPCODE_logicalor:
		case Bytecode.OPCODE_bitwiseor:
		case Bytecode.OPCODE_bitwisexor:
		case Bytecode.OPCODE_bitwiseand:
		case Bytecode.OPCODE_shl:
		case Bytecode.OPCODE_shr:
		case Bytecode.OPCODE_is:
		case Bytecode.OPCODE_newobject:
		case Bytecode.OPCODE_dereference:
			return true;
		}
		return false;
	}

	public void writeType(Type type) {
		if (type instanceof Type.Array) {
			Type.Array arrT = (Type.Array) type;
			out.print("arr_t(");
			writeType(arrT.element());
			out.print(")");
		} else {
			String cType = typeMap.get(type);
			if (cType != null) {
				out.print(cType);
			} else {
				throw new IllegalArgumentException("Type not supported: " + type);
			}
		}
	}

	private static Type TYPE_I8 = Type.Nominal(new NameID(Trie.fromString("whiley/lang/Int"),"i8"));
	private static Type TYPE_I16 = Type.Nominal(new NameID(Trie.fromString("whiley/lang/Int"),"i16"));
	private static Type TYPE_I32 = Type.Nominal(new NameID(Trie.fromString("whiley/lang/Int"),"i32"));
	private static Type TYPE_I64 = Type.Nominal(new NameID(Trie.fromString("whiley/lang/Int"),"i64"));
	private static Type TYPE_U8 = Type.Nominal(new NameID(Trie.fromString("whiley/lang/Int"),"u8"));
	private static Type TYPE_U16 = Type.Nominal(new NameID(Trie.fromString("whiley/lang/Int"),"u16"));
	private static Type TYPE_U32 = Type.Nominal(new NameID(Trie.fromString("whiley/lang/Int"),"u32"));
	private static Type TYPE_U64 = Type.Nominal(new NameID(Trie.fromString("whiley/lang/Int"),"u64"));

	private static HashMap<Type,String> typeMap = new HashMap<Type,String>(){{
		// Integer types
		put(TYPE_I8,"int8_t");
		put(TYPE_I16,"int16_t");
		put(TYPE_I32,"int32_t");
		put(TYPE_I64,"int64_t");
		put(TYPE_U8,"uint8_t");
		put(TYPE_U16,"uint16_t");
		put(TYPE_U32,"uint32_t");
		put(TYPE_U64,"uint64_t");
		//
		put(Type.T_BOOL,"bool");
	}};

	private static String opcode(Bytecode.OperatorKind k) {
		switch(k) {
		case NEG:
			return "-";
		case NOT:
			return "!";
		case BITWISEINVERT:
			return "~";
		case DEREFERENCE:
			return "*";
		// Binary
		case ADD:
			return "+";
		case SUB:
			return "-";
		case MUL:
			return "*";
		case DIV:
			return "/";
		case REM:
			return "%";
		case EQ:
			return "==";
		case NEQ:
			return "!=";
		case LT:
			return "<";
		case LTEQ:
			return "<=";
		case GT:
			return ">";
		case GTEQ:
			return ">=";
		case AND:
			return "&&";
		case OR:
			return "||";
		case BITWISEOR:
			return "|";
		case BITWISEXOR:
			return "^";
		case BITWISEAND:
			return "&";
		case LEFTSHIFT:
			return "<<";
		case RIGHTSHIFT:
			return ">>";
		case IS:
			return "is";
		case NEW:
			return "new";
		default:
			throw new IllegalArgumentException("unknown operator kind : " + k);
		}
	}

	private void tabIndent(int indent) {
		indent = indent * 4;
		for(int i=0;i<indent;++i) {
			out.print(" ");
		}
	}

	@SuppressWarnings("unchecked")
	private Location<VariableDeclaration> getVariableDeclaration(Location<?> loc) {
		switch (loc.getOpcode()) {
		case Bytecode.OPCODE_vardecl:
		case Bytecode.OPCODE_vardeclinit:
			return (Location<VariableDeclaration>) loc;
		case Bytecode.OPCODE_aliasdecl:
			return getVariableDeclaration(loc.getOperand(0));
		}
		throw new IllegalArgumentException("invalid location provided: " + loc);
	}
}
