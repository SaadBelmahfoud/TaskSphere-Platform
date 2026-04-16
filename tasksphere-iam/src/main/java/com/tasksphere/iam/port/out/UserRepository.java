package com.tasksphere.iam.port.out;

import com.tasksphere.iam.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {
    // Héritage automatique de findById, save, findByUsername (généré par Spring Data JPA)

    Optional<UserEntity> findByUsername(String username);

}