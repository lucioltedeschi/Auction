-- Agregar columna duracionItemMinutos a Auctions
-- Ejecutar UNA SOLA VEZ contra la base de datos

ALTER TABLE Auctions
  ADD duracionItemMinutos INT NOT NULL DEFAULT 3;

GO
