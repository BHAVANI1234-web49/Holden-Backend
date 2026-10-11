package com.example.holden.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "certificate_shares")
public class CertificateShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long certificateId;

    private String certificateTitle;

    private String sharedByUsername;

    private String sharedWithUsername;

    private LocalDateTime sharedDate;

    private boolean canDownload = true;

    // ACTIVE or REVOKED
    private String status = "ACTIVE";

    public CertificateShare() {
    }

    public CertificateShare(Long certificateId, String certificateTitle, String sharedByUsername, String sharedWithUsername) {
        this.certificateId = certificateId;
        this.certificateTitle = certificateTitle;
        this.sharedByUsername = sharedByUsername;
        this.sharedWithUsername = sharedWithUsername;
        this.sharedDate = LocalDateTime.now();
        this.status = "ACTIVE";
        this.canDownload = true;
    }

    public Long getId() {
        return id;
    }

    public Long getCertificateId() {
        return certificateId;
    }

    public void setCertificateId(Long certificateId) {
        this.certificateId = certificateId;
    }

    public String getCertificateTitle() {
        return certificateTitle;
    }

    public void setCertificateTitle(String certificateTitle) {
        this.certificateTitle = certificateTitle;
    }

    public String getSharedByUsername() {
        return sharedByUsername;
    }

    public void setSharedByUsername(String sharedByUsername) {
        this.sharedByUsername = sharedByUsername;
    }

    public String getSharedWithUsername() {
        return sharedWithUsername;
    }

    public void setSharedWithUsername(String sharedWithUsername) {
        this.sharedWithUsername = sharedWithUsername;
    }

    public LocalDateTime getSharedDate() {
        return sharedDate;
    }

    public void setSharedDate(LocalDateTime sharedDate) {
        this.sharedDate = sharedDate;
    }

    public boolean isCanDownload() {
        return canDownload;
    }

    public void setCanDownload(boolean canDownload) {
        this.canDownload = canDownload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
