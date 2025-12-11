package sunshine_dental_care.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import sunshine_dental_care.entities.Inventory;

@Repository
public interface InventoryRepo extends JpaRepository<Inventory, Integer> {
}
