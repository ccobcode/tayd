# tayd / tayc

一个基于 [frp](https://github.com/fatedier/frp) 的本地服务发布工具，将本机 Web、TCP 或 UDP 服务发布到自己的公网服务器。服务端和客户端使用独立的命令与运行目录。

| 命令 | 职责 | 安装脚本默认目录 |
| --- | --- | --- |
| `tayd` | 服务端，管理 `frps` | `~/.tayd` |
| `tayc` | 客户端，管理 `frpc` 和端口映射 | `~/.tayc` |

Android 客户端名为 **TayC**，内置 `frpc`、本地代理与事件转发模块。

## 快速开始

### 服务端安装

将 `HOST` 替换为客户端可访问的服务器域名或 IP，默认连接端口为 `7000`：

```sh
curl -fsSL https://raw.githubusercontent.com/ccobcode/tayd/main/scripts/install-server.sh | sh -s -- HOST
```

需要按域名发布 HTTP/HTTPS 服务时，声明对应的服务端监听端口：

```sh
curl -fsSL https://raw.githubusercontent.com/ccobcode/tayd/main/scripts/install-server.sh | sh -s -- HOST --http-port 80 --https-port 443
```

多个 HTTP/HTTPS 映射可以共享对应的监听端口，由 frp 按域名路由。

安装完成后会打印连接信息、Linux/macOS 与 Windows PowerShell 客户端安装命令，以及 Android 扫码配置用的二维码。

```sh
tayd info       # 再次显示连接信息、安装命令和二维码
tayd restart    # 重启 frps
```

### 客户端安装

在客户端执行服务端输出的对应平台安装命令，安装后使用 `tayc` 管理映射。Unix 命令入口为 `~/.local/bin/tayc`，Windows 为 `$HOME\.local\bin\tayc.cmd`。

## 客户端基础用法

### 发布 Web / TCP 服务

```sh
tayc add web 3000 18080
```

对应的访问关系：

```text
http://HOST:18080 -> 127.0.0.1:3000
```

### 按域名发布 HTTP 服务

服务端需已启用 `--http-port`，并将域名解析到服务端：

```sh
tayc add site 3000 http://site.example.com
```

HTTPS 映射使用 `https://` 远端地址，服务端需启用 `--https-port`。

### 发布 UDP 服务

```sh
tayc add dns udp://5353 5353
```

### 管理映射与运行状态

```sh
tayc list              # 列出映射
tayc show web          # 查看指定映射
tayc remove web        # 删除映射
tayc verify            # 校验 frpc 配置
tayc install           # 直接启动 frpc，不注册系统服务
tayc restart           # 重启 frpc
tayc help              # 查看完整命令和参数
```

`add` / `remove` 会保存映射并重新生成配置；如果客户端已在运行，会尝试重启以应用修改。未运行时只保存配置。使用 `--no-restart` 可跳过自动重启。

需要系统自启动时使用独立的服务命令，支持 Linux systemd、macOS launchd 与 Windows 启动项：

```sh
tayc service install
tayc service status
tayc service uninstall
```

`service uninstall` 用于移除系统服务或启动项，保留客户端安装目录。

## Android 客户端 TayC

使用 **Jetpack Compose / Material 3** 界面，支持 **Android 8.0+（API 26）**，提供英文和简体中文，主导航为 **首页 / 服务 / 设置**（Home / Services / Settings）。

扫码连接服务端：

1. 在服务端运行 `tayd info` 显示二维码。
2. 在 TayC 的 **服务 → FRPC** 页面点击 **扫描服务器二维码**（Scan server QR）。
3. 扫码导入服务端地址、端口与 Token。

### 内置模块

| 模块 | 用途 |
| --- | --- |
| FRPC | 内置 `frpc` 客户端，配置服务端与端口映射 |
| WebHook | 配置 WebHook 通道，转发订阅的事件 |
| HTTP Proxy | 本机 HTTP / CONNECT 代理 |
| SOCKS5 | 本机 SOCKS5 代理 |
| Event Listener | 订阅来电、短信与应用通知事件 |

Android 源码结构见 [Android 文档](android/README.md)。

## 运行目录与配置

- 安装脚本默认将服务端放在 `~/.tayd`，客户端放在 `~/.tayc`；可通过 `INSTALL_DIR` 指定安装目录。
- 直接运行二进制时，可通过 `TAYD_HOME` / `TAYC_HOME` 指定对应的运行目录；未设置时按二进制所在位置推导。
- 服务端通过 `server-install.json` 保存连接参数，重装时保留已保存的地址、Token 和端口；已有服务端配置缺少有效元数据时停止更新。
- 客户端安装标记为 `.tayc`，卸载时校验该标记；显式强制卸载使用 `TAYC_FORCE_UNINSTALL=1`。
- Android 应用标识为 `com.cclilshy.tayc`，二维码格式为 `tayc://server?addr=HOST&port=7000&token=TOKEN`，由 `tayd info` 生成。

## 停止与卸载

服务端的 `uninstall` **只停止 frps**，保留安装目录、配置和命令入口；需要恢复运行时执行 `tayd install`：

```sh
~/.local/bin/tayd uninstall
```

客户端的 `uninstall` 会停止 frpc、移除当前 TayC 自启动项和命令入口，并**删除整个客户端安装目录及其中的配置**。

Unix：

```sh
~/.local/bin/tayc uninstall
```

Windows PowerShell：

```powershell
& "$HOME\.local\bin\tayc.cmd" uninstall
```

## 开发与验证

本机构建两个 CLI 并运行 Rust 单元测试；Shell 集成测试需要可用的 Docker 环境，脚本会自动在容器中运行。

```sh
cargo build --release --bins
cargo test
bash tests/tayd-tayc.sh
```

多平台 CLI 构建使用 [build.sh](build.sh)，输出 `tayd-*` 和 `tayc-*` 两组二进制。
