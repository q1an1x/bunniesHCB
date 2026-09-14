# 分阶段运行、现场验收与回滚

本次 PR 的默认入口是离线验证。生产服务切换和 ETS 下载尚未执行。以下步骤用于准备可审阅的切换，不是无人值守部署脚本。需要现场观察的步骤集中在最后的真实控制阶段。

## 1. 版本与备份

构建和运行均使用 Java 25。保留当前生产分发包、systemd unit、配对身份与配置名称文件；新版本使用独立目录。不要用 `installDist` 的结果覆盖运行中的原目录。

```sh
./gradlew test installDist distTar --no-daemon
python3 -m unittest discover -s tools -p 'test_*.py'
shasum -a 256 build/distributions/bunniesHCB-1.0-SNAPSHOT.tar
```

分发包名称仍为 `1.0-SNAPSHOT`，所以部署记录必须同时保存 **Git commit 和分发包 SHA-256**，不能仅靠文件名区分版本。仓库中的 `gradle.lockfile` 固定运行时依赖，CI 不使用家庭项目或凭据。

备份至少包括：原分发目录、原 unit 及 drop-in、原 `homekit-auth.bin`、存在时的 `configured-names.properties`。配对文件包含长期私钥，备份目录权限 0700、文件 0600。审查开始时已在仓库外保存原始 Git bundle、脏工作区和生产归档；具体位置在私有审查附录。

## 2. 离线验证与 ETS 对照

```sh
build/install/bunniesHCB/bin/bunniesHCB --mode offline --inventory /private/hcb-contract.json
python3 tools/audit_ets.py /private/home.knxproj \
  --contract /private/hcb-contract.json --output /private/ets-audit.json
```

期望目录为 72 个实体（66 个 KNX 实体、6 个模式开关）、255 项 KNX 绑定（包含新增 11 项只观察的面板输入）。ETS 工具的退出码 0 允许存在 warning，必须阅读汇总和报告；当前项目仍有窗帘 stop 未关联、3 项无读响应、11 项多读响应和44 项子类型复核。详细拓扑报告留在私有目录。

## 3. 在 homelab 仅观察

把候选分发包解到独立目录，使用独立状态目录。保持原服务运行：

```sh
/opt/bunnieshcb/releases/CANDIDATE/bin/bunniesHCB \
  --mode observe --knx-gateway knxd.example.invalid \
  --state-dir /var/lib/bunnieshcb-observe --observe-seconds 60
```

示例域名需要替换为已确认的 knxd 入口。多网卡机器应加 `--local-address` 指定可达该入口的本地 IPv4；不要将本机没有的地址填入该选项。默认 UDP 3671，不启用 NAT；仅在实际网络需要时添加 `--knx-nat`。不建议为了测试改动原有路由、防火墙、knxd 或 Siemens 设置。

`observe` 只建立普通 KNXnet/IP 会话，接收并更新本地内存状态。应用不发 group read/write，不启动 HomeKit、HA、烤箱、照明策略或时间广播。正常连接和 keepalive 包仍然存在。它占用 knxd 的一个客户端地址，不是独占 bus monitor。

核对收到报文、`droppedTelegrams=0`、运行期间出现 `CONNECTED`，以及到时正常退出。退出后的 `health.json` 应标为 `STOPPED`。确认原服务 PID、启动时间及重启计数没有变化。若没有收到报文，先查候选日志、路由/本地地址和 knxd 连接，不通过试写灯具来判断链路。

## 4. 准备服务与凭据

`deploy/bunnieshcb.service.example` 采用专用用户、私有状态目录和 systemd 文件系统限制，且仍默认 `observe`。它没有在当前生产机上安装。示例准备路径为：

| 内容 | 建议路径与权限 |
| --- | --- |
| 版本包 | `/opt/bunnieshcb/releases/COMMIT/`，只读应用文件 |
| 当前版本链接 | `/opt/bunnieshcb/current`，仅在停止候选服务后切换 |
| 配对/名称/模式意图/锁/health | `/var/lib/bunnieshcb/`，服务用户所有，0700 |
| 非敏感连接配置 | `/etc/bunnieshcb/controller.env`，root 管理 |
| HA 令牌 | `/etc/bunnieshcb/ha-token`，root:服务组，0640，或使用 systemd credentials |

原服务的令牌在命令行里；新服务应从文件或环境读取，避免继续放在 `ExecStart` 参数中。不要把原 unit 原样贴进 issue/PR。若使用 `HCB_HA_TOKEN_FILE`，还须配置 HA host，文件内容为令牌本身，不含变量名或引号；不要同时提供多个令牌来源。

真实切换前先停止原服务，再复制当时最新的配对/名称文件到候选状态目录，以免错过备份之后发生的配对变化。不要生成新身份，也不要让两个进程用同一身份同时监听。新的状态目录锁仅能阻止使用**同一目录**的重复进程，不能发现旧程序或使用另一目录的控制器。

## 5. 真实控制验收

只有准备好原版本恢复方式、有人可观察房间且确认切换窗口后，再将候选启动命令改成以下三个阶段。每阶段只运行一个 live 控制器；原服务保持停止。不要直接沿用旧 unit 的参数启动新版，否则新版默认离线验证后会退出。

阶段 A：保留直接 HomeKit 控制，先不启用软件自动化和 KNX 时间广播。

```sh
/opt/bunnieshcb/current/bin/bunniesHCB \
  --mode live --knx-gateway knxd.example.invalid \
  --state-dir /var/lib/bunnieshcb
```

启动状态同步可能需要数分钟：每个无响应对象有 2 秒超时，组发送有间隔。`SYNCHRONIZING` 时不会执行控制写入，45 秒静默检测也不在此阶段判定失败。某些地址读失败只导致对应值未知，不导致整个照明进程退出。缺少状态的实体在家庭 App 显示不可用是需要排查的信号，不能靠假定初始 OFF 消除。

阶段 B：通过 A 后，增加 `--enable-automations`。这个开关同时启用家庭模式和 HCB 软件按钮。先按 [模式行为表](house-modes.md) 验收日常/观影，再验收睡眠、访客、清扫和离家；手动覆盖默认 30 分钟，可用 `--manual-hold-minutes` 调整。

阶段 C：需要 HCB 作为时间源时再增加 `--enable-time-service`；保留既有时间组地址与时区（默认 Asia/Shanghai）。确认没有同时运行旧的 HCB 时间广播。HA 可作为单独步骤启用。照明验收中保持烤箱关闭；其控制测试另设有人值守的窗口。

| 验收项目 | 方法 | 通过标准 / 失败后的处理 |
| --- | --- | --- |
| 配对身份 | 原 Apple 家庭直接连接候选，不删除旧配件、不重新配对 | 原配件可用；失败先回滚并核对身份文件、端口、mDNS 和依赖 |
| 灯开关、调光、调色 | 每类先选一盏灯，小范围操作并观察真实状态 | 发命令后由反馈更新；墙控紧随操作时反馈不被吞掉 |
| 物理墙控 | 分别测试直接 ETS 关联和 HCB 软件按钮 | 明确哪些功能依赖进程，直接控制符合原 ETS 逻辑 |
| 窗帘 | 先检查 stop 地址关联，再低风险短行程检查方向、停止和位置 | 不根据软件百分比推断已到位；方向异常立即停止并核对 ETS |
| 厨房/浴室/卧室 | 占用变化、已手动点亮、夜间优先、恒照度稳定性 | 没有互相覆盖、反复闪动或离开后误关手动灯 |
| 场景与覆盖 | 回忆场景，关闭自动化，再重复 OFF；复测重复场景 | 手动设置取消自动恢复；延时任务不重复 |
| 故障恢复 | 只中断候选进程到 knxd 的会话，恢复后查看 state/日志 | 自动重连、没有重复 HomeKit 配件、不重放旧命令；不为测试切断整屋总线供电 |
| 45 秒静默 | 已由假传输/虚拟时间测试；若做现场模拟，限制到候选会话 | 约 45–50 秒静默后进入重连，正常报文恢复后稳定；不要求特定心跳地址 |

现场 ETS 改动单独备份工程、一次修改一类问题并记录下载对象；不要把这一步混入 Java 服务切换。尤其不应未经检查将所有 R 标志关闭或将所有 1 bit 对象改成同一个子类型。

## 6. 观察运行和回滚

日常最小检查是读取 `health.json`、unit 状态和日志，而不是写入设备探活。

```sh
systemctl show bunnieshcb-candidate --property=ActiveState --property=NRestarts
journalctl -u bunnieshcb-candidate --since '10 minutes ago' --no-pager
cat /var/lib/bunnieshcb/health.json
```

`updatedAt` 必须持续更新。`entitiesWithKnownState` 包含没有可读状态的按钮，不能视为“已知状态的物理设备百分比”。该文件也记录家庭模式阶段和房间手动覆盖期限；HA 和烤箱的状态仍看各自日志。连续重连应先核对网关、路由和报文接收；有报文但个别配件未知，应查该状态地址与读响应者。队列溢出意味着状态处理跟不上，应保留日志并停止自动化排查，不能简单调大容量掩盖问题。

出现错误动作、反馈明显不一致、窗帘无法停止、重连循环、重复配件或配对异常时：

1. 停止候选服务，确认其 Java 进程已经退出。
2. 保留候选日志和状态副本供排查，不覆盖原备份。
3. 恢复原 unit/启动方式和原分发目录，启动原服务。
4. 优先继续使用原服务自己的配对身份；如切换期间确实发生配对变更，先备份双方状态，再明确选择应恢复的文件，不重新配对掩盖问题。
5. 用墙面直接控制及一项 HomeKit 操作确认恢复，记录回滚时刻和原因。

审查过程中没有改写实际 KNX 地址或下载 ETS，因此代码回滚不需要执行 ETS 恢复；如果现场阶段另行修改过 ETS，应按那次工程备份单独回滚。
