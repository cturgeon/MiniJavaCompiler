package miniJava.ContextualAnalyser;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.SyntacticAnalyzer.SourcePosition;

public class TypeChecks implements Visitor<TypeDenoter, TypeDenoter> {
	public Package ast;
	public ErrorReporter eReport;
	public boolean hasMain = false;

	public TypeChecks(Package ast, ErrorReporter eReport) {
		this.ast = ast;
		this.eReport = eReport;
	}

	public void check() {
		ast.visit(this, null);
	}

	@Override
	public TypeDenoter visitPackage(Package prog, TypeDenoter arg) {
		ClassDeclList classes = prog.classDeclList;
		for (ClassDecl c : classes) {
			c.visit(this, arg);
		}
		return null;
	}

	@Override
	public TypeDenoter visitClassDecl(ClassDecl cd, TypeDenoter arg) {
		for (FieldDecl f : cd.fieldDeclList) {
			f.visit(this, arg);
		}
		for (MethodDecl m : cd.methodDeclList) {
			m.visit(this, arg);
		}
		return null;
	}

	@Override
	public TypeDenoter visitFieldDecl(FieldDecl fd, TypeDenoter arg) {
		fd.type = fd.type.visit(this, arg);
		return fd.type;
	}

	@Override
	public TypeDenoter visitMethodDecl(MethodDecl md, TypeDenoter arg) {
		TypeDenoter mType = md.type.visit(this, arg);
		md.type = mType;
		if (md.name.equals("main")) {
			if (hasMain) {
				eReport.reportError("*** can only have one main method at: " + md.posn.start);
			}
			hasMain = true;
			md.parameterDeclList.get(0).visit(this, arg);
			TypeDenoter typeDenoter = md.parameterDeclList.get(0).type;
			if (!(typeDenoter instanceof ArrayType)) {
				eReport.reportError("*** main must have an array at: " + md.posn.end);
			} else if (!((ClassType) ((ArrayType) typeDenoter).eltType).className.spelling.equals("String")) {
				eReport.reportError("*** main method must have String array at: " + md.posn.end);
			}
		}

		md.returnType = null;
		for (ParameterDecl p : md.parameterDeclList) {
			p.type.visit(this, null);
		}
		for (Statement s : md.statementList) {
			s.methodDecl = md;
			s.visit(this, arg);
		}
		if (mType.typeKind == TypeKind.VOID) {
			if (md.returnType != null && md.returnType.typeKind != TypeKind.VOID) {
				eReport.reportError("*** returning value in void method: " + md.posn.end);
			}
			if (md.statementList.size() == 0
					|| !(md.statementList.get(md.statementList.size() - 1) instanceof ReturnStmt)) {
				md.statementList.add(new ReturnStmt(null, new SourcePosition(md.posn.end, md.posn.end)));
			}
		} else {
			if (!(md.statementList.get(md.statementList.size() - 1) instanceof ReturnStmt)) {
				eReport.reportError("*** last line needs to be return stmt: " + md.posn.end);
			} else {
				return md.returnType;
			}
			if (md.returnType == null) {
				eReport.reportError("*** Type Checking Error at line " + md.posn.start
						+ ":Not returning value for method: " + md.name);
			} else {

				md.returnType = md.returnType.visit(this, arg);
			}
		}
		return mType;
	}

	@Override
	public TypeDenoter visitBlockStmt(BlockStmt stmt, TypeDenoter arg) {
		for (Statement s : stmt.sl) {
			s.visit(this, null);
		}
		return null;
	}

	@Override
	public TypeDenoter visitVardeclStmt(VarDeclStmt stmt, TypeDenoter arg) {
		TypeDenoter var = stmt.varDecl.type.visit(this, arg);
		TypeDenoter expr = stmt.initExp.visit(this, arg);
		if (var == null) {
			var = new BaseType(TypeKind.ERROR, stmt.posn);
		}
		if (expr == null) {
			expr = new BaseType(TypeKind.ERROR, stmt.posn);
		}

		if (stmt.initExp instanceof RefExpr) {
			RefExpr refExpr = (RefExpr) stmt.initExp;
			if (refExpr.ref.getDecl() instanceof ClassDecl || refExpr.ref.getDecl() instanceof MethodDecl) {
				eReport.reportError("*** varDeclStmt error at: " + stmt.posn.start);
			}
		}
		
		
		if (!checkEqual(var, expr)) {
			if (var instanceof ClassType && expr instanceof ClassType) {
				Identifier varClass = ((ClassType) var).className;
				Identifier exprClass = ((ClassType) expr).className;
				if (varClass.decl == null || exprClass.decl == null) {
					eReport.reportError("*** varDeclStmt error at: " + stmt.posn.start);
				} else if (!varClass.spelling.equals(exprClass.spelling)) {
					eReport.reportError("*** varDeclStmt error at: " + stmt.posn.start + "var Class != expr Class");
				}
			} else if (expr.typeKind == TypeKind.ERROR) {
				if (var.typeKind == TypeKind.INT) {
					eReport.reportError("*** varDeclStmt error int at: " + stmt.posn.start);
				}
			} else if (var instanceof ArrayType && expr instanceof ArrayType) {
				
			} else if (var instanceof ArrayType && expr instanceof BaseType) {
			}
			else {
				eReport.reportError("*** varDeclStmt error else at: " + stmt.posn.start);
			}
			return new BaseType(TypeKind.ERROR, stmt.posn);
		}
		return var;
	}

	@Override
	public TypeDenoter visitAssignStmt(AssignStmt stmt, TypeDenoter arg) {
		TypeDenoter left = stmt.ref.visit(this, arg);
		TypeDenoter right = stmt.val.visit(this, arg);
		if (stmt.val.getType() != null) {
			if (right.typeKind != TypeKind.NULL) {
				if (!left.typeKind.equals(right.typeKind)) {
					eReport.reportError("*** left and right do not match at: " + stmt.posn.start);
				}
			}
		}
		return null;
	}

	@Override
	public TypeDenoter visitIxAssignStmt(IxAssignStmt stmt, TypeDenoter arg) {
		TypeDenoter var = stmt.ref.visit(this, arg);
		TypeDenoter expr = stmt.exp.visit(this, arg);

		if (var.typeKind == TypeKind.ARRAY) {
			if (((ArrayType) var).eltType.typeKind == TypeKind.INT) {
				return new BaseType(TypeKind.INT, null);
			}

		}
		if (expr.typeKind != TypeKind.NULL) {
			if (!checkEqual(var, expr)) {
				return new BaseType(TypeKind.ERROR, null);
			} else if (!var.typeKind.equals(expr.typeKind)) {
				eReport.reportError("*** left and right do not match at: " + stmt.posn.start);
			}
		}
		return null;
	}

	@Override
	public TypeDenoter visitCallStmt(CallStmt stmt, TypeDenoter arg) {
		if (stmt.methodRef.getDecl() instanceof MethodDecl) {
			MethodDecl cMethod = (MethodDecl) stmt.methodRef.getDecl();
			if (!cMethod.name.equals("println")) {
				for (int i = 0; i < stmt.argList.size(); i++) {
					TypeDenoter argType = stmt.argList.get(i).visit(this, arg);
					TypeDenoter mArgType = cMethod.parameterDeclList.get(i).visit(this, arg);
					if (mArgType != null) {
						if (!argType.typeKind.equals(mArgType.typeKind)) {
							eReport.reportError("*** call stmt args !correct type at: " + stmt.posn.start);
						}
					}
				}
			} else {
				TypeDenoter argType = stmt.argList.get(0).visit(this, arg);
				if (!(argType.typeKind == TypeKind.INT) && !(argType.typeKind == TypeKind.ERROR)) {
					eReport.reportError("*** can only print int:" + stmt.posn.start);
				}
			}
		} else {
			if (!(stmt.methodRef instanceof ThisRef)) {
				eReport.reportError("*** calling something that is not a method at: " + stmt.posn.start + " spelling: "
						+ stmt.methodRef.getId().spelling);
			} else if (stmt.methodRef instanceof ThisRef) {
				eReport.reportError("*** this cannot be a method at: " + stmt.posn.start);
			}
		}
		return null;
	}

	@Override
	public TypeDenoter visitReturnStmt(ReturnStmt stmt, TypeDenoter arg) {
		TypeDenoter type;
		if (stmt.returnExpr == null) {
			type = new BaseType(TypeKind.VOID, stmt.posn);
		} else {
			type = stmt.returnExpr.visit(this, arg);
		}

		if (stmt.methodDecl == null) {
			eReport.reportError("*** errors at: " + stmt.posn.start);
		} else {
			stmt.methodDecl.returnType = type;
		}
		return null;
	}

	@Override
	public TypeDenoter visitIfStmt(IfStmt stmt, TypeDenoter arg) {
		TypeDenoter cond;
		if (stmt.cond != null) {
			cond = stmt.cond.visit(this, arg);
			if (cond.typeKind != TypeKind.BOOLEAN) {
				eReport.reportError("*** condition of if stmt can only be a boolean");
			}
		}
		stmt.thenStmt.visit(this, arg);
		if (stmt.elseStmt != null) {
			stmt.elseStmt.visit(this, arg);
		}
		return null;
	}

	@Override
	public TypeDenoter visitWhileStmt(WhileStmt stmt, TypeDenoter arg) {
		TypeDenoter cond;
		if (stmt.cond != null) {
			cond = stmt.cond.visit(this, arg);
			if (cond.typeKind != TypeKind.BOOLEAN) {
				eReport.reportError("*** condition of if stmt can only be a boolean");
			}
		}
		stmt.body.visit(this, arg);
		return null;
	}

	@Override
	public TypeDenoter visitUnaryExpr(UnaryExpr expr, TypeDenoter arg) {
		String op = expr.operator.spelling;
		TypeDenoter exprType = expr.expr.visit(this, arg);
		if (op.equals("!")) {
			if (exprType.typeKind == TypeKind.BOOLEAN) {
				return new BaseType(TypeKind.BOOLEAN, new SourcePosition());
			} else {
				eReport.reportError("*** type following \"!\" unary op must be boolean at : " + expr.posn.start);
			}
		} else if (op.equals("-")) {
			if (exprType.typeKind == TypeKind.INT) {
				return new BaseType(TypeKind.INT, new SourcePosition());
			} else {
				eReport.reportError("*** type following \"-\" unary op must be int at: " + expr.posn.start);
			}
		}
		return new BaseType(TypeKind.ERROR, new SourcePosition());
	}

	@Override
	public TypeDenoter visitBinaryExpr(BinaryExpr expr, TypeDenoter arg) {
		String op = expr.operator.spelling;
		TypeDenoter left = expr.left.visit(this, null);
		TypeDenoter right = expr.right.visit(this, null);
		if (op.equals("<=") || op.equals("<") || op.equals(">") || op.equals(">=")) {

			if (left.typeKind == TypeKind.INT && right.typeKind == TypeKind.INT) {
				return new BaseType(TypeKind.BOOLEAN, null);
			} else if (left.typeKind == TypeKind.ERROR && right.typeKind == TypeKind.INT) {
				return new BaseType(TypeKind.BOOLEAN, null);
			} else {
				eReport.reportError("*** these are used for ints at: " + expr.posn.start);
				return new BaseType(TypeKind.ERROR, null);
			}
		} else if (op.equals("!=") || op.equals("==")) {
			if (!checkValid(left) || !checkValid(right) || !checkEqual(left, right)) {
				eReport.reportError("*** expr does not match at: " + expr.posn.start);
				return new BaseType(TypeKind.ERROR, expr.posn);
			}
			return new BaseType(TypeKind.BOOLEAN, expr.posn);
		} else if (op.equals("&&") || op.equals("||")) {
			if (left.typeKind == TypeKind.BOOLEAN && right.typeKind == TypeKind.BOOLEAN) {
				return new BaseType(TypeKind.BOOLEAN, null);
			} else {
				eReport.reportError("*** expr does not match at: " + expr.posn.start);
				return new BaseType(TypeKind.ERROR, null);
			}
		} else if (op.equals("*") || op.equals("/") || op.equals("+") || op.equals("-")) {


			if (left.typeKind == TypeKind.INT && right.typeKind == TypeKind.INT) {
				return new BaseType(TypeKind.INT, null);
			} else if (left.typeKind == TypeKind.ERROR && right.typeKind == TypeKind.INT) {
				return new BaseType(TypeKind.INT, null);
			} else if (left.typeKind == TypeKind.INT && right.typeKind == TypeKind.ERROR) {
				return new BaseType(TypeKind.INT, null);
			} else {
				eReport.reportError("*** left and right do not match: l: " + left.typeKind + " r: " + right.typeKind
						+ " at: " + expr.posn.start);
				return new BaseType(TypeKind.ERROR, null);
			}
		} else {
			eReport.reportError("*** op is not good at: " + expr.posn.start);
			return new BaseType(TypeKind.ERROR, null);
		}
	}

	@Override
	public TypeDenoter visitRefExpr(RefExpr expr, TypeDenoter arg) {
		return expr.ref.visit(this, arg);
	}

	@Override
	public TypeDenoter visitIxExpr(IxExpr expr, TypeDenoter arg) {
		return expr.ref.visit(this, arg);
	}

	@Override
	public TypeDenoter visitCallExpr(CallExpr expr, TypeDenoter arg) {
		MethodDecl mDecl = (MethodDecl) expr.functionRef.getDecl();
		TypeDenoter mDeclType = null;
		if (expr.argList.size() != mDecl.parameterDeclList.size()) {
			eReport.reportError("*** args does not match method args size at: " + expr.posn.start);
			return new BaseType(TypeKind.ERROR, null);
		} else {
			for (int i = 0; i < expr.argList.size(); i++) {
				TypeDenoter argType = expr.argList.get(i).visit(this, null);
				TypeDenoter mArgType = mDecl.parameterDeclList.get(0).type;
				if (!argType.typeKind.equals(mArgType.typeKind)) {
					eReport.reportError("*** args does not match method arg type at: " + expr.posn.start);
					return new BaseType(TypeKind.ERROR, null);
				}
			}
			mDeclType = mDecl.type;
		}
		return mDeclType;
	}

	@Override
	public TypeDenoter visitParameterDecl(ParameterDecl pd, TypeDenoter arg) {
		return null;
	}

	@Override
	public TypeDenoter visitVarDecl(VarDecl decl, TypeDenoter arg) {
		return decl.type.visit(this, arg);
	}

	@Override
	public TypeDenoter visitBaseType(BaseType type, TypeDenoter arg) {
		return type;
	}

	@Override
	public TypeDenoter visitClassType(ClassType type, TypeDenoter arg) {
		return type;
	}

	@Override
	public TypeDenoter visitArrayType(ArrayType type, TypeDenoter arg) {
		TypeDenoter aType = type.eltType.visit(this, arg);
		return new ArrayType(aType, new SourcePosition());
	}

	@Override
	public TypeDenoter visitLiteralExpr(LiteralExpr expr, TypeDenoter arg) {
		return expr.lit.visit(this, arg);
	}

	@Override
	public TypeDenoter visitNewObjectExpr(NewObjectExpr expr, TypeDenoter arg) {
		return expr.classtype.visit(this, arg);
	}

	@Override
	public TypeDenoter visitNewArrayExpr(NewArrayExpr expr, TypeDenoter arg) {
		return new ArrayType(expr.eltType, new SourcePosition());
	}

	@Override
	public TypeDenoter visitThisRef(ThisRef ref, TypeDenoter arg) {
		return ref.decl.type;
	}

	@Override
	public TypeDenoter visitIdRef(IdRef ref, TypeDenoter arg) {
		if (ref.id.decl == null) {
			return new BaseType(TypeKind.INT, new SourcePosition());
		}
		return ref.id.decl.type;
	}

	@Override
	public TypeDenoter visitQRef(QualRef ref, TypeDenoter arg) {
		if (ref.id.decl == null) {
			return new BaseType(TypeKind.ERROR, new SourcePosition());
		}
		return ref.id.decl.type;
	}

	@Override
	public TypeDenoter visitIdentifier(Identifier id, TypeDenoter arg) {
		return id.decl.type;
	}

	@Override
	public TypeDenoter visitOperator(Operator op, TypeDenoter arg) {
		return null;
	}

	@Override
	public TypeDenoter visitIntLiteral(IntLiteral num, TypeDenoter arg) {
		return new BaseType(TypeKind.INT, new SourcePosition());
	}

	@Override
	public TypeDenoter visitBooleanLiteral(BooleanLiteral bool, TypeDenoter arg) {
		return new BaseType(TypeKind.BOOLEAN, new SourcePosition());
	}

	@Override
	public TypeDenoter visitNullLiteral(NullLiteral nullLiteral, TypeDenoter arg) {
		return new BaseType(TypeKind.NULL, new SourcePosition());
	}

	private boolean checkValid(TypeDenoter type) {
		if (type == null) {
			return false;
		} else if (type.typeKind == TypeKind.UNSUPPORTED) {
			return false;
		} else if (type.typeKind == TypeKind.ERROR) {
			return true;
		}
		return true;
	}

	private boolean checkEqual(TypeDenoter type1, TypeDenoter type2) {
		if (type1 instanceof ClassType || type2 instanceof ClassType) {
			if (type1.typeKind == TypeKind.CLASS && type2.typeKind == TypeKind.NULL) {
				return true;
			} else if (type1.typeKind == TypeKind.NULL && type2.typeKind == TypeKind.CLASS) {
				return true;
			} else if (!(type1 instanceof ClassType) || !(type2 instanceof ClassType)) {
				return false;
			}
			Identifier cName1 = ((ClassType) type1).className;
			Identifier cName2 = ((ClassType) type2).className;
			if (cName1.decl != null && cName2.decl != null) {
				if (cName1.decl.type == null || cName2.decl.type == null) {
					return false;
				} else if (cName1.decl.type.typeKind == TypeKind.UNSUPPORTED
						|| cName2.decl.type.typeKind == TypeKind.UNSUPPORTED) {
					return false;
				}
			}
			return cName1.spelling.equals(cName2.spelling);
		} else if (type1 instanceof ArrayType || type2 instanceof ArrayType) {
			if (type1.typeKind == TypeKind.NULL || type2.typeKind == TypeKind.NULL) {
				return true;
			}
			if (!(type1 instanceof ArrayType) || !(type2 instanceof ArrayType)) {
				return false;
			}
			return checkEqual(((ArrayType) type1).eltType, ((ArrayType) type2).eltType);

		}
		return type1.typeKind == type2.typeKind;
	}

	@Override
	public TypeDenoter visitArrayLengthRef(ArrayLengthRef arrayLengthRef, TypeDenoter o) {
		// TODO Auto-generated method stub
		return null;
	}
}