# 魔法少女小圆 · Minecraft 模组

> 📌 **接手先读 `docs/项目总览.md`** —— 一份文档读完整个项目（做了什么、没做什么、坑在哪）。
> 本 README 是简版操作说明；设计与细节在 `docs/` 下。

以灵魂宝石与污秽为核心循环的 Minecraft 模组。**独立模组**：不装 Iron's Spells 'n Spellbooks
也能正常加载与运行；装了则自动启用兼容联动。

当前进度：**框架与内容都已落地**——契约/宝石/污秽/魔女化主循环完整，三位魔法少女中
**献身系与赎罪系技能已实现并验证**（各 5 主动 / 3 主动 + 3 被动），索取系技能待补。
最新状态与待办以 `docs/项目总览.md` 为准。

---

## 1. 环境基线

| 项目 | 版本 / 路径 |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.10（ForgeGradle 6.0.54） |
| JDK | Microsoft OpenJDK **17.0.20+8**，`E:\CodexCache\madoka\toolchains\jdk-17.0.20+8` |
| Gradle | 8.14.5（由 wrapper 管理） |
| Gradle 用户目录 | `E:\CodexCache\madoka\.gradle-home`（`GRADLE_USER_HOME`） |
| 映射 | Mojang 官方映射 `official` |
| 工程根目录 | `E:\CodexCache\madoka\coding` |

> `gradle.properties` 指向工作区内的 JDK 17，并关闭 JDK 自动下载。
> 启动 wrapper 前按第 7 节设置 `JAVA_HOME` 和缓存路径。

## 2. 依赖结构（独立模组）

| 依赖 | 坐标 | 必需性 | 说明 |
|---|---|---|---|
| GeckoLib | `maven.modrinth:8BmcQJ2H:aC5KMoNg` | **必需** | 后续做实体模型（魔女等）要用 |
| Iron's Spells 'n Spellbooks | `maven.modrinth:irons-spells-n-spellbooks:1.20.1-3.16.3` | 可选 | 装了才启用兼容联动；`compileOnly` 进编译期 |
| Curios | `maven.modrinth:curios:5.14.1+1.20.1` | 可选 | 铁魔法联调依赖 |

- **不装铁魔法时**：模组正常加载，学派法强走本模组自己的属性
  （`madoka_magic:<school>_spell_power`，见 `registry/MadokaAttributes.java`）。
- **装了铁魔法时**：`compat/IronsCompat` 会把宝石加成镜像到铁魔法同名学派属性上。
  铁魔法专属的事件监听放在 `compat/IronsTaintEvents`，**只有在检测到铁魔法时才注册**，
  所以没装时不会触发 `NoClassDefFoundError`。
- 想在开发环境里联调可选前置，用开关：

```powershell
.\gradlew.bat runData -PwithOptional
```

> **GeckoLib 必须钉版本 id**：Modrinth Maven 按版本号解析时，`4.8.4` 会命中另一个
> Minecraft 版本（Java 21）的构建，开发环境会抛
> `NoSuchMethodError: net.minecraft.client.Minecraft.m_91087_()`。
> `8BmcQJ2H:aC5KMoNg` 才是 Forge 1.20.1 的 4.8.4。
> `irons-lib` 是铁魔法自己的硬依赖，只有装了铁魔法才需要，Maven 上也只有
> `1.20.1-2.1.0 / 2.2.0 / 2.3.0`（`1.20.1-2.0.0` 不存在）。

## 3. 数据驱动（加内容不用改代码）

魔法少女定义与技能元数据都在 JSON 里，放进资源包即可覆盖：

```
src/main/resources/data/madoka_magic/
├─ magical_girls/   一位魔法少女 = 一个 JSON（含属性、配色、魔力总量、契约材料、专属维度）
└─ spells/          一个技能 = 一个 JSON（含冷却、魔力消耗、图标、解锁等级）
```

加一位新魔法少女 / 一个新技能，**不需要改 Java、不需要重新编译**。
字段说明与踩坑见 `docs/数据驱动说明.md`。

## 4. 开发环境里必须保留的开关

`build.gradle` 的 `runs { configureEach { ... } }` 里有这一行：

```groovy
jvmArgs '-Dmixin.env.disableRefMap=true'
```

**装了铁魔法做联调（`-PwithOptional`）时它是必需的**，删掉会启动失败：

Iron's Spells 'n Spellbooks 与 Iron's Lib 的 mixin 注解用的是官方（Mojang）方法名，
而 ForgeGradle 在重映射时会把它们 jar 里的 `*_refmap.json` 改写成 SRG 名。
开发环境里 Minecraft 类是官方名，Mixin 拿着 refmap 去找 `Entity.m_7307_` 必然找不到，
于是报：

```
Mixin apply failed mixins.irons_spellbooks.json:EntityMixin -> net.minecraft.world.entity.Entity:
Critical injection failure: @Inject annotation on isAlliedTo could not find any targets matching
'Lnet/minecraft/world/entity/Entity;m_7307_(...)Z'
```

关掉 refmap 使用后，Mixin 直接用注解里写着的官方名，注入正常。
不装铁魔法时这个开关无害，可以保留。
（注意：有效的开关是 `mixin.env.disableRefMap`，`mixin.env.remapRefMap=false` 无效。）

## 5. 轮盘施法

`research/轮盘施法实现参考.md` 拆解了 Iron's Spells 'n Spellbooks 与 Ars Nouveau
两套轮盘实现的全部关键代码（反编译源码就在 `research/` 下），
含角度数学、扇形绘制、输入屏蔽、动画、选中同步，以及给我们的实现建议与坑位清单。

## 6. 目录结构

```
Madoka_magic/
├─ build.gradle / gradle.properties / settings.gradle   构建配置
├─ gradlew / gradlew.bat / gradle/wrapper/              Gradle wrapper
├─ src/main/java/io/madoka/magic/
│  ├─ MadokaMagic.java              主入口（@Mod("madoka_magic")，构造器注入）
│  ├─ config/MadokaConfig.java      配置：污秽数值、魔女数值
│  ├─ data/SoulGemSchool.java       9 颗灵魂宝石 → 本模组自己的学派属性
│  ├─ event/TaintEvents.java        污秽累积等运行期钩子（只用原版事件）
│  ├─ compat/IronsCompat.java       铁魔法可选兼容的入口（不 import 铁魔法类）
│  ├─ compat/IronsBridge.java       真正触碰铁魔法类的桥接（只有装了才加载）
│  ├─ compat/IronsTaintEvents.java  铁魔法专属事件监听（只有装了才注册）
│  └─ registry/MadokaAttributes.java, ModItems.java, ModEntities.java
├─ src/main/resources/
│  ├─ META-INF/mods.toml            模组元数据与依赖声明
│  ├─ pack.mcmeta
│  └─ assets/madoka_magic/          语言文件、logo
├─ research/                        轮盘施法的研究资料（见 research/轮盘施法实现参考.md）
│  ├─ ars_nouveau/                  Ars Nouveau 轮盘相关类的反编译源码
│  ├─ irons_spellbooks/             铁魔法轮盘相关类的反编译源码
│  └─ mods/                         两个模组 jar 的完整解包
├─ docs/                             设计提案 v2.0（当前） + v1.0 备份 + C 盘审计记录
├─ run/ / run-data/                  开发客户端与数据生成的运行目录
└─ build/libs/madoka_magic-1.0.0.jar 构建产物
```

## 7. 常用命令

```powershell
Set-Location -LiteralPath 'E:\CodexCache\madoka\coding'
$madokaWorkspace = (Resolve-Path -LiteralPath '..').Path
$env:JAVA_HOME = Join-Path $madokaWorkspace 'toolchains\jdk-17.0.20+8'
$env:GRADLE_USER_HOME = Join-Path $madokaWorkspace '.gradle-home'
$env:TEMP = Join-Path $madokaWorkspace '.tmp'
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Force -Path $env:GRADLE_USER_HOME, $env:TEMP | Out-Null
$env:JAVA_TOOL_OPTIONS = "-Duser.home=$madokaWorkspace -Djava.io.tmpdir=$env:TEMP"

.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runData     # 无 GUI 的加载自检，用来验证依赖是否齐全
```

`runData` 是最快的体检方式：它会完整走一遍 Forge 的加载流程（注册表、配置、所有前置模组），
几十秒内就能看出依赖有没有配错，日志在 `run-data\logs\latest.log`。

## 8. 中文编码的两个坑

1. **`gradle.properties` 里的中文必须写成 `\uXXXX` 转义。**
   Gradle 按 ISO-8859-1 读取 `.properties`，直接写中文会让 `mods.toml` / `pack.mcmeta` 里的
   模组名变成 `é­å°å¥³...` 这种乱码。当前文件里 `mod_name` / `mod_authors` / `mod_description`
   都已经是转义形式。
2. **资源处理要显式指定 UTF-8**（已在 `build.gradle` 里配好）：
   ```groovy
   tasks.named('processResources', ProcessResources).configure {
       filteringCharset = 'UTF-8'
   }
   ```
   否则 Groovy 模板展开同样会按平台字符集处理中文。

## 9. 工作区内的构建文件

- 工作区的 `.gradle-home\` 保存 Gradle 发行包、依赖缓存、Minecraft 资源和 Gradle 日志，`.tmp\` 保存临时文件。
- 工程的 `.gradle\`、`build\`、`run\`、`run-data\` 保存工程缓存、构建产物和游戏运行文件。
- 第 7 节的环境变量只作用于当前 PowerShell 窗口及其子进程。

## 10. 已核实的 Iron's API（1.20.1-3.16.3，均来自本地 jar 反查）

| 用途 | 真实名称 |
|---|---|
| 模组 ID | `irons_spellbooks` |
| Java 包名 | `io.redspace.ironsspellbooks`（不是 `net.irons_spellbooks`） |
| 学派实例 | `io.redspace.ironsspellbooks.api.registry.SchoolRegistry#FIRE/ICE/LIGHTNING/HOLY/ENDER/BLOOD/EVOCATION/NATURE/ELDRITCH` |
| 学派法强属性 | `io.redspace.ironsspellbooks.api.registry.AttributeRegistry#FIRE_SPELL_POWER` 等 9 个 |
| 属性 ID 前缀 | `attribute.irons_spellbooks.*`（如 `attribute.irons_spellbooks.fire_spell_power`） |
| 其他属性 | `AttributeRegistry#MAX_MANA` / `MANA_REGEN` / `SPELL_POWER` / `SPELL_RESIST` |
| 施法事件 | `io.redspace.ironsspellbooks.api.events.SpellOnCastEvent`（继承 `PlayerEvent`，有 `getSchoolType()` / `getSpellId()`） |
| 饰品 API | `top.theillusivec4.curios.api.CuriosApi`、`top.theillusivec4.curios.api.type.capability.ICurioItem` |

## 11. 下一步（待方向确认）

设计提案 v2.0 的核心循环：签约选学派宝石 → 施法累积污秽 → 污秽满则死亡并原地生成魔女 → 击败魔女得悲叹之种 → 种子净化污秽。这条主循环**已实现**。

**已完成**：契约与灵魂宝石、污秽与魔女化、魔女实体与半魔女化身、阵营层（眷属不可见 + 同阵营减免）、
数据驱动（3 位魔法少女 + 17 个技能）、献身系技能、赎罪系技能、轮盘与确认界面。

**还没做（按优先级）**：
1. 索取系「蜂蜜 / 注射」，并重写 `illusion` / `dandelion_drift`（当前与设计不符）
2. 三个魔女化结界（自毁 / 索取 / 错爱）
3. 转投魔女线（单挑魔女 → 契约变魔女之吻 → 自身化半魔女）
4. HUD（罪责 / 约定池 / 花期等数据接口已同步到客户端）、素材替换、成神路线

> 细节与踩坑见 `docs/项目总览.md`；两个已实现的技能系看
> `docs/献身系技能设计.md`、`docs/赎罪系技能设计.md`。
