package backend.voyago.SpringBackend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.voyago.SpringBackend.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Never use a single-result finder here — duplicate emails exist in older
     * data and Optional would throw NonUniqueResultException.
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email) ORDER BY u.uid ASC")
    List<User> findAllByEmailIgnoreCase(@Param("email") String email);

    default Optional<User> findOneByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        List<User> users = findAllByEmailIgnoreCase(email.trim());
        return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }

    default boolean existsByEmailIgnoreCase(String email) {
        return email != null && !findAllByEmailIgnoreCase(email.trim()).isEmpty();
    }

    Optional<User> findByTagIgnoreCase(String tag);
    boolean existsByTagIgnoreCase(String tag);
    List<User> findByTagIsNull();

    /**
     * People search for the invite picker. Matches display name or handle only —
     * email is deliberately not searchable and is never returned to the caller.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.uid <> :selfUid
              AND (LOWER(u.full_name) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(u.tag)       LIKE LOWER(CONCAT(:q, '%')))
            ORDER BY u.full_name
            """)
    List<User> searchByNameOrTag(@Param("q") String q, @Param("selfUid") Long selfUid, Pageable page);
}
