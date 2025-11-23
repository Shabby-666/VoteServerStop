# VoteServerStop 服务器重启投票插件

## 插件简介
这是一个 Minecraft 服务器插件，让玩家通过投票来决定是否重启服务器。再也不用管理员一个人决定什么时候重启服务器了，大家可以一起投票决定！

## 功能特点
- 玩家可以发起重启投票
- 其他玩家可以投票同意或反对
- 投票时间结束后自动统计结果
- 如果没人反对就自动重启服务器
- 如果有人反对就取消重启
- 支持配置各种参数
- 支持管理员命令管理插件
- 可控制谁可以发起投票

## 安装方法
1. 把 `VoteServerStop-2.0-RELEASE-jar-with-dependencies.jar` 文件放进服务器的 `plugins` 文件夹
2. 启动服务器
3. 插件会自动生成配置文件 `config.yml`

## 使用方法

### 发起投票
- 玩家输入：`/votestop` - 使用默认时间发起投票
- 玩家输入：`/votestop 60` - 发起60秒的投票（最少60秒）

### 参与投票
- 同意重启：`/vote OK`
- 反对重启：`/vote NO`

### 管理员命令
- `/voteserverstop reload` - 重载配置文件
- `/voteserverstop disable` - 禁用插件功能
- `/voteserverstop enable` - 启用插件功能

## 投票规则
1. 只有一个人在线时：
   - 发起投票后进入等待期
   - 如果投同意票，立即开始15秒倒计时
   - 如果投反对票，取消重启
   
2. 多人在线时：
   - 发起投票后进入等待期（默认120秒）
   - 等待期内可以投同意票或反对票
   - 时间结束后统计结果：
     - 有人反对 → 取消重启
     - 没人投票 → 取消重启
     - 只有人同意 → 开始15秒倒计时重启

## 配置文件说明
配置文件位置：`plugins/VoteServerStop/config.yml`

```yaml
# 重启冷却时间（秒）- 两次重启之间最少间隔
restart-cooldown-seconds: 300

# 重启等待时间（秒）- 默认投票等待时间
restart-wait-seconds: 120

# 倒计时时间（秒）- 重启前的倒计时
countdown-seconds: 15

# 最小投票时间（秒）- 投票时间不能少于这个值
min-vote-time-seconds: 60

# 谁可以投票（默认所有玩家）
# 注：在此处无论如何设置没有voteserverstop.use权限的人都是无法投票的，需要用LuckPerms一类的权限管理插件改权限
# All: 所有玩家
# Op: 仅OP
# Console: 仅控制台
vote-permission: All
```

## 权限设置
- `voteserverstop.use` - 使用投票功能（默认所有玩家都有）
- `voteserverstop.admin` - 管理员权限，可以设置更短的投票时间（默认只有OP有）

## 常见问题
Q: 为什么我不能发起投票？
A: 可能还在冷却时间内，或者没有 voteserverstop.use 权限，或者配置文件中限制了发起投票的权限

Q: 投票结束后为什么没有重启？
A: 可能有人投了反对票，或者没有人投票

Q: 怎么修改投票时间？
A: 编辑 config.yml 文件，修改对应的数值，然后重启服务器

## 联系作者
如果有问题可以联系插件作者，但一般情况下这个插件很稳定，不需要联系。