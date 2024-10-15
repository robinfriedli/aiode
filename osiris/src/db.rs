use std::{fs, io};

use diesel::{ConnectionError, ConnectionResult};
use diesel_async::{
    pg::TransactionBuilder,
    pooled_connection::{
        deadpool::{Object, Pool},
        AsyncDieselConnectionManager, ManagerConfig,
    },
    scoped_futures::ScopedBoxFuture,
    AsyncPgConnection,
};
use futures::{future::BoxFuture, FutureExt};
use lazy_static::lazy_static;
use rustls::pki_types::CertificateDer;

use crate::error::{Error, TransactionRuntimeError};

lazy_static! {
    pub static ref DATABASE_URL: String = std::env::var("OSIRIS_DATABASE_URL").expect(
        "Missing environment variable OSIRIS_DATABASE_URL must be set to connect to postgres"
    );
    pub static ref PG_ENABLE_SSL: bool = std::env::var("OSIRIS_PG_ENABLE_SSL")
        .map(|val| val
            .parse::<bool>()
            .expect("OSIRIS_PG_ENABLE_SSL is not a valid boolean"))
        .unwrap_or_default();
    pub static ref PG_SSL_CERT_PATH: Option<String> = std::env::var("OSIRIS_PG_SSL_CERT_PATH").ok();
    pub static ref CONNECTION_POOL: Pool<AsyncPgConnection> = {
        let database_connection_manager = if *PG_ENABLE_SSL {
            let mut config = ManagerConfig::default();
            config.custom_setup = Box::new(establish_pg_ssl_connection);
            AsyncDieselConnectionManager::<AsyncPgConnection>::new_with_config(
                DATABASE_URL.clone(),
                config,
            )
        } else {
            AsyncDieselConnectionManager::<AsyncPgConnection>::new(DATABASE_URL.clone())
        };
        let max_db_connections = std::env::var("OSIRIS_MAX_DB_CONNECTIONS")
            .unwrap_or_else(|_| String::from("10"))
            .parse::<usize>()
            .expect("OSIRIS_MAX_DB_CONNECTIONS is not a valid usize");
        Pool::builder(database_connection_manager)
            .max_size(max_db_connections)
            .build()
            .expect("Failed to initialise connection pool")
    };
}

pub type DbConnection = Object<AsyncPgConnection>;

pub async fn acquire_db_connection() -> Result<DbConnection, Error> {
    CONNECTION_POOL
        .get()
        .await
        .map_err(|e| Error::DatabaseConnectionError(e.to_string()))
}

pub async fn run_retryable_transaction<'b, 'c, T: 'b, F>(
    connection: &mut AsyncPgConnection,
    function: F,
) -> Result<T, Error>
where
    F: for<'r> FnOnce(
            &'r mut AsyncPgConnection,
        ) -> ScopedBoxFuture<'b, 'r, Result<T, TransactionRuntimeError>>
        + Clone
        + Send
        + 'c,
{
    run_retryable_transaction_with_level(connection.build_transaction().read_committed(), function)
        .await
}

pub async fn run_serializable_transaction<'b, 'c, T: 'b, F>(
    connection: &mut AsyncPgConnection,
    function: F,
) -> Result<T, Error>
where
    F: for<'r> FnOnce(
            &'r mut AsyncPgConnection,
        ) -> ScopedBoxFuture<'b, 'r, Result<T, TransactionRuntimeError>>
        + Clone
        + Send
        + 'c,
{
    run_retryable_transaction_with_level(connection.build_transaction().serializable(), function)
        .await
}

async fn run_retryable_transaction_with_level<'b, 'c, T, F>(
    mut transaction_builder: TransactionBuilder<'c, AsyncPgConnection>,
    function: F,
) -> Result<T, Error>
where
    T: 'b,
    F: for<'r> FnOnce(
            &'r mut AsyncPgConnection,
        ) -> ScopedBoxFuture<'b, 'r, Result<T, TransactionRuntimeError>>
        + Clone
        + Send
        + 'c,
{
    let mut retry_count: usize = 0;
    loop {
        retry_count += 1;
        let transaction_result = transaction_builder
            .run::<_, TransactionRuntimeError, _>(function.clone())
            .await;

        match transaction_result {
            Err(TransactionRuntimeError::Retry(_)) if retry_count <= 10 => { /* retry max 10 attempts */
            }
            Err(TransactionRuntimeError::Retry(e)) => break Err(e),
            Err(TransactionRuntimeError::Rollback(e)) => break Err(e),
            Ok(res) => break Ok(res),
        }
    }
}

// enable TLS for AsyncPgConnection, see https://github.com/weiznich/diesel_async/blob/main/examples/postgres/pooled-with-rustls

fn establish_pg_ssl_connection(config: &str) -> BoxFuture<ConnectionResult<AsyncPgConnection>> {
    let fut = async {
        // We first set up the way we want rustls to work.
        let rustls_config = rustls::ClientConfig::builder()
            .with_root_certificates(root_certs())
            .with_no_client_auth();
        let tls = tokio_postgres_rustls::MakeRustlsConnect::new(rustls_config);
        let (client, conn) = tokio_postgres::connect(config, tls)
            .await
            .map_err(|e| ConnectionError::BadConnection(e.to_string()))?;
        tokio::spawn(async move {
            if let Err(e) = conn.await {
                eprintln!("Database connection: {e}");
            }
        });
        AsyncPgConnection::try_from(client).await
    };
    fut.boxed()
}

fn root_certs() -> rustls::RootCertStore {
    let mut roots = rustls::RootCertStore::empty();
    let certs =
        rustls_native_certs::load_native_certs().expect("Failed to load native certificates");
    roots.add_parsable_certificates(certs);
    if let Some(ref pg_ssl_cert_path) = *PG_SSL_CERT_PATH {
        let certs =
            load_certs(pg_ssl_cert_path.as_str()).expect("Failed to load pg ssl certificate");
        roots.add_parsable_certificates(certs);
    }
    roots
}

fn load_certs(cert_path: &str) -> io::Result<Vec<CertificateDer<'static>>> {
    let certfile = fs::File::open(cert_path)?;
    let mut reader = io::BufReader::new(certfile);

    let certs = rustls_pemfile::certs(&mut reader);
    certs.collect()
}
