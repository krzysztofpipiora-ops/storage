package pl.twojanazwa;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Main extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, Map<Material, Integer>> storage = new HashMap<>();
    private File storageFile;
    private FileConfiguration storageConfig;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("schowek") != null) {
            getCommand("schowek").setExecutor(this);
        }
        
        loadStorageData();

        // Sprawdzanie limitów co 1 sekundę
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    checkLimits(p);
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    @Override
    public void onDisable() {
        saveStorageData();
    }

    private void checkLimits(Player p) {
        UUID uuid = p.getUniqueId();
        storage.putIfAbsent(uuid, new HashMap<>());
        Map<Material, Integer> s = storage.get(uuid);

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
            p.sendMessage("§6§lSchowek: §7Nadmiar " + m.name() + " (§e" + over + "§7) trafia do depozytu!");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        openStorageMenu((Player) sender);
        return true;
    }

    public void openStorageMenu(Player p) {
        Inventory gui = Bukkit.createInventory(null, 27, "§8§lWirtualny Schowek");
        Map<Material, Integer> s = storage.getOrDefault(p.getUniqueId(), new HashMap<>());

        gui.setItem(11, createGuiItem(Material.ENCHANTED_GOLDEN_APPLE, "§6§lKOXY", s.getOrDefault(Material.ENCHANTED_GOLDEN_APPLE, 0)));
        gui.setItem(13, createGuiItem(Material.GOLDEN_APPLE, "§e§lREFILE", s.getOrDefault(Material.GOLDEN_APPLE, 0)));
        gui.setItem(15, createGuiItem(Material.ENDER_PEARL, "§d§lPERŁY", s.getOrDefault(Material.ENDER_PEARL, 0)));

        p.openInventory(gui);
    }

    private ItemStack createGuiItem(Material m, String n, int a) {
        ItemStack i = new ItemStack(m);
        ItemMeta mt = i.getItemMeta();
        if (mt != null) {
            mt.setDisplayName(n);
            mt.setLore(Arrays.asList("§7W schowku: §e" + a, "", "§aKliknij, aby wypłacić do limitu!"));
            i.setItemMeta(mt);
        }
        return i;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals("§8§lWirtualny Schowek")) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Player p = (Player) e.getWhoClicked();
        withdraw(p, e.getCurrentItem().getType());
        openStorageMenu(p);
    }

    private void withdraw(Player p, Material m) {
        Map<Material, Integer> s = storage.get(p.getUniqueId());
        if (s == null || s.getOrDefault(m, 0) <= 0) return;

        int lim = (m == Material.ENCHANTED_GOLDEN_APPLE) ? 2 : (m == Material.GOLDEN_APPLE ? 8 : 3);
        int inInv = 0;
        for (ItemStack is : p.getInventory().getContents()) {
            if (is != null && is.getType() == m) inInv += is.getAmount();
        }

        if (inInv >= lim) {
            p.sendMessage("§cMasz już limit w EQ!");
            return;
        }

        int toWith = Math.min(lim - inInv, s.get(m));
        s.put(m, s.get(m) - toWith);
        p.getInventory().addItem(new ItemStack(m, toWith));
        p.sendMessage("§aWypłacono §2" + toWith + " §asprzedmioty.");
    }

    private void loadStorageData() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        storageFile = new File(getDataFolder(), "storage.yml");
        if (!storageFile.exists()) {
            try { storageFile.createNewFile(); } catch (IOException ignored) {}
        }
        storageConfig = YamlConfiguration.loadConfiguration(storageFile);
        
        if (storageConfig.getConfigurationSection("data") != null) {
            for (String uuidStr : storageConfig.getConfigurationSection("data").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                Map<Material, Integer> items = new HashMap<>();
                for (String matStr : storageConfig.getConfigurationSection("data." + uuidStr).getKeys(false)) {
                    items.put(Material.valueOf(matStr), storageConfig.getInt("data." + uuidStr + "." + matStr));
                }
                storage.put(uuid, items);
            }
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
