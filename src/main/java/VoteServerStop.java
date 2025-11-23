import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * VoteServerStop - Minecraft Spigot 1.20.1服务器重启插件
 * 根据要求：支持配置文件自定义参数，不在投票时间内投反对票则服务器重启
 */
public class VoteServerStop extends JavaPlugin {

    // 重启投票状态标志 - 用于标识是否有重启投票正在进行中
    private boolean restartVoteActive = false;
    // 反对重启的玩家集合 - 存储所有投反对票的玩家名称，避免重复投票
    private final Set<String> noVoters = new HashSet<>();
    // 同意重启的玩家集合 - 存储所有投同意票的玩家名称，避免重复投票
    private final Set<String> yesVoters = new HashSet<>();
    // 重启发起人名称 - 记录发起重启投票的玩家名称
    private String restartInitiator = null;
    // 重启倒计时任务ID - 用于取消重启倒计时任务
    private int restartTaskId = -1;
    // 倒计时任务ID - 用于取消最终的重启倒计时任务
    private int countdownTaskId = -1;
    // 上次重启结束时间，用于实现冷却 - 记录上一次重启完成的时间戳，用于计算冷却时间
    private long lastRestartEndTime = 0;
    // 插件禁用状态标志 - 用于标识插件是否被禁用
    private boolean pluginDisabled = false;
    
    // 配置参数 - 从配置文件读取
    private long restartCooldownMs;   // 重启冷却时间（毫秒）- 两次重启之间的最小间隔时间
    private int restartWaitSeconds;  // 重启等待时间（秒）- 默认的投票等待时间
    private int countdownSeconds;    // 倒计时时间（秒）- 重启前的倒计时时间
    private int minVoteTimeSeconds;  // 最小投票时间（秒）- 玩家可设置的最小投票时间
    private String votePermission;   // 投票权限设置 - 控制谁可以发起投票
    
    /**
     * 插件启用时的初始化方法
     * 生成配置文件并加载配置参数
     */
    @Override
    public void onEnable() {
        // 打印ASCII艺术字
        displayBanner();
        
        // 生成默认配置文件
        saveDefaultConfig();
        
        // 加载配置参数
        loadConfig();
        
        getLogger().info(ChatColor.GREEN + "VoteServerStop plugin enabled! Config loaded successfully.");
        getLogger().info(ChatColor.YELLOW + "Restart cooldown: " + (restartCooldownMs / 1000) + "s, Wait time: " + restartWaitSeconds + "s, Countdown: " + countdownSeconds + "s");
    }
    
    /**
     * 打印ASCII艺术字横幅
     */
    private void displayBanner() {
        System.out.println("___    __       _____       ________                                    _____________                 ");
        System.out.println("__ |  / /______ __  /______ __  ___/_____ ___________   _______ __________  ___/__  /_______ ________ ");
        System.out.println("__ | / / _  __ \\_  __/_  _ \\_____ \\ _  _ \\__  ___/__ | / /_  _ \\__  ___/_____ \\ _  __/_  __ \\___  __ \\ ");
        System.out.println("__ |/ /  / /_/ // /_  /  __/____/ / /  __/_  /    __ |/ / /  __/_  /    ____/ / / /_  / /_/ /__  /_/ / ");
        System.out.println("_____/   \\____/ \\__/  \\___/ /____/  \\___/ /_/     _____/  \\___/ /_/     /____/  \\__/  \\____/ _  .___/ ");
        System.out.println("                                                                                              /_/      ");
        System.out.println("VoteServerStop - Minecraft Spigot 1.20.1服务器重启插件");
        System.out.println("启动完成！");
        System.out.println("Github：https://github.com/Shabby-666/VoteServerStop 记得给个Star qwq");
    }
    
    /**
     * 加载配置文件参数
     * 从config.yml读取配置项并进行验证，确保参数在合理范围内
     */
    private void loadConfig() {
        FileConfiguration config = getConfig();
        
        // 设置默认值并读取配置
        config.addDefault("restart-cooldown-seconds", 300);  // 默认5分钟冷却
        config.addDefault("restart-wait-seconds", 120);      // 默认2分钟等待
        config.addDefault("countdown-seconds", 15);          // 默认15秒倒计时
        config.addDefault("min-vote-time-seconds", 60);      // 默认最小投票时间60秒
        config.addDefault("vote-permission", "voteserverstop.use"); // 默认投票权限
        
        // 保存默认值到配置文件
        config.options().copyDefaults(true);
        saveConfig();
        
        // 读取配置到变量
        restartCooldownMs = config.getInt("restart-cooldown-seconds") * 1000L;
        restartWaitSeconds = config.getInt("restart-wait-seconds");
        countdownSeconds = config.getInt("countdown-seconds");
        minVoteTimeSeconds = config.getInt("min-vote-time-seconds");
        votePermission = config.getString("vote-permission");
        
        // 验证配置合理性
        // 重启冷却时间验证：最少1分钟冷却
        if (restartCooldownMs < 60000) {  
            restartCooldownMs = 60000;
            getLogger().warning("Restart cooldown too short, set to 60s minimum");
        }
        // 重启等待时间验证：最少为配置的最小投票时间
        if (restartWaitSeconds < minVoteTimeSeconds) {  
            restartWaitSeconds = minVoteTimeSeconds;
            getLogger().warning("Restart wait time too short, set to " + minVoteTimeSeconds + "s minimum");
        }
        // 倒计时时间验证：最少5秒倒计时
        if (countdownSeconds < 5) {  
            countdownSeconds = 5;
            getLogger().warning("Countdown time too short, set to 5s minimum");
        }
        // 最小投票时间配置验证
        // 最少30秒，防止管理员设置过短的时间
        if (minVoteTimeSeconds < 30) {  
            minVoteTimeSeconds = 30;
            getLogger().warning("Min vote time too short, set to 30s minimum");
        }
        // 最多5分钟，防止管理员设置过长的时间
        if (minVoteTimeSeconds > 300) {  
            minVoteTimeSeconds = 300;
            getLogger().warning("Min vote time too long, set to 300s maximum");
        }
    }

    /**
     * 插件禁用时的清理方法
     * 记录插件禁用日志，取消所有定时任务
     */
    @Override
    public void onDisable() {
        // 取消所有可能存在的定时任务
        if (restartTaskId != -1) {
            Bukkit.getScheduler().cancelTask(restartTaskId);
        }
        if (countdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(countdownTaskId);
        }
        getLogger().info(ChatColor.RED + "VoteServerStop plugin disabled!");
    }

    /**
     * 命令处理方法，处理/votestop和/vote命令
     * @param sender 命令发送者
     * @param cmd 命令对象
     * @param label 命令标签
     * @param args 命令参数
     * @return 是否成功执行命令
     */
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("votestop")) {
            // 新逻辑：处理重启投票命令，支持传入时间参数
            return handleRestartVoteCommand(sender, args);
        } else if (cmd.getName().equalsIgnoreCase("vote")) {
            // 新逻辑：处理投票命令，支持OK和NO两种选项
            if (args.length > 0) {
                if (args[0].equalsIgnoreCase("NO")) {
                    return handleObjectionCommand(sender, args);
                } else if (args[0].equalsIgnoreCase("OK")) {
                    return handleAgreementCommand(sender, args);
                }
            }
            // 如果没有参数或参数不是NO/OK，则显示帮助信息
            sender.sendMessage(ChatColor.GOLD + "===== VoteServerStop 投票系统帮助 =====");
            sender.sendMessage(ChatColor.GREEN + "/vote OK " + ChatColor.YELLOW + "- 同意重启");
            sender.sendMessage(ChatColor.RED + "/vote NO " + ChatColor.YELLOW + "- 反对重启");
            sender.sendMessage(ChatColor.GOLD + "=====================================");
            return true;
        } else if (cmd.getName().equalsIgnoreCase("voteserverstop")) {
            // 处理voteserverstop管理命令
            return handleAdminCommand(sender, args);
        }
        return false;
    }
    
    /**
     * 处理重启投票命令
     * 新逻辑：支持玩家和控制台指定投票时间（最少为配置文件中的最小值），单人直接触发，多人需要指定时间无反对
     * @param sender 命令发送者
     * @param args 命令参数，第一个参数为可选的投票时间（秒）
     * @return 是否成功执行命令
     */
    private boolean handleRestartVoteCommand(CommandSender sender, String[] args) {
        // 检查插件是否被禁用
        if (pluginDisabled) {
            sender.sendMessage(ChatColor.RED + "VoteServerStop插件当前已被禁用！");
            return true;
        }
        
        // 检查发起投票的权限
        if (!checkVotePermission(sender)) {
            sender.sendMessage(ChatColor.RED + "你没有权限发起投票！");
            return true;
        }
        
        // 检查是否已经有重启计划在进行中
        if (restartVoteActive) {
            sender.sendMessage(ChatColor.RED + "已经有重启计划在进行中！");
            return true;
        }
        
        // 检查重启冷却时间 - 普通用户需要遵守冷却时间限制
        long currentTime = System.currentTimeMillis();
        // 控制台发起的投票不受冷却时间限制
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (currentTime - lastRestartEndTime < restartCooldownMs && !player.hasPermission("voteserverstop.admin")) {
                long remainingSeconds = (restartCooldownMs - (currentTime - lastRestartEndTime)) / 1000;
                player.sendMessage(ChatColor.RED + "重启冷却中！请在 " + remainingSeconds + " 秒后再尝试。");
                return true;
            }
            
            // 检查玩家是否有重启权限
            if (!player.hasPermission(votePermission)) {
                player.sendMessage(ChatColor.RED + "你没有权限发起重启！");
                return true;
            }
        }
        
        // 处理指定的投票时间参数
        int customWaitSeconds = restartWaitSeconds; // 默认使用配置文件中的时间
        if (args.length > 0) {
            try {
                customWaitSeconds = Integer.parseInt(args[0]);
                // 验证时间范围 - 不能小于配置的最小投票时间
                if (customWaitSeconds < minVoteTimeSeconds) {
                    sender.sendMessage(ChatColor.RED + "投票时间不能少于" + minVoteTimeSeconds + "秒！");
                    return true;
                }
                // 验证时间范围 - 不能超过1小时
                if (customWaitSeconds > 3600) { 
                    sender.sendMessage(ChatColor.RED + "投票时间不能超过3600秒（1小时）！");
                    return true;
                }
            } catch (NumberFormatException e) {
                // 参数不是有效数字时的错误提示
                sender.sendMessage(ChatColor.RED + "请输入有效的时间（秒）！");
                return true;
            }
        }

        // 获取在线玩家数量，用于判断是单人模式还是多人模式
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        
        // 开始新的重启投票流程
        restartVoteActive = true;
        // 记录重启发起人（玩家名称或控制台）
        restartInitiator = (sender instanceof Player) ? ((Player) sender).getName() : "控制台";
        noVoters.clear(); // 清空之前的反对票记录

        // 检查是否只有一人在线 - 单人模式处理
        if (onlinePlayers == 1) {
            // 单人模式：启动投票等待，即使只有一人也可以反对
            Bukkit.broadcastMessage(ChatColor.GOLD + "【服务器重启投票】");
            Bukkit.broadcastMessage(ChatColor.YELLOW + restartInitiator + " 发起了服务器重启！");
            Bukkit.broadcastMessage(ChatColor.RED + "⚠️ 警告：如果不在 " + customWaitSeconds + " 秒内投反对票，则服务器重启！！！⚠️");
            Bukkit.broadcastMessage(ChatColor.RED + "使用 /vote NO 反对重启");
            
            // 设置重启等待计时器 - 在指定时间后检查是否有反对票
            restartTaskId = Bukkit.getScheduler().runTaskLater(this, () -> {
                // 检查是否有人反对
                if (noVoters.isEmpty()) {
                    // 没有人反对，开始重启倒计时
                    Bukkit.broadcastMessage(ChatColor.GOLD + "【投票结果】");
                    Bukkit.broadcastMessage(ChatColor.GREEN + "投票时间结束，无人反对，服务器将重启！");
                    startRestartCountdown(countdownSeconds);
                } else {
                    // 有人反对，取消重启
                    Bukkit.broadcastMessage(ChatColor.RED + "【重启取消】");
                    Bukkit.broadcastMessage(ChatColor.RED + "有 " + noVoters.size() + " 人反对重启，重启已取消！");
                    resetRestartState();
                }
            }, customWaitSeconds * 20L).getTaskId(); // Bukkit调度器使用ticks为单位，1秒=20ticks
        } else {
            // 多人模式：使用指定时间自动重启，有人反对则取消
            Bukkit.broadcastMessage(ChatColor.GOLD + "【服务器重启投票】");
            Bukkit.broadcastMessage(ChatColor.YELLOW + restartInitiator + " 发起了服务器重启！");
            Bukkit.broadcastMessage(ChatColor.RED + "⚠️ 警告：如果不在 " + customWaitSeconds + " 秒内投反对票，则服务器重启！！！⚠️");
            Bukkit.broadcastMessage(ChatColor.RED + "使用 /vote NO 反对重启 或 /vote OK 同意重启");
            
            // 设置重启等待计时器 - 在指定时间后检查投票结果
            restartTaskId = Bukkit.getScheduler().runTaskLater(this, () -> {
                // 投票时间结束，检查投票结果
                // 根据规则：无人投票则视为全体不同意
                if (noVoters.isEmpty()) {
                    // 没有人明确反对，但需要检查是否有人同意或反对
                    // 如果在线玩家大于1且没有人投票（同意或反对），则视为全体不同意
                    if (onlinePlayers > 1 && yesVoters.isEmpty()) {
                        Bukkit.broadcastMessage(ChatColor.GOLD + "【投票结果】");
                        Bukkit.broadcastMessage(ChatColor.RED + "投票时间结束，无人参与投票，视为全体不同意，重启已取消！");
                        resetRestartState();
                    } else {
                        // 有人同意或者只有一个玩家在线的情况，按照原逻辑处理
                        Bukkit.broadcastMessage(ChatColor.GOLD + "【投票结果】");
                        Bukkit.broadcastMessage(ChatColor.GREEN + "投票时间结束，无人反对，服务器将重启！");
                        startRestartCountdown(countdownSeconds);
                    }
                } else {
                    // 有人反对，取消重启
                    Bukkit.broadcastMessage(ChatColor.RED + "【重启取消】");
                    Bukkit.broadcastMessage(ChatColor.RED + "有 " + noVoters.size() + " 人反对重启，重启已取消！");
                    resetRestartState();
                }
            }, customWaitSeconds * 20L).getTaskId(); // Bukkit调度器使用ticks为单位，1秒=20ticks
        }

        return true;
    }
    
    /**
     * 开始重启倒计时
     * @param seconds 倒计时秒数
     */
    private void startRestartCountdown(int seconds) {
        Bukkit.broadcastMessage(ChatColor.GOLD + "【重启倒计时】");
        Bukkit.broadcastMessage(ChatColor.RED + "⚠️ 警告：如果不在 " + seconds + " 秒内投反对票，则服务器重启！！！⚠️");
        
        // 创建倒计时任务
        countdownTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            private int countdown = seconds;
            
            @Override
            public void run() {
                if (countdown > 0) {
                    // 最后5秒每秒提示
                    if (countdown <= 5) {
                        Bukkit.broadcastMessage(ChatColor.RED + "服务器重启倒计时: " + countdown + " 秒！");
                    }
                    countdown--;
                } else {
                    // 倒计时结束，执行重启
                    Bukkit.broadcastMessage(ChatColor.RED + "服务器正在重启...");
                    Bukkit.getScheduler().cancelTask(countdownTaskId);
                    countdownTaskId = -1; // 重置倒计时任务ID
                    
                    // 1秒后执行重启
                    Bukkit.getScheduler().runTaskLater(VoteServerStop.this, () -> {
                        Bukkit.shutdown();
                    }, 20L);
                }
            }
        }, 0L, 20L);
    }

    /**
     * 命令补全方法，为/vote命令提供OK和NO选项的自动补全
     * @param sender 命令发送者
     * @param cmd 命令对象
     * @param alias 命令别名
     * @param args 命令参数
     * @return 补全选项列表
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        // 处理vote命令的补全
        if (cmd.getName().equalsIgnoreCase("vote")) {
            // 确保是玩家且有重启投票在进行
            if (sender instanceof Player && restartVoteActive) {
                Player player = (Player) sender;
                String playerName = player.getName();
                
                // 检查玩家是否已经投票
                if (!noVoters.contains(playerName)) {
                    // 提供OK和NO选项的补全
                    if (args.length == 1) {
                        String arg = args[0].toLowerCase();
                        if ("ok".startsWith(arg)) {
                            completions.add("OK");
                        }
                        if ("no".startsWith(arg)) {
                            completions.add("NO");
                        }
                    }
                }
            }
            return completions;
        }
        
        // 处理voteserverstop命令的补全
        if (cmd.getName().equalsIgnoreCase("voteserverstop")) {
            // 确保发送者有管理员权限
            if (sender.hasPermission("votestop.admin")) {
                if (args.length == 1) {
                    String arg = args[0].toLowerCase();
                    if ("reload".startsWith(arg)) {
                        completions.add("reload");
                    }
                    if ("disable".startsWith(arg)) {
                        completions.add("disable");
                    }
                    if ("enable".startsWith(arg)) {
                        completions.add("enable");
                    }
                }
            }
            return completions;
        }

        // 对于votestop命令，不需要补全参数
        return null;
    }

    /**
     * 处理反对重启命令
     * 新逻辑：任意玩家投反对票立即取消重启，不考虑其他因素
     * @param sender 命令发送者
     * @param args 命令参数（只有NO选项）
     * @return 是否成功执行命令
     */
    private boolean handleObjectionCommand(CommandSender sender, String[] args) {
        // 检查插件是否被禁用
        if (pluginDisabled) {
            sender.sendMessage(ChatColor.RED + "VoteServerStop插件当前已被禁用！");
            return true;
        }
        
        // 只有玩家可以投票
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家可以参与投票！");
            return true;
        }

        Player player = (Player) sender;
        String playerName = player.getName();

        if (!restartVoteActive) {
            player.sendMessage(ChatColor.RED + "当前没有进行中的重启计划！");
            return true;
        }
        
        // 检查是否有投票权限
        if (!player.hasPermission(votePermission)) {
            player.sendMessage(ChatColor.RED + "你没有权限参与投票！");
            return true;
        }

        // 检查玩家是否已经反对
        if (noVoters.contains(playerName)) {
            player.sendMessage(ChatColor.RED + "你已经反对过了！");
            return true;
        }

        if (args.length != 1 || !args[0].equalsIgnoreCase("NO")) {
            player.sendMessage(ChatColor.YELLOW + "用法: " + ChatColor.RED + "/vote NO " + ChatColor.YELLOW + "反对重启");
            return true;
        }

        // 玩家反对重启
        noVoters.add(playerName);
        
        // 立即取消所有重启相关的任务
        if (restartTaskId != -1) {
            Bukkit.getScheduler().cancelTask(restartTaskId);
            restartTaskId = -1; // 重置任务ID
        }
        
        if (countdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(countdownTaskId);
            countdownTaskId = -1; // 重置倒计时任务ID
        }
        
        // 广播反对消息和取消重启
        Bukkit.broadcastMessage(ChatColor.GOLD + "VoteServerStop >> " + playerName + " 投票选择 反对");
        Bukkit.broadcastMessage(ChatColor.RED + playerName + " 反对重启服务器！");
        Bukkit.broadcastMessage(ChatColor.RED + "【重启取消】");
        Bukkit.broadcastMessage(ChatColor.RED + "服务器重启已取消！");
        
        // 重置重启状态
        resetRestartState();

        return true;
    }
    
    /**
     * 添加处理同意投票的命令方法
     * @param sender 命令发送者
     * @param args 命令参数
     * @return 是否成功执行命令
     */
    private boolean handleAgreementCommand(CommandSender sender, String[] args) {
        // 检查插件是否被禁用
        if (pluginDisabled) {
            sender.sendMessage(ChatColor.RED + "VoteServerStop插件当前已被禁用！");
            return true;
        }
        
        // 只有玩家可以投票
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家可以参与投票！");
            return true;
        }

        Player player = (Player) sender;
        String playerName = player.getName();
        String voteChoice = args.length > 0 ? args[0].toUpperCase() : "OK"; // 默认为OK

        if (!restartVoteActive) {
            player.sendMessage(ChatColor.RED + "当前没有进行中的重启计划！");
            return true;
        }
        
        // 检查是否有投票权限
        if (!player.hasPermission("voteserverstop.use")) {
            player.sendMessage(ChatColor.RED + "你没有权限参与投票！");
            return true;
        }

        // 检查玩家是否已经投票（在反对票集合中）
        if (noVoters.contains(playerName)) {
            player.sendMessage(ChatColor.RED + "你已经投过反对票了！");
            return true;
        }
        
        // 检查玩家是否已经投票（在同意票集合中）
        if (yesVoters.contains(playerName)) {
            player.sendMessage(ChatColor.RED + "你已经投过同意票了！");
            return true;
        }

        // 检查参数是否为OK
        if (!voteChoice.equals("OK")) {
            player.sendMessage(ChatColor.YELLOW + "用法: " + ChatColor.GREEN + "/vote OK " + ChatColor.YELLOW + "同意重启");
            return true;
        }

        // 玩家同意重启（添加到同意票集合中）
        yesVoters.add(playerName);
        Bukkit.broadcastMessage(ChatColor.GOLD + "VoteServerStop >> " + playerName + " 投票选择 同意");
        player.sendMessage(ChatColor.GREEN + "你已投票同意重启服务器！");
        
        // 检查是否只有一人在线，如果是则立即启动15秒倒计时
        if (Bukkit.getOnlinePlayers().size() == 1) {
            // 取消原有的等待任务
            if (restartTaskId != -1) {
                Bukkit.getScheduler().cancelTask(restartTaskId);
                restartTaskId = -1;
            }
            
            // 立即启动15秒倒计时
            Bukkit.broadcastMessage(ChatColor.GOLD + "【投票结果】");
            Bukkit.broadcastMessage(ChatColor.GREEN + "唯一在线玩家同意重启，服务器将在15秒后重启！");
            startRestartCountdown(15); // 使用15秒倒计时
        }

        return true;
    }
    
    /**
     * 重置重启状态
     * 新逻辑：统一的重启状态清理方法，用于投票结束后的资源回收
     */
    private void resetRestartState() {
        restartVoteActive = false;
        noVoters.clear();
        yesVoters.clear();
        restartInitiator = null;
        
        // 确保任务ID重置
        if (restartTaskId != -1) {
            Bukkit.getScheduler().cancelTask(restartTaskId);
            restartTaskId = -1;
        }
        
        if (countdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(countdownTaskId);
            countdownTaskId = -1;
        }
        
        // 记录重启结束时间，用于冷却计算
        lastRestartEndTime = System.currentTimeMillis();
    }
    
    /**
     * 检查发起投票的权限
     * @param sender 命令发送者
     * @return 是否有权限发起投票
     */
    private boolean checkVotePermission(CommandSender sender) {
        // 如果是控制台发送者
        if (!(sender instanceof Player)) {
            // 检查配置是否允许控制台发起投票
            return "Console".equalsIgnoreCase(votePermission) || 
                   "All".equalsIgnoreCase(votePermission) || 
                   !"Op".equalsIgnoreCase(votePermission);
        }
        
        Player player = (Player) sender;
        
        // 检查是否为"All"（所有玩家都可以发起投票）
        if ("All".equalsIgnoreCase(votePermission)) {
            return true;
        }
        
        // 检查是否为"Op"（仅OP可以发起投票）
        if ("Op".equalsIgnoreCase(votePermission)) {
            return player.isOp();
        }
        
        // 检查是否为"Console"（仅控制台可以发起投票）
        if ("Console".equalsIgnoreCase(votePermission)) {
            return false; // 玩家不能发起投票
        }
        
        // 检查自定义权限节点
        return player.hasPermission(votePermission);
    }
    
    /**
     * 处理管理命令
     * @param sender 命令发送者
     * @param args 命令参数
     * @return 是否成功执行命令
     */
    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        // 检查是否有管理员权限
        if (!sender.hasPermission("voteserverstop.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
            return true;
        }
        
        // 检查是否有参数
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "用法: /voteserverstop <reload|disable|enable>");
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "reload":
                return handleReloadCommand(sender);
            case "disable":
                return handleDisableCommand(sender);
            case "enable":
                return handleEnableCommand(sender);
            default:
                sender.sendMessage(ChatColor.RED + "未知的子命令: " + subCommand);
                sender.sendMessage(ChatColor.RED + "用法: /voteserverstop <reload|disable|enable>");
                return true;
        }
    }
    
    /**
     * 处理重载配置命令
     * @param sender 命令发送者
     * @return 是否成功执行命令
     */
    private boolean handleReloadCommand(CommandSender sender) {
        try {
            // 重新加载配置文件
            reloadConfig();
            loadConfig(); // 重新加载配置参数
            sender.sendMessage(ChatColor.GREEN + "配置文件重载成功！");
            getLogger().info(ChatColor.GREEN + "配置文件已被 " + sender.getName() + " 重载");
            return true;
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "重载配置文件时出错: " + e.getMessage());
            getLogger().severe("重载配置文件时出错: " + e.getMessage());
            return true;
        }
    }
    
    /**
     * 处理禁用插件命令
     * @param sender 命令发送者
     * @return 是否成功执行命令
     */
    private boolean handleDisableCommand(CommandSender sender) {
        if (pluginDisabled) {
            sender.sendMessage(ChatColor.YELLOW + "插件已经处于禁用状态！");
            return true;
        }
        
        pluginDisabled = true;
        sender.sendMessage(ChatColor.GREEN + "VoteServerStop插件已禁用！");
        getLogger().info(ChatColor.RED + "VoteServerStop插件已被 " + sender.getName() + " 禁用");
        
        // 如果有正在进行的投票，取消它
        if (restartVoteActive) {
            // 取消所有重启相关的任务
            if (restartTaskId != -1) {
                Bukkit.getScheduler().cancelTask(restartTaskId);
                restartTaskId = -1;
            }
            
            if (countdownTaskId != -1) {
                Bukkit.getScheduler().cancelTask(countdownTaskId);
                countdownTaskId = -1;
            }
            
            // 重置重启状态
            resetRestartState();
            Bukkit.broadcastMessage(ChatColor.RED + "【投票取消】VoteServerStop插件已被禁用，所有投票已取消！");
        }
        
        return true;
    }
    
    /**
     * 处理启用插件命令
     * @param sender 命令发送者
     * @return 是否成功执行命令
     */
    private boolean handleEnableCommand(CommandSender sender) {
        if (!pluginDisabled) {
            sender.sendMessage(ChatColor.YELLOW + "插件已经处于启用状态！");
            return true;
        }
        
        pluginDisabled = false;
        sender.sendMessage(ChatColor.GREEN + "VoteServerStop插件已启用！");
        getLogger().info(ChatColor.GREEN + "VoteServerStop插件已被 " + sender.getName() + " 启用");
        return true;
    }
    
    // 不再需要旧的投票结束方法，已替换为新的重启逻辑
    // endVote方法已移除，功能由resetRestartState和startRestartCountdown替代
}