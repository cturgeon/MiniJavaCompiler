package miniJava;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import miniJava.ErrorReporter;
import miniJava.SyntacticAnalyzer.Parser;
import miniJava.SyntacticAnalyzer.Scanner;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.CodeGenerator.CodeGen;
import miniJava.ContextualAnalyser.IdChecks;
import miniJava.ContextualAnalyser.TypeChecks;

public class Compiler {

	public static void main(String[] args) {

		/*
		 * note that a compiler should read the file specified by args[0] instead of
		 * reading from the keyboard!
		 */

		InputStream inputStream = null;
		try {
			inputStream = new FileInputStream(args[0]);
		} catch (FileNotFoundException e) {
			System.out.println("Input file " + args[0] + " not found");
			System.exit(3);
		}

		ErrorReporter reporter = new ErrorReporter();
		Scanner scanner = new Scanner(inputStream, reporter);
		Parser parser = new Parser(scanner, reporter);
		Package ast = parser.parse();
		try {
			new ASTDisplay().showTree(ast);
		} catch (Exception e) {
			
		}

		if (reporter.hasErrors()) {
			System.out.println("INVALID miniJava program");
			System.exit(4);
		} else {
			System.out.println("valid AST");
		}
		
		IdChecks idChecker = new IdChecks(ast, reporter);
		idChecker.check();
		
		if (reporter.hasErrors()) {
			System.out.println("INVALID miniJava program");
			System.exit(4);
		} else {
			System.out.println("valid id check");
		}
		
		TypeChecks typeChecker = new TypeChecks(ast, reporter);
		typeChecker.check();
		
		if (reporter.hasErrors()) {
			System.out.println("INVALID miniJava program");
			System.exit(4);
		} else {
			System.out.println("valid type check");
		}
		
		CodeGen codeGen = new CodeGen(ast, reporter);
		codeGen.generateCode(args[0]);
		
		if (reporter.hasErrors()) {
			System.out.println("INVALID miniJava program");
			System.exit(4);
		} else {
			System.out.println("valid code generation");
			System.exit(0);
		}

		
	}
}