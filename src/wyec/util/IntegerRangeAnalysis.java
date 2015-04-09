package wyec.util;

import java.util.*;

import wycc.util.Pair;
import wyec.lang.IntegerRange;
import wyec.lang.RangeFrame;
import wyil.lang.Attribute;
import wyil.lang.Code;
import wyil.lang.CodeBlock;
import wyil.lang.CodeUtils;
import wyil.lang.Codes;
import wyil.lang.Constant;
import wyil.lang.Type;
import wyil.lang.WyilFile;
import wyil.util.AttributedCodeBlock;

/**
 * Perform an integer range analysis for all variables in all methods, functions
 * and types within a give WyIL file. This will produce a set of ranges at each
 * program point which are stored as bytecode attributes.
 * 
 * @author David J. Pearce
 *
 */
public class IntegerRangeAnalysis {

	public void apply(WyilFile wyf) {
		for (WyilFile.Block d : wyf.blocks()) {
			if (d instanceof WyilFile.Type) {
				apply((WyilFile.Type) d);
			} else if (d instanceof WyilFile.FunctionOrMethod) {
				apply((WyilFile.FunctionOrMethod) d);
			}
		}
	}

	public void apply(WyilFile.Type td) {
		AttributedCodeBlock invariant = td.invariant();
		if (invariant != null) {
			// Construct the initial frame...
			int nVars = Math.max(1, invariant.numSlots());
			RangeFrame frame = new RangeFrame(this, nVars);
			frame.write(0, initialise(td.type()));
			// Construct the variable ranges map
			VariableRangesMap frames = new VariableRangesMap();
			CodeBlock.Index start = new CodeBlock.Index(null, 0);
			frames.put(start, frame);
			// Build the label map...
			Map<String, CodeBlock.Index> labels = CodeUtils
					.buildLabelMap(invariant);
			// Now, perform the analysis
			transfer(start, invariant, frames, labels);
			// Store the computed attributes for other passes to use.			
			// FIXME: invariant.attributes().add((Attribute.Map) frames);
		}
	}

	public void apply(WyilFile.FunctionOrMethod td) {
		Type.FunctionOrMethod fmt = td.type();
		int nVars = Math.max(td.type().params().size(), td.body().numSlots());
		RangeFrame frame = new RangeFrame(this, nVars);

		// First, go though and initialise every parameter with its base range.
		// This is determine from the type of the parameter, which could be
		// influence by any type invariants declared for it.
		for (int i = 0; i != fmt.params().size(); ++i) {
			Type parameterType = fmt.params().get(i);
			frame.write(i, initialise(parameterType));
		}

		// Second, go through and apply every precondition to constrain the
		// parameters before entry into the function/method's body.
		for (AttributedCodeBlock block : td.precondition()) {
			Map<String, CodeBlock.Index> labels = CodeUtils
					.buildLabelMap(block);
			frame = transfer(frame, block);
		}

		// Third, construct the variable ranges attribute map and the labels
		// map.
		VariableRangesMap frames = new VariableRangesMap();
		CodeBlock.Index start = new CodeBlock.Index(null, 0);
		frames.put(start, frame);

		// Fourth run through the function/method's body to populate the
		// variable ranges map with ranges for each variable at each program
		// point.
		transfer(start, td.body(), frames, CodeUtils.buildLabelMap(td.body()));
		
		// Finally, we can store the frames map with the function or method.
		// FIXME: c.body().attributes().add((Attribute.Map) frames);
	}

	public RangeFrame transfer(RangeFrame frame, AttributedCodeBlock block) {
		// Construct the variable ranges map
		VariableRangesMap frames = new VariableRangesMap();
		CodeBlock.Index start = new CodeBlock.Index(null, 0);
		frames.put(start, frame);
		// Build the label map...
		Map<String, CodeBlock.Index> labels = CodeUtils.buildLabelMap(block);
		// Now, perform the analysis
		transfer(start, block, frames, labels);
		// At this point we find all the return statements and join them
		// together to generate the final frame which will pass out of the
		// block.
		RangeFrame result = null;
		
		// FIXME: the following is a hack which exploits the fact that I only
		// ever call this function with a precondition, and I happen to know
		// that the return statement is always the last bytecode in the block.
		// This is not really a general solution, though it does work for now at
		// least.
		CodeBlock.Index last = new CodeBlock.Index(null,block.size()-1);
		
		return frames.get(last);
		
	}

	public void transfer(CodeBlock.Index index, AttributedCodeBlock block,
			VariableRangesMap frames, Map<String, CodeBlock.Index> labels) {

		for (int i = 0; i != block.size(); ++i) {
			System.out.println("FRAME(" + i + ") = " + frames.get(index));
			transfer(index, block.get(i), frames, labels);
			index = index.next();
		}
	}

	public void transfer(CodeBlock.Index index, Code bytecode,
			VariableRangesMap frames, Map<String, CodeBlock.Index> labels) {
		if (bytecode instanceof Code.Unit) {
			transfer(index, (Code.Unit) bytecode, frames, labels);
		} else if (bytecode instanceof Code.Compound) {
			transfer(index, (Code.Compound) bytecode, frames, labels);
		} else {
			// deal with branching instruction
			throw new RuntimeException("Need to implement: "
					+ bytecode.getClass().getName());
		}
	}

	public void transfer(CodeBlock.Index index, Code.Compound bytecode,
			VariableRangesMap frames, Map<String, CodeBlock.Index> labels) {
		throw new RuntimeException("Need to implement: "
				+ bytecode.getClass().getName());
	}

	public void transfer(CodeBlock.Index index, Codes.Goto bytecode,
			VariableRangesMap frames, Map<String, CodeBlock.Index> labels) {
		//
		CodeBlock.Index target = labels.get(bytecode.target);
		joinInto(target, frames.get(index), frames);
	}

	public void transfer(CodeBlock.Index index, Codes.If bytecode,
			VariableRangesMap frames, Map<String, CodeBlock.Index> labels) {

		RangeFrame frame = frames.get(index);
		RangeFrame trueFrame = frame.clone();
		RangeFrame falseFrame = frame.clone();
		CodeBlock.Index target = labels.get(bytecode.target);
		// Let's read the operands and see whether we can do anything
		IntegerRange leftOperand = frame.read(bytecode.leftOperand);
		IntegerRange rightOperand = frame.read(bytecode.rightOperand);
		// In practice, it should be guaranteed that if one is not-null then
		// both are not-null. But, we'll check them both anyway.
		if (leftOperand != null && rightOperand != null) {
			Pair<IntegerRange, IntegerRange> trueOperands = null;
			Pair<IntegerRange, IntegerRange> falseOperands = null;

			switch (bytecode.op) {
			case EQ:
				trueOperands = leftOperand.equals(rightOperand);
				falseOperands = leftOperand.notEquals(rightOperand);
				break;
			case NEQ:
				trueOperands = leftOperand.notEquals(rightOperand);
				falseOperands = leftOperand.equals(rightOperand);
				break;
			case LT:
				trueOperands = leftOperand.lessThan(rightOperand);
				falseOperands = leftOperand.greaterThanOrEquals(rightOperand);
				break;
			case LTEQ:
				trueOperands = leftOperand.lessThanOrEquals(rightOperand);
				falseOperands = leftOperand.greaterThan(rightOperand);
				break;
			case GT:
				trueOperands = leftOperand.greaterThan(rightOperand);
				falseOperands = leftOperand.lessThanOrEquals(rightOperand);
				break;
			case GTEQ:
				trueOperands = leftOperand.greaterThanOrEquals(rightOperand);
				falseOperands = leftOperand.lessThan(rightOperand);
				break;
			}
			// Now, if we actually did something then lets update the operands
			// in each branch accordingly.
			if (trueOperands != null) {
				trueFrame.write(bytecode.leftOperand, trueOperands.first());
				trueFrame.write(bytecode.rightOperand, trueOperands.second());
				falseFrame.write(bytecode.leftOperand, falseOperands.first());
				falseFrame.write(bytecode.rightOperand, falseOperands.second());
			}
		}
		joinInto(target, trueFrame, frames);
		joinInto(index.next(), falseFrame, frames);
	}

	public void transfer(CodeBlock.Index index, Code.Unit bytecode,
			VariableRangesMap frames, Map<String, CodeBlock.Index> labels) {
		
		RangeFrame frame = frames.get(index);
		
		if(frame == null) {
			// This indicates we have encountered an unreachable statement. For
			// example, this can occur when dead return statements have not been
			// remove using DeadCodeElimination.
			return;
		} else {		
			frame = frame.clone();
		}

		if (bytecode instanceof Codes.BinaryOperator) {
			transfer(index, (Codes.BinaryOperator) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.Convert) {
			transfer(index, (Codes.Convert) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.Const) {
			transfer(index, (Codes.Const) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.Debug) {
			transfer(index, (Codes.Debug) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.Fail) {
			transfer(index, (Codes.Fail) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.FieldLoad) {
			transfer(index, (Codes.FieldLoad) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.TupleLoad) {
			transfer(index, (Codes.TupleLoad) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.IndirectInvoke) {
			transfer(index, (Codes.IndirectInvoke) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.Invoke) {
			transfer(index, (Codes.Invoke) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.Invert) {
			transfer(index, (Codes.Invert) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.Goto) {
			// deal with unconditional branching instruction
			transfer(index, (Codes.Goto) bytecode, frames, labels);
		} else if (bytecode instanceof Codes.If) {
			// deal with conditional branching instruction
			transfer(index, (Codes.If) bytecode, frames, labels);
		} else if (bytecode instanceof Codes.Lambda) {
			transfer(index, (Codes.Lambda) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.Label) {
			transfer(index, (Codes.Label) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.ListOperator) {
			transfer(index, (Codes.ListOperator) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.LengthOf) {
			transfer(index, (Codes.LengthOf) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.SubList) {
			transfer(index, (Codes.SubList) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.IndexOf) {
			transfer(index, (Codes.IndexOf) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.Assign) {
			transfer(index, (Codes.Assign) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.Update) {
			transfer(index, (Codes.Update) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.NewMap) {
			transfer(index, (Codes.NewMap) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.NewList) {
			transfer(index, (Codes.NewList) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.NewRecord) {
			transfer(index, (Codes.NewRecord) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.NewSet) {
			transfer(index, (Codes.NewSet) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.NewTuple) {
			transfer(index, (Codes.NewTuple) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.UnaryOperator) {
			transfer(index, (Codes.UnaryOperator) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.Dereference) {
			transfer(index, (Codes.Dereference) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.Return) {
			transfer(index, (Codes.Return) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.SetOperator) {
			transfer(index, (Codes.SetOperator) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.Nop) {
			transfer(index, (Codes.Nop) bytecode, frame, frames);
		} else if (bytecode instanceof Codes.NewObject) {
			transfer(index, (Codes.NewObject) bytecode, frame, frames);
		}
	}

	public void transfer(CodeBlock.Index index, Codes.Assign code,
			RangeFrame frame, VariableRangesMap frames) {
		frame.write(code.target(), frame.read(code.operand(0)));
		joinInto(index.next(), frame, frames);
	}

	public void transfer(CodeBlock.Index index, Codes.BinaryOperator code,
			RangeFrame frame, VariableRangesMap frames) {
		// TODO: implement me!
		throw new RuntimeException("Need to implement: "
				+ code.getClass().getName());
	}

	public void transfer(CodeBlock.Index index, Codes.Convert code,
			RangeFrame frame, VariableRangesMap frames) {
		// TODO: implement me!
		throw new RuntimeException("Need to implement: "
				+ code.getClass().getName());
	}

	public void transfer(CodeBlock.Index index, Codes.Const code,
			RangeFrame frame, VariableRangesMap frames) {
		frame.write(code.target(), convert(code.constant));
		joinInto(index.next(), frame, frames);
	}

	public void transfer(CodeBlock.Index index, Codes.Debug code,
			RangeFrame frame, VariableRangesMap frames) {
		// do nothing
		joinInto(index.next(), frame, frames);
	}

	public void transfer(CodeBlock.Index index, Codes.Fail code,
			RangeFrame frame, VariableRangesMap frames) {
		// In this case, we really do nothing
	}

	public void transfer(CodeBlock.Index index, Codes.FieldLoad code,
			RangeFrame frame, VariableRangesMap frames) {
		// TODO: implement me!
		throw new RuntimeException("Need to implement: "
				+ code.getClass().getName());
	}

	public void transfer(CodeBlock.Index index, Codes.TupleLoad code,
			RangeFrame frame, VariableRangesMap frames) {
		// TODO: implement me!
		throw new RuntimeException("Need to implement: "
				+ code.getClass().getName());
	}

	public void transfer(CodeBlock.Index index, Codes.IndirectInvoke code,
			RangeFrame frame, VariableRangesMap frames) {
		frame.havoc(code.target());
		joinInto(index.next(), frame, frames);
	}

	public void transfer(CodeBlock.Index index, Codes.Invoke code,
			RangeFrame frame, VariableRangesMap frames) {
		// TODO: implement me!
		throw new RuntimeException("Need to implement: "
				+ code.getClass().getName());
	}

	public void transfer(CodeBlock.Index index, Codes.Invert code,
			RangeFrame frame, VariableRangesMap frames) {
		frame.havoc(code.target());
		joinInto(index.next(), frame, frames);
	}

	public void transfer(CodeBlock.Index index, Codes.Lambda code,
			RangeFrame frame, VariableRangesMap frames) {
		frame.havoc(code.target());
		joinInto(index.next(), frame, frames);
	}

	public void transfer(CodeBlock.Index index, Codes.ListOperator code,
			RangeFrame frame, VariableRangesMap frames) {
		// TODO: implement me!
		throw new RuntimeException("Need to implement: "
				+ code.getClass().getName());
	}

	public void transfer(CodeBlock.Index index, Codes.LengthOf code,
			RangeFrame frame, VariableRangesMap frames) {
		// TODO: implement me!
		throw new RuntimeException("Need to implement: "
				+ code.getClass().getName());
	}

	public void transfer(CodeBlock.Index index, Codes.SubList code,
			RangeFrame frame, VariableRangesMap frames) {
		// TODO: implement me!
		throw new RuntimeException("Need to implement: "
				+ code.getClass().getName());
	}

	public void transfer(CodeBlock.Index index, Codes.IndexOf code,
			RangeFrame frame, VariableRangesMap frames) {
		// TODO: implement me!
		throw new RuntimeException("Need to implement: "
				+ code.getClass().getName());
	}

	public void transfer(CodeBlock.Index index, Codes.NewMap code,
			RangeFrame frame, VariableRangesMap frames) {
		// TODO: implement me!
		throw new RuntimeException("Need to implement: "
				+ code.getClass().getName());
	}

	public void transfer(CodeBlock.Index index, Codes.NewRecord code,
			RangeFrame frame, VariableRangesMap frames) {
		// TODO: implement me!
		throw new RuntimeException("Need to implement: "
				+ code.getClass().getName());
	}

	public void transfer(CodeBlock.Index index, Codes.NewTuple code,
			RangeFrame frame, VariableRangesMap frames) {
		// TODO: implement me!
		throw new RuntimeException("Need to implement: "
				+ code.getClass().getName());
	}

	public void transfer(CodeBlock.Index index, Codes.NewSet code,
			RangeFrame frame, VariableRangesMap frames) {
		// TODO: implement me!
		throw new RuntimeException("Need to implement: "
				+ code.getClass().getName());
	}

	public void transfer(CodeBlock.Index index, Codes.Update code,
			RangeFrame frame, VariableRangesMap frames) {
		// TODO: implement me!
		throw new RuntimeException("Need to implement: "
				+ code.getClass().getName());
	}

	public void transfer(CodeBlock.Index index, Codes.NewList code,
			RangeFrame frame, VariableRangesMap frames) {
		// TODO: implement me!
		throw new RuntimeException("Need to implement: "
				+ code.getClass().getName());
	}

	public void transfer(CodeBlock.Index index, Codes.UnaryOperator code,
			RangeFrame frame, VariableRangesMap frames) {
		// TODO: implement me!
		throw new RuntimeException("Need to implement: "
				+ code.getClass().getName());
	}

	public void transfer(CodeBlock.Index index, Codes.Dereference code,
			RangeFrame frame, VariableRangesMap frames) {
		frame.havoc(code.target());
		joinInto(index.next(), frame, frames);
	}

	public void transfer(CodeBlock.Index index, Codes.Return code,
			RangeFrame frame, VariableRangesMap frames) {
		// Don't need to do anything here
	}

	public void transfer(CodeBlock.Index index, Codes.SetOperator code,
			RangeFrame frame, VariableRangesMap frames) {
		frame.havoc(code.target());
		joinInto(index.next(), frame, frames);
	}

	public void transfer(CodeBlock.Index index, Codes.Label code,
			RangeFrame frame, VariableRangesMap frames) {
		joinInto(index.next(), frame, frames);
	}

	public void transfer(CodeBlock.Index index, Codes.Nop code,
			RangeFrame frame, VariableRangesMap frames) {
		joinInto(index.next(), frame, frames);
	}

	public void transfer(CodeBlock.Index index, Codes.NewObject code,
			RangeFrame frame, VariableRangesMap frames) {
		frame.havoc(code.target());
		joinInto(index.next(), frame, frames);
	}

	public void joinInto(CodeBlock.Index index, RangeFrame frame,
			VariableRangesMap frames) {
		RangeFrame current = frames.get(index);
		if (current == null) {
			current = frame;
		} else {
			current = current.join(frame);
		}
		frames.put(index, current);
	}

	/**
	 * Initialise a range type representing complete set of values described by
	 * a given type.
	 * 
	 * @param t
	 * @return
	 */
	private static IntegerRange initialise(Type t) {
		if (t instanceof Type.Int) {
			return IntegerRange.TOP;
		} else {
			return null;
		}
	}

	/**
	 * Convert a WyIL constant into a range type. In some cases, no conversion
	 * is possible and we'll simply return null to indicate this.
	 * 
	 * @param constant
	 * @return
	 */
	public static IntegerRange convert(Constant constant) {
		if (constant instanceof Constant.Integer) {
			Constant.Integer i = (Constant.Integer) constant;
			return new IntegerRange(i.value, i.value);
		} else {
			return null;
		}
	}
}
