/**
 * miniJava Abstract Syntax Tree classes
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import  miniJava.SyntacticAnalyzer.SourcePosition;

public abstract class Statement extends AST {

	// pa4 add
  public MethodDecl methodDecl;

public Statement(SourcePosition posn) {
    super (posn);
  }

}
