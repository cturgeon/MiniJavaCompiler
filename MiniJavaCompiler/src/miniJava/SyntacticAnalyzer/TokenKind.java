package miniJava.SyntacticAnalyzer;

public enum TokenKind {
	NUM, EOT, CLASS, VOID, PUBLIC, 
	PRIVATE, STATIC, INT,
	THIS, RETURN, IF, ELSE, TRUE,
	FALSE, NEW, ID, BOOL, WHILE,
	COMMENT, NEG,
	
	DISJ, CONJ, EQUALITY, RELATIONAL, ADDI, MULTI, NOT,
	
	

	SEMICOLON, LCURL, RCURL, LBRACK, RBRACK, COMMA, DOT, 
	ASSIGN, LPAREN, RPAREN, ERROR,
	
	NULL
}