package com.example.holden.certificate;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.holden.entity.ActivityLog;
import com.example.holden.entity.CertificateShare;

@RestController
@RequestMapping("/api/certificates")
@CrossOrigin(origins = {"https://holdenproject.netlify.app", "http://localhost:3000", "http://localhost:5173"})
public class CertificateController {

    private final CertificateService service;

    public CertificateController(CertificateService service) {
        this.service = service;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadCertificate(
            @RequestParam String title,
            @RequestParam String issuer,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate issueDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate expiryDate,
            @RequestParam String ownerUsername,
            @RequestParam("file") MultipartFile file) {

        try {
            Certificate certificate = service.uploadCertificate(
                    title,
                    issuer,
                    issueDate,
                    expiryDate,
                    ownerUsername,
                    file
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Certificate uploaded successfully.",
                    "certificate", certificate
            ));

        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", exception.getMessage()
            ));

        } catch (IOException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Unable to save the certificate file to disk."
            ));
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Upload failed: " + exception.getMessage()
            ));
        }
    }

    @GetMapping
    public ResponseEntity<?> getCertificates(@RequestParam String ownerUsername) {
        try {
            List<Certificate> certificates = service.getCertificates(ownerUsername);
            return ResponseEntity.ok(certificates);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCertificateById(
            @PathVariable Long id,
            @RequestParam String username) {
        try {
            Certificate certificate = service.getCertificateById(id, username);
            return ResponseEntity.ok(certificate);
        } catch (SecurityException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", exception.getMessage()));
        }
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<?> downloadCertificate(
            @PathVariable Long id,
            @RequestParam String username) {
        try {
            Map<String, Object> result = service.downloadCertificate(id, username);
            Resource resource = (Resource) result.get("resource");
            Certificate cert = (Certificate) result.get("certificate");

            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            if (cert.getFileType() != null) {
                try {
                    mediaType = MediaType.parseMediaType(cert.getFileType());
                } catch (Exception ignored) {
                }
            }

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + cert.getFileName() + "\"")
                    .body(resource);

        } catch (SecurityException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", exception.getMessage()));
        } catch (IOException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error reading certificate file."));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCertificate(
            @PathVariable Long id,
            @RequestParam String username,
            @RequestBody Map<String, Object> body) {
        try {
            String title = (String) body.get("title");
            String issuer = (String) body.get("issuer");
            LocalDate issueDate = body.get("issueDate") != null ? LocalDate.parse((String) body.get("issueDate")) : null;
            LocalDate expiryDate = body.get("expiryDate") != null ? LocalDate.parse((String) body.get("expiryDate")) : null;

            Certificate updated = service.updateCertificate(id, title, issuer, issueDate, expiryDate, username);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Certificate updated successfully.",
                    "certificate", updated
            ));
        } catch (SecurityException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCertificate(
            @PathVariable Long id,
            @RequestParam String username) {
        try {
            service.deleteCertificate(id, username);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Certificate deleted successfully."
            ));
        } catch (SecurityException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", exception.getMessage()));
        } catch (IOException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Failed to delete file from storage."));
        }
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<?> verifyIntegrity(
            @PathVariable Long id,
            @RequestParam String username) {
        try {
            Map<String, Object> result = service.verifyIntegrity(id, username);
            return ResponseEntity.ok(result);
        } catch (SecurityException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", exception.getMessage()));
        } catch (IOException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error reading file for verification."));
        }
    }

    @PostMapping("/{id}/share")
    public ResponseEntity<?> shareCertificate(
            @PathVariable Long id,
            @RequestParam String ownerUsername,
            @RequestParam String recipientUsername) {
        try {
            CertificateShare share = service.shareCertificate(id, ownerUsername, recipientUsername);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Certificate shared successfully with @" + recipientUsername + ".",
                    "share", share
            ));
        } catch (SecurityException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @PostMapping("/{id}/revoke-share")
    public ResponseEntity<?> revokeShare(
            @PathVariable Long id,
            @RequestParam String ownerUsername,
            @RequestParam String recipientUsername) {
        try {
            service.revokeShare(id, ownerUsername, recipientUsername);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Sharing access revoked for @" + recipientUsername + "."
            ));
        } catch (SecurityException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @GetMapping("/{id}/shares")
    public ResponseEntity<?> getCertificateShares(
            @PathVariable Long id,
            @RequestParam String ownerUsername) {
        try {
            List<CertificateShare> shares = service.getCertificateShares(id, ownerUsername);
            return ResponseEntity.ok(shares);
        } catch (SecurityException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", exception.getMessage()));
        }
    }

    @GetMapping("/shared-with-me")
    public ResponseEntity<?> getSharedWithMe(@RequestParam String username) {
        try {
            List<Map<String, Object>> shared = service.getSharedWithMe(username);
            return ResponseEntity.ok(shared);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<?> getSummaryStats(@RequestParam String username) {
        try {
            Map<String, Object> stats = service.getSummaryStats(username);
            return ResponseEntity.ok(stats);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @GetMapping("/activities")
    public ResponseEntity<?> getActivities(@RequestParam String username) {
        try {
            List<ActivityLog> activities = service.getActivities(username);
            return ResponseEntity.ok(activities);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }
}