fn main() {
    if let Err(err) = tayc::run_client() {
        eprintln!("ERROR: {err}");
        std::process::exit(1);
    }
}
