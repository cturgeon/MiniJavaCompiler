package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.SourcePosition;

public class ArrayLengthRef extends Reference{
	
	public Reference array;
	public int length;

	public ArrayLengthRef(Reference array, SourcePosition posn) {
		super(posn);
		this.array = array;
		this.length = 0;

	}

	@Override
	public Identifier getId() {
		return array.getId();
	}

	@Override
	public TypeDenoter getType() {
		return array.getType();
	}

	@Override
	public Declaration getDecl() {
		return array.getDecl();
	}

	@Override
	public <A, R> R visit(Visitor<A, R> v, A o) {
		return v.visitArrayLengthRef(this, o);
	}

}
