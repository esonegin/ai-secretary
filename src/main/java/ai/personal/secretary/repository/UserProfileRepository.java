package ai.personal.secretary.repository;

/**
 * @author onegines
 * @date 20.03.2026
 */


import ai.personal.secretary.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    Optional<UserProfile> findFirstByOrderByIdAsc();
}