package main.java.depenses;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Serveur HTTP léger (bibliothèque Java intégrée, zéro dépendance).
 *
 * Routes :
 *   GET    /api/depenses          → liste toutes les transactions
 *   POST   /api/depenses          → ajoute une transaction (dépense ou revenu)
 *   PUT    /api/depenses/{id}     → modifie une transaction
 *   DELETE /api/depenses/{id}     → supprime une transaction
 *   GET    /api/stats             → statistiques (totalGeneral, totalRevenus, solde, …)
 *   GET    /api/budgets           → liste des budgets
 *   POST   /api/budgets           → définit un budget
 *   GET    /api/conseils          → conseils automatiques
 *   GET    /                      → sert index.html
 */
public class Serveur {

    private static final int PORT = 8080;
    private static final DepenseService service   = new DepenseService();
    // Instance unique du service IA : conserve l'historique de conversation en mémoire
    private static final AIService      aiService = new AIService();

    public static void main(String[] args) throws IOException {

        // ── Données de démo (uniquement au premier lancement) ─────
        if (service.getTout().isEmpty()) {
            service.ajouter("2024-01-05", "Alimentation", 87.50,  "Courses Carrefour",     "depense");
            service.ajouter("2024-01-12", "Transport",    45.00,  "Plein d'essence",        "depense");
            service.ajouter("2024-01-20", "Loisirs",      29.99,  "Netflix + Spotify",      "depense");
            service.ajouter("2024-01-31", "Salaire",    1800.00,  "Salaire janvier",        "revenu");
            service.ajouter("2024-02-03", "Alimentation", 112.30, "Courses + resto",        "depense");
            service.ajouter("2024-02-14", "Santé",         55.00, "Pharmacie",              "depense");
            service.ajouter("2024-02-28", "Logement",     750.00, "Loyer février",          "depense");
            service.ajouter("2024-02-28", "Salaire",    1800.00,  "Salaire février",        "revenu");
            service.ajouter("2024-03-01", "Logement",     750.00, "Loyer mars",             "depense");
            service.ajouter("2024-03-10", "Transport",     89.00, "Train Nîmes-Paris",      "depense");
            service.ajouter("2024-03-22", "Loisirs",       65.00, "Concert",                "depense");
            service.ajouter("2024-03-31", "Salaire",    1800.00,  "Salaire mars",           "revenu");
            System.out.println("  12 transactions de démo ajoutées (premier lancement).");
        }

        // ── Création du serveur ───────────────────────────────────
        HttpServer serveur = HttpServer.create(new InetSocketAddress(PORT), 0);

        serveur.createContext("/api/depenses",   Serveur::handleDepenses);
        serveur.createContext("/api/stats",      Serveur::handleStats);
        serveur.createContext("/api/budgets",    Serveur::handleBudgets);
        serveur.createContext("/api/conseils",   Serveur::handleConseils);
        serveur.createContext("/api/programmees", Serveur::handleProgrammees);
        serveur.createContext("/",               Serveur::handleFrontend);

        serveur.start();
        System.out.println("==============================================");
        System.out.println("  Serveur démarré sur http://localhost:" + PORT);
        System.out.println("  Ouvre ce lien dans ton navigateur !");
        System.out.println("==============================================");
    }

    // ── Handler : /api/depenses ───────────────────────────────────
    private static void handleDepenses(HttpExchange exchange) throws IOException {
        ajouterCorsHeaders(exchange);
        String method = exchange.getRequestMethod();

        if (method.equals("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }

        if (method.equals("GET")) {
            envoyerJson(exchange, 200, service.listToJson());

        } else if (method.equals("POST")) {
            String body = lireBody(exchange);
            String date = extraireChamp(body, "date");
            String cat  = extraireChamp(body, "categorie");
            String desc = extraireChamp(body, "description");
            String type = extraireChamp(body, "type");
            if (type.isEmpty()) type = "depense";
            double mont = Double.parseDouble(extraireChamp(body, "montant"));
            Depense d = service.ajouter(date, cat, mont, desc, type);
            envoyerJson(exchange, 201, d.toJson());

        } else if (method.equals("PUT")) {
            String path = exchange.getRequestURI().getPath();
            int id = Integer.parseInt(path.split("/")[path.split("/").length - 1]);
            String body = lireBody(exchange);
            String date = extraireChamp(body, "date");
            String cat  = extraireChamp(body, "categorie");
            String desc = extraireChamp(body, "description");
            String type = extraireChamp(body, "type");
            if (type.isEmpty()) type = "depense";
            double mont = Double.parseDouble(extraireChamp(body, "montant"));
            boolean ok = service.modifier(id, date, cat, mont, desc, type);
            envoyerJson(exchange, ok ? 200 : 404, ok ? "{\"ok\":true}" : "{\"ok\":false}");

        } else if (method.equals("DELETE")) {
            String path = exchange.getRequestURI().getPath();
            int id = Integer.parseInt(path.split("/")[path.split("/").length - 1]);
            boolean ok = service.supprimer(id);
            envoyerJson(exchange, ok ? 200 : 404, ok ? "{\"ok\":true}" : "{\"ok\":false}");

        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    // ── Handler : /api/programmees ───────────────────────────────
    // GET    /api/programmees          → liste toutes
    // POST   /api/programmees          → ajoute une transaction programmée
    // PUT    /api/programmees/{id}     → modifie
    // DELETE /api/programmees/{id}     → supprime
    // POST   /api/programmees/{id}/executer → exécute (convertit en transaction réelle)
    private static void handleProgrammees(HttpExchange exchange) throws IOException {
        ajouterCorsHeaders(exchange);
        String method = exchange.getRequestMethod();
        String path   = exchange.getRequestURI().getPath();
        String[] parts = path.split("/"); // ["", "api", "programmees", <id?>, <action?>]

        if (method.equals("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }

        if (method.equals("GET") && parts.length == 3) {
            envoyerJson(exchange, 200, service.programmeesToJson());

        } else if (method.equals("POST") && parts.length == 3) {
            String body      = lireBody(exchange);
            String date      = extraireChamp(body, "date");
            String cat       = extraireChamp(body, "categorie");
            String desc      = extraireChamp(body, "description");
            String type      = extraireChamp(body, "type");
            String recurrence = extraireChamp(body, "recurrence");
            if (type.isEmpty())       type       = "depense";
            if (recurrence.isEmpty()) recurrence = "aucune";
            double mont = Double.parseDouble(extraireChamp(body, "montant"));
            TransactionProgrammee tp = service.ajouterProgrammee(date, cat, mont, desc, type, recurrence);
            envoyerJson(exchange, 201, tp.toJson());

        } else if (method.equals("PUT") && parts.length == 4) {
            int    id        = Integer.parseInt(parts[3]);
            String body      = lireBody(exchange);
            String date      = extraireChamp(body, "date");
            String cat       = extraireChamp(body, "categorie");
            String desc      = extraireChamp(body, "description");
            String type      = extraireChamp(body, "type");
            String recurrence = extraireChamp(body, "recurrence");
            if (type.isEmpty())       type       = "depense";
            if (recurrence.isEmpty()) recurrence = "aucune";
            double mont = Double.parseDouble(extraireChamp(body, "montant"));
            boolean ok = service.modifierProgrammee(id, date, cat, mont, desc, type, recurrence);
            envoyerJson(exchange, ok ? 200 : 404, ok ? "{\"ok\":true}" : "{\"ok\":false}");

        } else if (method.equals("DELETE") && parts.length == 4) {
            int id = Integer.parseInt(parts[3]);
            boolean ok = service.supprimerProgrammee(id);
            envoyerJson(exchange, ok ? 200 : 404, ok ? "{\"ok\":true}" : "{\"ok\":false}");

        } else if (method.equals("POST") && parts.length == 5 && "executer".equals(parts[4])) {
            int id = Integer.parseInt(parts[3]);
            Depense d = service.executerProgrammee(id);
            if (d != null) envoyerJson(exchange, 200, d.toJson());
            else           envoyerJson(exchange, 404, "{\"ok\":false}");

        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    // ── Handler : /api/stats ──────────────────────────────────────
    private static void handleStats(HttpExchange exchange) throws IOException {
        ajouterCorsHeaders(exchange);
        if (exchange.getRequestMethod().equals("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }
        envoyerJson(exchange, 200, service.statsToJson());
    }

    // ── Handler : /api/budgets ────────────────────────────────────
    private static void handleBudgets(HttpExchange exchange) throws IOException {
        ajouterCorsHeaders(exchange);
        String method = exchange.getRequestMethod();
        if (method.equals("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }

        if (method.equals("GET")) {
            envoyerJson(exchange, 200, service.budgetsToJson());
        } else if (method.equals("POST")) {
            String body = lireBody(exchange);
            String cat  = extraireChamp(body, "categorie");
            double mont = Double.parseDouble(extraireChamp(body, "montant"));
            service.definirBudget(cat, mont);
            envoyerJson(exchange, 200, "{\"ok\":true}");
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    // ── Handler : /api/conseils ───────────────────────────────────
    // GET  → conseils automatiques (règles statiques, pas d'IA)
    // POST → conseils personnalisés via l'IA Groq
    private static void handleConseils(HttpExchange exchange) throws IOException {
        ajouterCorsHeaders(exchange);
        String method = exchange.getRequestMethod();

        if (method.equals("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }

        if (method.equals("GET")) {
            // Conseils générés par les règles statiques existantes
            envoyerJson(exchange, 200, service.conseilsToJson());

        } else if (method.equals("POST")) {
            // Conseil IA : lire le message utilisateur
            String body    = lireBody(exchange);
            String message = extraireChamp(body, "message");

            if (message.isEmpty()) {
                envoyerJson(exchange, 400, "{\"erreur\":\"Le champ message est requis.\"}");
                return;
            }

            // Récupérer toutes les dépenses pour le contexte de l'IA
            List<Depense> depenses = new ArrayList<>(service.getTout());

            // Appeler le service IA et récupérer la réponse
            String reponseIA = aiService.demanderConseil(depenses, message);

            // Retourner la réponse en JSON
            String json = "{\"reponse\":\"" + echapperPourJson(reponseIA) + "\"}";
            envoyerJson(exchange, 200, json);

        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    // ── Handler : fichiers statiques ──────────────────────────────
    private static void handleFrontend(HttpExchange exchange) throws IOException {
        String uri  = exchange.getRequestURI().getPath();
        String file = uri.equals("/") ? "index.html" : uri.substring(1);
        File   f    = new File(file);

        if (!f.exists()) {
            String msg = "404 — Fichier introuvable : " + file;
            exchange.sendResponseHeaders(404, msg.length());
            exchange.getResponseBody().write(msg.getBytes());
            exchange.getResponseBody().close();
            return;
        }

        String type = file.endsWith(".html") ? "text/html; charset=utf-8"
                    : file.endsWith(".css")  ? "text/css"
                    : file.endsWith(".js")   ? "application/javascript"
                    : "application/octet-stream";

        byte[] bytes = Files.readAllBytes(f.toPath());
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    // ── Utilitaires ───────────────────────────────────────────────

    private static void envoyerJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    /**
     * Échappe les caractères spéciaux pour une insertion sûre dans une valeur JSON.
     * Nécessaire pour encapsuler la réponse IA (qui peut contenir des guillemets, etc.).
     */
    private static String echapperPourJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"",  "\\\"")
                .replace("\n",  "\\n")
                .replace("\r",  "\\r")
                .replace("\t",  "\\t");
    }

    private static void ajouterCorsHeaders(HttpExchange ex) {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin",  "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static String lireBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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
            while (fin < json.length() && "0123456789.".indexOf(json.charAt(fin)) >= 0) fin++;
            return json.substring(debut, fin);
        }
    }
}
