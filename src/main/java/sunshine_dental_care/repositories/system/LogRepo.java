package sunshine_dental_care.repositories.system;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sunshine_dental_care.entities.Log;

@Repository
public interface LogRepo
        extends JpaRepository<Log, Integer>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Log> {
    // Save log khi thay đổi lịch drag and drop từ reception
}
