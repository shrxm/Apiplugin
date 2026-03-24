package kr.ssapi.actions;

import kr.ssapi.config.Config;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class FixedTeleportAction implements DonationAction {
    private JavaPlugin plugin;
    private final ConcurrentLinkedQueue<RandomSelectionTask> selectionQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isProcessingQueue = new AtomicBoolean(false);

    private class RandomSelectionTask {
        private final Player player;
        private final List<Location> locations;
        private final Consumer<Location> onComplete;

        public RandomSelectionTask(Player player, List<Location> locations, Consumer<Location> onComplete) {
            this.player = player;
            this.locations = locations;
            this.onComplete = onComplete;
        }
    }

    @Override
    public void setPlugin(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Player player) {
        Config config = Config.getInstance();

        List<Location> locations = loadLocations();

        if (locations.isEmpty()) {
            player.sendMessage("§c텔레포트 좌표가 설정되지 않았습니다.");
            return;
        }

        List<String> locationNames = new ArrayList<>();
        for (int i = 0; i < locations.size(); i++) {
            locationNames.add("위치 " + (i + 1));
        }

        showRandomSelectionTitle(player, locationNames, selectedObj -> {
            int index = locationNames.indexOf(selectedObj.toString());
            Location selectedLocation = locations.get(index);

            player.teleport(selectedLocation);

            String message = config.getMessage("messages.donation.teleport")
                    .replace("{location}", selectedObj.toString());

            if (!message.isEmpty()) {
                player.sendMessage(message);
            }
        });
    }

    private List<Location> loadLocations() {
        List<Location> locations = new ArrayList<>();

        List<Map<?, ?>> list = plugin.getConfig().getMapList("donation-actions.settings.random-teleport.locations");

        for (Map<?, ?> map : list) {
            String world = (String) map.get("world");
            double x = ((Number) map.get("x")).doubleValue();
            double y = ((Number) map.get("y")).doubleValue();
            double z = ((Number) map.get("z")).doubleValue();
            float yaw = map.containsKey("yaw") ? ((Number) map.get("yaw")).floatValue() : 0;
            float pitch = map.containsKey("pitch") ? ((Number) map.get("pitch")).floatValue() : 0;

            Location loc = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
            locations.add(loc);
        }

        return locations;
    }

    private void showRandomSelectionTitle(Player player, List<?> options, Consumer<Object> onComplete) {
        selectionQueue.offer(new RandomSelectionTask(player, (List<Location>) options, loc -> onComplete.accept(loc)));
        processNextInQueue();
    }

    private void processNextInQueue() {
        if (isProcessingQueue.get() || selectionQueue.isEmpty()) return;

        isProcessingQueue.set(true);
        RandomSelectionTask task = selectionQueue.poll();

        if (task != null) {
            executeRandomSelection(task.player, task.locations, task.onComplete);
        }
    }

    private void executeRandomSelection(Player player, List<Location> locations, Consumer<Location> onComplete) {
        AtomicInteger count = new AtomicInteger(0);
        AtomicInteger taskId = new AtomicInteger();
        Random random = new Random();

        List<String> names = new ArrayList<>();
        for (int i = 0; i < locations.size(); i++) {
            names.add("위치 " + (i + 1));
        }

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);

        taskId.set(Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (count.get() >= 20) {
                Bukkit.getScheduler().cancelTask(taskId.get());

                int index = random.nextInt(locations.size());
                Location selected = locations.get(index);
                String name = names.get(index);

                player.sendTitle("§a추첨 완료!", "§f" + name, 10, 40, 10);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);

                onComplete.accept(selected);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    isProcessingQueue.set(false);
                    processNextInQueue();
                }, 60L);
                return;
            }

            int index = random.nextInt(locations.size());
            String name = names.get(index);

            player.sendTitle("§e추첨 중...", "§f" + name, 0, 5, 0);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1);

            count.incrementAndGet();
        }, 0L, 2L));
    }
}