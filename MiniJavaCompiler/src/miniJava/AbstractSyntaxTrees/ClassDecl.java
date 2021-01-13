/**
 * miniJava Abstract Syntax Tree classes
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import  miniJava.SyntacticAnalyzer.SourcePosition;
import miniJava.SyntacticAnalyzer.Token;
import miniJava.SyntacticAnalyzer.TokenKind;

public class ClassDecl extends Declaration {

  public ClassDecl(String cn, FieldDeclList fdl, MethodDeclList mdl, SourcePosition posn) {
	  super(cn, null, posn);
	  fieldDeclList = fdl;
	  methodDeclList = mdl;
	  className = cn;
  }
  
  public <A,R> R visit(Visitor<A, R> v, A o) {
      return v.visitClassDecl(this, o);
  }
      
  public FieldDeclList fieldDeclList;
  public MethodDeclList methodDeclList;
  
  // PA3 addition
  public String className; 
  public Identifier id = new Identifier(new Token(TokenKind.ID, className, posn));

  public boolean existsMember(String name, boolean isStatic, boolean isPublic) {
      for (FieldDecl fieldDecl : fieldDeclList) {
          if (name.equals(fieldDecl.name)) {
              if (isStatic && !fieldDecl.isStatic) continue;
              if (isPublic && fieldDecl.isPrivate) continue;
              return true;
          }
      }

      for (MethodDecl methodDecl : methodDeclList) {
          if (name.equals(methodDecl.name)) {
              if (isStatic && !methodDecl.isStatic) continue;
              if (isPublic && methodDecl.isPrivate) continue;
              return true;
          }
      }
      return false;
  }
}
