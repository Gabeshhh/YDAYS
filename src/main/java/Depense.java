package main.java;

import java.util.Locale;

public class Depense {
    // Attributs de la classe Depenses 
    private int id;
    private String date;
    private String categorie;
    private double montant;
    private String description;
    private String type; // A def plus tard

    
    // Constructeur de la classe 
    public Depense(int id, String date, String categorie, double montant, String description, String type){
        this.id = id;
        this.date = date;
        this.categorie = categorie;
        this.montant = montant;
        this.description = description;
        this.type = type;
    }

    // Getters 
    public int getId() { return id; }
    public String getDate() { return date; }
    public String getCategorie() { return categorie; }
    public double getMontant() { return montant; }
    public String getDescription() { return description; }
    public String getType() { return type; }


    // Renvoie en JSON
    public String toJson() {
        return String.format(Locale.US,
            "{\"id\":%d,\"date\":\"%s\",\"categorie\":\"%s\",\"montant\":%.2f,\"description\":\"%s\",\"type\":\"%s\"}",
            id, date, categorie, montant, description.replace("\"", "\\\""), type
        );
    }

    // Overide
    @Override
    public String toString(){
        return String.format("[%s] %s — %.2f€ (%s) [%s]", date, categorie, montant, description, type);
    }
}
