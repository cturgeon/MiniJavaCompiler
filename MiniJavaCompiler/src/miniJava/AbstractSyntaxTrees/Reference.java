/**
 * miniJava Abstract Syntax Tree classes
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.SourcePosition;

public abstract class Reference extends AST {
	public Reference(SourcePosition posn) {
		super(posn);
	}

	// pa3 add
	public abstract Identifier getId();

	public abstract TypeDenoter getType();

	public abstract Declaration getDecl();

	public boolean isStatic = false;
}
