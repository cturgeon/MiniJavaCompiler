/**
 *  Scan the a single line of input
 *
 *  Grammar:
 *   num ::= digit digit*
 *   digit ::= '0' | ... | '9'
 *   oper ::= '+' | '*'
 *   
 *   whitespace is the space character
 */
package miniJava.SyntacticAnalyzer;

import java.io.*;

import miniJava.SyntacticAnalyzer.TokenKind;
import miniJava.ErrorReporter;

public class Scanner {

	private InputStream inputStream;
	private ErrorReporter reporter;

	private char currentChar;
	private StringBuilder currentSpelling;
	private int curLine;

	// true when end of line is found
	private boolean eot = false;

	public Scanner(InputStream inputStream, ErrorReporter reporter) {
		this.inputStream = inputStream;
		this.reporter = reporter;
		this.curLine = 1;

		// initialize scanner state
		readChar();
	}

	/**
	 * skip whitespace and scan next token
	 */
	public Token scan() {
		SourcePosition posn = new SourcePosition();
		posn.start = curLine;
		posn.end = curLine;

		// skip whitespace
		while (!eot && (currentChar == ' ' || findNewLine())) {
			skipIt();
		}

		// start of a token: collect spelling and identify token kind
		currentSpelling = new StringBuilder();
		TokenKind kind = scanToken();
		String spelling = currentSpelling.toString();

		// return new token
		return new Token(kind, spelling, posn);
	}

	public void findComments() {
		if (currentChar == '/') {
			skipIt();
			while (true && !eot) {

				if (eot) {
					scanError("fail");
				}

				if (currentChar == '\n' || currentChar == '\r') {
					skipIt();
					return;
				}

				skipIt();
			}
		} else if (currentChar == '*') {
			skipIt();
			while (true && !eot) {

				if (currentChar == '*') {
					skipIt();
					if (currentChar == '/') {
						skipIt();
						return;
					}
				}
				if (currentChar != '*') {
					skipIt();
				}
			}
			if (eot) {
				scanError("fail");
			}

		}
	}

	public boolean findNewLine() {
		return (currentChar == '\t' || currentChar == '\n' || currentChar == '\r');
	}

	/**
	 * determine token kind
	 */
	public TokenKind scanToken() {
		while (currentChar == '/') {
			if (currentChar == '/') {
				skipIt();
				if (currentChar == '/' || currentChar == '*') {
					findComments();
				} else {
					currentSpelling.append('/');
					return TokenKind.MULTI;
				}
			}
			while (!eot && (currentChar == ' ' || findNewLine())) {
				skipIt();
			}
		}

		while (!eot && (currentChar == ' ' || findNewLine())) {
			skipIt();
		}

		while (currentChar == '\\') {
			skipIt();
			if (currentChar == 't' || currentChar == 'n' || currentChar == 'r') {
				skipIt();
			}
			while (!eot && (currentChar == ' ' || findNewLine())) {
				skipIt();
			}
		}

		if (eot)
			return (TokenKind.EOT);
		if (currentChar == '-') {
			takeIt();
			return TokenKind.NEG; // -
		} else if (currentChar == '*') {
			takeIt();
			return TokenKind.MULTI; // *
		} else if (currentChar == '+') {
			takeIt();
			return TokenKind.ADDI; // +
		} else if (currentChar == '=') {
			takeIt();
			if (currentChar == '=') {
				takeIt();
				return TokenKind.EQUALITY; // ==
			} else {
				return TokenKind.ASSIGN; // = or assign
			}
		} else if (currentChar == '!') {
			takeIt();
			if (currentChar != '=') {
				return TokenKind.NOT; // !
			} else if (currentChar == '=') {
				takeIt();
				return TokenKind.EQUALITY; // !=
			}
		} else if (currentChar == '>' || currentChar == '<') {
			takeIt();
			if (currentChar == '=') {
				takeIt();
			}
			return TokenKind.RELATIONAL; // < | > | <= | >=
		} else if (currentChar == '&') {
			takeIt();
			if (currentChar == '&') {
				takeIt();
				return TokenKind.CONJ; // &&
			}
			return TokenKind.ERROR;
		} else if (currentChar == '|') {
			takeIt();
			if (currentChar == '|') {
				takeIt();
				return TokenKind.DISJ; // ||
			}
			return TokenKind.ERROR;
		} else if (currentChar == '{') {
			takeIt();
			return TokenKind.LCURL;
		} else if (currentChar == '}') {
			takeIt();
			return TokenKind.RCURL;
		} else if (currentChar == '(') {
			takeIt();
			return TokenKind.LPAREN;
		} else if (currentChar == ')') {
			takeIt();
			return TokenKind.RPAREN;
		} else if (currentChar == '[') {
			takeIt();
			return TokenKind.LBRACK;
		} else if (currentChar == ']') {
			takeIt();
			return TokenKind.RBRACK;
		} else if (currentChar == ',') {
			takeIt();
			return TokenKind.COMMA;
		} else if (currentChar == '.') {
			takeIt();
			return TokenKind.DOT;
		} else if (currentChar == ';') {
			takeIt();
			return TokenKind.SEMICOLON;
		} else if (isDigit(currentChar)) {
			takeIt();
			while (isDigit(currentChar)) {
				takeIt();
			}
			return TokenKind.NUM;
		}

		if (isAlphaLower(currentChar) || isAlphaUpper(currentChar)) {
			takeIt();
		} else {
			return TokenKind.ERROR;
		}

		while (isAlphaLower(currentChar) || isAlphaUpper(currentChar) || isDigit(currentChar) || currentChar == '_') {
			takeIt();
		}
		String spelling = currentSpelling.toString();
		if (spelling.equals("class")) {
			return TokenKind.CLASS;
		} else if (spelling.equals("void")) {
			return TokenKind.VOID;
		} else if (spelling.equals("public")) {
			return TokenKind.PUBLIC;
		} else if (spelling.equals("private")) {
			return TokenKind.PRIVATE;
		} else if (spelling.equals("static")) {
			return TokenKind.STATIC;
		} else if (spelling.equals("int")) {
			return TokenKind.INT;
		} else if (spelling.equals("boolean")) {
			return TokenKind.BOOL;
		} else if (spelling.equals("this")) {
			return TokenKind.THIS;
		} else if (spelling.equals("return")) {
			return TokenKind.RETURN;
		} else if (spelling.equals("if")) {
			return TokenKind.IF;
		} else if (spelling.equals("else")) {
			return TokenKind.ELSE;
		} else if (spelling.equals("true")) {
			return TokenKind.TRUE;
		} else if (spelling.equals("false")) {
			return TokenKind.FALSE;
		} else if (spelling.equals("new")) {
			return TokenKind.NEW;
		} else if (spelling.equals("while")) {
			return TokenKind.WHILE;
		} else if (spelling.equals("null")) {
			return TokenKind.NULL;
		} else if (!spelling.isEmpty()) {
			return TokenKind.ID;
		} else if (!eot) {
			scanError("Unrecognized character '" + currentChar + "' in input");
			return (TokenKind.ERROR);
		}
		return TokenKind.ERROR;

	}

	private void takeIt() {
		currentSpelling.append(currentChar);
		nextChar();
	}

	private void skipIt() {
		nextChar();
	}

	private boolean isDigit(char c) {
		return (c >= '0') && (c <= '9');
	}

	private boolean isAlphaLower(char c) {
		return ((c >= 'a') && (c <= 'z'));
	}

	private boolean isAlphaUpper(char c) {
		return (c >= 'A') && (c <= 'Z');
	}

	private void scanError(String m) {
		reporter.reportError("Scan Error:  " + m);
	}

	/**
	 * advance to next char in inputstream detect end of file or end of line as end
	 * of input
	 */
	private void nextChar() {
		if (!eot)
			readChar();
	}

	private void readChar() {
		try {
            int c = inputStream.read();
            currentChar = (char) c;
            if ((char)c == '\n') curLine++;
            if (c == -1) {
                eot = true;
            }
        } catch (IOException e) {
            scanError("I/O Exception!");
            eot = true;
        }
	}
}
