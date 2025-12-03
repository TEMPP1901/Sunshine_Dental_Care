package sunshine_dental_care.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import sunshine_dental_care.entities.UserDevice;

@Repository
public interface UserDeviceRepo extends JpaRepository<UserDevice, Integer> {
    List<UserDevice> findByUserId(Integer userId);

    Optional<UserDevice> findByUserIdAndFcmToken(Integer userId, String fcmToken);

    void deleteByFcmToken(String fcmToken);
}
