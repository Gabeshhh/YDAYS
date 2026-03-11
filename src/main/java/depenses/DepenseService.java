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

    public List<TransactionProgrammee> getProgrammees(){
        return Collections.unmodifiableList(programmees);
    }

    // 
    public Depense executerProgrammee(int id) {
        for (int i = 0; i < programmees.size(); i++) {
            TransactionProgrammee tp = programmees.get(i);
            if (tp.getId() == id) {
                Depense d = ajouter(tp.getDate(), tp.getCategorie(),
                        tp.getMontant(), tp.getDescription(), tp.getType());
                if ("aucune".equals(tp.getRecurrence())) {
                    programmees.remove(i);
                } else {
                    String nextDate = calculerProchaineDate(tp.getDate(), tp.getRecurrence());
                    programmees.set(i, new TransactionProgrammee(tp.getId(), nextDate,
                            tp.getCategorie(), tp.getMontant(), tp.getDescription(),
                            tp.getType(), tp.getRecurrence()));
                }
                sauvegarderProgrammees();
                return d;
            }
        }
        return null;
    }

    private String calculerProchaineDate(String date, String recurrence) {
        LocalDate d = LocalDate.parse(date);
        switch (recurrence) {
            case "hebdomadaire": return d.plusWeeks(1).toString();
            case "mensuelle":    return d.plusMonths(1).toString();
            case "annuelle":     return d.plusYears(1).toString();
            default:             return date;
        }
    }

    // Budgets du mois 
    public void definirBudget(String categorie, double montant) {
        if (montant <= 0) budgets.remove(categorie);
        else              budgets.put(categorie, montant);
        sauvegarderBudgets();
    }

    public Map<String, Double> getBudgets() {
        return Collections.unmodifiableMap(budgets);
    }


    // Stats 
    // Total de toutes les dépenses (type = "depense").
    public double totalGeneral() {
        double total = 0;
        for (Depense d : depenses)
            if ("depense".equals(d.getType())) total += d.getMontant();
        return total;
    }

    // Total de tous les revenus (type = "revenu").
    public double totalRevenus() {
        double total = 0;
        for (Depense d : depenses)
            if ("revenu".equals(d.getType())) total += d.getMontant();
        return total;
    }

    // Solde = revenus - dépenses. 
    public double solde() {
        return totalRevenus() - totalGeneral();
    }

    // Total dépenses par catégorie (hors revenus).
    public Map<String, Double> totalParCategorie() {
        Map<String, Double> totaux = new HashMap<>();
        for (Depense d : depenses) {
            if (!"depense".equals(d.getType())) continue;
            String cat = d.getCategorie();
            totaux.put(cat, totaux.getOrDefault(cat, 0.0) + d.getMontant());
        }
        return totaux;
    }

    public Map<String, Double> totalParMois() {
        Map<String, Double> totaux = new HashMap<>();
        for (Depense d : depenses) {
            if (!"depense".equals(d.getType())) continue;
            String mois = d.getDate().substring(0, 7);
            totaux.put(mois, totaux.getOrDefault(mois, 0.0) + d.getMontant());
        }
        return totaux;
    }

    public Map<String, Double> totalRevenuParMois() {
        Map<String, Double> totaux = new HashMap<>();
        for (Depense d : depenses) {
            if (!"revenu".equals(d.getType())) continue;
            String mois = d.getDate().substring(0, 7);
            totaux.put(mois, totaux.getOrDefault(mois, 0.0) + d.getMontant());
        }
        return totaux;
    }

    private Map<String, Double> totalParCategoriePourMois(String mois) {
        Map<String, Double> totaux = new HashMap<>();
        for (Depense d : depenses) {
            if (!"depense".equals(d.getType())) continue;
            if (!d.getDate().startsWith(mois)) continue;
            String cat = d.getCategorie();
            totaux.put(cat, totaux.getOrDefault(cat, 0.0) + d.getMontant());
        }
        return totaux;
    }

    public String moisLePlusCouteux() {
        Map<String, Double> parMois = totalParMois();
        if (parMois.isEmpty()) return "Aucune donnée";
        String moisMax = null; double maxTotal = -1;
        for (Map.Entry<String, Double> e : parMois.entrySet()) {
            if (e.getValue() > maxTotal) { maxTotal = e.getValue(); moisMax = e.getKey(); }
        }
        return moisMax;
    }

    public String categorieLesPlusChere() {
        Map<String, Double> parCat = totalParCategorie();
        if (parCat.isEmpty()) return "Aucune donnée";
        String catMax = null; double maxTotal = -1;
        for (Map.Entry<String, Double> e : parCat.entrySet()) {
            if (e.getValue() > maxTotal) { maxTotal = e.getValue(); catMax = e.getKey(); }
        }
        return catMax;
    }

    // Conseils avec IA (Groq)
    public List<String> genererConseils() {
        List<String> conseils = new ArrayList<>();
        String moisActuel = LocalDate.now().toString().substring(0, 7);
        Map<String, Double> parMois = totalParMois();
        double totalMoisActuel = parMois.getOrDefault(moisActuel, 0.0);

        // Règle 1 : dépassement de budget
        Map<String, Double> depCatMois = totalParCategoriePourMois(moisActuel);
        for (Map.Entry<String, Double> b : budgets.entrySet()) {
            String cat    = b.getKey();
            double budget = b.getValue();
            double dep    = depCatMois.getOrDefault(cat, 0.0);
            if (budget <= 0) continue;
            double pct = (dep / budget) * 100;
            if (pct > 100)
                conseils.add(String.format("DANGER Dépassement %s : %.0f€ dépensés pour %.0f€ prévu (%.0f%%)", cat, dep, budget, pct));
            else if (pct > 80)
                conseils.add(String.format("ATTENTION Budget %s bientôt atteint : %.0f€ / %.0f€ (%.0f%%)", cat, dep, budget, pct));
        }

        // Règle 2 : évolution vs mois précédent
        String moisPrec  = LocalDate.now().minusMonths(1).toString().substring(0, 7);
        double totalPrec = parMois.getOrDefault(moisPrec, -1.0);
        if (totalPrec > 0 && totalMoisActuel > 0) {
            double evo = ((totalMoisActuel - totalPrec) / totalPrec) * 100;
            if (evo > 20)
                conseils.add(String.format("HAUSSE Tes dépenses ont augmenté de %.0f%% vs le mois dernier (%.0f€ vs %.0f€)", evo, totalMoisActuel, totalPrec));
            else if (evo < -20)
                conseils.add(String.format("BAISSE Bonne nouvelle ! Tu dépenses %.0f%% de moins que le mois dernier (%.0f€ vs %.0f€)", Math.abs(evo), totalMoisActuel, totalPrec));
        }

        // Règle 3 : catégorie dominante
        double total = totalGeneral();
        if (total > 0) {
            for (Map.Entry<String, Double> e : totalParCategorie().entrySet()) {
                double pct = (e.getValue() / total) * 100;
                if (pct > 50)
                    conseils.add(String.format("INFO %s représente %.0f%% de tes dépenses totales — pense à équilibrer", e.getKey(), pct));
            }
        }

        // Règle 4 : aucune dépense ce mois
        if (totalMoisActuel == 0 && !depenses.isEmpty())
            conseils.add("INFO Aucune dépense saisie ce mois. Pense à enregistrer tes achats !");

        return conseils;
    }

    // JSON 
    public String statsToJson() {
        Map<String, Double> parCat  = totalParCategorie();
        Map<String, Double> parMois = totalParMois();
        StringBuilder sb = new StringBuilder("{");
        sb.append(String.format(Locale.US, "\"totalGeneral\":%.2f,", totalGeneral()));
        sb.append(String.format(Locale.US, "\"totalRevenus\":%.2f,", totalRevenus()));
        sb.append(String.format(Locale.US, "\"solde\":%.2f,", solde()));
        sb.append(String.format(Locale.US, "\"moisLePlusCouteux\":\"%s\",", moisLePlusCouteux()));
        sb.append(String.format(Locale.US, "\"categorieTopDepense\":\"%s\",", categorieLesPlusChere()));
        sb.append("\"parCategorie\":{");
        boolean first = true;
        for (Map.Entry<String, Double> e : parCat.entrySet()) {
            if (!first) sb.append(",");
            sb.append(String.format(Locale.US, "\"%s\":%.2f", e.getKey(), e.getValue()));
            first = false;
        }
        sb.append("},");
        List<String> moisTries = new ArrayList<>(parMois.keySet());
        Collections.sort(moisTries);
        sb.append("\"parMois\":{");
        first = true;
        for (String mois : moisTries) {
            if (!first) sb.append(",");
            sb.append(String.format(Locale.US, "\"%s\":%.2f", mois, parMois.get(mois)));
            first = false;
        }
        sb.append("}}");
        return sb.toString();
    }

    public String listToJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < depenses.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(depenses.get(i).toJson());
        }
        sb.append("]");
        return sb.toString();
    }

    public String budgetsToJson() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Double> e : budgets.entrySet()) {
            if (!first) sb.append(",");
            sb.append(String.format(Locale.US, "\"%s\":%.2f", e.getKey(), e.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    public String programmeesToJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < programmees.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(programmees.get(i).toJson());
        }
        sb.append("]");
        return sb.toString();
    }

    public String conseilsToJson() {
        List<String> conseils = genererConseils();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < conseils.size(); i++) {
            if (i > 0) sb.append(",");
            String c = conseils.get(i).replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append("\"").append(c).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }


    // Persistance en DB (envoyer)
    private void sauvegarderDepenses() {
        try {
            Files.writeString(Path.of(FICHIER_DEPENSES), listToJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Erreur sauvegarde : " + e.getMessage());
        }
    }

    private void sauvegarderBudgets() {
        try {
            Files.writeString(Path.of(FICHIER_BUDGETS), budgetsToJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Erreur sauvegarde budgets : " + e.getMessage());
        }
    }

    private void sauvegarderProgrammees() {
        try {
            Files.writeString(Path.of(FICHIER_PROGRAMMEES), programmeesToJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Erreur sauvegarde programmées : " + e.getMessage());
        }
    }

    private void charger() {
        try {
            Path p = Path.of(FICHIER_DEPENSES);
            if (Files.exists(p)) {
                chargerDepensesDepuisJson(Files.readString(p, StandardCharsets.UTF_8));
                System.out.println("  " + depenses.size() + " transaction(s) chargée(s).");
            }
        } catch (IOException e) {
            System.err.println("Erreur chargement : " + e.getMessage());
        }
        try {
            Path p = Path.of(FICHIER_BUDGETS);
            if (Files.exists(p)) {
                chargerBudgetsDepuisJson(Files.readString(p, StandardCharsets.UTF_8));
                System.out.println("  " + budgets.size() + " budget(s) chargé(s).");
            }
        } catch (IOException e) {
            System.err.println("Erreur chargement budgets : " + e.getMessage());
        }
        try {
            Path p = Path.of(FICHIER_PROGRAMMEES);
            if (Files.exists(p)) {
                chargerProgrammeesDepuisJson(Files.readString(p, StandardCharsets.UTF_8));
                System.out.println("  " + programmees.size() + " transaction(s) programmée(s) chargée(s).");
            }
        } catch (IOException e) {
            System.err.println("Erreur chargement programmées : " + e.getMessage());
        }
    }

    private void chargerDepensesDepuisJson(String json) {
        json = json.trim();
        if (json.equals("[]") || json.isEmpty()) return;
        json = json.substring(1, json.length() - 1);
        int depth = 0, start = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') { if (depth == 0) start = i; depth++; }
            else if (c == '}') { depth--; if (depth == 0) parseDepense(json.substring(start, i + 1)); }
        }
    }

    private void parseDepense(String obj) {
        try {
            int    id      = Integer.parseInt(extraireChamp(obj, "id"));
            String date    = extraireChamp(obj, "date");
            String cat     = extraireChamp(obj, "categorie");
            double montant = Double.parseDouble(extraireChamp(obj, "montant"));
            String desc    = extraireChamp(obj, "description");
            String type    = extraireChamp(obj, "type");
            if (type.isEmpty()) type = "depense"; // rétrocompatibilité
            depenses.add(new Depense(id, date, cat, montant, desc, type));
            if (id >= nextId) nextId = id + 1;
        } catch (NumberFormatException e) {
            System.err.println("Erreur parsing : " + e.getMessage());
        }
    }

    private void chargerBudgetsDepuisJson(String json) {
        json = json.trim();
        if (json.equals("{}") || json.isEmpty()) return;
        json = json.substring(1, json.length() - 1).trim();
        int i = 0;
        while (i < json.length()) {
            while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == ',')) i++;
            if (i >= json.length() || json.charAt(i) != '"') break;
            i++;
            int keyEnd = json.indexOf('"', i);
            if (keyEnd == -1) break;
            String key = json.substring(i, keyEnd);
            i = keyEnd + 1;
            while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == ':')) i++;
            int numStart = i;
            while (i < json.length() && "0123456789.".indexOf(json.charAt(i)) >= 0) i++;
            try { budgets.put(key, Double.parseDouble(json.substring(numStart, i))); }
            catch (NumberFormatException e) { System.err.println("Erreur budget '" + key + "'"); }
        }
    }

    private void chargerProgrammeesDepuisJson(String json) {
        json = json.trim();
        if (json.equals("[]") || json.isEmpty()) return;
        json = json.substring(1, json.length() - 1);
        int depth = 0, start = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') { if (depth == 0) start = i; depth++; }
            else if (c == '}') { depth--; if (depth == 0) parseProgrammee(json.substring(start, i + 1)); }
        }
    }

    private void parseProgrammee(String obj) {
        try {
            int    id         = Integer.parseInt(extraireChamp(obj, "id"));
            String date       = extraireChamp(obj, "date");
            String cat        = extraireChamp(obj, "categorie");
            double montant    = Double.parseDouble(extraireChamp(obj, "montant"));
            String desc       = extraireChamp(obj, "description");
            String type       = extraireChamp(obj, "type");
            String recurrence = extraireChamp(obj, "recurrence");
            if (type.isEmpty())       type       = "depense";
            if (recurrence.isEmpty()) recurrence = "aucune";
            programmees.add(new TransactionProgrammee(id, date, cat, montant, desc, type, recurrence));
            if (id >= nextProgId) nextProgId = id + 1;
        } catch (NumberFormatException e) {
            System.err.println("Erreur parsing programmée : " + e.getMessage());
        }
    }

    private static String extraireChamp(String json, String champ) {
        String recherche = "\"" + champ + "\"";
        int debut = json.indexOf(recherche);
        if (debut == -1) return "";
        debut = json.indexOf(":", debut) + 1;
        while (debut < json.length() && json.charAt(debut) == ' ') debut++;
        if (json.charAt(debut) == '"') {
            debut++;
            int fin = json.indexOf('"', debut);
            return json.substring(debut, fin);
        } else {
            int fin = debut;
            while (fin < json.length() && "0123456789.-".indexOf(json.charAt(fin)) >= 0) fin++;
            return json.substring(debut, fin);
        }
    }
}
