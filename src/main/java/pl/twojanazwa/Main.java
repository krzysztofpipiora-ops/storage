package pl.twojanazwa;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Main extends JavaPlugin implements Listener, CommandExecutor {

    private final Set<Location> supplyDropLocations = new HashSet<>();
    private final Map<Location, Integer> openingProgress = new HashMap<>();
    private final Map<UUID, Map<Material, Integer>> storage = new HashMap<>();
    private int lastSpawnHour = -1;
    private File storageFile;
    private FileConfiguration storageConfig;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("zrzut").setExecutor(this);
        getCommand("schowek").setExecutor(this);
        
        loadStorageData();

        new BukkitRunnable() {
            @Override
            public void run() {
                checkSchedule();
                for (Player p : Bukkit.getOnlinePlayers()) checkLimits(p);
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    @Override
    public void onDisable() {
        saveStorageData();
    }

    // --- SYSTEM SCHOWKA I LIMITÓW ---
    private void checkLimits(Player p) {
        storage.putIfAbsent(p.getUniqueId(), new HashMap<>());
        Map<Material, Integer> s = storage.get(p.getUniqueId());
        manageItem(p, Material.ENCHANTED_GOLDEN_APPLE, 2, s);
        manageItem(p, Material.GOLDEN_APPLE, 8, s);
        manageItem(p, Material.ENDER_PEARL, 3, s);
    }

    private void manageItem(Player p, Material m, int lim, Map<Material, Integer> s) {
        int count = 0;
        for (ItemStack is : p.getInventory().getContents()) {
            if (is != null && is.getType() == m) count += is.getAmount();
        }
        if (count > lim) {
            int over = count - lim;
            p.getInventory().removeItem(new ItemStack(m, over));
            s.put(m, s.getOrDefault(m, 0) + over);
            p.sendMessage("§6§lSchowek: §7Przeniesiono nadmiar (§e" + over + "§7) do depozytu!");
        }
    }

    // --- SYSTEM ZRZUTÓW ---
    private void checkSchedule() {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY);
        if (h >= 18 && h <= 22 && c.get(Calendar.MINUTE) == 0 && h != lastSpawnHour) {
            spawnSupplyDrop();
            lastSpawnHour = h;
        }
    }

    private void spawnSupplyDrop() {
        World w = Bukkit.getWorld("world");
        if (w == null) return;
        Random r = new Random();
        int x = r.nextInt(2001) - 1000;
        int z = r.nextInt(2001) - 1000;
        int y = w.getHighestBlockYAt(x, z);
        Location l = new Location(w, x, y + 1, z);
        l.getBlock().setType(Material.CHEST);
        supplyDropLocations.add(l);
        w.strikeLightningEffect(l);
        Bukkit.broadcastMessage("§6§l[ZRZUT] §fPojawił się na: §eX:" + x + " Z:" + z);
    }

    // --- OBSŁUGA KOMEND ---
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        if (cmd.getName().equalsIgnoreCase("schowek")) {
            openStorageMenu(p);
        } else if (cmd.getName().equalsIgnoreCase("zrzut") && p.hasPermission("zrzut.admin")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("spawn")) {
                spawnSupplyDrop();
                p.sendMessage("§aZespawnowano zrzut!");
            }
        }
        return true;
    }

    public void openStorageMenu(Player p) {
        Inventory gui = Bukkit.createInventory(null, 27, "§8§lWirtualny Schowek");
        Map<Material, Integer> s = storage.getOrDefault(p.getUniqueId(), new HashMap<>());
        gui.setItem(11, createGuiItem(Material.ENCHANTED_GOLDEN_APPLE, "§6KOXY", s.getOrDefault(Material.ENCHANTED_GOLDEN_APPLE, 0)));
        gui.setItem(13, createGuiItem(Material.GOLDEN_APPLE, "§eREFILE", s.getOrDefault(Material.GOLDEN_APPLE, 0)));
        gui.setItem(15, createGuiItem(Material.ENDER_PEARL, "§dPERŁY", s.getOrDefault(Material.ENDER_PEARL, 0)));
        p.openInventory(gui);
    }

    private ItemStack createGuiItem(Material m, String n, int a) {
        ItemStack i = new ItemStack(m);
        ItemMeta mt = i.getItemMeta();
        mt.setDisplayName(n);
        mt.setLore(Arrays.asList("§7W schowku: §e" + a, "§aKliknij, aby wypłacić do limitu!"));
        i.setItemMeta(mt);
        return i;
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals("§8§lWirtualny Schowek")) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        withdraw((Player) e.getWhoClicked(), e.getCurrentItem().getType());
        openStorageMenu((Player) e.getWhoClicked());
    }

    private void withdraw(Player p, Material m) {
        Map<Material, Integer> s = storage.get(p.getUniqueId());
        int lim = (m == Material.ENCHANTED_GOLDEN_APPLE) ? 2 : (m == Material.GOLDEN_APPLE ? 8 : 3);
        int inInv = 0;
        for (ItemStack is : p.getInventory().getContents()) if (is != null && is.getType() == m) inInv += is.getAmount();
        if (inInv >= lim) return;
        int toWith = Math.min(lim - inInv, s.getOrDefault(m, 0));
        if (toWith <= 0) return;
        s.put(m, s.get(m) - toWith);
        p.getInventory().addItem(new ItemStack(m, toWith));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.CHEST || !supplyDropLocations.contains(b.getLocation())) return;
        e.setCancelled(true);
        if (openingProgress.containsKey(b.getLocation())) return;
        openingProgress.put(b.getLocation(), 0);
        startOpening(b.getLocation());
    }

    private void startOpening(Location l) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!openingProgress.containsKey(l)) { this.cancel(); return; }
                int prog = openingProgress.get(l);
                List<Player> near = new ArrayList<>();
                for (Entity e : l.getWorld().getNearbyEntities(l, 10, 10, 10)) if (e instanceof Player) near.add((Player) e);
                if (near.size() > 1) { near.forEach(p -> sendAction(p, "§c§lWALKA! §7(Pauza)")); return; }
                if (near.isEmpty()) { openingProgress.remove(l); this.cancel(); return; }
                if (prog >= 300) { finish(l); openingProgress.remove(l); supplyDropLocations.remove(l); this.cancel(); return; }
                openingProgress.put(l, prog + 1);
                near.forEach(p -> sendAction(p, "§eOtwieranie: §6" + (prog * 100 / 300) + "%"));
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void sendAction(Player p, String m) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(m));
    }

    private void finish(Location l) {
        Chest c = (Chest) l.getBlock().getState();
        c.getInventory().addItem(new ItemStack(Material.DIAMOND, 5), new ItemStack(Material.NETHERITE_SCRAP, 1));
        Bukkit.broadcastMessage("§6§l[ZRZUT] §aZostał otwarty!");
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) { if (isNear(e.getBlock().getLocation())) e.setCancelled(true); }
    @EventHandler
    public void onPlace(BlockPlaceEvent e) { if (isNear(e.getBlock().getLocation())) e.setCancelled(true); }
    private boolean isNear(Location l) {
        for (Location d : supplyDropLocations) if (d.getWorld().equals(l.getWorld()) && d.distance(l) <= 10) return true;
        return false;
    }

    // --- ZAPIS I ODCZYT ---
    private void loadStorageData() {
        storageFile = new File(getDataFolder(), "storage.yml");
        if (!storageFile.exists()) saveResource("storage.yml", false);
        storageConfig = YamlConfiguration.loadConfiguration(storageFile);
        if (storageConfig.getConfigurationSection("data") == null) return;
        for (String uuidStr : storageConfig.getConfigurationSection("data").getKeys(false)) {
            UUID uuid = UUID.fromString(uuidStr);
            Map<Material, Integer> items = new HashMap<>();
            for (String matStr : storageConfig.getConfigurationSection("data." + uuidStr).getKeys(false)) {
                items.put(Material.valueOf(matStr), storageConfig.getInt("data." + uuidStr + "." + matStr));
            }
            storage.put(uuid, items);
        }
    }

    private void saveStorageData() {
        storageConfig.set("data", null);
        for (Map.Entry<UUID, Map<Material, Integer>> entry : storage.entrySet()) {
            for (Map.Entry<Material, Integer> item : entry.getValue().entrySet()) {
                storageConfig.set("data." + entry.getKey().toString() + "." + item.getKey().name(), item.getValue());
            }
        }
        try { storageConfig.save(storageFile); } catch (IOException ignored) {}
    }
}
