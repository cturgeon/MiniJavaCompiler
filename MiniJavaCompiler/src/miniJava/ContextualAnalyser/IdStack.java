package miniJava.ContextualAnalyser;

import java.util.HashMap;
import java.util.Stack;

import miniJava.AbstractSyntaxTrees.*;
import miniJava.CodeGenerator.RuntimeEntity;

public class IdStack {

	public Stack<HashMap<String, Declaration>> allIdStack;
	public HashMap<String, Declaration> curTable;

	public IdStack() {
		curTable = null;
		allIdStack = new Stack<>();
		stdEnv();
	}

	public IdStack(HashMap<String, Declaration> idTable) {
		curTable = idTable;
		allIdStack = new Stack<>();
		allIdStack.push(curTable);
	}

	public Object enter(String id, Declaration decl) {
		if (curTable.containsKey(id)) {
			return null;
		} else {
			curTable.put(id, decl);
		}
		return decl;
	}
	
	public void print() {
		System.out.println(allIdStack.toString());
	}

	public Declaration retrieve(String id) {
		for (int i = (allIdStack.size() - 1); i >= 0; i--) {
			HashMap<String, Declaration> hashMapIdTable = allIdStack.get(i);
			if (hashMapIdTable.containsKey(id)) {
				return hashMapIdTable.get(id);
			}
		}
		return null;
	}

	public Declaration retrieveClass(String id) {
		for (int i = (allIdStack.size() - 1); i >= 0; i--) {
			HashMap<String, Declaration> hashMapIdTable = allIdStack.get(i);
			if (hashMapIdTable.containsKey(id) && hashMapIdTable.get(id) instanceof ClassDecl) {
				return hashMapIdTable.get(id);
			}
		}
		return null;
	}

	public Declaration retrieveField(String id) {
		for (int i = (allIdStack.size() - 1); i >= 0; i--) {
			HashMap<String, Declaration> hashMapIdTable = allIdStack.get(i);
			if (hashMapIdTable.containsKey(id) && hashMapIdTable.get(id) instanceof FieldDecl) {
				return hashMapIdTable.get(id);
			}
		}
		return null;
	}

	public Declaration retrieveMethod(String id) {
		for (int i = (allIdStack.size() - 1); i >= 0; i--) {
			HashMap<String, Declaration> hashMapIdTable = allIdStack.get(i);
			if (hashMapIdTable.containsKey(id) && hashMapIdTable.get(id) instanceof MethodDecl) {
				return hashMapIdTable.get(id);
			}
		}
		return null;
	}

	public Declaration retrieveMember(String id) {
		for (int i = (allIdStack.size() - 1); i >= 0; i--) {
			HashMap<String, Declaration> hashMapIdTable = allIdStack.get(i);
			if (hashMapIdTable.containsKey(id) && hashMapIdTable.get(id) instanceof MemberDecl) {
				return hashMapIdTable.get(id);
			}
		}
		return null;
	}

	public int getCurrentLevel() {
		return (allIdStack.size() - 1);
	}

	public HashMap<String, Declaration> getCurrentIdTable() {
		return this.curTable;
	}

	public boolean isInCurrLevel(String id) {
		if (curTable.containsKey(id)) {
			return true;
		}
		return false;
	}

	public void openScope() {
		curTable = new HashMap<>();
		allIdStack.push(curTable);
	}

	public void closeScope() {
		allIdStack.pop();
		curTable = allIdStack.peek();
	}

//	class System { public static _PrintStream out; }
//	class _PrintStream { public void println(int n){} }
//	class String { }
	private void stdEnv() {
		openScope();

		ClassDecl stringClass = new ClassDecl("String", null, null, null);
		stringClass.type = new BaseType(TypeKind.CLASS, null);
		enter(stringClass.name, stringClass);

		FieldDecl fieldDecl = new FieldDecl(true, false, null, "_PrintStream", null);
		FieldDeclList fieldListSystem = new FieldDeclList();
		fieldListSystem.add(fieldDecl);
		ClassDecl systemClass = new ClassDecl("System", fieldListSystem, null, null);
		VarDecl out = new VarDecl(new BaseType(TypeKind.CLASS, null), "out", null);
		systemClass.type = new BaseType(TypeKind.CLASS, null);
		out.type = new BaseType(TypeKind.CLASS, null);
		enter(out.name, out);
		enter(systemClass.name, systemClass);

		MemberDecl memberPrint = new FieldDecl(true, true, null, "println", null);
		ParameterDecl paraPrint = new ParameterDecl(new BaseType(TypeKind.INT, null), "n", null);
		ParameterDeclList paraList = new ParameterDeclList();
		paraList.add(paraPrint);
		MethodDecl printMethods = new MethodDecl(memberPrint, paraList, null, null);
		MethodDeclList methodListPrint = new MethodDeclList();
		methodListPrint.add(printMethods);
		ClassDecl printClass = new ClassDecl("_PrintStream", null, methodListPrint, null);
		printClass.type = new BaseType(TypeKind.CLASS, null);
		
		enter(printClass.methodDeclList.get(0).name, printClass.methodDeclList.get(0));
		enter(printClass.name, printClass);
		VarDecl intN = new VarDecl(new BaseType(TypeKind.INT, null), "n", null);
		enter(intN.name, intN);
	}

}
