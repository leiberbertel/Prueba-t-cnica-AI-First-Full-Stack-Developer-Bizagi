-- ADR-0007 · Eliminación de cuentas asíncrona, por lotes.

-- Marca de cuenta eliminada: la fila se conserva (anonimizada) hasta que se purgan sus datos.
alter table users add column deleted_at timestamptz;
create index ix_users_pending_purge on users (deleted_at) where deleted_at is not null;

-- Cada módulo borra sus propios datos: la base de datos solo impide borrar un usuario con datos pendientes.
alter table predictions drop constraint predictions_user_id_fkey;
alter table predictions add constraint fk_predictions_user
    foreign key (user_id) references users (id) on delete restrict;

alter table refresh_tokens drop constraint refresh_tokens_user_id_fkey;
alter table refresh_tokens add constraint fk_refresh_tokens_user
    foreign key (user_id) references users (id) on delete restrict;

-- Purga de refresh tokens vencidos por lotes.
create index ix_refresh_tokens_expires on refresh_tokens (expires_at);
