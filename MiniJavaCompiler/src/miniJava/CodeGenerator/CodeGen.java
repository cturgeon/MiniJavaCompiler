package miniJava.CodeGenerator;

import java.util.ArrayList;
import java.util.Stack;
import mJAM.Disassembler;
import mJAM.Interpreter;
import mJAM.Machine;
import mJAM.Machine.Op;
import mJAM.Machine.Prim;
import mJAM.ObjectFile;
import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.SyntacticAnalyzer.TokenKind;

public class CodeGen implements Visitor<Object, Object> {

	private AST ast;
	ErrorReporter eReport;
	int offSets;
	int mainCodeAddy;
	int staticFieldAddy;
	int localPopCount;
	int curMethodParaInt;
	ArrayList<Patch> patchList;

	public CodeGen(AST ast, ErrorReporter eReport) {
		this.eReport = eReport;
		this.ast = ast;
		this.patchList = new ArrayList<Patch>();
	}

	public void generateCode(String fileName) {
		Machine.initCodeGen();

		staticFieldAddy = Machine.nextInstrAddr();

		Machine.emit(Op.PUSH, 0);
		Machine.emit(Op.LOADL, 0);
		Machine.emit(Prim.newarr);

		mainCodeAddy = Machine.nextInstrAddr();

		Machine.emit(Op.CALL, Machine.Reg.CB, 0);
		Machine.emit(Op.HALT, 0, 0, 0);

		ast.visit(this, null);

		String objectCodeFileName = fileName.replace(".java", ".mJAM");
		ObjectFile objF = new ObjectFile(objectCodeFileName);
		System.out.print("Writing object code file " + objectCodeFileName + " ... ");
		if (objF.write()) {
			System.out.println("FAILED!");
			return;
		} else
			System.out.println("SUCCEEDED");
		
		

		// create asm file using disassembler
		String asmCodeFileName = objectCodeFileName.replace(".mJAM", ".asm");
		System.out.print("Writing assembly file " + asmCodeFileName + " ... ");
		Disassembler d = new Disassembler(objectCodeFileName);
		if (d.disassemble()) {
			System.out.println("FAILED!");
			return;
		} else
			System.out.println("SUCCEEDED");

		/*
		 * run code using debugger
		 * 
		 */
//		System.out.println("Running code in debugger ... ");
//		Interpreter.debug(objectCodeFileName, asmCodeFileName);
//		System.out.println("*** mJAM execution completed");
		
		Interpreter.interpret(objectCodeFileName);
		
	}

	@Override
	public Object visitPackage(Package prog, Object arg) {
		int staticFieldInt = 0;
		for (ClassDecl c : prog.classDeclList) {
			int instFieldInt = 0;
			for (FieldDecl f : c.fieldDeclList) {
				if (f.isStatic) {
					f.runEntity = new RuntimeEntity(staticFieldInt++);
				} else {
					f.runEntity = new RuntimeEntity(instFieldInt++);
				}
			}
			c.runEntity = new RuntimeEntity(instFieldInt);
		}

		Machine.patch(staticFieldAddy, staticFieldInt);
		Machine.patch(mainCodeAddy, Machine.nextInstrAddr());

		for (ClassDecl c : prog.classDeclList) {
			for (MethodDecl m : c.methodDeclList) {
				if (m.name.equals("main")) {
					patchList.add(new Patch(mainCodeAddy, m));
				}
				if (m.type.typeKind == TypeKind.VOID) {
					if (!(m.statementList.get(m.statementList.size() - 1) instanceof ReturnStmt)) {
						m.statementList
								.add(new ReturnStmt(null, m.statementList.get(m.statementList.size() - 1).posn));
					}
				}
				if (m.statementList.size() == 0) {
					m.statementList.add(new ReturnStmt(null, m.posn));
				}
			}
		}

		for (ClassDecl c : prog.classDeclList) {
			c.visit(this, null);
		}

		for (Patch p : patchList) {
			Machine.patch(p.codeAddy, p.md.runEntity.offset);
		}

		return null;
	}

	@Override
	public Object visitClassDecl(ClassDecl cd, Object arg) {
		for (FieldDecl f : cd.fieldDeclList) {
			f.visit(this, null);
		}
		for (MethodDecl m : cd.methodDeclList) {
			m.visit(this, null);
		}

		return null;
	}

	@Override
	public Object visitFieldDecl(FieldDecl fd, Object arg) {
		fd.type.visit(this, null);
		return null;
	}

	@Override
	public Object visitMethodDecl(MethodDecl md, Object arg) {
		offSets = 3;
		curMethodParaInt = md.parameterDeclList.size();
		md.type.visit(this, null);

		int paraOffsets = -md.parameterDeclList.size();
		for (ParameterDecl p : md.parameterDeclList) {
			p.visit(this, null);
			p.runEntity = new RuntimeEntity(paraOffsets++);
		}

		md.runEntity = new RuntimeEntity(Machine.nextInstrAddr());

		for (Statement s : md.statementList) {
			s.visit(this, null);
		}

		return null;
	}

	@Override
	public Object visitBlockStmt(BlockStmt stmt, Object arg) {
		localPopCount = 0;
		for (Statement s : stmt.sl) {
			s.visit(this, null);
			if (localPopCount > 0) {
				offSets = offSets - localPopCount;
				Machine.emit(Op.POP, localPopCount);
			}
		}
		return null;
	}

	@Override
	public Object visitVardeclStmt(VarDeclStmt stmt, Object arg) {
		localPopCount++;
		stmt.varDecl.visit(this, null);
		stmt.initExp.visit(this, null);
		return null;
	}

	@Override
	public Object visitAssignStmt(AssignStmt stmt, Object arg) {
		if (stmt.ref.isStatic) {
			stmt.val.visit(this, null);
			Machine.emit(Op.STORE, Machine.Reg.SB, stmt.ref.getDecl().runEntity.offset);
		} else if (stmt.ref instanceof IdRef) {
			IdRef idRef = (IdRef) stmt.ref;
			if (idRef.getDecl() instanceof FieldDecl) {
				Machine.emit(Op.LOADA, Machine.Reg.OB, 0);
				Machine.emit(Op.LOADL, idRef.id.decl.runEntity.offset);
				stmt.val.visit(this, null);
				Machine.emit(Prim.fieldupd);
			} else {
				int load = ((IdRef) stmt.ref).id.decl.runEntity.offset;
				stmt.val.visit(this, null);
				if (((IdRef) stmt.ref).getDecl() instanceof FieldDecl) {
					FieldDecl fieldDecl = (FieldDecl) ((IdRef) stmt.ref).getDecl();
					if (fieldDecl.isStatic) {
						Machine.emit(Op.STORE, Machine.Reg.SB, load);
					}
				} else {
					if (((IdRef) stmt.ref).id.isStatic) {
						Machine.emit(Op.STORE, Machine.Reg.SB, load);
					} else {
						Machine.emit(Op.STORE, Machine.Reg.LB, load);
					}
				}
			}
		} else if (stmt.ref instanceof QualRef) {
			visitQualRefHelp((QualRef) stmt.ref);
			stmt.val.visit(this, null);
			Machine.emit(Prim.fieldupd);
		}
		return null;
	}

	@Override
	public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg) {
		if (stmt.ref.isStatic) {
			stmt.ix.visit(this, null);
			stmt.exp.visit(this, null);
			Machine.emit(Op.STORE, Machine.Reg.SB, stmt.ref.getDecl().runEntity.offset);
		} else if (stmt.ref instanceof IdRef) {
			IdRef idRef = (IdRef) stmt.ref;
			if (idRef.getDecl() instanceof FieldDecl) {
				Machine.emit(Op.LOADA, Machine.Reg.OB, 0);
				Machine.emit(Op.LOADL, idRef.id.decl.runEntity.offset);
				stmt.ix.visit(this, null);
				stmt.exp.visit(this, null);
				Machine.emit(Prim.fieldupd);
			} else {
				int load = ((IdRef) stmt.ref).id.decl.runEntity.offset;
				stmt.ix.visit(this, null);
				if (((IdRef) stmt.ref).getDecl() instanceof FieldDecl) {
					FieldDecl fieldDecl = (FieldDecl) ((IdRef) stmt.ref).getDecl();
					if (fieldDecl.isStatic) {
						Machine.emit(Op.STORE, Machine.Reg.SB, load);
					}
				}
			}
		} else if (stmt.ref instanceof QualRef) {
			stmt.ix.visit(this, null);
			stmt.exp.visit(this, null);
			Machine.emit(Prim.fieldupd);
		}
		return null;
	}

	@Override
	public Object visitCallStmt(CallStmt stmt, Object arg) {
		for (Expression e : stmt.argList) {
			e.visit(this, null);
		}
		if (stmt.methodRef.getId().spelling.equals("println")) {
			Machine.emit(Prim.putintnl);
		} else {
			int callAddy = Machine.nextInstrAddr();
			if (stmt.methodRef.isStatic) {
				Machine.emit(Op.CALL, Machine.Reg.CB, 0);
				patchList.add(new Patch(callAddy, (MethodDecl) stmt.methodRef.getDecl()));
			} else {
				stmt.methodRef.visit(this, null);
				if (stmt.methodRef instanceof QualRef) {
					QualRef mQRef = (QualRef) stmt.methodRef;
					Reference mRef = mQRef.ref;
					mRef.visit(this, null);
				} else {
					visitThisRef(null, null);
				}

				callAddy = Machine.nextInstrAddr();
				Machine.emit(Op.CALLI, Machine.Reg.CB, 0);
				patchList.add(new Patch(callAddy, (MethodDecl) stmt.methodRef.getDecl()));
			}
		}
		return null;
	}

	@Override
	public Object visitReturnStmt(ReturnStmt stmt, Object arg) {
		if (stmt.returnExpr != null) {
			stmt.returnExpr.visit(this, null);
		}
		int returnValue = stmt.returnExpr == null ? 0 : 1;
		Machine.emit(Op.RETURN, returnValue, 0, curMethodParaInt);
		return null;
	}

	@Override
	public Object visitIfStmt(IfStmt stmt, Object arg) {
		stmt.cond.visit(this, null);
		int addy1 = Machine.nextInstrAddr();
		Machine.emit(Op.JUMPIF, 0, Machine.Reg.CB, 0);
		stmt.thenStmt.visit(this, null);
		int addy2 = Machine.nextInstrAddr();
		Machine.emit(Op.JUMP, 0, Machine.Reg.CB, 0);
		Machine.patch(addy1, Machine.nextInstrAddr());
		if (stmt.elseStmt != null) {
			stmt.elseStmt.visit(this, null);
		}
		Machine.patch(addy2, Machine.nextInstrAddr());
		return null;
	}

	@Override
	public Object visitWhileStmt(WhileStmt stmt, Object arg) {
		int addy1 = Machine.nextInstrAddr();
		Machine.emit(Op.JUMP, 0, Machine.Reg.CB, 0);
		stmt.body.visit(this, null);
		int addy2 = Machine.nextInstrAddr();
		stmt.cond.visit(this, null);
		Machine.emit(Op.JUMPIF, 1, Machine.Reg.CB, addy1 + 1);
		Machine.patch(addy1, addy2);
		return null;
	}

	@Override
	public Object visitUnaryExpr(UnaryExpr expr, Object arg) {
		if (expr.operator.kind == TokenKind.NEG) {
			Machine.emit(Op.LOADL, 0);
		}
		expr.expr.visit(this, null);
		expr.operator.visit(this, null);
		return null;
	}

	@Override
	public Object visitBinaryExpr(BinaryExpr expr, Object arg) {
		if (expr.operator.kind == TokenKind.CONJ) {
			expr.left.visit(this, null);
			Machine.emit(Op.LOAD, Machine.Reg.ST, -1);
			
			int jump = Machine.nextInstrAddr();
			Machine.emit(Op.JUMPIF, 0, Machine.Reg.CB, 0);
			expr.right.visit(this, null);
			expr.operator.visit(this, null);
			Machine.patch(jump, Machine.nextInstrAddr());
		} else if (expr.operator.kind == TokenKind.DISJ) {
			expr.left.visit(this, null);
			Machine.emit(Op.LOAD, Machine.Reg.ST, -1);
			
			int jump = Machine.nextInstrAddr();
			Machine.emit(Op.JUMPIF, 1, Machine.Reg.CB, 0);
			expr.right.visit(this, null);
			expr.operator.visit(this, null);
			Machine.patch(jump, Machine.nextInstrAddr());
		} else {
			expr.left.visit(this, null);
			expr.right.visit(this, null);
			expr.operator.visit(this, null);
		}
		return null;
	}

	@Override
	public Object visitRefExpr(RefExpr expr, Object arg) {
		if (expr.ref.isStatic) {
			Machine.emit(Op.LOAD, Machine.Reg.SB, expr.ref.getDecl().runEntity.offset);
		} else if (expr.ref instanceof IdRef) {
			expr.ref.visit(this, null);
		} else if (expr.ref instanceof QualRef) {
			expr.ref.visit(this, null);
		} else if (expr.ref instanceof ThisRef) {
			Machine.emit(Op.LOADA, Machine.Reg.OB, 0);
		}
		return null;
	}

	@Override
	public Object visitIxExpr(IxExpr expr, Object arg) {
		if (expr.ref.isStatic) {
			Machine.emit(Op.LOAD, Machine.Reg.SB, expr.ref.getDecl().runEntity.offset);
		} else if (expr.ref instanceof IdRef || expr.ref instanceof QualRef) {
			expr.ref.visit(this, null);
		}
		return null;
	}

	@Override
	public Object visitCallExpr(CallExpr expr, Object arg) {
		for (Expression e : expr.argList)
			e.visit(this, null);

		if (!expr.functionRef.getId().spelling.equals("println")) {
			expr.functionRef.visit(this, null);
			int callAddy = Machine.nextInstrAddr();
			if (((MethodDecl) expr.functionRef.getDecl()).isStatic) {
				Machine.emit(Op.CALL, Machine.Reg.CB, 0);
				patchList.add(new Patch(callAddy, (MethodDecl) expr.functionRef.getDecl()));
			} else {
				if (expr.functionRef instanceof QualRef) {
					QualRef qRef = (QualRef) expr.functionRef;
					qRef.ref.visit(this, null);
				} else {
					Machine.emit(Op.LOADA, Machine.Reg.OB, 0);
				}
				callAddy = Machine.nextInstrAddr();
				Machine.emit(Op.CALLI, Machine.Reg.CB, 0);
				patchList.add(new Patch(callAddy, (MethodDecl) expr.functionRef.getDecl()));
			}
		}
		return null;
	}

	@Override
	public Object visitLiteralExpr(LiteralExpr expr, Object arg) {
		expr.lit.visit(this, null);
		return null;
	}

	@Override
	public Object visitNewObjectExpr(NewObjectExpr expr, Object arg) {
		Machine.emit(Op.LOADL, -1);
		Machine.emit(Op.LOADL, expr.classtype.className.decl.runEntity.offset);
		Machine.emit(Prim.newobj);
		return null;
	}

	@Override
	public Object visitNewArrayExpr(NewArrayExpr expr, Object arg) {
		expr.sizeExpr.visit(this, null);
		Machine.emit(Prim.newarr);
		return null;
	}

	@Override
	public Object visitThisRef(ThisRef ref, Object arg) {
		Machine.emit(Op.LOADA, Machine.Reg.OB, 0);
		return null;
	}

	@Override
	public Object visitIdRef(IdRef ref, Object arg) {
		if (ref.getDecl() instanceof FieldDecl) {
			FieldDecl f = (FieldDecl) ref.getDecl();
			int load = ref.id.decl.runEntity.offset;
			if (f.isStatic) {
				Machine.emit(Op.LOAD, Machine.Reg.SB, load);
			} else {
				Machine.emit(Op.LOAD, Machine.Reg.OB, load);
			}
		} else if (ref.id.decl.runEntity != null) {
			int load = ref.id.decl.runEntity.offset;
			if (ref.id.isStatic) {
				Machine.emit(Op.LOAD, Machine.Reg.SB, load);
			} else if (!(ref.id.decl instanceof MethodDecl)) {
				Machine.emit(Op.LOAD, Machine.Reg.LB, load);
			}
		}
		return null;
	}

	@Override
	public Object visitQRef(QualRef ref, Object arg) {
		if (ref.id.spelling.equals("length")) {
			if (ref.getDecl() instanceof FieldDecl) {
				int load = ref.ref.getDecl().runEntity.offset;
				FieldDecl fieldDecl = (FieldDecl) ref.getDecl();
				if (fieldDecl.isStatic) {
					Machine.emit(Op.LOAD, Machine.Reg.SB, load);
				} else {
					Machine.emit(Op.LOAD, Machine.Reg.OB, load);
				}
			} else if (ref.id.decl.runEntity != null) {
				int load = ref.ref.getDecl().runEntity.offset;
				if (ref.id.isStatic) {
					Machine.emit(Op.LOAD, Machine.Reg.SB, load);
				} else if (!(ref.id.decl instanceof MethodDecl)) {
					Machine.emit(Op.LOAD, Machine.Reg.LB, load);
				}
			}
			Machine.emit(Prim.arraylen);
		} else if (ref.id.decl.runEntity != null) {
			visitQualRefHelp(ref);
			Machine.emit(Prim.fieldref);
		}
		return null;
	}

	public void visitQualRefHelp(QualRef ref) {
		if (ref.id.decl.runEntity != null) {
			Stack<Integer> stackOffSet = new Stack<Integer>();
			stackOffSet.push(ref.id.decl.runEntity.offset);
			while (ref.ref instanceof QualRef) {
				ref = (QualRef) ref.ref;
				stackOffSet.push(ref.getDecl().runEntity.offset);
			}
			ref.ref.visit(this, null);
			int stackSize = stackOffSet.size();
			for (int i = 0; i < stackSize; i++) {
				int fOffSet = stackOffSet.pop();
				Machine.emit(Op.LOADL, fOffSet);
				if (i + 1 < stackSize) {
					Machine.emit(Prim.fieldref);
				}
			}
		}
	}

	@Override
	public Object visitOperator(Operator op, Object arg) {
		if (op.kind == TokenKind.NOT) {
			Machine.emit(Prim.neg);
		} else if (op.spelling.equals("+")) {
			Machine.emit(Prim.add);
		} else if (op.spelling.equals("-")) {
			Machine.emit(Prim.sub);
		} else if (op.spelling.equals("*")) {
			Machine.emit(Prim.mult);
		} else if (op.spelling.equals("/")) {
			Machine.emit(Prim.div);
		} else if (op.spelling.equals("<")) {
			Machine.emit(Prim.lt);
		} else if (op.spelling.equals(">")) {
			Machine.emit(Prim.gt);
		} else if (op.spelling.equals(">=")) {
			Machine.emit(Prim.ge);
		} else if (op.spelling.equals("<=")) {
			Machine.emit(Prim.le);
		} else if (op.spelling.equals("==")) {
			Machine.emit(Prim.eq);
		} else if (op.spelling.equals("&&")) {
			Machine.emit(Prim.and);
		} else if (op.spelling.equals("||")) {
			Machine.emit(Prim.or);
		} else if (op.spelling.equals("!=")) {
			Machine.emit(Prim.ne);
		}
		return null;
	}

	@Override
	public Object visitIdentifier(Identifier id, Object arg) {
		return null;
	}

	@Override
	public Object visitParameterDecl(ParameterDecl pd, Object arg) {
		pd.type.visit(this, null);
		return null;
	}

	@Override
	public Object visitVarDecl(VarDecl decl, Object arg) {
		decl.runEntity = new RuntimeEntity(offSets++);
		decl.type.visit(this, null);
		return null;
	}

	@Override
	public Object visitBaseType(BaseType type, Object arg) {
		return null;
	}

	@Override
	public Object visitClassType(ClassType type, Object arg) {
		type.className.visit(this, null);
		return null;
	}

	@Override
	public Object visitArrayType(ArrayType type, Object arg) {
		type.eltType.visit(this, null);
		return null;
	}

	@Override
	public Object visitIntLiteral(IntLiteral num, Object arg) {
		Machine.emit(Op.LOADL, Integer.parseInt(num.spelling));
		return null;
	}

	@Override
	public Object visitBooleanLiteral(BooleanLiteral bool, Object arg) {
		if (bool.spelling.equals("true")) {
			Machine.emit(Op.LOADL, Machine.trueRep);
		} else if (bool.spelling.equals("false")) {
			Machine.emit(Op.LOADL, Machine.falseRep);
		}
		return null;
	}

	@Override
	public Object visitNullLiteral(NullLiteral nullLiteral, Object arg) {
		Machine.emit(Op.LOADL, Machine.nullRep);
		return null;
	}

	@Override
	public Object visitArrayLengthRef(ArrayLengthRef arrayLengthRef, Object o) {
		return null;
	}

}
