fn main() {
    if let Err(err) = tayc::run_server() {
        eprintln!("ERROR: {err}");
        std::process::exit(1);
    }
}
