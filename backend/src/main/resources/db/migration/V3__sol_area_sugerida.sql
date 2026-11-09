-- El area sugerida orienta la distribucion, sin convertirse necesariamente en destinatario.
-- Las SOL historicas se preservan sin reescribir destinos ni responsables.
ALTER TABLE solicitation ADD COLUMN requested_area_scope BIGINT;
ALTER TABLE solicitation ADD CONSTRAINT fk_sol_requested_area FOREIGN KEY (requested_area_scope) REFERENCES org_scope(id);
