package miniJava.SyntacticAnalyzer;

public class SourcePosition {
	SourcePosition posn;
	
	public int start, end;
	
	public SourcePosition() {
		start = 0;
		end = 0;
	}
	
	public SourcePosition(int s, int f) {
		start = s;
		end = f;
	}

}
