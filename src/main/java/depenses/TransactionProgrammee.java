package main.java.depenses;

import java.util.Locale;

public class TransactionProgrammee {
    // Attributs 
    private int id;
    private String date;
    private String categorie;
    private double montant;
    private String description;
    private String type;
    private String reccurence;

    // Constructeur
    public TransactionProgrammee(int id, String date, String categorie, double montant, String description, String type, String recurrence) {
        this.id = id;
        this.date = date;
        this.categorie = categorie;
        this.montant = montant;
        this.description = description;
        this.type = (type != null && !type.isEmpty()) ? type : "depenses";
        this.reccurence = (recurrence != null && !recurrence.isEmpty()) ? recurrence : "aucune";
    }

    // Getters
    public int getId() { return id; }
    public String getDate() { return date; }
    public String getCategorie() { return categorie; }
    public double getMontant() { return montant; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public String getRecurrence() { return reccurence; }

    // JSON 
    public String toJson() {
        return String.format(Locale.US,
            "{\"id\":%d,\"date\":\"%s\",\"categorie\":\"%s\",\"montant\":%.2f," +
            "\"description\":\"%s\",\"type\":\"%s\",\"recurrence\":\"%s\"}",
            id, date, categorie, montant,
            description.replace("\\", "\\\\").replace("\"", "\\\""),
            type, reccurence
        );
    }

    
    // Overide 
    @Override
    public String toString(){
        return String.format("[%s] %s - %.2f€ (%s) [%s] reccurence:%s", date, categorie, montant, description, type, reccurence);
    }

}