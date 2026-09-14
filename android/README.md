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

## WebHook JavaScript

Each channel has an optional synchronous script. An empty script sends the original event JSON unchanged. The editor highlights JavaScript without changing the text or cursor offsets.

The execution boundary is `{event, channel} -> {event, channel}`. `event` is the JSON produced by the event source, currently `{"type":"call|sms|notification","data":...}`. `channel` is an independent delivery snapshot:

```json
{
  "name": "ops",
  "url": "https://HOST/webhook",
  "proxyUrl": "",
  "headers": {
    "Content-Type": "application/json; charset=utf-8",
    "Accept": "application/json"
  }
}
```

```js
function onEvent(event, channel) {
    channel.headers["X-Event-Type"] = event.type;
    return {
        event: { kind: event.type, payload: event.data },
        channel: channel,
    };
}
```

Only the returned `event` is serialized as the UTF-8 POST body. The returned channel controls that request's URL, HTTP proxy and headers; the `{event, channel}` wrapper is never sent. Results do not update the saved channel or other deliveries. Return a complete channel, not a partial patch: `event`, `channel` and the required channel fields must be the object's own properties, not inherited from prototypes. `name`, `url` and the string-valued `headers` object are required; absent or empty `proxyUrl` means a direct connection. Proxy connections currently support `http://` URLs only; an `https://` proxy fails explicitly instead of receiving plaintext HTTP.

Headers are initialized before the script and are not merged back afterwards. HTTP framing headers and proxy authentication are managed by the transport. Redirects are reported instead of silently sending the request to another target. An explicit `event: null` sends JSON `null`; a missing event is an error.

Scripts run in a fresh Rhino interpreter scope on the existing background dispatch queue. Java, file/network bindings, async delivery and `fetch` are not exposed. Source is limited to 32,768 UTF-16 code units; script input and serialized output are limited to 1 MiB, with a 500 ms / one-million-instruction execution budget and bounded interpreter recursion. These limits are not a separate process or a hard heap quota.

Saving checks syntax without running top-level code. Missing handlers, exceptions, limits and invalid output skip that channel and record an error in WebHook logs; they never fall back to the original payload. Other channels continue normally. Editing still requires stopping the gateway.
