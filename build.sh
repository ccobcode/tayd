#!/bin/sh
set -eu

RUST_IMAGE=${RUST_IMAGE:-rust:1.85-bookworm}

build_darwin() {
	cargo_bin=${CARGO_BIN:-cargo}
	rustc_bin=${RUSTC_BIN:-}
	if [ -z "${CARGO_BIN:-}" ] && command -v rustup >/dev/null 2>&1 && rustup which cargo >/dev/null 2>&1; then
		cargo_bin=$(rustup which cargo)
		rustc_bin=$(rustup which rustc)
	fi
	command -v "$cargo_bin" >/dev/null 2>&1 || {
		echo "cargo is required to build darwin binaries" >&2
		exit 1
	}

	if [ -n "$rustc_bin" ]; then
		RUSTC="$rustc_bin" "$cargo_bin" build --release
	else
		"$cargo_bin" build --release
	fi
	darwin_arch=$(uname -m | sed 's/x86_64/amd64/;s/aarch64/arm64/')
	/usr/bin/install -S -m 755 target/release/tayc "bin/tayc-darwin-$darwin_arch"
	/usr/bin/install -S -m 755 target/release/tayd "bin/tayd-darwin-$darwin_arch"

	if rustup target list --installed | grep -F x86_64-apple-darwin >/dev/null 2>&1; then
		if [ -n "$rustc_bin" ]; then
			RUSTC="$rustc_bin" "$cargo_bin" build --release --target x86_64-apple-darwin
		else
			"$cargo_bin" build --release --target x86_64-apple-darwin
		fi
		/usr/bin/install -S -m 755 target/x86_64-apple-darwin/release/tayc bin/tayc-darwin-amd64
		/usr/bin/install -S -m 755 target/x86_64-apple-darwin/release/tayd bin/tayd-darwin-amd64
	fi
}

build_linux() {
	platform=$1
	suffix=$2
	target_dir=$3

	docker run --rm \
		--platform "$platform" \
		-u "$(id -u):$(id -g)" \
		-e CARGO_HOME=/work/.cargo-home \
		-e CARGO_TARGET_DIR="/work/target/$target_dir" \
		-v "$PWD":/work \
		-w /work \
		"$RUST_IMAGE" \
		sh -c "cargo build --release && cp /work/target/$target_dir/release/tayc /work/bin/tayc-$suffix && cp /work/target/$target_dir/release/tayd /work/bin/tayd-$suffix"
}

build_windows_amd64() {
	uid=$(id -u)
	gid=$(id -g)

	docker run --rm \
		-e CARGO_HOME=/work/.cargo-home \
		-e CARGO_TARGET_DIR=/work/target/windows-amd64 \
		-e HOST_UID="$uid" \
		-e HOST_GID="$gid" \
		-v "$PWD":/work \
		-w /work \
		"$RUST_IMAGE" \
		sh -c "apt-get update && apt-get install -y --no-install-recommends gcc-mingw-w64-x86-64 && rustup target add x86_64-pc-windows-gnu && cargo build --release --target x86_64-pc-windows-gnu && cp /work/target/windows-amd64/x86_64-pc-windows-gnu/release/tayc.exe /work/bin/tayc-windows-amd64.exe && cp /work/target/windows-amd64/x86_64-pc-windows-gnu/release/tayd.exe /work/bin/tayd-windows-amd64.exe && chown -R \"\$HOST_UID:\$HOST_GID\" /work/bin/tayc-windows-amd64.exe /work/bin/tayd-windows-amd64.exe /work/target/windows-amd64 /work/.cargo-home"
}

mkdir -p bin target .cargo-home

if [ "$(uname -s)" = "Darwin" ]; then
	build_darwin
fi

build_linux linux/amd64 linux-amd64 linux-amd64
build_linux linux/arm64/v8 linux-arm64 linux-arm64
build_windows_amd64

chmod +x bin/tayc-* bin/tayd-*
