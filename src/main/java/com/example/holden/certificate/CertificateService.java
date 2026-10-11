package com.example.holden.certificate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.holden.entity.ActivityLog;
import com.example.holden.entity.CertificateShare;
import com.example.holden.repository.ActivityLogRepository;
import com.example.holden.repository.CertificateShareRepository;
import com.example.holden.repository.UserRepository;

@Service
public class CertificateService {

    private final CertificateRepository repository;
    private final CertificateShareRepository shareRepository;
    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;

    @Value("${holden.upload-dir:uploads/certificates}")
    private String uploadDirectory;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg"
    );

    public CertificateService(
            CertificateRepository repository,
            CertificateShareRepository shareRepository,
            ActivityLogRepository activityLogRepository,
            UserRepository userRepository) {
        this.repository = repository;
        this.shareRepository = shareRepository;
        this.activityLogRepository = activityLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Certificate uploadCertificate(
            String title,
            String issuer,
            LocalDate issueDate,
            LocalDate expiryDate,
            String ownerUsername,
            MultipartFile file) throws IOException {

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Certificate title is required.");
        }

        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("Issuing organization is required.");
        }

        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }

        if (ownerUsername.contains(",")) {
            ownerUsername = ownerUsername.split(",")[0].trim();
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please select a certificate file.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size must not exceed 10 MB.");
        }

        if (issueDate != null && expiryDate != null && expiryDate.isBefore(issueDate)) {
            throw new IllegalArgumentException("Expiry date cannot be before issue date.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Only PDF, PNG, and JPG files are allowed.");
        }

        Path directory = Paths.get(uploadDirectory).toAbsolutePath().normalize();
        Files.createDirectories(directory);

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = "certificate";
        }
        originalName = Paths.get(originalName).getFileName().toString();

        String extension;
        if ("application/pdf".equals(contentType)) {
            extension = ".pdf";
        } else if ("image/png".equals(contentType)) {
            extension = ".png";
        } else {
            extension = ".jpg";
        }

        String storedName = UUID.randomUUID() + extension;
        Path destination = directory.resolve(storedName).normalize();

        if (!destination.startsWith(directory)) {
            throw new IllegalArgumentException("Invalid file path.");
        }

        String hash = calculateHash(file);

        try (InputStream input = file.getInputStream()) {
            Files.copy(input, destination);
        }

        try {
            Certificate certificate = new Certificate();
            certificate.setTitle(title.trim());
            certificate.setIssuer(issuer.trim());
            certificate.setIssueDate(issueDate);
            certificate.setExpiryDate(expiryDate);
            certificate.setOwnerUsername(ownerUsername.trim().toLowerCase());
            certificate.setFileName(originalName);
            certificate.setStoredFileName(storedName);
            certificate.setFileType(contentType);
            certificate.setSha256Hash(hash);
            certificate.setUploadedDate(LocalDate.now());

            Certificate saved = repository.save(certificate);

            // Log activity
            logActivity(ownerUsername.trim().toLowerCase(), "UPLOAD", saved.getId(), saved.getTitle(),
                    "Uploaded certificate: " + saved.getTitle() + " (" + originalName + ")");

            return saved;
        } catch (RuntimeException exception) {
            Files.deleteIfExists(destination);
            throw exception;
        }
    }

    public List<Certificate> getCertificates(String ownerUsername) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }
        return repository.findByOwnerUsername(ownerUsername.trim().toLowerCase());
    }

    public Certificate getCertificateById(Long id, String requesterUsername) {
        if (requesterUsername == null || requesterUsername.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }

        Certificate cert = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found."));

        String cleanRequester = requesterUsername.trim().toLowerCase();

        // Check if owner
        boolean isOwner = cert.getOwnerUsername().equalsIgnoreCase(cleanRequester);
        // Check if shared
        boolean isShared = shareRepository
                .findByCertificateIdAndSharedWithUsernameAndStatus(id, cleanRequester, "ACTIVE")
                .isPresent();

        if (!isOwner && !isShared) {
            throw new SecurityException("Access denied: You do not have permission to view this certificate.");
        }

        // Log view
        logActivity(cleanRequester, "VIEW", cert.getId(), cert.getTitle(), "Viewed certificate details");

        return cert;
    }

    public Map<String, Object> downloadCertificate(Long id, String requesterUsername) throws IOException {
        if (requesterUsername == null || requesterUsername.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }

        Certificate cert = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found."));

        String cleanRequester = requesterUsername.trim().toLowerCase();

        boolean isOwner = cert.getOwnerUsername().equalsIgnoreCase(cleanRequester);
        Optional<CertificateShare> shareOpt = shareRepository
                .findByCertificateIdAndSharedWithUsernameAndStatus(id, cleanRequester, "ACTIVE");

        if (!isOwner) {
            if (shareOpt.isEmpty() || !shareOpt.get().isCanDownload()) {
                throw new SecurityException("Access denied: You are not authorized to download this file.");
            }
        }

        Path directory = Paths.get(uploadDirectory).toAbsolutePath().normalize();
        Path filePath = directory.resolve(cert.getStoredFileName()).normalize();

        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("Stored certificate file not found on disk.");
        }

        Resource resource = new UrlResource(filePath.toUri());

        // Log download
        logActivity(cleanRequester, "DOWNLOAD", cert.getId(), cert.getTitle(),
                "Downloaded certificate: " + cert.getFileName());

        Map<String, Object> result = new HashMap<>();
        result.put("resource", resource);
        result.put("certificate", cert);
        return result;
    }

    @Transactional
    public Certificate updateCertificate(
            Long id,
            String title,
            String issuer,
            LocalDate issueDate,
            LocalDate expiryDate,
            String requesterUsername) {

        Certificate cert = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found."));

        if (!cert.getOwnerUsername().equalsIgnoreCase(requesterUsername.trim().toLowerCase())) {
            throw new SecurityException("Only the certificate owner can update details.");
        }

        if (title != null && !title.isBlank()) {
            cert.setTitle(title.trim());
        }
        if (issuer != null && !issuer.isBlank()) {
            cert.setIssuer(issuer.trim());
        }
        if (issueDate != null) {
            cert.setIssueDate(issueDate);
        }
        if (expiryDate != null) {
            cert.setExpiryDate(expiryDate);
        }

        Certificate updated = repository.save(cert);
        logActivity(requesterUsername.trim().toLowerCase(), "UPDATE", updated.getId(), updated.getTitle(),
                "Updated certificate metadata");
        return updated;
    }

    @Transactional
    public void deleteCertificate(Long id, String requesterUsername) throws IOException {
        Certificate cert = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found."));

        if (!cert.getOwnerUsername().equalsIgnoreCase(requesterUsername.trim().toLowerCase())) {
            throw new SecurityException("Only the certificate owner can delete this certificate.");
        }

        // Delete physical file
        Path directory = Paths.get(uploadDirectory).toAbsolutePath().normalize();
        Path filePath = directory.resolve(cert.getStoredFileName()).normalize();
        Files.deleteIfExists(filePath);

        // Delete all shares
        List<CertificateShare> shares = shareRepository.findByCertificateId(id);
        shareRepository.deleteAll(shares);

        // Log delete before removing record
        logActivity(requesterUsername.trim().toLowerCase(), "DELETE", cert.getId(), cert.getTitle(),
                "Deleted certificate: " + cert.getTitle());

        repository.delete(cert);
    }

    public Map<String, Object> verifyIntegrity(Long id, String requesterUsername) throws IOException {
        Certificate cert = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found."));

        String cleanRequester = requesterUsername.trim().toLowerCase();
        boolean isOwner = cert.getOwnerUsername().equalsIgnoreCase(cleanRequester);
        boolean isShared = shareRepository
                .findByCertificateIdAndSharedWithUsernameAndStatus(id, cleanRequester, "ACTIVE")
                .isPresent();

        if (!isOwner && !isShared) {
            throw new SecurityException("Access denied: You do not have permission to verify this certificate.");
        }

        Path directory = Paths.get(uploadDirectory).toAbsolutePath().normalize();
        Path filePath = directory.resolve(cert.getStoredFileName()).normalize();

        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("The certificate file is missing from storage.");
        }

        String computedHash;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(filePath)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            computedHash = HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available.", e);
        }

        boolean verified = computedHash.equalsIgnoreCase(cert.getSha256Hash());

        logActivity(cleanRequester, "VERIFY_INTEGRITY", cert.getId(), cert.getTitle(),
                verified ? "Integrity check PASSED (SHA-256 match)" : "Integrity check FAILED (hash mismatch)");

        Map<String, Object> response = new HashMap<>();
        response.put("certificateId", cert.getId());
        response.put("title", cert.getTitle());
        response.put("storedHash", cert.getSha256Hash());
        response.put("computedHash", computedHash);
        response.put("verified", verified);
        response.put("message", verified
                ? "Integrity Verified: The file matches its original SHA-256 fingerprint with no tampering detected."
                : "Security Warning: Hash mismatch! The file content differs from its original record.");
        return response;
    }

    @Transactional
    public CertificateShare shareCertificate(Long id, String ownerUsername, String recipientUsername) {
        if (recipientUsername == null || recipientUsername.isBlank()) {
            throw new IllegalArgumentException("Recipient username is required.");
        }

        String cleanOwner = ownerUsername.trim().toLowerCase();
        String cleanRecipient = recipientUsername.trim().toLowerCase();

        if (cleanOwner.equalsIgnoreCase(cleanRecipient)) {
            throw new IllegalArgumentException("You cannot share a certificate with yourself.");
        }

        if (!userRepository.findByUsername(cleanRecipient).isPresent()) {
            throw new IllegalArgumentException("User '" + recipientUsername + "' does not exist in Holden.");
        }

        Certificate cert = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found."));

        if (!cert.getOwnerUsername().equalsIgnoreCase(cleanOwner)) {
            throw new SecurityException("Only the certificate owner can share this certificate.");
        }

        Optional<CertificateShare> existing = shareRepository
                .findByCertificateIdAndSharedWithUsernameAndStatus(id, cleanRecipient, "ACTIVE");

        if (existing.isPresent()) {
            return existing.get();
        }

        CertificateShare share = new CertificateShare(cert.getId(), cert.getTitle(), cleanOwner, cleanRecipient);
        CertificateShare saved = shareRepository.save(share);

        logActivity(cleanOwner, "SHARE", cert.getId(), cert.getTitle(),
                "Shared certificate with user: " + cleanRecipient);

        return saved;
    }

    @Transactional
    public void revokeShare(Long id, String ownerUsername, String recipientUsername) {
        String cleanOwner = ownerUsername.trim().toLowerCase();
        String cleanRecipient = recipientUsername.trim().toLowerCase();

        Certificate cert = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found."));

        if (!cert.getOwnerUsername().equalsIgnoreCase(cleanOwner)) {
            throw new SecurityException("Only the certificate owner can revoke sharing.");
        }

        Optional<CertificateShare> existing = shareRepository
                .findByCertificateIdAndSharedWithUsernameAndStatus(id, cleanRecipient, "ACTIVE");

        if (existing.isPresent()) {
            CertificateShare share = existing.get();
            share.setStatus("REVOKED");
            shareRepository.save(share);

            logActivity(cleanOwner, "REVOKE_SHARE", cert.getId(), cert.getTitle(),
                    "Revoked sharing access for user: " + cleanRecipient);
        } else {
            throw new IllegalArgumentException("No active share found for user: " + recipientUsername);
        }
    }

    public List<CertificateShare> getCertificateShares(Long id, String ownerUsername) {
        Certificate cert = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found."));

        if (!cert.getOwnerUsername().equalsIgnoreCase(ownerUsername.trim().toLowerCase())) {
            throw new SecurityException("Only the owner can view sharing access.");
        }

        return shareRepository.findByCertificateIdAndStatus(id, "ACTIVE");
    }

    public List<Map<String, Object>> getSharedWithMe(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }

        String cleanUsername = username.trim().toLowerCase();
        List<CertificateShare> shares = shareRepository.findBySharedWithUsernameAndStatus(cleanUsername, "ACTIVE");

        List<Map<String, Object>> result = new ArrayList<>();
        for (CertificateShare share : shares) {
            repository.findById(share.getCertificateId()).ifPresent(cert -> {
                Map<String, Object> item = new HashMap<>();
                item.put("certificate", cert);
                item.put("shareInfo", share);
                result.add(item);
            });
        }
        return result;
    }

    public Map<String, Object> getSummaryStats(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }

        String cleanUsername = username.trim().toLowerCase();
        List<Certificate> certs = repository.findByOwnerUsername(cleanUsername);

        LocalDate today = LocalDate.now();
        LocalDate soon = today.plusDays(30);

        long total = certs.size();
        long active = 0;
        long expiringSoon = 0;
        long expired = 0;

        for (Certificate c : certs) {
            if (c.getExpiryDate() == null) {
                active++;
            } else if (c.getExpiryDate().isBefore(today)) {
                expired++;
            } else if (!c.getExpiryDate().isAfter(soon)) {
                expiringSoon++;
            } else {
                active++;
            }
        }

        long sharedWithMeCount = shareRepository.findBySharedWithUsernameAndStatus(cleanUsername, "ACTIVE").size();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("active", active);
        stats.put("expiringSoon", expiringSoon);
        stats.put("expired", expired);
        stats.put("sharedWithMe", sharedWithMeCount);

        return stats;
    }

    public List<ActivityLog> getActivities(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }
        return activityLogRepository.findTop20ByUsernameOrderByTimestampDesc(username.trim().toLowerCase());
    }

    private void logActivity(String username, String action, Long certificateId, String certificateTitle, String details) {
        try {
            ActivityLog log = new ActivityLog(username, action, certificateId, certificateTitle, details);
            activityLogRepository.save(log);
        } catch (Exception ignored) {
        }
    }

    private String calculateHash(MultipartFile file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}