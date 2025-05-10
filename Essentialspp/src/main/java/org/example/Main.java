// This is a PaperMC Minecraft plugin main class.
package org.example;

// Paper API is fully compatible with Bukkit API for this use case.

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class Main extends JavaPlugin {
    // This plugin is built for Paper (PaperMC).

    private final HashMap<UUID, Integer> balances = new HashMap<>();
    private final Map<UUID, Long> tempBans = new HashMap<>();
    private final Map<UUID, String> banReasons = new HashMap<>();
    private final Map<UUID, Integer> warns = new HashMap<>();
    private final Set<UUID> godMode = new HashSet<>();
    private final Set<UUID> flyMode = new HashSet<>();
    private final Set<UUID> vanishMode = new HashSet<>();
    private final Map<UUID, Location> tpaRequests = new HashMap<>();
    private final Map<UUID, org.bukkit.permissions.PermissionAttachment> adminPerms = new HashMap<>();
    private Location spawnLocation = null;
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Map<UUID, Long> jailedPlayers = new HashMap<>();
    private final Map<UUID, Location> jailLocations = new HashMap<>();
    private Location jailLocation = null;
    private final Map<UUID, Map<String, Location>> homes = new HashMap<>();
    private final Map<UUID, Location> lastLocations = new HashMap<>();
    private boolean lockdown = false;
    private long lockdownEnd = 0;
    private boolean maintenance = false;
    private final Set<String> whitelist = new HashSet<>();
    private final Map<UUID, String> chatColors = new HashMap<>();
    private final List<String> serverRules = new ArrayList<>();
    private String motd = "Welcome to the server!";
    private final Set<UUID> silencedPlayers = new HashSet<>();
    private final Set<UUID> spyChat = new HashSet<>();
    private final Set<UUID> invisiblePlayers = new HashSet<>();
    private boolean pvpEnabled = true;

    @Override
    public void onEnable() {
        getLogger().info("Essentials++ enabled!");
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                Player p = event.getPlayer();
                if (spawnLocation != null && !p.hasPlayedBefore()) {
                    p.teleport(spawnLocation);
                }
                // Tempban check
                if (tempBans.containsKey(p.getUniqueId())) {
                    long until = tempBans.get(p.getUniqueId());
                    if (System.currentTimeMillis() < until) {
                        String reason = banReasons.getOrDefault(p.getUniqueId(), "No reason");
                        p.kickPlayer("§cYou are tempbanned!\n§7Reason: " + reason + "\n§7Unban: " + new Date(until));
                    } else {
                        tempBans.remove(p.getUniqueId());
                        banReasons.remove(p.getUniqueId());
                    }
                }
                if (lockdown && System.currentTimeMillis() < lockdownEnd && !p.hasPermission("essentialspp.admin")) {
                    p.kickPlayer("§cServer is in lockdown.");
                }
                if (maintenance && !p.hasPermission("essentialspp.admin")) {
                    p.kickPlayer("§cServer is in maintenance mode.");
                }
                if (!whitelist.isEmpty() && !whitelist.contains(p.getName()) && !p.hasPermission("essentialspp.admin")) {
                    p.kickPlayer("§cYou are not whitelisted.");
                }
                if (invisiblePlayers.contains(p.getUniqueId())) {
                    for (Player pl : Bukkit.getOnlinePlayers()) {
                        pl.hidePlayer(Main.this, p);
                    }
                    p.setInvisible(true);
                    p.setPlayerListName("");
                }
            }
            @org.bukkit.event.EventHandler
            public void onEntityDamage(org.bukkit.event.entity.EntityDamageEvent event) {
                if (event.getEntity() instanceof Player) {
                    Player p = (Player) event.getEntity();
                    if (godMode.contains(p.getUniqueId())) event.setCancelled(true);
                }
            }
            @org.bukkit.event.EventHandler
            public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
                tpaRequests.remove(event.getPlayer().getUniqueId());
                invisiblePlayers.remove(event.getPlayer().getUniqueId());
            }
            @org.bukkit.event.EventHandler
            public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
                // ...можна додати додаткову логіку...
            }
            @org.bukkit.event.EventHandler
            public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
                if (frozenPlayers.contains(event.getPlayer().getUniqueId())) {
                    event.setTo(event.getFrom());
                }
                if (jailedPlayers.containsKey(event.getPlayer().getUniqueId())) {
                    Location jailLoc = jailLocation;
                    if (jailLoc != null && event.getTo() != null && event.getTo().distance(jailLoc) > 3) {
                        event.setTo(jailLoc);
                    }
                }
            }
            @org.bukkit.event.EventHandler
            public void onPlayerTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
                lastLocations.put(event.getPlayer().getUniqueId(), event.getFrom());
            }
            @org.bukkit.event.EventHandler
            public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
                lastLocations.put(event.getEntity().getUniqueId(), event.getEntity().getLocation());
            }
            @org.bukkit.event.EventHandler
            public void onAsyncPlayerChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
                String color = chatColors.get(event.getPlayer().getUniqueId());
                if (color != null) {
                    event.setMessage("§" + color + event.getMessage());
                }
                if (silencedPlayers.contains(event.getPlayer().getUniqueId())) {
                    event.setCancelled(true);
                }
            }
            @org.bukkit.event.EventHandler
            public void onPlayerRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
                if (spawnLocation != null) {
                    event.setRespawnLocation(spawnLocation);
                }
            }
            @org.bukkit.event.EventHandler
            public void onEntityDamageByEntity(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
                if (!pvpEnabled && event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
                    event.setCancelled(true);
                }
            }
            @org.bukkit.event.EventHandler
            public void onPlayerCommandPreprocess(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
                if (spyChat.isEmpty()) return;
                String msg = event.getMessage();
                if (msg.startsWith("/msg ") || msg.startsWith("/tell ") || msg.startsWith("/w ")) {
                    for (UUID uuid : spyChat) {
                        Player admin = Bukkit.getPlayer(uuid);
                        if (admin != null && admin.isOnline()) {
                            admin.sendMessage("§7[SpyChat] " + event.getPlayer().getName() + ": " + msg);
                        }
                    }
                }
            }
        }, this);

        if (serverRules.isEmpty()) {
            serverRules.add("§e1. Не використовувати чіти.");
            serverRules.add("§e2. Не ображати інших гравців.");
            serverRules.add("§e3. Не спамити у чаті.");
            serverRules.add("§e4. Не руйнувати чужі будівлі.");
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Essentials++ disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("money")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                UUID uuid = player.getUniqueId();
                int balance = balances.computeIfAbsent(uuid, k -> 500);
                player.sendMessage("§aYour balance: §6$" + balance);
            } else {
                sender.sendMessage("§cOnly players can use this command.");
            }
            return true;
        }
        if (command.getName().equalsIgnoreCase("pay")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can use this command.");
                return true;
            }
            if (args.length != 2) {
                sender.sendMessage("§cUsage: /pay <player> <amount>");
                return true;
            }
            Player from = (Player) sender;
            Player to = Bukkit.getPlayerExact(args[0]);
            if (to == null || !to.isOnline()) {
                from.sendMessage("§cPlayer not found or not online.");
                return true;
            }
            if (to.getUniqueId().equals(from.getUniqueId())) {
                from.sendMessage("§cYou cannot pay yourself.");
                return true;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                from.sendMessage("§cAmount must be a number.");
                return true;
            }
            if (amount <= 0) {
                from.sendMessage("§cAmount must be positive.");
                return true;
            }
            UUID fromId = from.getUniqueId();
            UUID toId = to.getUniqueId();
            int fromBalance = balances.computeIfAbsent(fromId, k -> 500);
            int toBalance = balances.computeIfAbsent(toId, k -> 500);
            if (fromBalance < amount) {
                from.sendMessage("§cYou don't have enough money.");
                return true;
            }
            balances.put(fromId, fromBalance - amount);
            balances.put(toId, toBalance + amount);
            from.sendMessage("§aYou paid §6$" + amount + "§a to §e" + to.getName() + "§a.");
            to.sendMessage("§aYou received §6$" + amount + "§a from §e" + from.getName() + "§a.");
            return true;
        }

        String cmd = command.getName().toLowerCase();

        if (cmd.equals("tempban")) {
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /tempban <player> <time(m/h/d)> <reason>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
            long duration = parseDuration(args[1]);
            if (duration <= 0) {
                sender.sendMessage("§cInvalid time format.");
                return true;
            }
            String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            tempBans.put(target.getUniqueId(), System.currentTimeMillis() + duration);
            banReasons.put(target.getUniqueId(), reason);
            target.kickPlayer("§cYou are tempbanned!\n§7Reason: " + reason + "\n§7Unban: " + new Date(System.currentTimeMillis() + duration));
            sender.sendMessage("§aPlayer tempbanned.");
            return true;
        }

        if (cmd.equals("warn")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /warn <player> <reason>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
            String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            warns.put(target.getUniqueId(), warns.getOrDefault(target.getUniqueId(), 0) + 1);
            target.sendMessage("§cYou have been warned! Reason: " + reason);
            sender.sendMessage("§aWarn issued.");
            return true;
        }

        if (cmd.equals("clearchat")) {
            for (int i = 0; i < 100; i++) {
                for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage("");
            }
            Bukkit.broadcastMessage("§7Chat has been cleared.");
            return true;
        }

        if (cmd.equals("setspawn")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can use this command.");
                return true;
            }
            spawnLocation = ((Player) sender).getLocation();
            sender.sendMessage("§aSpawn point set.");
            return true;
        }

        if (cmd.equals("tp")) {
            if (args.length == 2) {
                Player p1 = Bukkit.getPlayerExact(args[0]);
                Player p2 = Bukkit.getPlayerExact(args[1]);
                if (p1 == null || p2 == null) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                if (sender.hasPermission("essentialspp.admin")) {
                    p1.teleport(p2);
                    sender.sendMessage("§aTeleported " + p1.getName() + " to " + p2.getName());
                } else {
                    sender.sendMessage("§cNo permission.");
                }
                return true;
            } else if (args.length == 1 && sender instanceof Player) {
                Player from = (Player) sender;
                Player to = Bukkit.getPlayerExact(args[0]);
                if (to == null) {
                    from.sendMessage("§cPlayer not found.");
                    return true;
                }
                if (from.hasPermission("essentialspp.admin")) {
                    from.teleport(to);
                    from.sendMessage("§aTeleported to " + to.getName());
                } else {
                    // send tpa request
                    tpaRequests.put(to.getUniqueId(), from.getLocation());
                    to.sendMessage("§e" + from.getName() + " wants to teleport to you. Type /tpa to accept.");
                    from.sendMessage("§aTeleport request sent to " + to.getName());
                }
                return true;
            }
            sender.sendMessage("§cUsage: /tp <player> [target]");
            return true;
        }

        if (cmd.equals("tpa")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can use this command.");
                return true;
            }
            Player to = (Player) sender;
            Location loc = tpaRequests.remove(to.getUniqueId());
            if (loc == null) {
                to.sendMessage("§cNo teleport requests.");
                return true;
            }
            to.teleport(loc);
            to.sendMessage("§aTeleport request accepted.");
            return true;
        }

        if (cmd.equals("tphere")) {
            if (args.length != 1 || !(sender instanceof Player)) {
                sender.sendMessage("§cUsage: /tphere <player>");
                return true;
            }
            Player from = (Player) sender;
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                from.sendMessage("§cPlayer not found.");
                return true;
            }
            target.teleport(from);
            from.sendMessage("§aTeleported " + target.getName() + " to you.");
            return true;
        }

        if (cmd.equals("tpall")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can use this command.");
                return true;
            }
            Player from = (Player) sender;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(from)) p.teleport(from);
            }
            from.sendMessage("§aAll players teleported to you.");
            return true;
        }

        if (cmd.equals("heal")) {
            if (args.length == 0 && sender instanceof Player) {
                Player p = (Player) sender;
                p.setHealth(p.getMaxHealth());
                p.setFoodLevel(20);
                p.sendMessage("§aYou have been healed!");
                return true;
            } else if (args.length == 1) {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                target.setHealth(target.getMaxHealth());
                target.setFoodLevel(20);
                target.sendMessage("§aYou have been healed!");
                sender.sendMessage("§aHealed " + target.getName());
                return true;
            }
            sender.sendMessage("§cUsage: /heal [player]");
            return true;
        }

        if (cmd.equals("feed")) {
            if (args.length == 0 && sender instanceof Player) {
                Player p = (Player) sender;
                p.setFoodLevel(20);
                p.sendMessage("§aYou have been fed!");
                return true;
            } else if (args.length == 1) {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                target.setFoodLevel(20);
                target.sendMessage("§aYou have been fed!");
                sender.sendMessage("§aFed " + target.getName());
                return true;
            }
            sender.sendMessage("§cUsage: /feed [player]");
            return true;
        }

        if (cmd.equals("god")) {
            if (args.length == 0 && sender instanceof Player) {
                Player p = (Player) sender;
                toggleSet(godMode, p);
                p.sendMessage("§aGod mode: " + (godMode.contains(p.getUniqueId()) ? "ON" : "OFF"));
                return true;
            } else if (args.length == 1) {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                toggleSet(godMode, target);
                target.sendMessage("§aGod mode: " + (godMode.contains(target.getUniqueId()) ? "ON" : "OFF"));
                sender.sendMessage("§aGod mode toggled for " + target.getName());
                return true;
            }
            sender.sendMessage("§cUsage: /god [player]");
            return true;
        }

        if (cmd.equals("fly")) {
            if (args.length == 0 && sender instanceof Player) {
                Player p = (Player) sender;
                toggleSet(flyMode, p);
                p.setAllowFlight(flyMode.contains(p.getUniqueId()));
                p.sendMessage("§aFly mode: " + (flyMode.contains(p.getUniqueId()) ? "ON" : "OFF"));
                return true;
            } else if (args.length == 1) {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                toggleSet(flyMode, target);
                target.setAllowFlight(flyMode.contains(target.getUniqueId()));
                target.sendMessage("§aFly mode: " + (flyMode.contains(target.getUniqueId()) ? "ON" : "OFF"));
                sender.sendMessage("§aFly mode toggled for " + target.getName());
                return true;
            }
            sender.sendMessage("§cUsage: /fly [player]");
            return true;
        }

        if (cmd.equals("speed")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /speed <player> <speed>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
            float speed;
            try {
                speed = Float.parseFloat(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cSpeed must be a number.");
                return true;
            }
            if (speed < 0.1f) speed = 0.1f;
            if (speed > 1.0f) speed = 1.0f;
            if (target.isFlying()) {
                target.setFlySpeed(speed);
            } else {
                target.setWalkSpeed(speed);
            }
            sender.sendMessage("§aSpeed set for " + target.getName());
            return true;
        }

        if (cmd.equals("invsee")) {
            if (args.length != 1 || !(sender instanceof Player)) {
                sender.sendMessage("§cUsage: /invsee <player>");
                return true;
            }
            Player viewer = (Player) sender;
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                viewer.sendMessage("§cPlayer not found.");
                return true;
            }
            viewer.openInventory(target.getInventory());
            viewer.sendMessage("§aOpened inventory of " + target.getName());
            return true;
        }

        if (cmd.equals("vanish")) {
            if (args.length == 0 && sender instanceof Player) {
                Player p = (Player) sender;
                toggleVanish(p);
                p.sendMessage("§aVanish: " + (vanishMode.contains(p.getUniqueId()) ? "ON" : "OFF"));
                return true;
            } else if (args.length == 1) {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                toggleVanish(target);
                target.sendMessage("§aVanish: " + (vanishMode.contains(target.getUniqueId()) ? "ON" : "OFF"));
                sender.sendMessage("§aVanish toggled for " + target.getName());
                return true;
            }
            sender.sendMessage("§cUsage: /vanish [player]");
            return true;
        }

        if (command.getName().equalsIgnoreCase("setessentialsppadmin")) {
            if (args.length != 1) {
                sender.sendMessage("§cUsage: /setEssentialsppAdmin <player>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
            // Remove old attachment if exists
            if (adminPerms.containsKey(target.getUniqueId())) {
                target.removeAttachment(adminPerms.get(target.getUniqueId()));
            }
            org.bukkit.permissions.PermissionAttachment attachment = target.addAttachment(this);
            attachment.setPermission("essentialspp.admin", true);
            adminPerms.put(target.getUniqueId(), attachment);
            sender.sendMessage("§aGave essentialspp.admin to " + target.getName());
            target.sendMessage("§aYou have been granted essentialspp.admin permission!");
            return true;
        }

        if (cmd.equals("sudo")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /sudo <player> <command>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
            String cmdStr = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            target.performCommand(cmdStr);
            sender.sendMessage("§aForced " + target.getName() + " to run: /" + cmdStr);
            return true;
        }

        if (cmd.equals("freeze")) {
            if (args.length != 1) {
                sender.sendMessage("§cUsage: /freeze <player>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
            if (frozenPlayers.contains(target.getUniqueId())) {
                frozenPlayers.remove(target.getUniqueId());
                target.sendMessage("§aYou have been unfrozen.");
                sender.sendMessage("§aUnfroze " + target.getName());
            } else {
                frozenPlayers.add(target.getUniqueId());
                target.sendMessage("§cYou have been frozen.");
                sender.sendMessage("§aFroze " + target.getName());
            }
            return true;
        }

        if (cmd.equals("jail")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /jail <player> <time(m/h/d)>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
            if (jailLocation == null) {
                jailLocation = (sender instanceof Player) ? ((Player) sender).getLocation() : target.getLocation();
            }
            long duration = parseDuration(args[1]);
            if (duration <= 0) {
                sender.sendMessage("§cInvalid time format.");
                return true;
            }
            jailedPlayers.put(target.getUniqueId(), System.currentTimeMillis() + duration);
            jailLocations.put(target.getUniqueId(), target.getLocation());
            target.teleport(jailLocation);
            target.sendMessage("§cYou have been jailed.");
            sender.sendMessage("§aJailed " + target.getName());
            return true;
        }

        if (cmd.equals("unjail")) {
            if (args.length != 1) {
                sender.sendMessage("§cUsage: /unjail <player>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
            jailedPlayers.remove(target.getUniqueId());
            Location loc = jailLocations.remove(target.getUniqueId());
            if (loc != null) target.teleport(loc);
            target.sendMessage("§aYou have been released from jail.");
            sender.sendMessage("§aUnjailed " + target.getName());
            return true;
        }

        if (cmd.equals("broadcast")) {
            if (args.length < 1) {
                sender.sendMessage("§cUsage: /broadcast <message>");
                return true;
            }
            String msg = String.join(" ", args);
            Bukkit.broadcastMessage("§6[Broadcast] §f" + msg);
            return true;
        }

        if (cmd.equals("whois")) {
            if (args.length != 1) {
                sender.sendMessage("§cUsage: /whois <player>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
            sender.sendMessage("§aName: §f" + target.getName());
            sender.sendMessage("§aUUID: §f" + target.getUniqueId());
            sender.sendMessage("§aIP: §f" + Objects.requireNonNull(target.getAddress()).getAddress().getHostAddress());
            sender.sendMessage("§aLocation: §f" + target.getLocation().toVector().toString());
            return true;
        }

        if (cmd.equals("sethome")) {
            if (!(sender instanceof Player) || args.length != 1) {
                sender.sendMessage("§cUsage: /sethome <name>");
                return true;
            }
            Player p = (Player) sender;
            homes.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(args[0], p.getLocation());
            p.sendMessage("§aHome '" + args[0] + "' set.");
            return true;
        }

        if (cmd.equals("home")) {
            if (!(sender instanceof Player) || args.length != 1) {
                sender.sendMessage("§cUsage: /home <name>");
                return true;
            }
            Player p = (Player) sender;
            Map<String, Location> playerHomes = homes.get(p.getUniqueId());
            if (playerHomes == null || !playerHomes.containsKey(args[0])) {
                p.sendMessage("§cHome not found.");
                return true;
            }
            lastLocations.put(p.getUniqueId(), p.getLocation());
            p.teleport(playerHomes.get(args[0]));
            p.sendMessage("§aTeleported to home '" + args[0] + "'.");
            return true;
        }

        if (cmd.equals("delhome")) {
            if (!(sender instanceof Player) || args.length != 1) {
                sender.sendMessage("§cUsage: /delhome <name>");
                return true;
            }
            Player p = (Player) sender;
            Map<String, Location> playerHomes = homes.get(p.getUniqueId());
            if (playerHomes == null || !playerHomes.containsKey(args[0])) {
                p.sendMessage("§cHome not found.");
                return true;
            }
            playerHomes.remove(args[0]);
            p.sendMessage("§aHome '" + args[0] + "' deleted.");
            return true;
        }

        if (cmd.equals("back")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can use this command.");
                return true;
            }
            Player p = (Player) sender;
            Location loc = lastLocations.get(p.getUniqueId());
            if (loc == null) {
                p.sendMessage("§cNo previous location.");
                return true;
            }
            p.teleport(loc);
            p.sendMessage("§aTeleported back.");
            return true;
        }

        if (cmd.equals("weather")) {
            if (args.length != 1) {
                sender.sendMessage("§cUsage: /weather <clear|rain|thunder>");
                return true;
            }
            String type = args[0].toLowerCase();
            World w = (sender instanceof Player) ? ((Player) sender).getWorld() : Bukkit.getWorlds().get(0);
            switch (type) {
                case "clear": w.setStorm(false); w.setThundering(false); break;
                case "rain": w.setStorm(true); w.setThundering(false); break;
                case "thunder": w.setStorm(true); w.setThundering(true); break;
                default: sender.sendMessage("§cUnknown weather type."); return true;
            }
            sender.sendMessage("§aWeather set to " + type + ".");
            return true;
        }

        if (cmd.equals("time")) {
            if (args.length != 1) {
                sender.sendMessage("§cUsage: /time <day|night|number>");
                return true;
            }
            World w = (sender instanceof Player) ? ((Player) sender).getWorld() : Bukkit.getWorlds().get(0);
            if (args[0].equalsIgnoreCase("day")) w.setTime(1000);
            else if (args[0].equalsIgnoreCase("night")) w.setTime(13000);
            else {
                try { w.setTime(Long.parseLong(args[0])); }
                catch (NumberFormatException e) { sender.sendMessage("§cInvalid time."); return true; }
            }
            sender.sendMessage("§aTime set.");
            return true;
        }

        if (cmd.equals("lockdown")) {
            if (args.length != 1) {
                sender.sendMessage("§cUsage: /lockdown <time(m/h/d)>");
                return true;
            }
            long duration = parseDuration(args[0]);
            if (duration <= 0) {
                sender.sendMessage("§cInvalid time format.");
                return true;
            }
            lockdown = true;
            lockdownEnd = System.currentTimeMillis() + duration;
            Bukkit.broadcastMessage("§cServer is now in lockdown for " + args[0] + ".");
            return true;
        }

        if (cmd.equals("whitelist")) {
            if (args.length == 0) {
                sender.sendMessage("§cUsage: /whitelist <add|remove|list> [player]");
                return true;
            }
            if (args[0].equalsIgnoreCase("add") && args.length == 2) {
                whitelist.add(args[1]);
                sender.sendMessage("§aAdded " + args[1] + " to whitelist.");
                return true;
            }
            if (args[0].equalsIgnoreCase("remove") && args.length == 2) {
                whitelist.remove(args[1]);
                sender.sendMessage("§aRemoved " + args[1] + " from whitelist.");
                return true;
            }
            if (args[0].equalsIgnoreCase("list")) {
                sender.sendMessage("§aWhitelist: " + String.join(", ", whitelist));
                return true;
            }
            sender.sendMessage("§cUsage: /whitelist <add|remove|list> [player]");
            return true;
        }

        if (cmd.equals("report")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /report <player> <reason>");
                return true;
            }
            String player = args[0];
            String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("essentialspp.admin")) {
                    p.sendMessage("§c[REPORT] §e" + sender.getName() + " -> " + player + ": " + reason);
                }
            }
            sender.sendMessage("§aReport sent.");
            return true;
        }

        if (cmd.equals("maintenance")) {
            if (args.length != 1) {
                sender.sendMessage("§cUsage: /maintenance <on|off>");
                return true;
            }
            if (args[0].equalsIgnoreCase("on")) {
                maintenance = true;
                Bukkit.broadcastMessage("§cServer is now in maintenance mode.");
            } else if (args[0].equalsIgnoreCase("off")) {
                maintenance = false;
                Bukkit.broadcastMessage("§aMaintenance mode disabled.");
            } else {
                sender.sendMessage("§cUsage: /maintenance <on|off>");
            }
            return true;
        }

        if (cmd.equals("chatcolor")) {
            if (args.length != 2) {
                sender.sendMessage("§cUsage: /chatcolor <player> <color>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
            String color = args[1].toLowerCase();
            if (!color.matches("[0-9a-f]")) {
                sender.sendMessage("§cInvalid color. Use 0-9 or a-f.");
                return true;
            }
            chatColors.put(target.getUniqueId(), color);
            sender.sendMessage("§aSet chat color for " + target.getName());
            target.sendMessage("§aYour chat color is now §" + color + color);
            return true;
        }

        if (cmd.equals("rules")) {
            if (serverRules.isEmpty()) {
                sender.sendMessage("§cNo rules set.");
                return true;
            }
            for (String rule : serverRules) sender.sendMessage(rule);
            return true;
        }

        if (cmd.equals("setrule")) {
            if (args.length == 0) {
                sender.sendMessage("§cUsage: /setrule <rule text>");
                return true;
            }
            String rule = String.join(" ", args);
            serverRules.add("§e" + (serverRules.size() + 1) + ". " + rule);
            sender.sendMessage("§aRule added: " + rule);
            return true;
        }

        if (cmd.equals("backup")) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    World world = Bukkit.getWorlds().get(0);
                    String backupName = "backup_" + System.currentTimeMillis();
                    java.nio.file.Path src = world.getWorldFolder().toPath();
                    java.nio.file.Path dest = src.getParent().resolve(backupName);
                    copyFolder(src, dest);
                    sender.sendMessage("§aBackup created: " + backupName);
                } catch (Exception e) {
                    sender.sendMessage("§cBackup failed: " + e.getMessage());
                }
            });
            return true;
        }

        if (cmd.equals("setmotd")) {
            if (args.length == 0) {
                sender.sendMessage("§cUsage: /setmotd <text>");
                return true;
            }
            motd = String.join(" ", args);
            sender.sendMessage("§aMOTD set: " + motd);
            return true;
        }

        if (cmd.equals("silence")) {
            if (args.length != 1) {
                sender.sendMessage("§cUsage: /silence <player>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
            if (silencedPlayers.contains(target.getUniqueId())) {
                silencedPlayers.remove(target.getUniqueId());
                sender.sendMessage("§aUnsilenced " + target.getName());
            } else {
                silencedPlayers.add(target.getUniqueId());
                sender.sendMessage("§aSilenced " + target.getName());
            }
            return true;
        }

        if (cmd.equals("resetworld")) {
            Bukkit.broadcastMessage("§cWorld is resetting...");
            Bukkit.getScheduler().runTaskLater(this, () -> {
                World world = Bukkit.getWorlds().get(0);
                for (Player p : world.getPlayers()) {
                    p.kickPlayer("§cWorld is resetting...");
                }
                try {
                    java.nio.file.Path worldPath = world.getWorldFolder().toPath();
                    deleteFolder(worldPath);
                    Bukkit.unloadWorld(world, false);
                    Bukkit.createWorld(new org.bukkit.WorldCreator(world.getName()));
                    sender.sendMessage("§aWorld reset complete.");
                } catch (Exception e) {
                    sender.sendMessage("§cReset failed: " + e.getMessage());
                }
            }, 40L);
            return true;
        }

        if (cmd.equals("setborder")) {
            if (args.length != 1) {
                sender.sendMessage("§cUsage: /setborder <size>");
                return true;
            }
            int size;
            try {
                size = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid size.");
                return true;
            }
            World w = (sender instanceof Player) ? ((Player) sender).getWorld() : Bukkit.getWorlds().get(0);
            w.getWorldBorder().setSize(size);
            sender.sendMessage("§aWorld border set to " + size);
            return true;
        }

        if (cmd.equals("forcerespawn")) {
            if (args.length != 1) {
                sender.sendMessage("§cUsage: /forcerespawn <player>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
            target.spigot().respawn();
            if (spawnLocation != null) target.teleport(spawnLocation);
            sender.sendMessage("§aForced respawn for " + target.getName());
            return true;
        }

        if (cmd.equals("track")) {
            if (args.length != 1) {
                sender.sendMessage("§cUsage: /track <player>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
            Location loc = target.getLocation();
            sender.sendMessage("§a" + target.getName() + " is at X:" + loc.getBlockX() + " Y:" + loc.getBlockY() + " Z:" + loc.getBlockZ());
            return true;
        }

        if (cmd.equals("spychat")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can use this command.");
                return true;
            }
            Player p = (Player) sender;
            if (spyChat.contains(p.getUniqueId())) {
                spyChat.remove(p.getUniqueId());
                p.sendMessage("§aSpyChat disabled.");
            } else {
                spyChat.add(p.getUniqueId());
                p.sendMessage("§aSpyChat enabled.");
            }
            return true;
        }

        if (cmd.equals("killall")) {
            if (args.length != 1) {
                sender.sendMessage("§cUsage: /killall <mob>");
                return true;
            }
            String mob = args[0].toUpperCase();
            int count = 0;
            for (World w : Bukkit.getWorlds()) {
                for (org.bukkit.entity.Entity e : w.getEntities()) {
                    if (e.getType().toString().equalsIgnoreCase(mob) && !(e instanceof Player)) {
                        e.remove();
                        count++;
                    }
                }
            }
            sender.sendMessage("§aRemoved " + count + " entities of type " + mob);
            return true;
        }

        if (cmd.equals("restartserver")) {
            Bukkit.broadcastMessage("§cServer is restarting...");
            Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 40L);
            return true;
        }

        if (cmd.equals("randomitem")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can use this command.");
                return true;
            }
            Player p = (Player) sender;
            org.bukkit.Material[] mats = org.bukkit.Material.values();
            org.bukkit.Material mat = mats[new Random().nextInt(mats.length)];
            if (!mat.isItem()) {
                p.sendMessage("§cTry again.");
                return true;
            }
            p.getInventory().addItem(new org.bukkit.inventory.ItemStack(mat, 1));
            p.sendMessage("§aYou received: " + mat.name());
            return true;
        }

        if (cmd.equals("invisible")) {
            if (args.length != 1) {
                sender.sendMessage("§cUsage: /invisible <player>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
            if (invisiblePlayers.contains(target.getUniqueId())) {
                invisiblePlayers.remove(target.getUniqueId());
                for (Player p : Bukkit.getOnlinePlayers()) p.showPlayer(this, target);
                target.setInvisible(false);
                target.setPlayerListName(target.getName());
                sender.sendMessage("§a" + target.getName() + " is now visible.");
            } else {
                invisiblePlayers.add(target.getUniqueId());
                for (Player p : Bukkit.getOnlinePlayers()) p.hidePlayer(this, target);
                target.setInvisible(true);
                target.setPlayerListName("");
                sender.sendMessage("§a" + target.getName() + " is now invisible.");
            }
            return true;
        }

        if (cmd.equals("forcerain")) {
            World w = (sender instanceof Player) ? ((Player) sender).getWorld() : Bukkit.getWorlds().get(0);
            w.setStorm(true);
            w.setThundering(false);
            sender.sendMessage("§aWeather set to rain.");
            return true;
        }

        if (cmd.equals("disablepvp")) {
            pvpEnabled = false;
            Bukkit.broadcastMessage("§cPvP is now disabled.");
            return true;
        }

        if (cmd.equals("enablepvp")) {
            pvpEnabled = true;
            Bukkit.broadcastMessage("§aPvP is now enabled.");
            return true;
        }

        return false;
    }

    private void toggleSet(Set<UUID> set, Player p) {
        if (set.contains(p.getUniqueId())) set.remove(p.getUniqueId());
        else set.add(p.getUniqueId());
    }

    private void toggleVanish(Player p) {
        if (vanishMode.contains(p.getUniqueId())) {
            vanishMode.remove(p.getUniqueId());
            for (Player pl : Bukkit.getOnlinePlayers()) pl.showPlayer(this, p);
        } else {
            vanishMode.add(p.getUniqueId());
            for (Player pl : Bukkit.getOnlinePlayers()) if (!pl.equals(p)) pl.hidePlayer(this, p);
        }
    }

    private long parseDuration(String s) {
        try {
            if (s.endsWith("m")) return Integer.parseInt(s.replace("m", "")) * 60_000L;
            if (s.endsWith("h")) return Integer.parseInt(s.replace("h", "")) * 60 * 60_000L;
            if (s.endsWith("d")) return Integer.parseInt(s.replace("d", "")) * 24 * 60 * 60_000L;
            return Long.parseLong(s) * 1000L;
        } catch (Exception e) {
            return -1;
        }
    }

    private void copyFolder(java.nio.file.Path src, java.nio.file.Path dest) throws java.io.IOException {
        java.nio.file.Files.walk(src).forEach(source -> {
            try {
                java.nio.file.Path destination = dest.resolve(src.relativize(source));
                if (java.nio.file.Files.isDirectory(source)) {
                    if (!java.nio.file.Files.exists(destination)) {
                        java.nio.file.Files.createDirectory(destination);
                    }
                } else {
                    java.nio.file.Files.copy(source, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                // ignore
            }
        });
    }

    private void deleteFolder(java.nio.file.Path path) throws java.io.IOException {
        if (!java.nio.file.Files.exists(path)) return;
        java.nio.file.Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .forEach(p -> {
                try { java.nio.file.Files.delete(p); } catch (Exception ignored) {}
            });
    }
}

