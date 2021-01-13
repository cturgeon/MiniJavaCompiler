package miniJava.CodeGenerator;

import miniJava.AbstractSyntaxTrees.MethodDecl;

public class Patch {
	int codeAddy;
	MethodDecl md;

	public Patch(int codeAddr, MethodDecl md) {
		this.codeAddy = codeAddr;
		this.md = md;
	}
}
