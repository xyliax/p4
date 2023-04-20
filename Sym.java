import java.util.List;
import java.util.HashMap;

public class Sym {
	private String type;
	private SymType symType;
	private HashMap<String, Sym> recordVars;
	private List<String> formalListVars;

	public Sym(String type, SymType symType) {
		this.type = type;
		this.symType = symType;
		this.recordVars = null;
		this.formalListVars = null;
	}

	public Sym(String type) {
		this(type, SymType.NORMAL);
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public SymType getSymType() {
		return symType;
	}

	public HashMap<String, Sym> getRecordVars() {
		return recordVars;
	}

	public void setRecordVars(HashMap<String, Sym> recordVars) {
		this.recordVars = recordVars;
	}

	public List<String> getFormalListVars() {
		return formalListVars;
	}

	public void setFormalListVars(List<String> formalListVars) {
		this.formalListVars = formalListVars;
	}

	public String toString() {
		return type;
	}
}

enum SymType {
	RECORD, FUNCTION, NORMAL;
}