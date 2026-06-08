-- Agregar columna subastaPreferida a Products
-- Ejecutar UNA SOLA VEZ contra la base de datos

ALTER TABLE Products
  ADD subastaPreferida INT NULL;

ALTER TABLE Products
  ADD CONSTRAINT fk_products_subastaPreferida
  FOREIGN KEY (subastaPreferida) REFERENCES Auctions(identificador);

GO
