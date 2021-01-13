package miniJava.ContextualAnalyser;

import java.util.HashMap;
import java.util.HashSet;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;

public class IdChecks implements Visitor<Integer, Object> {

	public Package ast;
	private IdStack idStack;
	private ClassDecl curClass;
	private ErrorReporter eReport;
	private boolean isStatic;
	private HashSet<String> locVars;
	private String curVarDecl;
	private boolean hasMain;

	public IdChecks(Package ast, ErrorReporter eReport) {
		this.ast = ast;
		this.idStack = new IdStack();
		this.eReport = eReport;
	}

	public Package check() {
		hasMain = false;
		ast.visit(this, 0);
		if (!hasMain) {
			eReport.reportError("*** no main method found");
		}
		return ast;
	}

	@Override
	public Object visitPackage(Package prog, Integer arg) {
		ClassDeclList classes = prog.classDeclList;
		idStack.openScope();
		for (ClassDecl c : classes) {
			if (idStack.enter(c.name, c) == null) {
				eReport.reportError("*** class already exists at: " + c.posn.start);
			}
			for (FieldDecl f : c.fieldDeclList) {
				f.classDecl = c;
			}
			for (MethodDecl m : c.methodDeclList) {
				m.classDecl = c;
			}
		}

		for (ClassDecl c : classes) {
			idStack.openScope();
			for (FieldDecl f : c.fieldDeclList) {
				f.visit(this, 0);
				if (idStack.enter(f.name, f) == null) {
					eReport.reportError("*** field: " + f.name + " already exists at: " + prog.posn.start);
				}
			}
			for (MethodDecl m : c.methodDeclList) {
				if (idStack.enter(m.name, m) == null) {
					eReport.reportError("*** method " + m.name + " already exists at: " + prog.posn.start);
				}
				if (m.name.equals("main")) {
					hasMain = true;
					if (m.parameterDeclList.size() > 1) {
						eReport.reportError("*** main method has too many para at: " + m.posn.start);
					}

					if (m.isPrivate) {
						eReport.reportError("*** main method can not be private: " + m.posn.start);
					}

					if (!m.isStatic) {
						eReport.reportError("*** main method needs to be static: " + m.posn.start);
					}

					if (m.type.typeKind != TypeKind.VOID) {
						eReport.reportError("*** main method must be void: " + m.posn.start);
					}
				}
			}
		}

		for (ClassDecl c : classes) {
			curClass = c;
			c.visit(this, 0);
		}
		idStack.closeScope();
		return null;
	}

	@Override
	public Object visitClassDecl(ClassDecl cd, Integer arg) {
		idStack.openScope();
		for (MethodDecl m : cd.methodDeclList) {
			m.visit(this, 0);
		}
		return null;
	}

	@Override
	public Object visitFieldDecl(FieldDecl fd, Integer arg) {
		fd.type.visit(this, 0);
		return null;
	}

	@Override
	public Object visitMethodDecl(MethodDecl md, Integer arg) {
		md.type.visit(this, 0);
		isStatic = md.isStatic;
		idStack.openScope();
		locVars = new HashSet<>();
		for (ParameterDecl p : md.parameterDeclList) {
			p.visit(this, 0);
		}
		for (Statement s : md.statementList) {
			s.visit(this, 0);
		}
		idStack.closeScope();
		return null;
	}

	@Override
	public Object visitParameterDecl(ParameterDecl pd, Integer arg) {
		pd.type.visit(this, 0);
		if (locVars.contains(pd.name)) {
			eReport.reportError("*** Duplicate ID in parameter: " + pd.name + " at: " + pd.posn.start);
		} else {
			locVars.add(pd.name);
		}
		if (idStack.enter(pd.name, pd) == null) {
			eReport.reportError("*** Duplicate ID in parameter: " + pd.name + " at: " + pd.posn.start);
		}
		return null;
	}

	@Override
	public Object visitVarDecl(VarDecl decl, Integer arg) {
		decl.type.visit(this, 0);
		if (locVars.contains(decl.name)) {
			eReport.reportError("*** Duplicate ID var declaration: " + decl.name + " at: " + decl.posn.start);
		} else {
			locVars.add(decl.name);
		}
		if (idStack.enter(decl.name, decl) == null) {
			eReport.reportError("*** Duplicate ID var declaration: " + decl.name + " at: " + decl.posn.start);
		}
		return null;
	}

	@Override
	public Object visitClassType(ClassType type, Integer arg) {
		if (idStack.retrieveClass(type.className.spelling) == null) {
			eReport.reportError("*** No such class exists");
		} else {
			type.className.decl = idStack.retrieveClass(type.className.spelling);
		}
		return null;
	}

	@Override
	public Object visitArrayType(ArrayType type, Integer arg) {
		type.eltType.visit(this, 0);
		return null;
	}

	@Override
	public Object visitBlockStmt(BlockStmt stmt, Integer arg) {
		idStack.openScope();
		for (Statement s : stmt.sl) {
			s.visit(this, 0);
		}
		HashMap<String, Declaration> curTable = idStack.curTable;
		for (String s : curTable.keySet()) {
			if (locVars.contains(s)) {
				locVars.remove(s);
			}
		}
		return null;
	}

	@Override
	public Object visitVardeclStmt(VarDeclStmt stmt, Integer arg) {
		curVarDecl = stmt.varDecl.name;
		stmt.initExp.visit(this, 0);
		curVarDecl = "";
		stmt.varDecl.visit(this, 0);
		return null;
	}

	@Override
	public Object visitAssignStmt(AssignStmt stmt, Integer arg) {

		if (stmt.ref instanceof ThisRef) {
			eReport.reportError("*** Cannot assign \"this\" to anything" + " at: " + stmt.posn.start);
		}

		if (stmt.ref instanceof QualRef) {
			if (stmt.ref.getId().spelling.equals("length")) {
				eReport.reportError("*** cannot assign length at: " + stmt.posn.start);
			}
		}

		stmt.ref.visit(this, 0);
		stmt.val.visit(this, 0);

		if (stmt.ref.getDecl() instanceof FieldDecl) {
			if (stmt.ref.isStatic) {
			}
		}

		return null;
	}

	@Override
	public Object visitIxAssignStmt(IxAssignStmt stmt, Integer arg) {
		if (stmt.ref instanceof ThisRef) {
			eReport.reportError("*** Cannot assign \"this\" to anything" + " at: " + stmt.posn.start);
		}
		stmt.ref.visit(this, 0);
		stmt.exp.visit(this, 0);
		stmt.ix.visit(this, 0);
		return null;
	}

	@Override
	public Object visitCallStmt(CallStmt stmt, Integer arg) {
		stmt.methodRef.visit(this, 0);
		for (Expression e : stmt.argList) {
			e.visit(this, 0);
		}
		return null;
	}

	@Override
	public Object visitReturnStmt(ReturnStmt stmt, Integer arg) {
		if (stmt.returnExpr != null)
			stmt.returnExpr.visit(this, 0);
		return null;
	}

	@Override
	public Object visitIfStmt(IfStmt stmt, Integer arg) {
		stmt.cond.visit(this, 0);
		if (stmt.thenStmt instanceof VarDeclStmt) {
			eReport.reportError("*** can not do in If stmt" + " at: " + stmt.posn.start);
		} else {
			stmt.thenStmt.visit(this, 0);
		}
		if (stmt.elseStmt != null) {
			if (stmt.elseStmt instanceof VarDeclStmt) {
				eReport.reportError("*** can not do in else stmt" + " at: " + stmt.posn.start);
			} else {
				stmt.elseStmt.visit(this, 0);
			}
		}
		return null;
	}

	@Override
	public Object visitWhileStmt(WhileStmt stmt, Integer arg) {
		stmt.cond.visit(this, 0);
		if (stmt.body != null) {
			if (stmt.body instanceof VarDeclStmt) {
				eReport.reportError("*** can not do in while body" + " at: " + stmt.posn.start);
			} else {
				stmt.body.visit(this, 0);
			}
		}
		return null;
	}

	@Override
	public Object visitUnaryExpr(UnaryExpr expr, Integer arg) {
		expr.expr.visit(this, 0);
		return null;
	}

	@Override
	public Object visitBinaryExpr(BinaryExpr expr, Integer arg) {
		expr.left.visit(this, 0);
		expr.right.visit(this, 0);
		return null;
	}

	@Override
	public Object visitRefExpr(RefExpr expr, Integer arg) {
		expr.ref.visit(this, 0);
		if (expr.ref instanceof IdRef
				&& (expr.ref.getDecl() instanceof ClassDecl || expr.ref.getDecl() instanceof MethodDecl)) {
			eReport.reportError("*** Cant do these things" + " at: " + expr.posn.start);
		}
		return null;
	}

	@Override
	public Object visitIxExpr(IxExpr expr, Integer arg) {
		expr.ixExpr.visit(this, 0);
		if (expr.ref instanceof IdRef
				&& (expr.ref.getDecl() instanceof ClassDecl || expr.ref.getDecl() instanceof MethodDecl)) {
			eReport.reportError("*** Cant do these things" + " at: " + expr.posn.start);
		}
		return null;
	}

	@Override
	public Object visitCallExpr(CallExpr expr, Integer arg) {
		expr.functionRef.visit(this, 2);
		for (Expression e : expr.argList) {
			e.visit(this, 0);
		}
		return null;
	}

	@Override
	public Object visitLiteralExpr(LiteralExpr expr, Integer arg) {
		expr.lit.visit(this, 0);
		return null;
	}

	@Override
	public Object visitNewObjectExpr(NewObjectExpr expr, Integer arg) {
		expr.classtype.className.visit(this, 0);
		return null;
	}

	@Override
	public Object visitNewArrayExpr(NewArrayExpr expr, Integer arg) {
		expr.eltType.visit(this, 0);
		expr.sizeExpr.visit(this, 0);
		return null;
	}

	@Override
	public Object visitThisRef(ThisRef ref, Integer arg) {
		if (isStatic) {
			eReport.reportError("*** Error \"this\" should be in static" + " at: " + ref.posn.start);
		}
		ref.decl = curClass;
		ref.isStatic = false;
		return ref;
	}

	@Override
	public Object visitIdRef(IdRef ref, Integer arg) {
		ref.id.visit(this, arg);
		ref.isStatic = ref.id.isStatic;
		if (ref.id.spelling.equals(curVarDecl)) {
			eReport.reportError("*** IDRef Error: " + ref.id.spelling + " at: " + ref.posn.start);
		}
		return null;
	}

	@Override
	public Object visitQRef(QualRef ref, Integer arg) {
		ref.ref.visit(this, 0);
		Declaration oldDecl = ref.ref.getDecl();
		boolean oldStatic = isStatic;
		isStatic = ref.ref.isStatic;

		if (oldDecl == null)
			return null;
		if (oldDecl instanceof MethodDecl) {
			eReport.reportError("*** can not have method in middle of QRef: " + ref.posn.start);
		}

		if (oldDecl instanceof ClassDecl) {
			ClassDecl classDecl = (ClassDecl) oldDecl;
			ref.id.visit(this, 3);
			ref.isStatic = ref.id.isStatic;
			Declaration declaration = ref.getDecl();
			boolean sameClass = classDecl == curClass;
			if (declaration instanceof MemberDecl) {
				if (!sameClass) {
					String checkName = declaration.name;
					if (!classDecl.existsMember(checkName, isStatic, true)) {
						eReport.reportError("*** can not find static and public member at: " + ref.posn.start);
					}
				} else {
					if (!classDecl.existsMember(declaration.name, isStatic, false)) {
						eReport.reportError("*** can not find member at: " + ref.posn.start);
					}
				}
			} else if (ref.id.spelling.equals("out")) {

			} else {
				eReport.reportError("*** expected member decl at: " + ref.posn.start);
			}

		} else {
			ref.id.visit(this, 3);
			ref.isStatic = ref.id.isStatic;
			Declaration decl = ref.getDecl();
			if (oldDecl.type.typeKind == TypeKind.CLASS) {
				ClassDecl classDecl = (ClassDecl) (oldDecl.type.getDecl());
				if (oldDecl instanceof ClassDecl)
					classDecl = (ClassDecl) oldDecl;
				boolean sameClass = classDecl == curClass;

				if (decl instanceof MemberDecl) {
					if (!sameClass) {
						String checkName = decl.name;
						if (classDecl == null) {
						} else if (!classDecl.existsMember(checkName, false, true)) {
							eReport.reportError("*** can not find public member at: " + ref.ref.posn.start);
						}
					} else {
						if (!classDecl.existsMember(decl.name, false, false)) {
							eReport.reportError("*** can not find public member at: " + ref.ref.posn.start);
						}
					}
				} else {
					eReport.reportError("*** expected member decl at: " + ref.posn.start);
				}
			} else if (oldDecl.type.typeKind == TypeKind.ARRAY) {
				if (!ref.id.spelling.equals("length")) {
					eReport.reportError("*** ref to member of an array at: " + ref.id.posn.start);
				}
			} else {
				eReport.reportError("*** ref to member f primitive type at: " + ref.posn.start);
			}
		}
		isStatic = oldStatic;
		return null;
	}

	@Override
	public Object visitIdentifier(Identifier id, Integer arg) {
		id.isStatic = false;

		if (id.spelling.equals("length")) {
			id.decl = new FieldDecl(false, false, new BaseType(TypeKind.INT, id.posn), id.spelling, id.posn);
			return null;
		}

		if (id.spelling.equals("out")) {
			id.decl = idStack.retrieve(id.spelling);
			return null;
		}
		if (arg == 3) {
			Declaration declaration = idStack.retrieveMember(id.spelling);
			if (declaration == null) {
				eReport.reportError("*** expected a method name at: " + id.posn.start + "spelling: " + id.spelling);
			} else {
				id.decl = declaration;
			}
			return null;

		}
		Declaration decl = idStack.retrieve(id.spelling);
		if (decl == null) {
			eReport.reportError("*** cant find decl: " + id.spelling + " at line: " + id.posn.start);
		} else {
			if (decl instanceof ClassDecl) {
				id.decl = decl;
				id.isStatic = true;
			} else {
				if (decl instanceof MemberDecl) {
					MemberDecl memberDecl = (MemberDecl) decl;
					if ((isStatic && !memberDecl.isStatic)) {
						eReport.reportError("*** ref to non-static var in static method: " + id.posn.start);
					}
					boolean sameClass = curClass == memberDecl.classDecl;
					if (!sameClass && !isStatic && memberDecl.isStatic) {
						eReport.reportError("*** ref to static var in non-static method: " + id.posn.start);
					}
				}
				decl.type.visit(this, 0);
				id.decl = decl;
			}
		}
		return null;
	}

	@Override
	public Object visitBaseType(BaseType type, Integer arg) {
		return null;
	}

	@Override
	public Object visitOperator(Operator op, Integer arg) {
		return null;
	}

	@Override
	public Object visitIntLiteral(IntLiteral num, Integer arg) {
		return null;
	}

	@Override
	public Object visitBooleanLiteral(BooleanLiteral bool, Integer arg) {
		return null;
	}

	@Override
	public Object visitNullLiteral(NullLiteral nullLiteral, Integer o) {
		return null;
	}

	@Override
	public Object visitArrayLengthRef(ArrayLengthRef arrayLengthRef, Integer o) {
		// TODO Auto-generated method stub
		return null;
	}
}
