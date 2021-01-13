package miniJava.SyntacticAnalyzer;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.AbstractSyntaxTrees.*;

public class Parser {

	private Scanner scanner;
	private ErrorReporter reporter;
	private Token token;
	private boolean trace = true;
	SourcePosition posn;

	public Parser(Scanner scanner, ErrorReporter reporter) {
		this.scanner = scanner;
		this.reporter = reporter;
		this.posn = new SourcePosition();
	}

	/**
	 * SyntaxError is used to unwind parse stack when parse fails
	 *
	 */
	class SyntaxError extends Error {
		private static final long serialVersionUID = 1L;
	}

	/**
	 * parse input, catch possible parse error
	 * 
	 * @return
	 */
	public Package parse() {
		Package ast = null;
		token = scanner.scan();
		try {
			ast = parseProgram();
		} catch (SyntaxError e) {
		}

		return ast;
	}

	public Package parseProgram() throws SyntaxError {
		Package packageAST = null;
		ClassDeclList classDeclListAST = new ClassDeclList();
		SourcePosition posn = new SourcePosition();
		startPosn(posn);
		if (token.kind == TokenKind.CLASS) {
			SourcePosition declPosn = new SourcePosition();
			startPosn(declPosn);
			while (token.kind == TokenKind.CLASS) {
				ClassDecl classDeclAST = parseClassDeclaration();
				classDeclListAST.add(classDeclAST);
			}
			endPosn(declPosn);
			packageAST = new Package(classDeclListAST, declPosn);
		} else if (token.kind != TokenKind.EOT) {
			parseError("invalid " + token.kind);
		} else {
			packageAST = new Package(classDeclListAST, posn);
		}
		endPosn(posn);
		accept(TokenKind.EOT);
		return packageAST;
	}

	public ClassDecl parseClassDeclaration() throws SyntaxError {
		ClassDecl classDecl = null;

		FieldDeclList fieldDeclList = new FieldDeclList();
		MethodDeclList methodDeclList = new MethodDeclList();

		MemberDecl memberDecl = null;
		FieldDecl fieldDecl = null;
		MethodDecl methodDecl = null;
		SourcePosition memberPosn = new SourcePosition();
		startPosn(memberPosn);

		accept(TokenKind.CLASS);

		Identifier idClass = new Identifier(token);
		String idClassString = token.spelling;
		accept(TokenKind.ID);
		accept(TokenKind.LCURL);
		while (token.kind != TokenKind.RCURL) {
			Boolean isPrivate = parseVisibility();
			Boolean isStatic = parseAccess();
			TypeDenoter typeDecl = parseType();
			Identifier idDecl = new Identifier(token);
			String idDeclString = token.spelling;
			accept(TokenKind.ID);

			memberDecl = new FieldDecl(isPrivate, isStatic, typeDecl, idDeclString, memberPosn);
			if (token.kind == TokenKind.SEMICOLON && typeDecl.typeKind != TypeKind.VOID) {
				acceptIt();
				fieldDecl = new FieldDecl(memberDecl, null);
				fieldDeclList.add(fieldDecl);
			} else if (token.kind == TokenKind.LPAREN) {
				acceptIt();
				ParameterDeclList paraDeclList = new ParameterDeclList();
				StatementList statementList = new StatementList();
				Expression returnStmt = null;

				if (token.kind != TokenKind.RPAREN) {
					paraDeclList = parseParameterList();
				}
				accept(TokenKind.RPAREN);
				accept(TokenKind.LCURL);
				while (token.kind != TokenKind.RCURL) {
					Statement stmt = parseStatement();
					statementList.add(stmt);
				}
				accept(TokenKind.RCURL);

				methodDecl = new MethodDecl(memberDecl, paraDeclList, statementList, memberPosn);
				methodDeclList.add(methodDecl);

			} else if (token.kind == TokenKind.ASSIGN) {
				acceptIt();
				parseType();
				accept(TokenKind.SEMICOLON);
				fieldDecl = new FieldDecl(memberDecl, null);
				fieldDeclList.add(fieldDecl);
			}else {
				parseError("Invalid Term - " + token.kind);
			}

		}
		accept(TokenKind.RCURL);

		classDecl = new ClassDecl(idClassString, fieldDeclList, methodDeclList, memberPosn);
		endPosn(memberPosn);
		return classDecl;

	}

	// boolean isPrivate is true;
	public Boolean parseVisibility() throws SyntaxError {
		SourcePosition posn = new SourcePosition();
		startPosn(posn);
		Boolean b = false;
		if (token.kind == TokenKind.PUBLIC || token.kind == TokenKind.PRIVATE) {
			if (token.kind == TokenKind.PRIVATE) {
				b = true;
			}
			acceptIt();
		}
		endPosn(posn);
		return b;
	}

	public Boolean parseAccess() throws SyntaxError {
		SourcePosition posn = new SourcePosition();
		startPosn(posn);
		Boolean b = false;
		if (token.kind == TokenKind.STATIC) {
			b = true;
			acceptIt();
		}
		endPosn(posn);
		return b;
	}

	public TypeDenoter parseType() throws SyntaxError {
		SourcePosition posn = new SourcePosition();
		startPosn(posn);
		switch (token.kind) {

		case TRUE:
		case FALSE:
		case BOOL:
			acceptIt();
			endPosn(posn);
			return new BaseType(TypeKind.BOOLEAN, posn);

		case ID:
			Identifier id = new Identifier(token);
			ClassType classType = new ClassType(id, posn);
			acceptIt();
			if (token.kind == TokenKind.LBRACK) {
				acceptIt();
				accept(TokenKind.RBRACK);
				endPosn(posn);
				return new ArrayType(classType, posn);
			}
			endPosn(posn);
			return classType;

		case INT:
		case NUM:
			BaseType baseType = new BaseType(TypeKind.INT, posn);
			acceptIt();
			if (token.kind == TokenKind.LBRACK) {
				acceptIt();
				accept(TokenKind.RBRACK);
				endPosn(posn);
				return new ArrayType(baseType, posn);
			}
			endPosn(posn);
			return baseType;

		case VOID:
			acceptIt();
			endPosn(posn);
			return new BaseType(TypeKind.VOID, posn);

		default:
			parseError("Invalid Term - " + token.kind);
			endPosn(posn);
			return new BaseType(TypeKind.ERROR, posn);
		}
	}

	public ParameterDeclList parseParameterList() throws SyntaxError {
		SourcePosition posn = new SourcePosition();
		startPosn(posn);
		ParameterDeclList paraDeclList = new ParameterDeclList();
		TypeDenoter type = parseType();
		if (type.typeKind == TypeKind.VOID) {
			parseError("Invalid Term - " + token.kind);
		}
		Identifier id = new Identifier(token); // TODO may be unneeded
		String idString = token.spelling;
		accept(TokenKind.ID);

		ParameterDecl paraDecl = new ParameterDecl(type, idString, posn);
		paraDeclList.add(paraDecl);

		while (token.kind == TokenKind.COMMA) {
			acceptIt();
			TypeDenoter type1 = parseType();
			Identifier id1 = new Identifier(token); // TODO may be unneeded
			String id1String = token.spelling;
			accept(TokenKind.ID);
			ParameterDecl paraDecl1 = new ParameterDecl(type1, id1String, posn);
			paraDeclList.add(paraDecl1);

		}
		endPosn(posn);
		return paraDeclList;
	}

	public ExprList parseArgumentList() throws SyntaxError {
		ExprList exprList = new ExprList();
		exprList.add(parseExpression());
		while (token.kind == TokenKind.COMMA) {
			acceptIt();
			exprList.add(parseExpression());
		}
		return exprList;
	}

	public Statement parseStatement() throws SyntaxError {
		SourcePosition posn = new SourcePosition();
		startPosn(posn);
		Statement stmt = null;

		if (token.kind == TokenKind.IF) {
			acceptIt();

			Expression ifExpr = null;
			Statement thenStmt = null;
			Statement elseStmt = null;

			accept(TokenKind.LPAREN);
			ifExpr = parseExpression();
			accept(TokenKind.RPAREN);
			thenStmt = parseStatement();
			if (token.kind == TokenKind.ELSE) {
				acceptIt();
				elseStmt = parseStatement();
			}

			IfStmt ifStmt = new IfStmt(ifExpr, thenStmt, elseStmt, posn);
			endPosn(posn);
			stmt = ifStmt;
		} else if (token.kind == TokenKind.WHILE) {
			Expression whileExpr = null;
			Statement whileStmt1 = null;

			acceptIt();
			accept(TokenKind.LPAREN);
			whileExpr = parseExpression();
			accept(TokenKind.RPAREN);
			whileStmt1 = parseStatement();

			endPosn(posn);
			WhileStmt whileStmt = new WhileStmt(whileExpr, whileStmt1, posn);
			stmt = whileStmt;
		} else if (token.kind == TokenKind.LCURL) {
			acceptIt();
			StatementList stmtList = new StatementList();
			Statement blockStmt1 = null;

			while (token.kind != TokenKind.RCURL) {
				blockStmt1 = parseStatement();
				stmtList.add(blockStmt1);
			}

			accept(TokenKind.RCURL);

			endPosn(posn);
			BlockStmt blockStmt = new BlockStmt(stmtList, posn);
			stmt = blockStmt;
		} else if (token.kind == TokenKind.ID) {
			Identifier idType = new Identifier(token);
			String idTypeString = token.spelling;

			Reference refDecl = null;
			VarDecl varDecl = null;
			Expression ixExpr = null;

			Boolean isType = false;
			SourcePosition varPos = new SourcePosition();

			acceptIt();
			if (token.kind == TokenKind.ID) {
				Identifier id1 = new Identifier(token);
				String id1TypeString = token.spelling;
				isType = true;
				acceptIt();
				varPos.start = idType.posn.start;
				varPos.end = id1.posn.end;
				ClassType classType = new ClassType(idType, idType.posn);
				varDecl = new VarDecl(classType, id1TypeString, id1.posn);
			} else if (token.kind == TokenKind.LBRACK) {
				acceptIt();
				if (token.kind == TokenKind.RBRACK) { // type id[] id array
					acceptIt();
					Identifier id1 = new Identifier(token);
					String id1TypeString = token.spelling;
					acceptIt();
					isType = true;
					varPos.start = idType.posn.start;
					varPos.end = id1.posn.end;

					ClassType classType = new ClassType(idType, idType.posn);
					ArrayType arrayType = new ArrayType(classType, varPos);

					varDecl = new VarDecl(arrayType, id1TypeString, posn);
				} else { // ref [expr]
					SourcePosition refPos = new SourcePosition();
					refPos.start = posn.start;
					endPosn(refPos);
					refDecl = new IdRef(idType, refPos);
					ixExpr = parseExpression();
					accept(TokenKind.RBRACK);
				}
			} else if (token.kind == TokenKind.DOT) { // id.id.id.id[expr] = expr;
				SourcePosition idPosn = new SourcePosition();
				idPosn.start = posn.start;
				endPosn(idPosn);
				refDecl = new IdRef(idType, posn);
				while (token.kind == TokenKind.DOT) {
					acceptIt();
					if (token.kind == TokenKind.THIS) {
						parseError("Invalid Term - " + token.kind);
					}
					Identifier id2 = new Identifier(token);
					String id2String = token.spelling;
					accept(TokenKind.ID);
					endPosn(idPosn);
					refDecl = new QualRef(refDecl, id2, idPosn);
				}
				if (token.kind == TokenKind.LBRACK) {
					acceptIt();
					ixExpr = parseExpression();
					accept(TokenKind.RBRACK);
					endPosn(idPosn);
				}
			} else {
				refDecl = new IdRef(idType, idType.posn);
			}

			if (token.kind == TokenKind.ASSIGN) { // Type id =
				acceptIt();
				Expression expr = parseExpression();
				accept(TokenKind.SEMICOLON);
				endPosn(posn);
				if (ixExpr != null) {
					stmt = new IxAssignStmt(refDecl, ixExpr, expr, posn);
				} else if (varDecl != null) {
					stmt = new VarDeclStmt(varDecl, expr, posn);
				} else {
					stmt = new AssignStmt(refDecl, expr, posn);
				}
			} else if (token.kind == TokenKind.LPAREN && !isType && ixExpr == null) { // ref ( args )
				acceptIt();
				ExprList argsList = new ExprList();
				if (token.kind != TokenKind.RPAREN) {
					argsList = parseArgumentList();
				}
				accept(TokenKind.RPAREN);
				accept(TokenKind.SEMICOLON);
				endPosn(posn);
				stmt = new CallStmt(refDecl, argsList, posn);
			} else {
				refDecl = new IdRef(idType, idType.posn);
			}
		} else if (token.kind == TokenKind.THIS) { // ref = || ref()
			SourcePosition thisPosn = token.posn;
			acceptIt();
			Reference refDecl = new ThisRef(thisPosn);
			Expression ixExpr = null;
			if (token.kind == TokenKind.DOT) {
				while (token.kind == TokenKind.DOT) {
					acceptIt();
					if (token.kind == TokenKind.THIS) {
						parseError("Invalid Term - " + token.kind);
					}
					Identifier id2 = new Identifier(token);
					accept(TokenKind.ID);
					refDecl = new QualRef(refDecl, id2, posn);
				}
				if (token.kind == TokenKind.LBRACK) {
					acceptIt();
					ixExpr = parseExpression();
					accept(TokenKind.RBRACK);
				}
			} else if (token.kind == TokenKind.LBRACK) {
				acceptIt();
				ixExpr = parseExpression();
				accept(TokenKind.RBRACK);
			}

			if (token.kind == TokenKind.ASSIGN) {
				acceptIt();
				Expression expr = parseExpression();
				accept(TokenKind.SEMICOLON);
				if (ixExpr != null) {
					endPosn(posn);
					stmt = new IxAssignStmt(refDecl, ixExpr, expr, posn);
				} else {
					endPosn(posn);
					stmt = new AssignStmt(refDecl, expr, posn);
				}
			} else if (token.kind == TokenKind.LPAREN) {
				acceptIt();
				ExprList argsList = new ExprList();
				if (token.kind != TokenKind.RPAREN) {
					argsList = parseArgumentList();
				}
				accept(TokenKind.RPAREN);
				accept(TokenKind.SEMICOLON);

				stmt = new CallStmt(refDecl, argsList, posn);
			} else {
				parseError("Invalid Term - " + token.kind);
			}
		} else if (token.kind == TokenKind.RETURN) {
			acceptIt();
			if (token.kind == TokenKind.SEMICOLON) {
				acceptIt();
				endPosn(posn);
				stmt = new ReturnStmt(null, posn);
			} else {
				Expression expr = parseExpression();
				accept(TokenKind.SEMICOLON);
				endPosn(posn);
				stmt = new ReturnStmt(expr, posn);
			}
		} else if (token.kind == TokenKind.INT || token.kind == TokenKind.BOOL) {
			TypeDenoter type = parseType();
			Identifier id = new Identifier(token);
			String idString = token.spelling;
			accept(TokenKind.ID);
			accept(TokenKind.ASSIGN);
			Expression expr = parseExpression();
			accept(TokenKind.SEMICOLON);
			endPosn(posn);
			VarDecl varDecl = new VarDecl(type, idString, posn);
			stmt = new VarDeclStmt(varDecl, expr, posn);
		} else {
			parseError("Invalid Term - " + token.kind);
		}
		return stmt;
	}

	public Expression parseExpression() throws SyntaxError { // down the rabbit hole we goooooo!
		return parseDisj();
	}

	public Expression parseDisj() throws SyntaxError { // ||
		SourcePosition posn = new SourcePosition();
		startPosn(posn);
		Expression expr = null;
		expr = parseConj();
		while (token.kind == TokenKind.DISJ) {
			Operator op = new Operator(token);
			acceptIt();
			Expression rightExpr = parseConj();
			endPosn(posn);
			expr = new BinaryExpr(op, expr, rightExpr, posn);
		}
		return expr;
	}

	public Expression parseConj() throws SyntaxError { // &&
		SourcePosition posn = new SourcePosition();
		startPosn(posn);
		Expression expr = null;
		expr = parseEquality();
		while (token.kind == TokenKind.CONJ) {
			Operator op = new Operator(token);
			acceptIt();
			Expression rightExpr = parseEquality();
			endPosn(posn);
			expr = new BinaryExpr(op, expr, rightExpr, posn);
		}
		return expr;
	}

	public Expression parseEquality() throws SyntaxError { // == || !=
		SourcePosition posn = new SourcePosition();
		startPosn(posn);
		Expression expr = null;
		expr = parseRelational();
		while (token.kind == TokenKind.EQUALITY) {
			Operator op = new Operator(token);
			acceptIt();
			Expression rightExpr = parseRelational();
			endPosn(posn);
			expr = new BinaryExpr(op, expr, rightExpr, posn);
		}
		return expr;
	}

	public Expression parseRelational() throws SyntaxError { // < || > || <= || >=
		SourcePosition posn = new SourcePosition();
		startPosn(posn);
		Expression expr = null;
		expr = parseAddi();
		while (token.kind == TokenKind.RELATIONAL) {
			Operator op = new Operator(token);
			acceptIt();
			Expression rightExpr = parseAddi();
			endPosn(posn);
			expr = new BinaryExpr(op, expr, rightExpr, posn);
		}
		return expr;
	}

	public Expression parseAddi() throws SyntaxError { // + || -
		SourcePosition posn = new SourcePosition();
		startPosn(posn);
		Expression expr = null;
		expr = parseMulti();
		while (token.kind == TokenKind.ADDI || token.kind == TokenKind.NEG) {
			Operator op = new Operator(token);
			acceptIt();
			Expression rightExpr = parseMulti();
			endPosn(posn);
			expr = new BinaryExpr(op, expr, rightExpr, posn);
		}
		return expr;
	}

	public Expression parseMulti() throws SyntaxError { // * || /
		SourcePosition posn = new SourcePosition();
		startPosn(posn);
		Expression expr = null;
		expr = parseUnary();
		while (token.kind == TokenKind.MULTI) {
			Operator op = new Operator(token);
			acceptIt();
			Expression rightExpr = parseUnary();
			endPosn(posn);
			expr = new BinaryExpr(op, expr, rightExpr, posn);
		}
		return expr;
	}

	public Expression parseUnary() throws SyntaxError { // - || !
		SourcePosition posn = new SourcePosition();
		startPosn(posn);
		Expression expr;
		if (token.kind == TokenKind.NOT || token.kind == TokenKind.NEG) {
			Operator op = new Operator(token);
			acceptIt();
			Expression rightExpr = parseUnary();
			endPosn(posn);
			expr = new UnaryExpr(op, rightExpr, posn);
		} else {
			expr = parseBaseExpression();
		}
		return expr;
	}

	public Expression parseBaseExpression() throws SyntaxError {
		SourcePosition posn = new SourcePosition();
		startPosn(posn);
		Expression expr = null;
		if (token.kind == TokenKind.NULL) {
			NullLiteral nullLit = new NullLiteral(token);
			acceptIt();
			endPosn(posn);
			expr = new LiteralExpr(nullLit, posn);
		} else if (token.kind == TokenKind.NUM) {
			IntLiteral intLit = new IntLiteral(token);
			acceptIt();
			endPosn(posn);
			expr = new LiteralExpr(intLit, posn);
		} else if (token.kind == TokenKind.TRUE || token.kind == TokenKind.FALSE) {
			BooleanLiteral boolLit = new BooleanLiteral(token);
			acceptIt();
			endPosn(posn);
			expr = new LiteralExpr(boolLit, posn);
		} else if (token.kind == TokenKind.NEW) {
			acceptIt();
			if (token.kind == TokenKind.ID) {
				Identifier id = new Identifier(token);
				ClassType classType = new ClassType(id, posn);
				acceptIt();
				if (token.kind == TokenKind.LPAREN) {
					acceptIt();
					accept(TokenKind.RPAREN);
					endPosn(posn);
					expr = new NewObjectExpr(classType, posn);
				} else if (token.kind == TokenKind.LBRACK) {
					acceptIt();
					Expression exprNew = parseExpression();
					accept(TokenKind.RBRACK);
					endPosn(posn);
					expr = new NewArrayExpr(classType, exprNew, posn);
				} else {
					parseError("Invalid New Expression");
				}
			} else if (token.kind == TokenKind.INT) {
				BaseType baseType = new BaseType(TypeKind.INT, posn);
				acceptIt();
				Expression exprNew = null;
				if (token.kind == TokenKind.LBRACK) {
					acceptIt();
					exprNew = parseExpression();
					accept(TokenKind.RBRACK);
				}
				endPosn(posn);
				expr = new NewArrayExpr(baseType, exprNew, posn);
			} else {
				parseError("Invalid Token - " + token.kind);
			}
		} else if (token.kind == TokenKind.LPAREN) {
			acceptIt();
			endPosn(posn);
			expr = parseExpression();
			accept(TokenKind.RPAREN);
		} else if (token.kind == TokenKind.ID || token.kind == TokenKind.THIS) {
			Identifier idType = null;
			Reference refDecl = null;
			Expression ixExpr = null;

			if (token.kind == TokenKind.ID) { // id.id || id.id[expr] || id.id (args) || id
				idType = new Identifier(token);
				refDecl = new IdRef(idType, posn);
				acceptIt();
				if (token.kind == TokenKind.LBRACK) {
					acceptIt();
					ixExpr = parseExpression();
					accept(TokenKind.RBRACK);
					endPosn(posn);
					refDecl = new IdRef(idType, posn);
					endPosn(posn);
					expr = new IxExpr(refDecl, ixExpr, posn);
				} else if (token.kind == TokenKind.DOT) {
					while (token.kind == TokenKind.DOT) {
						acceptIt();
						if (token.kind == TokenKind.THIS) {
							parseError("Invalid Term - " + token.kind);
						}
						Identifier id2 = new Identifier(token);
						accept(TokenKind.ID);
						endPosn(posn);
						refDecl = new QualRef(refDecl, id2, posn);
					}
					if (token.kind == TokenKind.LBRACK) { // id.id[expr]
						acceptIt();
						ixExpr = parseExpression();
						accept(TokenKind.RBRACK);
						endPosn(posn);
						expr = new IxExpr(refDecl, ixExpr, posn);
					} else if (token.kind == TokenKind.LPAREN) { // id.id(args)
						acceptIt();
						ExprList exprList = new ExprList();
						if (token.kind != TokenKind.RPAREN) {
							exprList = parseArgumentList();
						}
						accept(TokenKind.RPAREN);
						endPosn(posn);
						expr = new CallExpr(refDecl, exprList, posn);
					} else {
						endPosn(posn);
						expr = new RefExpr(refDecl, posn);
					}
				} else if (token.kind == TokenKind.LPAREN) { // id(args)
					acceptIt();
					ExprList exprList = new ExprList();
					if (token.kind != TokenKind.RPAREN) {
						exprList = parseArgumentList();
					}
					accept(TokenKind.RPAREN);
					endPosn(posn);
					expr = new CallExpr(refDecl, exprList, posn);
				} else {
					endPosn(posn);
					expr = new RefExpr(refDecl, posn);
				}
			} else if (token.kind == TokenKind.THIS) { // this.id[expr] || this.id (args)
				refDecl = new ThisRef(posn);
				acceptIt();
				if (token.kind == TokenKind.LBRACK) {
					acceptIt();
					ixExpr = parseExpression();
					accept(TokenKind.RBRACK);
					endPosn(posn);
					expr = new IxExpr(refDecl, ixExpr, posn);
				} else if (token.kind == TokenKind.DOT) {
					while (token.kind == TokenKind.DOT) {
						acceptIt();
						if (token.kind == TokenKind.THIS) {
							parseError("Invalid Term - " + token.kind);
						}
						Identifier id2 = new Identifier(token);
						accept(TokenKind.ID);
						endPosn(posn);
						refDecl = new QualRef(refDecl, id2, posn);
					}
					if (token.kind == TokenKind.LBRACK) {
						acceptIt();
						ixExpr = parseExpression();
						accept(TokenKind.RBRACK);
						endPosn(posn);
						expr = new IxExpr(refDecl, ixExpr, posn);
					} else if (token.kind == TokenKind.LPAREN) { // this.id(args)
						acceptIt();
						ExprList exprList = new ExprList();
						if (token.kind != TokenKind.RPAREN) {
							exprList = parseArgumentList();
						}
						accept(TokenKind.RPAREN);
						endPosn(posn);
						expr = new CallExpr(refDecl, exprList, posn);
					} else {
						endPosn(posn);
						expr = new RefExpr(refDecl, posn);
					}
				} else if (token.kind == TokenKind.LPAREN) { // this (args)
					acceptIt();
					ExprList exprList = new ExprList();
					if (token.kind != TokenKind.RPAREN) {
						exprList = parseArgumentList();
					}
					accept(TokenKind.RPAREN);
					endPosn(posn);
					expr = new CallExpr(refDecl, exprList, posn);
				} else {
					endPosn(posn);
					expr = new RefExpr(refDecl, posn);
				}
			}
		} else {
			parseError("Invalid Term - " + token.kind);
		}
		return expr;
	}

	/**
	 * accept current token and advance to next token
	 */
	private void acceptIt() throws SyntaxError {
		accept(token.kind);
	}

	/**
	 * verify that current token in input matches expected token and advance to next
	 * token
	 * 
	 * @param expectedToken
	 * @throws SyntaxError if match fails
	 */
	private void accept(TokenKind expectedTokenKind) throws SyntaxError {
		if (token.kind == expectedTokenKind) {
			if (trace)
				pTrace();
			token = scanner.scan();
		} else
			parseError("expecting '" + expectedTokenKind + "' but found '" + token.kind + "'");
	}

	/**
	 * report parse error and unwind call stack to start of parse
	 * 
	 * @param e string with error detail
	 * @throws SyntaxError
	 */
	private void parseError(String e) throws SyntaxError {
		reporter.reportError("Parse error: " + e);
		throw new SyntaxError();
	}

	// show parse stack whenever terminal is accepted
	private void pTrace() {
		StackTraceElement[] stl = Thread.currentThread().getStackTrace();
		for (int i = stl.length - 1; i > 0; i--) {
			if (stl[i].toString().contains("parse"))
				System.out.println(stl[i]);
		}
		System.out.println("accepting: " + token.kind + " (\"" + token.spelling + "\")");
		System.out.println();
	}

	public void startPosn(SourcePosition posn) {
		posn.start = token.posn.start;
	}

	public void endPosn(SourcePosition posn) {
		posn.end = token.posn.end;
	}
}
