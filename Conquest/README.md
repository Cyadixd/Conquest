# Conquest — Evenement PvP de controle de zones entre factions

Plugin pour Minecraft **1.8.8/1.8.9**, ecrit contre l'API Spigot/Bukkit standard
pour rester compatible avec Spigot, PaperSpigot, TacoSpigot, NachoSpigot,
CarbonSpigot, WindSpigot, FoxSpigot, PandaSpigot, ImanitySpigot, nSpigot et tout
autre fork Bukkit 1.8.8/1.8.9. Aucune dependance obligatoire a un Core PvP
Faction : l'integration Factions est detectee automatiquement par reflexion et
totalement optionnelle (voir plus bas).

## Compiler le plugin (IMPORTANT)

Comme pour tes autres projets, ce code a ete genere dans un environnement sans
acces au depot Maven de Spigot ni a un JDK installe : je n'ai pas pu compiler le
`.jar` moi-meme. Chez toi (machine avec Internet + Java 8+) :

```bash
wget https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar
java -jar BuildTools.jar --rev 1.8.8
cd Conquest
mvn clean package
```

Le jar sera dans `target/Conquest.jar`.

## Ajout : action bar visible uniquement dans la zone en cours de capture

Seuls les joueurs PHYSIQUEMENT PRESENTS dans une zone activement capturee
voient son action bar (`Zone Rouge : Capture 6s/10 par Pseudo 12/25`) — pas
tout le serveur. En dehors de toute zone capturee, un joueur voit la ligne
par defaut de `BossBarManager` (son total de points). Chaque zone gere sa
propre diffusion independamment (si plusieurs zones sont capturees en meme
temps par des joueurs differents, chacun ne voit que celle ou il se trouve).

## Ajout : transfert automatique du capteur quand sa faction plafonne

Des qu'une faction atteint 25/25 sur une zone, son capteur actuel y est
immediatement retire et remplace au hasard par un autre joueur present dans
la zone, appartenant a une faction pas encore plafonnee (si personne
d'eligible n'est present, la zone reste sans capteur jusqu'a ce que
quelqu'un d'eligible y entre). Un joueur d'une faction deja au plafond ne
peut plus non plus devenir capteur en entrant dans la zone.

## Correctif : le plafond de 25 points bloquait TOUTE la zone

Bug important : des qu'une faction atteignait 25 points sur une zone, la
zone entiere se "verrouillait" et plus personne (aucune autre faction) ne
pouvait plus y capturer quoi que ce soit. Ce n'etait pas le comportement
voulu : le plafond de 25 s'applique **par faction, par zone** —
independamment les unes des autres. Corrige : une faction qui atteint son
plafond sur une zone n'y gagne simplement plus de points, mais la zone
reste totalement ouverte pour toutes les autres factions, qui peuvent
continuer a y capturer jusqu'a leur propre plafond de 25.

## Correctif : un joueur sans faction (wilderness) pouvait quand meme capturer

Bug trouve : `isFactionMaxed(zone, factionId)` renvoyait `false` (donc
"pas plafonne", eligible) quand `factionId` valait `null` (joueur sans
faction), puisque `zone.getPoints(null)` vaut simplement 0. Un joueur en
wilderness pouvait donc devenir capteur normalement. Corrige a trois
endroits : `isFactionMaxed` traite maintenant explicitement `null` comme
"toujours plafonne" (donc jamais eligible), l'entree en zone ne designe
jamais un joueur sans faction comme capteur, et le tirage au sort du
nouveau capteur exclut aussi les joueurs sans faction.

## Correctif : renommer une faction en cours de partie creait un doublon

`getFactionId()` utilisait le TAG/NOM de la faction comme cle de stockage des
points (correctif precedent pour afficher un vrai nom au lieu d'un ID
numerique). Consequence non voulue : renommer sa faction en cours de partie
changeait cette cle, et les points deja captures restaient bloques sous
l'ANCIEN nom pendant que les nouveaux points s'accumulaient sous le NOUVEAU
nom — deux lignes distinctes dans `/conquest points` pour ce qui est en
realite la meme faction.

Corrige en separant clairement les deux notions :
- **`getFactionId()`** utilise maintenant un identifiant STABLE (`getId()` en
  priorite), qui ne change jamais meme si la faction se renomme — c'est cette
  valeur qui sert de cle pour stocker les points.
- **`getFactionDisplayName()`** resout separement le nom LISIBLE actuel
  (`getTag()`/`getComparisonTag()`), mis en cache et rafraichi a chaque fois
  qu'un membre de cette faction capture un point. Un renommage met donc a
  jour l'affichage sans jamais toucher aux points deja stockes.

## Ajout : compte a rebours avant le lancement

`/conquest start` ne lance plus l'evenement immediatement : il declenche un
compte a rebours (`general.start-countdown-seconds`, 60s par defaut). Pendant
ce temps, aucune capture n'est active. Annonces dans le chat + titre a
l'ecran toutes les 10 secondes, puis chaque seconde durant les 5 dernieres.
`/conquest stop` pendant le decompte l'annule proprement. Un redemarrage
serveur qui reprend un evenement deja en cours (StorageManager) ignore ce
decompte et reprend directement, pour ne pas faire revivre 60s d'attente a
une partie deja lancee.

## Correctif : /conquest stop ne remettait pas les scores a zero

`stop()` arretait bien les taches en cours mais ne reinitialisait jamais les
points/capteur/verrouillage des zones : une nouvelle manche demarree apres
repartait avec les scores de la precedente. Corrige : `stop()` (appele
manuellement ou automatiquement a la fin d'une victoire) remet desormais
toutes les zones a zero (`Zone#reset()`), pour qu'un nouveau `/conquest start`
reparte toujours sur une base totalement propre.

## Correctif : scoreboard qui affichait les points de toutes les factions

`ScoreboardManager` affichait, sur chaque zone, le score de la faction EN TETE
(la mieux placee), visible par tout le monde peu importe sa propre faction.
Corrige : chaque joueur ne voit desormais QUE les points de SA PROPRE faction
sur chaque zone (le total en bas de scoreboard etait deja correct, seule
l'affichage par zone fuitait l'info des autres factions).

## Correctif : alignement des zones sur les blocs entiers

`/conquest setpos1`/`setpos2` enregistraient auparavant la position EXACTE du
joueur (avec les decimales), pas le bloc entier. Consequence : la limite de la
zone tombait parfois au milieu d'un bloc de bordure plutot qu'a son bord, et
un joueur qui marchait sur ce dernier bloc pouvait traverser cette limite
invisible sans meme changer de bloc visuellement — declenchant une sortie de
zone (et donc une perte de capture) a tort. C'est corrige : les positions sont
maintenant arrondies au bloc entier, et le test d'appartenance a une zone
compare des coordonnees de bloc (inclusives), pas des coordonnees exactes.

**Important** : si tu avais deja defini tes zones 5x5 avant ce correctif,
refais `/conquest setpos1 <nom>` et `/conquest setpos2 <nom>` sur chacune
pour qu'elles soient resauvegardees avec des bornes propres.

## Ecarts assumes par rapport a la demande initiale (a lire)Je prefere etre transparent sur deux points plutot que de faire semblant que
tout colle a 100% a la liste de classes demandee :

1. **Pas de `ConfigManager` ni de `CaptureTask` en classes separees.**
   `plugin.getConfig()` (l'API standard Bukkit) est utilise directement partout
   plutot qu'enveloppe dans une classe wrapper qui n'aurait rien fait de plus.
   La logique de `CaptureTask` a ete integree directement dans
   `CaptureManager` (une seule tache planifiee qui parcourt les zones) plutot
   que dans une classe a part : ca evite une indirection inutile pour une
   boucle aussi simple. Si tu tiens absolument a la separation exacte des
   classes demandees, dis-le moi et je decoupe.

2. **Pas de vraie "BossBar" au sens 1.9+.** L'API `org.bukkit.boss.BossBar`
   n'existe tout simplement PAS dans Bukkit 1.8 (ajoutee en 1.9). Une "fausse"
   boss bar via entite (Wither/EnderDragon invisible) necessiterait de
   desactiver son IA et son animation de spawn, ce qui n'est pas exposable par
   l'API Bukkit 1.8 pure (`LivingEntity#setAI` n'existe pas non plus avant
   1.9) — seul du NMS profond et fragile le permettrait, ce qui casserait
   justement la compatibilite multi-forks que tu demandes. `BossBarManager`
   fournit donc l'equivalent fonctionnel le plus fiable sur cette version :
   une ligne de statut permanente (total de points) via le meme mecanisme que
   l'ActionBar, qui cede la priorite d'affichage a la capture en cours quand
   le joueur capture activement une zone. Si votre serveur tourne aussi un
   plugin comme BarAPI ou un autre wrapper boss-bar 1.8, je peux brancher
   Conquest dessus a la place — dis-le moi.

Tout le reste (zones, capture, points, penalites, victoire, scoreboard,
actionbar, titles, particules, sons, hologrammes, sauvegarde/reprise,
commandes, permissions) est implemente integralement.

## Architecture

```
fr.conquest/
├── ConquestPlugin.java              (classe principale, cable tous les managers)
├── model/
│   ├── Zone.java                    (etat d'une zone : bornes, points, capteur...)
│   └── ConquestState.java
├── integration/
│   └── FactionHook.java             (detection universelle Factions, repli solo)
├── managers/
│   ├── ConquestManager.java         (cycle de vie, totaux, victoire, penalite de mort)
│   ├── ZoneManager.java             (creation/suppression/positions des zones)
│   ├── CaptureManager.java          (timer de capture, points, verrouillage)
│   ├── StorageManager.java          (persistance data.yml, reprise apres redemarrage)
│   ├── MessageManager.java          (messages configurables + placeholders)
│   ├── ScoreboardManager.java
│   ├── BossBarManager.java          (voir "ecarts assumes" ci-dessus)
│   ├── ActionBarManager.java        (paquet NMS 1.8, seul moyen d'afficher une action bar)
│   ├── HologramManager.java         (integration optionnelle HolographicDisplays)
│   ├── ParticleManager.java
│   ├── CommandManager.java
│   └── ListenerManager.java
├── commands/
│   └── ConquestCommand.java
├── listeners/
│   ├── JoinListener.java
│   ├── QuitListener.java
│   ├── MoveListener.java
│   ├── DeathListener.java
│   ├── TeleportListener.java
│   └── RespawnListener.java
└── utils/
    └── NmsUtil.java                 (detection de version + helpers de reflexion)
```

## Commandes et permissions

```
/conquest start                 conquest.start (ou .admin)
/conquest stop                  conquest.stop (ou .admin)
/conquest reload                conquest.reload (ou .admin)
/conquest createzone <nom>      conquest.admin
/conquest deletezone <nom>      conquest.admin
/conquest setpos1 <nom>         conquest.admin
/conquest setpos2 <nom>         conquest.admin
/conquest info                  ouvert a tous
/conquest points                ouvert a tous
/conquest debug                 conquest.admin
```

## Fonctionnement resume

- Un admin cree des zones (`/conquest createzone`) et definit leurs deux coins
  (`/conquest setpos1`/`setpos2`), ou modifie directement `config.yml`.
- `/conquest start` lance l'evenement. Des lors, un joueur qui entre dans une
  zone libre en devient automatiquement le capteur ; s'il y reste 10 secondes
  (configurable), sa faction gagne 1 point sur cette zone (plafond 25 par
  defaut, puis la zone se verrouille).
- Mort/deconnexion/teleportation liberent immediatement la capture ; un
  nouveau capteur est choisi au hasard parmi les joueurs restes dans la zone.
- A la mort, la faction perd 10% (configurable) de son total, retire
  aleatoirement parmi les zones ou elle a des points (jamais sous zero).
- Premiere faction a 100 points (configurable) : victoire, feux d'artifice,
  commandes de recompense configurables, arret automatique.
- Toute la progression est sauvegardee en continu dans `data.yml` : un
  redemarrage serveur en pleine partie reprend exactement ou elle en etait.

## Integration Factions (optionnelle, universelle)

`FactionHook` detecte par reflexion, sans dependance de compilation, la
lignee MassiveCraft classique (Factions/FactionsUUID/SaberFactions/FactionsX
et forks proches) et PlaceholderAPI en secours. **Si aucun plugin Factions
n'est detecte, chaque joueur est traite comme sa propre faction** (son UUID
sert d'identifiant) : l'evenement continue de fonctionner normalement, comme
demande, sans jamais planter ni bloquer faute de Core PvP Faction installe.
