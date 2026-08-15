package fr.conquest.integration;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Detecte automatiquement un plugin Factions installe (n'importe quel fork),
 * SANS dependance de compilation directe (uniquement de la reflexion), et
 * fournit un identifiant de "faction" pour chaque joueur.
 *
 * Deux GENERATIONS d'API coexistent sous le paquet historique
 * "com.massivecraft.factions" selon le fork :
 *  A) API "nouvelle generation" : com.massivecraft.factions.entity.MPlayer
 *     avec MPlayer.get(joueur) statique.
 *  B) API "historique" (Factions original, FactionsUUID, SaberFactions...) :
 *     singleton com.massivecraft.factions.FPlayers.getInstance().getByPlayer(joueur).
 *
 * IMPORTANT (exigence du cahier des charges) : Conquest doit fonctionner
 * PARFAITEMENT sans aucun plugin Factions installe. Dans ce cas,
 * getFactionId() retombe sur un mode "solo" : chaque joueur est traite
 * comme sa propre faction (son UUID sert d'identifiant), ce qui permet a
 * toute la logique de points/capture de continuer a fonctionner sans
 * jamais lever d'exception ni bloquer l'evenement.
 *
 * DISTINCTION IMPORTANTE ID vs NOM AFFICHABLE : l'identifiant utilise pour
 * STOCKER les points (getFactionId) doit rester STABLE meme si la faction
 * est renommee en cours de partie - sinon un renommage cree artificiellement
 * une "nouvelle" faction dans les scores. Le NOM AFFICHE (getFactionDisplayName)
 * est resolu separement et peut donc changer librement sans casser les
 * scores stockes.
 */
public class FactionHook {

    private final Logger logger;

    private Method newGetMethod;
    private Method newGetFactionMethod;
    private Method newGetIdMethod;
    private Method newGetNameMethod;

    private Method oldGetInstanceMethod;
    private Method oldGetByPlayerMethod;
    private Method oldGetFactionMethod;
    private Method oldGetIdMethod;
    private Method oldGetNameMethod;

    private boolean placeholderApiAvailable = false;
    private Method papiSetPlaceholders;

    // id de faction (stable) -> dernier nom affichable connu (peut changer si renommage).
    // Mis a jour a chaque fois qu'un joueur de cette faction declenche un lookup.
    private final Map<String, String> displayNameCache = new HashMap<>();

    private static final String[] PAPI_CANDIDATES = {
            "%factionsuuid_faction_name%",
            "%factions_faction_name%",
            "%faction_name%",
            "%factionsx_faction_name%",
            "%kingdoms_kingdom%"
    };

    public FactionHook(Logger logger) {
        this.logger = logger;
        detectNewGeneration();
        detectOldGeneration();
        detectPlaceholderApi();

        if (!hasRealFactionPlugin()) {
            logger.info("[Conquest] Aucun plugin Factions detecte : mode solo active "
                    + "(chaque joueur compte comme sa propre faction). L'evenement fonctionne normalement.");
        }
    }

    // ================= Detection =================

    private void detectNewGeneration() {
        try {
            Class<?> mPlayerClass = Class.forName("com.massivecraft.factions.entity.MPlayer");
            Method getMethod = findMethodWithFlexibleParam(mPlayerClass, "get");
            if (getMethod == null) return;

            Method getFactionMethod = mPlayerClass.getMethod("getFaction");
            Class<?> factionClass = getFactionMethod.getReturnType();
            Method idMethod = findStableIdMethod(factionClass);
            if (idMethod == null) return;

            this.newGetMethod = getMethod;
            this.newGetFactionMethod = getFactionMethod;
            this.newGetIdMethod = idMethod;
            this.newGetNameMethod = findDisplayNameMethod(factionClass);
            logger.info("[Conquest] Plugin Factions detecte (API MPlayer / nouvelle generation).");
        } catch (Exception ignored) {
        }
    }

    private void detectOldGeneration() {
        try {
            Class<?> fPlayersClass = Class.forName("com.massivecraft.factions.FPlayers");
            Method getInstance = fPlayersClass.getMethod("getInstance");
            Object instance = getInstance.invoke(null);
            if (instance == null) return;

            Method getByPlayer = findMethodWithFlexibleParam(instance.getClass(), "getByPlayer");
            if (getByPlayer == null) return;

            Class<?> fPlayerClass = getByPlayer.getReturnType();
            Method getFactionMethod = fPlayerClass.getMethod("getFaction");
            Class<?> factionClass = getFactionMethod.getReturnType();
            Method idMethod = findStableIdMethod(factionClass);
            if (idMethod == null) return;

            this.oldGetInstanceMethod = getInstance;
            this.oldGetByPlayerMethod = getByPlayer;
            this.oldGetFactionMethod = getFactionMethod;
            this.oldGetIdMethod = idMethod;
            this.oldGetNameMethod = findDisplayNameMethod(factionClass);
            logger.info("[Conquest] Plugin Factions detecte (API FPlayer/FPlayers historique).");
        } catch (Exception ignored) {
        }
    }

    private Method findMethodWithFlexibleParam(Class<?> owner, String methodName) {
        for (Class<?> paramType : new Class<?>[]{Player.class, OfflinePlayer.class, Object.class}) {
            try {
                return owner.getMethod(methodName, paramType);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    /**
     * Identifiant STABLE (ne doit jamais changer, meme si la faction est
     * renommee) : priorite a getId(), qui est generalement une cle technique
     * interne invariante - contrairement a getTag()/getComparisonTag() qui
     * changent si la faction se renomme.
     */
    private Method findStableIdMethod(Class<?> factionClass) {
        for (String name : new String[]{"getId", "getUniqueId", "getComparisonTag", "getTag"}) {
            try {
                return factionClass.getMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    /**
     * Nom AFFICHABLE (peut changer librement si la faction se renomme) :
     * priorite a getTag()/getComparisonTag(), le nom lisible habituel.
     */
    private Method findDisplayNameMethod(Class<?> factionClass) {
        for (String name : new String[]{"getTag", "getComparisonTag", "getName"}) {
            try {
                return factionClass.getMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private void detectPlaceholderApi() {
        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (papi == null || !papi.isEnabled()) return;
        try {
            Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            papiSetPlaceholders = papiClass.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            placeholderApiAvailable = true;
            logger.info("[Conquest] PlaceholderAPI detecte, utilise en secours pour la detection de faction.");
        } catch (Exception ignored) {
        }
    }

    public boolean hasRealFactionPlugin() {
        return newGetMethod != null || oldGetInstanceMethod != null || placeholderApiAvailable;
    }

    // ================= Acces unifie =================

    private Object getPlayerEntity(Player player) {
        try {
            if (newGetMethod != null) {
                return newGetMethod.invoke(null, player);
            }
            if (oldGetInstanceMethod != null && oldGetByPlayerMethod != null) {
                Object instance = oldGetInstanceMethod.invoke(null);
                return oldGetByPlayerMethod.invoke(instance, player);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private Method getFactionMethod() {
        return newGetMethod != null ? newGetFactionMethod : oldGetFactionMethod;
    }

    private Method getIdMethod() {
        return newGetMethod != null ? newGetIdMethod : oldGetIdMethod;
    }

    private Method getNameMethod() {
        return newGetMethod != null ? newGetNameMethod : oldGetNameMethod;
    }

    /**
     * @return l'identifiant STABLE de la faction du joueur (ne change jamais
     * en cas de renommage). Si aucun plugin Factions n'est detecte, retombe
     * sur l'UUID du joueur (mode solo) : cette methode ne renvoie donc
     * JAMAIS null.
     */
    public String getFactionId(Player player) {
        String id = tryReflection(player);
        if (id != null) return id;

        String papiId = tryPlaceholderApi(player);
        if (papiId != null) return papiId;

        // Mode solo : le joueur est sa propre faction.
        return player.getUniqueId().toString();
    }

    /**
     * Nom lisible d'une faction pour l'affichage (messages, scoreboard...).
     * Utilise le dernier nom connu mis en cache (rafraichi a chaque
     * getFactionId() reussi) ; retombe sur l'identifiant brut si jamais
     * aucun joueur de cette faction n'a encore declenche de lookup depuis
     * le demarrage du plugin (ex. juste apres un redemarrage serveur).
     */
    public String getFactionDisplayName(String factionId) {
        if (factionId == null) return "?";

        String cached = displayNameCache.get(factionId);
        if (cached != null) return cached;

        try {
            UUID uuid = UUID.fromString(factionId);
            OfflinePlayer solo = Bukkit.getOfflinePlayer(uuid);
            if (solo.getName() != null) return solo.getName();
        } catch (IllegalArgumentException ignored) {
            // Ce n'est pas un UUID solo : c'est un identifiant de vraie faction.
        }
        return factionId;
    }

    private String tryReflection(Player player) {
        Object entity = getPlayerEntity(player);
        if (entity == null) return null;
        Method factionMethod = getFactionMethod();
        Method idMethod = getIdMethod();
        if (factionMethod == null || idMethod == null) return null;

        try {
            Object faction = factionMethod.invoke(entity);
            if (faction == null) return null;

            Object id = idMethod.invoke(faction);
            String idValue = id == null ? null : String.valueOf(id);
            if (isWilderness(idValue)) return null;

            // Rafraichit au passage le nom affichable en cache pour cet id
            // (couvre le cas d'un renommage : le prochain affichage montrera
            // le nouveau nom sans que l'id de stockage n'ait bouge).
            Method nameMethod = getNameMethod();
            if (nameMethod != null) {
                try {
                    Object name = nameMethod.invoke(faction);
                    if (name != null) {
                        displayNameCache.put(idValue, String.valueOf(name));
                    }
                } catch (Exception ignored) {
                }
            }

            return idValue;
        } catch (Exception e) {
            return null;
        }
    }

    private String tryPlaceholderApi(Player player) {
        if (!placeholderApiAvailable) return null;
        for (String placeholder : PAPI_CANDIDATES) {
            try {
                Object result = papiSetPlaceholders.invoke(null, player, placeholder);
                String value = result == null ? null : String.valueOf(result);
                if (value != null && !value.equals(placeholder) && !isWilderness(value)) {
                    return value;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private boolean isWilderness(String value) {
        if (value == null) return true;
        String v = value.trim();
        return v.isEmpty() || v.equalsIgnoreCase("wilderness") || v.equalsIgnoreCase("none") || v.equals("0");
    }
}
