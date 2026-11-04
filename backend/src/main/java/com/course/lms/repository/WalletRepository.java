package com.course.lms.repository;

import com.course.lms.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByOwnerIdAndType(Long ownerId, String type);

    Optional<Wallet> findFirstByTypeAndOwnerIsNull(String type);

    List<Wallet> findByType(String type);

    // Verrou d'écriture pour toute modification de solde
    // SQL natif « FOR UPDATE » : compatible MySQL et MariaDB (XAMPP)
    @Query(value = "select * from wallets where id = :id for update", nativeQuery = true)
    Optional<Wallet> findByIdForUpdate(@Param("id") Long id);
}
