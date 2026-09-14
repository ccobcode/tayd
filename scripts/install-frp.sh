#!/bin/sh
set -eu

FRP_VERSION=${FRP_VERSION:-0.69.0}
COMPONENT=${1:-}

die() {
    echo "ERROR: $*" >&2
    exit 1
}

case "$COMPONENT" in
frpc) INSTALL_DIR=${INSTALL_DIR:-"$HOME/.tayc-client"} ;;
frps) INSTALL_DIR=${INSTALL_DIR:-"$HOME/.tayd-server"} ;;
*) die "usage: install-frp.sh <frpc|frps>" ;;
esac

install_binary() {
    src=$1
    dest=$2
    tmp="$dest.tmp.$$"

    rm -f "$tmp"
    if cp "$src" "$tmp" && chmod 755 "$tmp" && mv -f "$tmp" "$dest"; then
        return 0
    fi

    rm -f "$tmp"
    return 1
}

frp_target() {
    os=$(uname -s | tr '[:upper:]' '[:lower:]')
    arch=$(uname -m)
    case "$arch" in
        x86_64|amd64)
            arch=amd64
            ;;
        arm64|aarch64)
            arch=arm64
            ;;
        *)
            die "unsupported architecture: $arch"
            ;;
    esac
    printf '%s_%s\n' "$os" "$arch"
}

if [ "${SKIP_FRP_DOWNLOAD:-0}" = "1" ]; then
    exit 0
fi

command -v curl >/dev/null 2>&1 || die "curl is required"
command -v tar >/dev/null 2>&1 || die "tar is required"

target=$(frp_target)
archive="frp_${FRP_VERSION}_${target}.tar.gz"
url="https://github.com/fatedier/frp/releases/download/v${FRP_VERSION}/${archive}"
tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT INT TERM

curl -fL "$url" -o "$tmp_dir/$archive"
tar -xzf "$tmp_dir/$archive" -C "$tmp_dir"

mkdir -p "$INSTALL_DIR/bin"
frp_dir=$(find "$tmp_dir" -type d -name "frp_${FRP_VERSION}_${target}" | head -n 1)
[ -n "$frp_dir" ] || die "could not find extracted FRP directory"

install_binary "$frp_dir/$COMPONENT" "$INSTALL_DIR/bin/$COMPONENT"
