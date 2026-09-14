# TayC Android

The client UI is built with Kotlin, Jetpack Compose, Material 3, and Material 3 adaptive navigation. The existing Java domain and runtime layers remain reusable and independently tested.

Sources are grouped by feature and role:

- `app/`: Compose activity orchestration and the gateway UI state holder.
- `ui/`: Material 3 theme, adaptive app shell, reusable components, and screens.
- `gateway/`: gateway settings, logs, startup policy, service lifecycle.
- `proxy/`: local HTTP proxy and SOCKS5 proxy domain/runtime engine.
- `frpc/`: embedded FRPC config, proxy mapping store, and process runtime.
- `webhook/`: WebHook channel model, store, HTTP client, and dispatcher.
- `event/`: Android event models, permissions, receivers, and notification listener.
- `qr/`: server QR payload parsing and scan UI.
- `network/`: network interface domain formatting and Android provider.
- `common/`: shared small utilities that do not belong to one feature.
