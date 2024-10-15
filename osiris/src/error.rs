use std::fmt;

use thiserror::Error;

#[allow(clippy::enum_variant_names)]
#[derive(Error, Debug)]
pub enum Error {
    #[error("Could not establish database connection: {0}")]
    DatabaseConnectionError(String),
    #[error("There has been an error executing a query: '{0}'")]
    QueryError(diesel::result::Error),
    #[error("There has been an error executing a serenity request: '{0}'")]
    SerenityError(serenity::Error),
    #[error("You must be a [supporter](https://ko-fi.com/R5R0XAC5J) to perform this action. To be verified as a supporter, you must have the supporters role in the [aiode discord](https://discord.gg/gdc25AG). This role is assigned automatically if your Ko-fi account is connected to discord.")]
    UserIsNotSupporterError,
    #[error("{0}")]
    InvalidCommandError(String),
    #[error("There is currently no private bot instance available. Check the [aiode discord](https://discord.gg/gdc25AG) to learn when new availability is added.")]
    NoPrivateBotAvailableError,
}

impl Error {
    pub fn error_code(&self) -> u32 {
        match self {
            Self::InvalidCommandError(_) => 400_001,
            Self::UserIsNotSupporterError => 400_002,
            Self::NoPrivateBotAvailableError => 400_004,

            Self::DatabaseConnectionError(_) => 500_001,
            Self::QueryError(_) => 500_002,
            Self::SerenityError(_) => 500_003,
        }
    }

    pub fn is_internal_error(&self) -> bool {
        match self {
            Self::UserIsNotSupporterError
            | Self::InvalidCommandError(_)
            | Self::NoPrivateBotAvailableError => false,
            Self::DatabaseConnectionError(_) | Self::QueryError(_) | Self::SerenityError(_) => true,
        }
    }
}

impl From<diesel::result::Error> for Error {
    fn from(e: diesel::result::Error) -> Self {
        Self::QueryError(e)
    }
}

impl From<serenity::Error> for Error {
    fn from(e: serenity::Error) -> Self {
        Self::SerenityError(e)
    }
}

#[derive(Debug)]
pub enum TransactionRuntimeError {
    Retry(Error),
    Rollback(Error),
}

impl fmt::Display for TransactionRuntimeError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Retry(e) => e.fmt(f),
            Self::Rollback(e) => e.fmt(f),
        }
    }
}

impl From<Error> for TransactionRuntimeError {
    fn from(e: Error) -> Self {
        TransactionRuntimeError::Rollback(e)
    }
}

impl From<diesel::result::Error> for TransactionRuntimeError {
    fn from(e: diesel::result::Error) -> Self {
        match e {
            diesel::result::Error::DatabaseError(
                diesel::result::DatabaseErrorKind::SerializationFailure,
                _,
            ) => TransactionRuntimeError::Retry(e.into()),
            _ => TransactionRuntimeError::Rollback(e.into()),
        }
    }
}
