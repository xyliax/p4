###
# This Makefile can be used to make a parser for the brevis language
# (parser.class) and to make a program (P4.class) that tests the 
# parser and the unparse and name-analysis methods in ast.java.
#
# make clean removes all generated files
#
###

JC = javac
FLAGS = -g  
CP = ./deps:.

P4.class: P4.java parser.class Yylex.class ASTnode.class
	$(JC) $(FLAGS) -cp $(CP) P4.java

parser.class: parser.java ASTnode.class Yylex.class ErrMsg.class
	$(JC) $(FLAGS) -cp $(CP) parser.java

parser.java: brevis.cup
	java -cp $(CP) java_cup.Main < brevis.cup

Yylex.class: brevis.jlex.java sym.class ErrMsg.class
	$(JC) $(FLAGS) -cp $(CP) brevis.jlex.java

ASTnode.class: ast.java SymTab.class
	$(JC) $(FLAGS) -cp $(CP) ast.java

brevis.jlex.java: brevis.jlex sym.class
	java -cp $(CP) JLex.Main brevis.jlex

sym.class: sym.java
	$(JC) $(FLAGS) -cp $(CP) sym.java

sym.java: brevis.cup
	java -cp $(CP) java_cup.Main < brevis.cup

ErrMsg.class: ErrMsg.java
	$(JC) $(FLAGS) -cp $(CP) ErrMsg.java

Sym.class: Sym.java
	$(JC) $(FLAGS) -cp $(CP) Sym.java

SymTab.class: SymTab.java Sym.class SymDuplicationException.class SymTabEmptyException.class
	$(JC) $(FLAGS) -cp $(CP) SymTab.java

SymDuplicationException.class: SymDuplicationException.java
	$(JC) $(FLAGS) -cp $(CP) SymDuplicationException.java

SymTabEmptyException.class: SymTabEmptyException.java
	$(JC) $(FLAGS) -cp $(CP) SymTabEmptyException.java

##test
test:
	java -cp $(CP) P4 nameErrors.brevis nameErrors.out
	java -cp $(CP) P4 test.brevis test.out

###
# clean
###
clean:
	rm -f *~ *.class parser.java brevis.jlex.java sym.java

## cleantest (delete test artifacts)
cleantest:
	rm -f *.out
