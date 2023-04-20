import java.util.*;

public class SymTab {
	private List<HashMap<String, Sym>> list;
	
	public SymTab() {
		list = new LinkedList<HashMap<String, Sym>>();
		list.add(new HashMap<String, Sym>());
	}
	
	public void addDecl(String name, Sym sym) 
	throws SymDuplicationException, SymTabEmptyException {
		if (name == null || sym == null)
			throw new IllegalArgumentException();
		
		if (list.isEmpty())
			throw new SymTabEmptyException();
		
		HashMap<String, Sym> symTab = list.get(0);
		if (symTab.containsKey(name))
			throw new SymDuplicationException();
		
		symTab.put(name, sym);
	}
	
	public void addScope() {
		list.add(0, new HashMap<String, Sym>());
	}
	
	public Sym lookupLocal(String name) 
	throws SymTabEmptyException {
		if (list.isEmpty())
			throw new SymTabEmptyException();
		
		HashMap<String, Sym> symTab = list.get(0); 
		return symTab.get(name);
	}
	
	public Sym lookupGlobal(String name) 
	throws SymTabEmptyException {
		if (list.isEmpty())
			throw new SymTabEmptyException();
		
		for (HashMap<String, Sym> symTab : list) {
			Sym sym = symTab.get(name);
			if (sym != null)
				return sym;
		}
		return null;
	}
	
	public void removeScope() throws SymTabEmptyException {
		if (list.isEmpty())
			throw new SymTabEmptyException();
		list.remove(0);
	}
	
	public void print() {
		System.out.print("\n--- Symbol Table ---\n");
		for (HashMap<String, Sym> symTab : list) {
			System.out.println(symTab.toString());
		}
		System.out.println();
	}
}
