import java.io.*;
import java.net.IDN;
import java.util.*;

// **********************************************************************
// The ASTnode class defines the nodes of the abstract-syntax tree that
// represents a brevis program.
//
// Internal nodes of the tree contain pointers to children, organized
// either in a list (for nodes that may have a variable number of 
// children) or as a fixed set of fields.
//
// The nodes for literals and identifiers contain line and character 
// number information; for string literals and identifiers, they also 
// contain a string; for integer literals, they also contain an integer 
// value.
//
// Here are all the different kinds of AST nodes and what kinds of 
// children they have.  All of these kinds of AST nodes are subclasses
// of "ASTnode".  Indentation indicates further subclassing:
//
//     Subclass              Children
//     --------              --------
//     ProgramNode           DeclListNode
//     DeclListNode          linked list of DeclNode
//     DeclNode:
//       VarDeclNode         TypeNode, IdNode, int
//       FnDeclNode          TypeNode, IdNode, FormalsListNode, FnBodyNode
//       FormalDeclNode      TypeNode, IdNode
//       RecordDeclNode      IdNode, DeclListNode
//
//     StmtListNode          linked list of StmtNode
//     ExpListNode           linked list of ExpNode
//     FormalsListNode       linked list of FormalDeclNode
//     FnBodyNode            DeclListNode, StmtListNode
//
//     TypeNode:
//       BoolNode            --- none ---
//       IntNode             --- none ---
//       VoidNode            --- none ---
//       RecordNode          IdNode
//
//     StmtNode:
//       AssignStmtNode      AssignExpNode
//       PostIncStmtNode     ExpNode
//       PostDecStmtNode     ExpNode
//       IfStmtNode          ExpNode, DeclListNode, StmtListNode
//       IfElseStmtNode      ExpNode, DeclListNode, StmtListNode,
//                                    DeclListNode, StmtListNode
//       WhileStmtNode       ExpNode, DeclListNode, StmtListNode
//       ReadStmtNode        ExpNode
//       WriteStmtNode       ExpNode
//       CallStmtNode        CallExpNode
//       ReturnStmtNode      ExpNode
//
//     ExpNode:
//       TrueNode            --- none ---
//       FalseNode           --- none ---
//       IdNode              --- none ---
//       IntLitNode          --- none ---
//       StrLitNode          --- none ---
//       DotAccessNode       ExpNode, IdNode
//       AssignExpNode       ExpNode, ExpNode
//       CallExpNode         IdNode, ExpListNode
//       UnaryExpNode        ExpNode
//         UnaryMinusNode
//         NotNode
//       BinaryExpNode       ExpNode ExpNode
//         PlusNode     
//         MinusNode
//         TimesNode
//         DivideNode
//         EqualsNode
//         NotEqualsNode
//         LessNode
//         LessEqNode
//         GreaterNode
//         GreaterEqNode
//         AndNode
//         OrNode
//
// Here are the different kinds of AST nodes again, organized according to
// whether they are leaves, internal nodes with linked lists of children, 
// or internal nodes with a fixed number of children:
//
// (1) Leaf nodes:
//        BoolNode,  IntNode,     VoidNode,   TrueNode,  FalseNode,
//        IdNode,    IntLitNode,  StrLitNode
//
// (2) Internal nodes with (possibly empty) linked lists of children:
//        DeclListNode, StmtListNode, ExpListNode, FormalsListNode
//
// (3) Internal nodes with fixed numbers of children:
//        ProgramNode,     VarDeclNode,     FnDeclNode,    FormalDeclNode,
//        RecordDeclNode,  FnBodyNode,      RecordNode,    AssignStmtNode,
//        PostIncStmtNode, PostDecStmtNode, IfStmtNode,    IfElseStmtNode,
//        WhileStmtNode,   ReadStmtNode,    WriteStmtNode, CallStmtNode,
//        ReturnStmtNode,  DotAccessNode,   AssignExpNode, CallExpNode,
//        UnaryExpNode,    UnaryMinusNode,  NotNode,       BinaryExpNode,   
//        PlusNode,        MinusNode,       TimesNode,     DivideNode,
//        EqualsNode,      NotEqualsNode,   LessNode,      LessEqNode,
//        GreaterNode,     GreaterEqNode,   AndNode,       OrNode
//
// **********************************************************************

// **********************************************************************
//   ASTnode class (base class for all other kinds of nodes)
// **********************************************************************

abstract class ASTnode {
    // every subclass must provide an unparse operation
    abstract public void unparse(PrintWriter p, int indent);

    // this method can be used by the unparse methods to do indenting
    protected void doIndent(PrintWriter p, int indent) {
        for (int k = 0; k < indent; k++)
            p.print(" ");
    }

    protected void symTabCheck(IdNode idNode, Sym sym, SymTab symTab) {
        String idName;
        if (idNode.getSym().getSymType() == SymType.RECORD) {
            idName = "record " + idNode.getName();
            sym.setType(idNode.getName());
        } else
            idName = idNode.getName();
        try {
            symTab.addDecl(idName, sym);
        } catch (SymDuplicationException e) {
            idNode.errorReport("Identifier multiply-declared");
        } catch (SymTabEmptyException e) {
            unexpected("Unexpected SymTabEmptyException - " + this);
        }
    }

    /* Debug use, this shall not be called for all time. */
    protected void unexpected(String msg) {
        System.err.println("\033[31m" + msg + "\033[0m");
        System.exit(-1);
    }
}

// **********************************************************************
// ProgramNode, DeclListNode, StmtListNode, ExpListNode,
// FormalsListNode, FnBodyNode
// **********************************************************************

class ProgramNode extends ASTnode {
    public ProgramNode(DeclListNode L) {
        myDeclList = L;
        symTab = new SymTab();
    }

    public void unparse(PrintWriter p, int indent) {
        myDeclList.unparse(p, indent);
    }

    public void nameAnalyze() {
        myDeclList.nameAnalyze(symTab);
    }

    // one child
    private DeclListNode myDeclList;
    // the symbol table
    private SymTab symTab;
}

class DeclListNode extends ASTnode {
    public DeclListNode(List<DeclNode> S) {
        myDecls = S;
    }

    public void unparse(PrintWriter p, int indent) {
        Iterator it = myDecls.iterator();
        try {
            while (it.hasNext()) {
                ((DeclNode) it.next()).unparse(p, indent);
            }
        } catch (NoSuchElementException ex) {
            System.err.println("unexpected NoSuchElementException in DeclListNode.print");
            System.exit(-1);
        }
    }

    public void nameAnalyze(SymTab symTab) {
        for (DeclNode declNode : myDecls) {
            declNode.nameAnalyze(symTab);
        }
    }

    public HashMap<String, Sym> getRecordVars(SymTab symTab) {
        HashMap<String, Sym> recordVars = new HashMap<>();
        symTab.addScope();
        for (DeclNode declNode : myDecls) {
            VarDeclNode varDeclNode = (VarDeclNode) declNode;
            varDeclNode.nameAnalyze(symTab);
            IdNode idNode = varDeclNode.getIdNode();
            String id = idNode.getName();
            String typeName = varDeclNode.getTypeNode().typeToString();
            Sym sym;
            if (idNode.getSym().getSymType() == SymType.RECORD) {
                sym = new Sym(typeName, SymType.RECORD);
                sym.setRecordVars(idNode.getSym().getRecordVars());
            } else {
                sym = new Sym(typeName);
            }
            if (!recordVars.containsKey(id))
                recordVars.put(id, sym);
        }
        try {
            symTab.removeScope();
        } catch (SymTabEmptyException e) {
            unexpected("Unexpected SymTabEmptyException - " + this);
        }
        return recordVars;
    }

    // list of children (DeclNodes)
    private List<DeclNode> myDecls;
}

class StmtListNode extends ASTnode {
    public StmtListNode(List<StmtNode> S) {
        myStmts = S;
    }

    public void unparse(PrintWriter p, int indent) {
        Iterator<StmtNode> it = myStmts.iterator();
        while (it.hasNext()) {
            it.next().unparse(p, indent);
        }
    }

    public void nameAnalyze(SymTab symTab) {
        for (StmtNode stmtNode : myStmts) {
            stmtNode.nameAnalyze(symTab);
        }
    }

    // list of children (StmtNodes)
    private List<StmtNode> myStmts;
}

class ExpListNode extends ASTnode {
    public ExpListNode(List<ExpNode> S) {
        myExps = S;
    }

    public void unparse(PrintWriter p, int indent) {
        Iterator<ExpNode> it = myExps.iterator();
        if (it.hasNext()) { // if there is at least one element
            it.next().unparse(p, indent);
            while (it.hasNext()) { // print the rest of the list
                p.print(", ");
                it.next().unparse(p, indent);
            }
        }
    }

    public void nameAnalyze(SymTab symTab) {
        for (ExpNode expNode : myExps) {
            expNode.nameAnalyze(symTab);
        }
    }

    // list of children (ExpNodes)
    private List<ExpNode> myExps;
}

class FormalsListNode extends ASTnode {
    public FormalsListNode(List<FormalDeclNode> S) {
        myFormals = S;
    }

    public void unparse(PrintWriter p, int indent) {
        Iterator<FormalDeclNode> it = myFormals.iterator();
        if (it.hasNext()) { // if there is at least one element
            it.next().unparse(p, indent);
            while (it.hasNext()) { // print the rest of the list
                p.print(", ");
                it.next().unparse(p, indent);
            }
        }
    }

    public void nameAnalyze(SymTab symTab) {
        symTab.addScope();
        for (FormalDeclNode formalDeclNode : myFormals) {
            formalDeclNode.nameAnalyze(symTab);
        }
    }

    public List<String> getSyms() {
        List<String> symList = new ArrayList<>();
        for (FormalDeclNode formalDeclNode : myFormals) {
            symList.add(formalDeclNode.getType().typeToString());
        }
        return symList;
    }

    // list of children (FormalDeclNodes)
    private List<FormalDeclNode> myFormals;
}

class FnBodyNode extends ASTnode {
    public FnBodyNode(DeclListNode declList, StmtListNode stmtList) {
        myDeclList = declList;
        myStmtList = stmtList;
    }

    public void unparse(PrintWriter p, int indent) {
        myDeclList.unparse(p, indent);
        myStmtList.unparse(p, indent);
    }

    public void nameAnalyze(SymTab symTab) {
        myDeclList.nameAnalyze(symTab);
        myStmtList.nameAnalyze(symTab);
        try {
            symTab.removeScope();
        } catch (SymTabEmptyException e) {
            unexpected("Unexpected SymTabEmptyException - " + this);
        }
    }

    // two children
    private DeclListNode myDeclList;
    private StmtListNode myStmtList;
}

// **********************************************************************
// **** DeclNode and its subclasses
// **********************************************************************

abstract class DeclNode extends ASTnode {
    public abstract void nameAnalyze(SymTab symTab);
}

class VarDeclNode extends DeclNode {
    public VarDeclNode(TypeNode type, IdNode id, int size) {
        myType = type;
        myId = id;
        mySize = size;
    }

    public void unparse(PrintWriter p, int indent) {
        doIndent(p, indent);
        myType.unparse(p, 0);
        p.print(" ");
        myId.unparse(p, 0);
        p.println(";");
    }

    @Override
    public void nameAnalyze(SymTab symTab) {
        Sym varSym = null;
        String type = myType.typeToString();
        if (mySize != NON_RECORD) /* This is a record */ {
            /* check if record_type exists */
            IdNode recordID = ((RecordNode) myType).getRecordID();
            String recordName = "record " + recordID.getName();
            Sym recordSym = null;
            try {
                recordSym = symTab.lookupGlobal(recordName);
            } catch (SymTabEmptyException symTabEmptyException) {
                unexpected("Unexpected SymTabEmptyException - " + this);
            }
            if (recordSym == null) /* invalid record_type */ {
                myId.errorReport("Name of record type invalid");
            } else {
                recordID.setSym(recordSym);
                varSym = new Sym(recordID.getName(), SymType.RECORD);
                varSym.setRecordVars(recordSym.getRecordVars());
                myId.setSym(varSym);
                try {
                    symTab.addDecl(myId.getName(), recordSym);
                } catch (SymDuplicationException e) {
                    myId.errorReport("Identifier multiply-declared");
                } catch (SymTabEmptyException e) {
                    unexpected("Unexpected SymTabEmptyException - " + this);
                }
            }
        } else {
            if ("void".equals(type)) /* void */ {
                myId.errorReport("Non-function declared void");
            } else /* integer or boolean */ {
                varSym = new Sym(type, SymType.NORMAL);
                myId.setSym(varSym);
                symTabCheck(myId, varSym, symTab);
            }
        }
    }

    public IdNode getIdNode() {
        return myId;
    }

    public TypeNode getTypeNode() {
        return myType;
    }

    // three children
    private TypeNode myType;
    private IdNode myId;
    private int mySize; // use value NON_RECORD if this is not a record type

    public static int NON_RECORD = -1;
}

class FnDeclNode extends DeclNode {
    public FnDeclNode(TypeNode type,
            IdNode id,
            FormalsListNode formalList,
            FnBodyNode body) {
        myType = type;
        myId = id;
        myFormalsList = formalList;
        myBody = body;
    }

    public void unparse(PrintWriter p, int indent) {
        doIndent(p, indent);
        myType.unparse(p, 0);
        p.print(" ");
        myId.unparse(p, 0);
        p.print("(");
        myFormalsList.unparse(p, 0);
        p.println(") {");
        myBody.unparse(p, indent + 4);
        p.println("}\n");
    }

    @Override
    public void nameAnalyze(SymTab symTab) {
        String idName = myId.getName();
        Sym newFunc = new Sym(myType.typeToString(), SymType.FUNCTION);
        newFunc.setFormalListVars(myFormalsList.getSyms());
        Sym oldFunc = null;
        try {
            oldFunc = symTab.lookupLocal(idName);
        } catch (SymTabEmptyException symTabEmptyException) {
            unexpected("Unexpected SymTabEmptyException - " + this);
        }
        if (oldFunc != null) {
            if (oldFunc.getSymType() == SymType.FUNCTION) {
                List<String> oldFormalList = oldFunc.getFormalListVars();
                if (oldFormalList.size() == newFunc.getFormalListVars().size()) {
                    boolean same = true;
                    for (int i = 0; i < oldFormalList.size(); i++) {
                        if (!oldFormalList.get(i).equals(newFunc.getFormalListVars().get(i))) {
                            same = false;
                            break;
                        }
                    }
                    if (same) {
                        myId.errorReport("Identifier multiply-declared");
                    }
                } else /* different number of formals */ {
                    try {
                        symTab.addDecl(idName, newFunc);
                    } catch (SymDuplicationException e) {
                    } catch (SymTabEmptyException e) {
                        unexpected("Unexpected SymTabEmptyException - " + this);
                    }
                }
            } else /* not a function */ {
                myId.errorReport("Identifier multiply-declared");
            }
        } else /* new unique function name */ {
            try {
                symTab.addDecl(idName, newFunc);
            } catch (SymDuplicationException e) {
                unexpected("Unexpected SymDuplicationException - " + this);
            } catch (SymTabEmptyException e) {
                unexpected("Unexpected SymTabEmptyException - " + this);
            }
        }
        myId.setSym(newFunc);
        myFormalsList.nameAnalyze(symTab);
        myBody.nameAnalyze(symTab);
    }

    // 4 children
    private TypeNode myType;
    private IdNode myId;
    private FormalsListNode myFormalsList;
    private FnBodyNode myBody;
}

class FormalDeclNode extends DeclNode {
    public FormalDeclNode(TypeNode type, IdNode id) {
        myType = type;
        myId = id;
    }

    public void unparse(PrintWriter p, int indent) {
        myType.unparse(p, 0);
        p.print(" ");
        myId.unparse(p, 0);
    }

    @Override
    public void nameAnalyze(SymTab symTab) {
        if ("void".equals(myType.typeToString())) {
            myId.errorReport("Non-function declared void");
        } else {
            Sym formalSym = new Sym(myType.typeToString());
            myId.setSym(formalSym);
            symTabCheck(myId, formalSym, symTab);
        }
    }

    public TypeNode getType() {
        return myType;
    }

    // two children
    private TypeNode myType;
    private IdNode myId;
}

class RecordDeclNode extends DeclNode {
    public RecordDeclNode(IdNode id, DeclListNode declList) {
        myId = id;
        myDeclList = declList;
    }

    public void unparse(PrintWriter p, int indent) {
        doIndent(p, indent);
        p.print("record ");
        myId.unparse(p, 0);
        p.println("(");
        myDeclList.unparse(p, indent + 4);
        doIndent(p, indent);
        p.println(");\n");
    }

    @Override
    public void nameAnalyze(SymTab symTab) {
        Sym recordSym = new Sym("record", SymType.RECORD);
        recordSym.setRecordVars(myDeclList.getRecordVars(symTab));
        myId.setSym(recordSym);
        symTabCheck(myId, recordSym, symTab);
    }

    // two children
    private IdNode myId;
    private DeclListNode myDeclList;
}

// **********************************************************************
// **** TypeNode and its subclasses
// **********************************************************************

abstract class TypeNode extends ASTnode {
    abstract String typeToString();
}

class BoolNode extends TypeNode {
    public BoolNode() {
    }

    public void unparse(PrintWriter p, int indent) {
        p.print("boolean");
    }

    @Override
    String typeToString() {
        return "boolean";
    }
}

class IntNode extends TypeNode {
    public IntNode() {
    }

    public void unparse(PrintWriter p, int indent) {
        p.print("integer");
    }

    @Override
    String typeToString() {
        return "integer";
    }
}

class VoidNode extends TypeNode {
    public VoidNode() {
    }

    public void unparse(PrintWriter p, int indent) {
        p.print("void");
    }

    @Override
    String typeToString() {
        return "void";
    }
}

class RecordNode extends TypeNode {
    public RecordNode(IdNode id) {
        myId = id;
    }

    public void unparse(PrintWriter p, int indent) {
        p.print("record ");
        myId.unparse(p, 0);
    }

    @Override
    String typeToString() {
        return myId.getName();
    }

    public IdNode getRecordID() {
        return myId;
    }

    // one child
    private IdNode myId;
}

// **********************************************************************
// **** StmtNode and its subclasses
// **********************************************************************

abstract class StmtNode extends ASTnode {
    public abstract void nameAnalyze(SymTab symTab);
}

class AssignStmtNode extends StmtNode {
    public AssignStmtNode(AssignExpNode assign) {
        myAssign = assign;
    }

    public void unparse(PrintWriter p, int indent) {
        doIndent(p, indent);
        myAssign.unparse(p, -1); // no parentheses
        p.println(";");
    }

    @Override
    public void nameAnalyze(SymTab symTab) {
        myAssign.nameAnalyze(symTab);
    }

    // one child
    private AssignExpNode myAssign;

}

class PostIncStmtNode extends StmtNode {
    public PostIncStmtNode(ExpNode exp) {
        myExp = exp;
    }

    public void unparse(PrintWriter p, int indent) {
        doIndent(p, indent);
        myExp.unparse(p, -1);
        p.println("++;");
    }

    @Override
    public void nameAnalyze(SymTab symTab) {
        myExp.nameAnalyze(symTab);
    }

    // one child
    private ExpNode myExp;
}

class PostDecStmtNode extends StmtNode {
    public PostDecStmtNode(ExpNode exp) {
        myExp = exp;
    }

    public void unparse(PrintWriter p, int indent) {
        doIndent(p, indent);
        myExp.unparse(p, -1);
        p.println("--;");
    }

    @Override
    public void nameAnalyze(SymTab symTab) {
        myExp.nameAnalyze(symTab);
    }

    // one child
    private ExpNode myExp;
}

class IfStmtNode extends StmtNode {
    public IfStmtNode(ExpNode exp, DeclListNode dlist, StmtListNode slist) {
        myDeclList = dlist;
        myExp = exp;
        myStmtList = slist;
    }

    public void unparse(PrintWriter p, int indent) {
        doIndent(p, indent);
        p.print("if (");
        myExp.unparse(p, -1);
        p.println(") {");
        myDeclList.unparse(p, indent + 4);
        myStmtList.unparse(p, indent + 4);
        doIndent(p, indent);
        p.println("}");
    }

    @Override
    public void nameAnalyze(SymTab symTab) {
        myExp.nameAnalyze(symTab);
        /* add new scope for if condition */
        symTab.addScope();
        myDeclList.nameAnalyze(symTab);
        myStmtList.nameAnalyze(symTab);
        try {
            symTab.removeScope();
        } catch (SymTabEmptyException e) {
            unexpected("Unexpected SymTabEmptyException - " + this);
        }
    }

    // three children
    private ExpNode myExp;
    private DeclListNode myDeclList;
    private StmtListNode myStmtList;
}

class IfElseStmtNode extends StmtNode {
    public IfElseStmtNode(ExpNode exp, DeclListNode dlist1,
            StmtListNode slist1, DeclListNode dlist2,
            StmtListNode slist2) {
        myExp = exp;
        myThenDeclList = dlist1;
        myThenStmtList = slist1;
        myElseDeclList = dlist2;
        myElseStmtList = slist2;
    }

    public void unparse(PrintWriter p, int indent) {
        doIndent(p, indent);
        p.print("if (");
        myExp.unparse(p, -1);
        p.println(") {");
        myThenDeclList.unparse(p, indent + 4);
        myThenStmtList.unparse(p, indent + 4);
        doIndent(p, indent);
        p.println("}");
        doIndent(p, indent);
        p.println("else {");
        myElseDeclList.unparse(p, indent + 4);
        myElseStmtList.unparse(p, indent + 4);
        doIndent(p, indent);
        p.println("}");
    }

    @Override
    public void nameAnalyze(SymTab symTab) {
        myExp.nameAnalyze(symTab);
        symTab.addScope();
        myThenDeclList.nameAnalyze(symTab);
        myThenStmtList.nameAnalyze(symTab);
        try {
            symTab.removeScope();
        } catch (SymTabEmptyException e) {
            unexpected("Unexpected SymTabEmptyException - " + this);
        }
        symTab.addScope();
        myElseDeclList.nameAnalyze(symTab);
        myElseStmtList.nameAnalyze(symTab);
        try {
            symTab.removeScope();
        } catch (SymTabEmptyException e) {
            unexpected("Unexpected SymTabEmptyException - " + this);
        }
    }

    // five children
    private ExpNode myExp;
    private DeclListNode myThenDeclList;
    private StmtListNode myThenStmtList;
    private StmtListNode myElseStmtList;
    private DeclListNode myElseDeclList;
}

class WhileStmtNode extends StmtNode {
    public WhileStmtNode(ExpNode exp, DeclListNode dlist, StmtListNode slist) {
        myExp = exp;
        myDeclList = dlist;
        myStmtList = slist;
    }

    public void unparse(PrintWriter p, int indent) {
        doIndent(p, indent);
        p.print("while (");
        myExp.unparse(p, -1);
        p.println(") {");
        myDeclList.unparse(p, indent + 4);
        myStmtList.unparse(p, indent + 4);
        doIndent(p, indent);
        p.println("}");
    }

    @Override
    public void nameAnalyze(SymTab symTab) {
        myExp.nameAnalyze(symTab);
        symTab.addScope();
        myDeclList.nameAnalyze(symTab);
        myStmtList.nameAnalyze(symTab);
        try {
            symTab.removeScope();
        } catch (SymTabEmptyException e) {
            unexpected("Unexpected SymTabEmptyException - " + this);
        }
    }

    // three children
    private ExpNode myExp;
    private DeclListNode myDeclList;
    private StmtListNode myStmtList;
}

class ReadStmtNode extends StmtNode {
    public ReadStmtNode(ExpNode e) {
        myExp = e;
    }

    public void unparse(PrintWriter p, int indent) {
        doIndent(p, indent);
        p.print("scan -> ");
        myExp.unparse(p, -1);
        p.println(";");
    }

    @Override
    public void nameAnalyze(SymTab symTab) {
        myExp.nameAnalyze(symTab);
    }

    // one child (actually can only be an IdNode or an ArrayExpNode)
    private ExpNode myExp;
}

class WriteStmtNode extends StmtNode {
    public WriteStmtNode(ExpNode exp) {
        myExp = exp;
    }

    public void unparse(PrintWriter p, int indent) {
        doIndent(p, indent);
        p.print("print <- ");
        myExp.unparse(p, -1);
        p.println(";");
    }

    @Override
    public void nameAnalyze(SymTab symTab) {
        myExp.nameAnalyze(symTab);
    }

    // one child
    private ExpNode myExp;
}

class CallStmtNode extends StmtNode {
    public CallStmtNode(CallExpNode call) {
        myCall = call;
    }

    public void unparse(PrintWriter p, int indent) {
        doIndent(p, indent);
        myCall.unparse(p, indent);
        p.println(";");
    }

    @Override
    public void nameAnalyze(SymTab symTab) {
        myCall.nameAnalyze(symTab);
    }

    // one child
    private CallExpNode myCall;
}

class ReturnStmtNode extends StmtNode {
    public ReturnStmtNode(ExpNode exp) {
        myExp = exp;
    }

    public void unparse(PrintWriter p, int indent) {
        doIndent(p, indent);
        p.print("return");
        if (myExp != null) {
            p.print(" ");
            myExp.unparse(p, -1);
        }
        p.println(";");
    }

    @Override
    public void nameAnalyze(SymTab symTab) {
        myExp.nameAnalyze(symTab);
    }

    // one child
    private ExpNode myExp; // possibly null
}

// **********************************************************************
// **** ExpNode and its subclasses
// **********************************************************************

abstract class ExpNode extends ASTnode {
    public void nameAnalyze(SymTab symTab) {
    }

    public boolean location() {
        return false;
    }
}

class TrueNode extends ExpNode {
    public TrueNode(int lineNum, int charNum) {
        myLineNum = lineNum;
        myCharNum = charNum;
    }

    public void unparse(PrintWriter p, int indent) {
        p.print("true");
    }

    private int myLineNum;
    private int myCharNum;
}

class FalseNode extends ExpNode {
    public FalseNode(int lineNum, int charNum) {
        myLineNum = lineNum;
        myCharNum = charNum;
    }

    public void unparse(PrintWriter p, int indent) {
        p.print("false");
    }

    private int myLineNum;
    private int myCharNum;
}

class IdNode extends ExpNode {
    public IdNode(int lineNum, int charNum, String strVal) {
        myLineNum = lineNum;
        myCharNum = charNum;
        myStrVal = strVal;
    }

    public void unparse(PrintWriter p, int indent) {
        if (indent == -1) /* already declared before */ {
            p.print(myStrVal);
            if (mySym != null)
                p.print("[" + mySym.getType() + "]");
        } else if (indent == -2) /* function */ {
            if (mySym != null)
                p.print(mySym.getType());
        } else /* declare */
            p.print(myStrVal);
    }

    @Override
    public void nameAnalyze(SymTab symTab) {
        try {
            mySym = symTab.lookupLocal(myStrVal);
        } catch (SymTabEmptyException e) {
            unexpected("Unexpected SymTabEmptyException - " + this);
        }
        if (mySym == null) {
            try {
                mySym = symTab.lookupGlobal(myStrVal);
            } catch (SymTabEmptyException e) {
                unexpected("Unexpected SymTabEmptyException - " + this);
            }
            if (mySym == null) {
                this.errorReport("Identifier undeclared");
            }
        }
    }

    public void errorReport(String msg) {
        ErrMsg.fatal(myLineNum, myCharNum, msg);
    }

    public String getName() {
        return myStrVal;
    }

    public Sym getSym() {
        return mySym;
    }

    public void setSym(Sym sym) {
        mySym = sym;
    }

    private int myLineNum;
    private int myCharNum;
    private String myStrVal;
    private Sym mySym; /* link to symbol table entry */
}

class IntLitNode extends ExpNode {
    public IntLitNode(int lineNum, int charNum, int intVal) {
        myLineNum = lineNum;
        myCharNum = charNum;
        myIntVal = intVal;
    }

    public void unparse(PrintWriter p, int indent) {
        p.print(myIntVal);
    }

    private int myLineNum;
    private int myCharNum;
    private int myIntVal;
}

class StringLitNode extends ExpNode {
    public StringLitNode(int lineNum, int charNum, String strVal) {
        myLineNum = lineNum;
        myCharNum = charNum;
        myStrVal = strVal;
    }

    public void unparse(PrintWriter p, int indent) {
        p.print(myStrVal);
    }

    private int myLineNum;
    private int myCharNum;
    private String myStrVal;
}

class DotAccessExpNode extends ExpNode {
    public DotAccessExpNode(ExpNode loc, IdNode id) {
        myLoc = loc;
        myId = id;
    }

    // **** unparse ****
    public void unparse(PrintWriter p, int indent) {
        p.print("(");
        myLoc.unparse(p, -1);
        p.print(").");
        myId.unparse(p, -1);
    }

    @Override
    public void nameAnalyze(SymTab symTab) {
        myId.setSym(locate(symTab, myLoc, myId));
    }

    private Sym locate(SymTab symTab, ExpNode loc, IdNode rhs) {
        if (loc.location()) /* not at leaf location */ {
            DotAccessExpNode lhs = (DotAccessExpNode) loc;
            ExpNode nextLoc = lhs.getLoc();
            IdNode nextId = lhs.getId();
            Sym nextSym = locate(symTab, nextLoc, nextId);
            nextId.setSym(nextSym);
            String idName;
            if (nextSym != null) {
                if (nextSym.getSymType() == SymType.RECORD) {
                    idName = "record " + nextSym.getType();
                    IdNode recordLoc = new IdNode(0, 0, idName);
                    recordLoc.setSym(nextSym);
                    nextSym = locate(symTab, recordLoc, rhs);
                    rhs.setSym(nextSym);
                } else /* non-record should not be dot-accessed */ {
                    nextId.errorReport("Dot-access of non-record type");
                }
            } else /* non-record should not be dot-accessed */ {
                nextId.errorReport("Dot-access of non-record type");
            }
            return nextSym;
        } else {
            IdNode lhs = (IdNode) loc;
            Sym sym = null;
            try {
                sym = symTab.lookupGlobal(lhs.getName());
            } catch (SymTabEmptyException e) {
                unexpected("Unexpected SymTabEmptyException - " + this);
            }
            lhs.setSym(sym);
            if (sym == null) {
                lhs.errorReport("Identifier undeclared");
            } else if (sym.getSymType() != SymType.RECORD) {
                lhs.errorReport("Dot-access of non-record type");
            } else {
                HashMap<String, Sym> recordVars = sym.getRecordVars();
                if (!recordVars.containsKey(rhs.getName())) /* bad field */ {
                    rhs.errorReport("Record field name invalid");
                }
                return recordVars.get(rhs.getName());
            }
            return null;
        }
    }

    public IdNode getId() {
        return myId;
    }

    public ExpNode getLoc() {
        return myLoc;
    }

    @Override
    public boolean location() {
        return true;
    }

    // two children
    private ExpNode myLoc;
    private IdNode myId;
}

class AssignExpNode extends ExpNode {
    public AssignExpNode(ExpNode lhs, ExpNode exp) {
        myLhs = lhs;
        myExp = exp;
    }

    public void unparse(PrintWriter p, int indent) {
        if (indent != -1)
            p.print("(");
        myLhs.unparse(p, -1);
        p.print(" = ");
        myExp.unparse(p, -1);
        if (indent != -1)
            p.print(")");
    }

    @Override
    public void nameAnalyze(SymTab symTab) {
        myLhs.nameAnalyze(symTab);
        myExp.nameAnalyze(symTab);
    }

    // two children
    private ExpNode myLhs;
    private ExpNode myExp;
}

class CallExpNode extends ExpNode {
    public CallExpNode(IdNode name, ExpListNode elist) {
        myId = name;
        myExpList = elist;
    }

    public CallExpNode(IdNode name) {
        myId = name;
        myExpList = new ExpListNode(new LinkedList<ExpNode>());
    }

    public void unparse(PrintWriter p, int indent) {
        myId.unparse(p, 0);
        p.print("[");
        myExpList.unparse(p, -2);
        p.print("->");
        p.print(funcType.getType());
        p.print("]");
        p.print("(");
        if (myExpList != null) {
            myExpList.unparse(p, -1);
        }
        p.print(")");
    }

    @Override
    public void nameAnalyze(SymTab symTab) {
        try {
            funcType = symTab.lookupGlobal(myId.getName());
        } catch (SymTabEmptyException e) {
            unexpected("Unexpected SymTabEmptyException - " + this);
        }
        if (funcType == null) {
            myId.errorReport("Identifier undeclared");
        }
        myExpList.nameAnalyze(symTab);
    }

    // two children
    private IdNode myId;
    private ExpListNode myExpList; // possibly null
    private Sym funcType;
}

abstract class UnaryExpNode extends ExpNode {
    public UnaryExpNode(ExpNode exp) {
        myExp = exp;
    }

    @Override
    public void nameAnalyze(SymTab symTab) {
        myExp.nameAnalyze(symTab);
    }

    // one child
    protected ExpNode myExp;
}

abstract class BinaryExpNode extends ExpNode {
    public BinaryExpNode(ExpNode exp1, ExpNode exp2) {
        myExp1 = exp1;
        myExp2 = exp2;
    }

    @Override
    public void nameAnalyze(SymTab symTab) {
        myExp1.nameAnalyze(symTab);
        myExp2.nameAnalyze(symTab);
    }

    // two children
    protected ExpNode myExp1;
    protected ExpNode myExp2;
}

// **********************************************************************
// ***** Subclasses of UnaryExpNode
// **********************************************************************

class UnaryMinusNode extends UnaryExpNode {
    public UnaryMinusNode(ExpNode exp) {
        super(exp);
    }

    public void unparse(PrintWriter p, int indent) {
        p.print("(-");
        myExp.unparse(p, -1);
        p.print(")");
    }
}

class NotNode extends UnaryExpNode {
    public NotNode(ExpNode exp) {
        super(exp);
    }

    public void unparse(PrintWriter p, int indent) {
        p.print("(\\");
        myExp.unparse(p, -1);
        p.print(")");
    }
}

// **********************************************************************
// **** Subclasses of BinaryExpNode
// **********************************************************************

class PlusNode extends BinaryExpNode {
    public PlusNode(ExpNode exp1, ExpNode exp2) {
        super(exp1, exp2);
    }

    public void unparse(PrintWriter p, int indent) {
        p.print("(");
        myExp1.unparse(p, -1);
        p.print(" + ");
        myExp2.unparse(p, -1);
        p.print(")");
    }
}

class MinusNode extends BinaryExpNode {
    public MinusNode(ExpNode exp1, ExpNode exp2) {
        super(exp1, exp2);
    }

    public void unparse(PrintWriter p, int indent) {
        p.print("(");
        myExp1.unparse(p, -1);
        p.print(" - ");
        myExp2.unparse(p, -1);
        p.print(")");
    }
}

class TimesNode extends BinaryExpNode {
    public TimesNode(ExpNode exp1, ExpNode exp2) {
        super(exp1, exp2);
    }

    public void unparse(PrintWriter p, int indent) {
        p.print("(");
        myExp1.unparse(p, -1);
        p.print(" * ");
        myExp2.unparse(p, -1);
        p.print(")");
    }
}

class DivideNode extends BinaryExpNode {
    public DivideNode(ExpNode exp1, ExpNode exp2) {
        super(exp1, exp2);
    }

    public void unparse(PrintWriter p, int indent) {
        p.print("(");
        myExp1.unparse(p, -1);
        p.print(" / ");
        myExp2.unparse(p, -1);
        p.print(")");
    }
}

class EqualsNode extends BinaryExpNode {
    public EqualsNode(ExpNode exp1, ExpNode exp2) {
        super(exp1, exp2);
    }

    public void unparse(PrintWriter p, int indent) {
        p.print("(");
        myExp1.unparse(p, -1);
        p.print(" == ");
        myExp2.unparse(p, -1);
        p.print(")");
    }
}

class NotEqualsNode extends BinaryExpNode {
    public NotEqualsNode(ExpNode exp1, ExpNode exp2) {
        super(exp1, exp2);
    }

    public void unparse(PrintWriter p, int indent) {
        p.print("(");
        myExp1.unparse(p, -1);
        p.print(" \\= ");
        myExp2.unparse(p, -1);
        p.print(")");
    }
}

class LessNode extends BinaryExpNode {
    public LessNode(ExpNode exp1, ExpNode exp2) {
        super(exp1, exp2);
    }

    public void unparse(PrintWriter p, int indent) {
        p.print("(");
        myExp1.unparse(p, -1);
        p.print(" < ");
        myExp2.unparse(p, -1);
        p.print(")");
    }
}

class LessEqNode extends BinaryExpNode {
    public LessEqNode(ExpNode exp1, ExpNode exp2) {
        super(exp1, exp2);
    }

    public void unparse(PrintWriter p, int indent) {
        p.print("(");
        myExp1.unparse(p, -1);
        p.print(" <= ");
        myExp2.unparse(p, -1);
        p.print(")");
    }
}

class GreaterNode extends BinaryExpNode {
    public GreaterNode(ExpNode exp1, ExpNode exp2) {
        super(exp1, exp2);
    }

    public void unparse(PrintWriter p, int indent) {
        p.print("(");
        myExp1.unparse(p, -1);
        p.print(" > ");
        myExp2.unparse(p, -1);
        p.print(")");
    }
}

class GreaterEqNode extends BinaryExpNode {
    public GreaterEqNode(ExpNode exp1, ExpNode exp2) {
        super(exp1, exp2);
    }

    public void unparse(PrintWriter p, int indent) {
        p.print("(");
        myExp1.unparse(p, -1);
        p.print(" >= ");
        myExp2.unparse(p, -1);
        p.print(")");
    }
}

class AndNode extends BinaryExpNode {
    public AndNode(ExpNode exp1, ExpNode exp2) {
        super(exp1, exp2);
    }

    public void unparse(PrintWriter p, int indent) {
        p.print("(");
        myExp1.unparse(p, -1);
        p.print(" && ");
        myExp2.unparse(p, -1);
        p.print(")");
    }
}

class OrNode extends BinaryExpNode {
    public OrNode(ExpNode exp1, ExpNode exp2) {
        super(exp1, exp2);
    }

    public void unparse(PrintWriter p, int indent) {
        p.print("(");
        myExp1.unparse(p, -1);
        p.print(" || ");
        myExp2.unparse(p, -1);
        p.print(")");
    }
}
