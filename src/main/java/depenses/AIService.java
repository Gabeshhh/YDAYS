package main.java.depenses;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Service de conseil budgétaire par intelligence artificielle.
 *
 * Utilise l'API Groq (compatible OpenAI) avec le modèle llama-3.3-70b-versatile.
 * La clé API est lue depuis la variable d'environnement GROQ_API_KEY.
 *
 * L'historique de conversation est conservé en mémoire durant toute la session
 * serveur, afin que l'IA se souvienne du contexte entre les messages.
 */
public class AIService {

    private static final String URL_API = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODELE  = "llama-3.3-70b-versatile";

    // Prompt système : définit le rôle et le comportement du conseiller IA
    private static final String PROMPT_SYSTEME =
            "Tu es un conseiller budgétaire bienveillant pour étudiants. " +
            "Tu analyses les dépenses fournies, tu réponds en français, " +
            "tu donnes des conseils concrets adaptés à un budget étudiant, " +
            "tu es encourageant et jamais moralisateur. " +
            "Si l'utilisateur pose une question hors budget, " +
            "tu réponds poliment que tu es spécialisé en gestion budgétaire.";

    private final HttpClient client; // Client HTTP Java 11+
    private final String cleApi;     // Clé API lue depuis la variable d'environnement

    // Historique de la conversation : liste de messages JSON bruts {role, content}
    // Conservé en mémoire pour que l'IA se souvienne du contexte durant la session
    private final List<String> historique = new ArrayList<>();

    public AIService() {
        this.client = HttpClient.newHttpClient();
        this.cleApi = System.getenv("GROQ_API_KEY");
        if (this.cleApi == null || this.cleApi.isBlank()) {
            System.err.println("[AIService] ATTENTION : variable GROQ_API_KEY non définie !");
        }
    }

    /**
     * Envoie un message à l'IA et retourne sa réponse textuelle.
     *
     * Au premier appel, les dépenses sont injectées dans le contexte pour que
     * l'IA dispose de toutes les données financières de l'utilisateur.
     * Les appels suivants réutilisent l'historique déjà établi.
     *
     * @param depenses          liste des transactions de l'utilisateur (contexte financier)
     * @param messageUtilisateur message saisi par l'utilisateur
     * @return réponse de l'IA en texte brut
     */
    public synchronized String demanderConseil(List<Depense> depenses, String messageUtilisateur) {

        // Vérification de la clé API avant tout appel réseau
        if (cleApi == null || cleApi.isBlank()) {
            return "Erreur : la variable GROQ_API_KEY n'est pas configurée sur le serveur. " +
                   "Copie .env.example en .env et ajoute ta clé Groq.";
        }

        try {
            // Au premier message, on enrichit la question avec le résumé des dépenses
            String contenuMessage = historique.isEmpty()
                    ? "Voici mes données financières actuelles :\n" + resumerDepenses(depenses)
                      + "\n\n" + messageUtilisateur
                    : messageUtilisateur;

            // Ajouter le message utilisateur à l'historique
            historique.add(formerMessage("user", contenuMessage));

            // Construire la requête HTTP vers l'API Groq
            HttpRequest requete = HttpRequest.newBuilder()
                    .uri(URI.create(URL_API))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Authorization", "Bearer " + cleApi)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            construireCorpsRequete(), StandardCharsets.UTF_8))
                    .build();

            // Envoyer la requête et récupérer la réponse
            HttpResponse<String> reponseHttp = client.send(requete,
                    HttpResponse.BodyHandlers.ofString());

            // Vérifier le code de statut HTTP
            if (reponseHttp.statusCode() != 200) {
                System.err.println("[AIService] Erreur HTTP " + reponseHttp.statusCode()
                        + " : " + reponseHttp.body());
                return "L'IA est temporairement indisponible (code HTTP "
                        + reponseHttp.statusCode() + ").";
            }

            // Extraire le texte de la réponse depuis le JSON retourné par Groq
            String reponseIA = extraireContenu(reponseHttp.body());

            // Sauvegarder la réponse IA dans l'historique pour le prochain tour
            historique.add(formerMessage("assistant", reponseIA));

            return reponseIA;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "La requête a été interrompue.";
        } catch (Exception e) {
            System.err.println("[AIService] Erreur : " + e.getMessage());
            return "Erreur de communication avec l'IA : " + e.getMessage();
        }
    }

    // Réinitialise l'historique de conversation pour démarrer une nouvelle session.
    public synchronized void reinitialiser() {
        historique.clear();
    }

    // ── Méthodes privées ──────────────────────────────────────────────────────

    /**
     * Génère un résumé textuel des dépenses à injecter dans le premier message.
     * Donne à l'IA le contexte financier complet de l'utilisateur.
     */
    private String resumerDepenses(List<Depense> depenses) {
        if (depenses.isEmpty()) return "Aucune dépense enregistrée pour le moment.";

        StringBuilder sb  = new StringBuilder();
        double totalDep   = 0;
        double totalRev   = 0;

        for (Depense d : depenses) {
            sb.append(String.format(Locale.US, "• [%s] %s : %.2f€ (%s) — %s\n",
                    d.getDate(), d.getCategorie(), d.getMontant(),
                    d.getType(), d.getDescription()));
            if ("depense".equals(d.getType())) totalDep += d.getMontant();
            else                                totalRev  += d.getMontant();
        }

        sb.append(String.format(Locale.US,
                "\nRécapitulatif : dépenses %.2f€ | revenus %.2f€ | solde %.2f€",
                totalDep, totalRev, totalRev - totalDep));

        return sb.toString();
    }

    /**
     * Construit le corps JSON de la requête avec le prompt système et l'historique complet.
     * Format attendu par l'API Groq (compatible OpenAI) :
     * { "model": "...", "messages": [{role, content}, ...], ... }
     */
    private String construireCorpsRequete() {
        StringBuilder messages = new StringBuilder("[");

        // Le prompt système est toujours en première position
        messages.append(formerMessage("system", PROMPT_SYSTEME));

        // Ajouter tous les messages de l'historique (utilisateur + IA)
        for (String msg : historique) {
            messages.append(",").append(msg);
        }
        messages.append("]");

        return String.format(
                "{\"model\":\"%s\",\"messages\":%s,\"max_tokens\":1024,\"temperature\":0.7}",
                MODELE, messages);
    }

    /**
     * Sérialise un message (rôle + contenu) en objet JSON brut.
     */
    private String formerMessage(String role, String contenu) {
        return "{\"role\":\"" + role + "\",\"content\":\"" + echapperJson(contenu) + "\"}";
    }

    /**
     * Extrait le champ "content" du premier choix dans la réponse JSON de l'API Groq.
     * Structure attendue : {"choices":[{"message":{"content":"..."},...}],...}
     *
     * Gère les séquences d'échappement JSON (\n, \", \\, etc.).
     */
    private String extraireContenu(String json) {
        String marqueur = "\"content\":\"";
        int debut = json.indexOf(marqueur);
        if (debut == -1) return "Réponse inattendue de l'API.";
        debut += marqueur.length();

        // Parcourir la chaîne caractère par caractère pour gérer l'échappement
        StringBuilder sb    = new StringBuilder();
        boolean        echap = false;

        for (int i = debut; i < json.length(); i++) {
            char c = json.charAt(i);
            if (echap) {
                // Interpréter la séquence d'échappement
                switch (c) {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    default:   sb.append('\\').append(c);
                }
                echap = false;
            } else if (c == '\\') {
                echap = true;          // prochain caractère est échappé
            } else if (c == '"') {
                break;                 // fin de la valeur JSON
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Échappe les caractères spéciaux pour une insertion sûre dans une chaîne JSON.
     */
    private static String echapperJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"",  "\\\"")
                .replace("\n",  "\\n")
                .replace("\r",  "\\r")
                .replace("\t",  "\\t");
    }
}
