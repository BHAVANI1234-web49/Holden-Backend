package com.example.holden.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.holden.entity.CertificateShare;

public interface CertificateShareRepository extends JpaRepository<CertificateShare, Long> {

    List<CertificateShare> findByCertificateIdAndStatus(Long certificateId, String status);

    List<CertificateShare> findBySharedWithUsernameAndStatus(String sharedWithUsername, String status);

    Optional<CertificateShare> findByCertificateIdAndSharedWithUsernameAndStatus(
            Long certificateId,
            String sharedWithUsername,
            String status
    );

    List<CertificateShare> findByCertificateId(Long certificateId);
}
