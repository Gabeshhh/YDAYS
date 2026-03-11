package main.java.depenses;

import java.util.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

public class DepenseService {
    // Attributs 
    private static final String FICHIER_DEPENSES   = "depenses.json";
    private static final String FICHIER_BUDGETS    = "budgets.json";
    private static final String FICHIER_PROGRAMMEES = "programmees.json";

     private List<Depense> depenses    = new ArrayList<>();
    private Map<String, Double> budgets     = new HashMap<>();
    private List<TransactionProgrammee> programmees = new ArrayList<>(); // Après
    private int nextId = 1;
    private int nextProgId = 1;

    // Méthode
    public DepenseService(){
        charger(); // Après
    }


    // CRUD - Transactions
    // Ajouter une dépense : 
    public Depense ajouter(String date, String categorie, double montant, String description, String type) {
        Depense dep = new Depense(nextId++, date, categorie, montant, description, type);
        depenses.add(dep);
        return dep;
    }

    // Supprimer une dépense avec son ID 
    public boolean supprimer(int id){
        boolean ok = depenses.removeIf(dep -> dep.getId() == id);
        if (ok) sauvegarderDepenses(); // Après
        return ok;
    }

    // Modifier une transaction 
    public boolean modifier(int id, String date, String categorie, double montant, String description, String type){
        for (int i = 0; i < depenses.size(); i++){
            if (depenses.get(i).getId() == id){
                depenses.set(i, new Depense(id, date, categorie, montant, description, type));
                sauvegarderDepenses(); // Après
                return true;
            }
        }
        return false;
    }

    
}
