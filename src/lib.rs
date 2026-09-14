mod cli;
mod endpoint;
mod frp;
mod model;
mod output;
mod paths;
mod service;
mod state;

pub type Result<T> = std::result::Result<T, Box<dyn std::error::Error>>;

pub fn run_client() -> Result<()> {
    cli::run_client()
}

pub fn run_server() -> Result<()> {
    cli::run_server()
}
