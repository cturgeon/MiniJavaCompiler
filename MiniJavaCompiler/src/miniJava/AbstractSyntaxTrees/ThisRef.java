/**
 * miniJava Abstract Syntax Tree classes
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.SourcePosition;

public class ThisRef extends BaseRef {

	public ThisRef(SourcePosition posn) {
		super(posn);
	}

	public Declaration decl;
	public Identifier id;

	@Override
	public <A, R> R visit(Visitor<A, R> v, A o) {
		return v.visitThisRef(this, o);
	}

	@Override
	public TypeDenoter getType() {
		return decl.type;
	}

	@Override
	public Declaration getDecl() {
		return decl;
	}

	@Override
	public Identifier getId() {
		return id;
	}

}
