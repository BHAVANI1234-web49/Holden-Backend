# Holden - Secure Digital Certificate Management System

## Project Description

Holden is a Java-based web application designed to securely store, manage, verify, and share digital certificates. It gives users control over their certificates and helps prevent unauthorized access.

## Objectives

- Securely upload and store digital certificates.
- Manage certificate details.
- Track certificate expiry dates.
- Verify certificate integrity using SHA-256 hashing.
- Share certificates with authorized users.
- Revoke sharing permissions.
- Track certificate-related activities.

## Technologies Used

- Java
- Spring Boot
- Spring Data JPA
- Hibernate
- MySQL
- Maven
- REST APIs

## Main Features

### 1. User Authentication
Supports user registration and login.

### 2. Certificate Management
Allows users to upload, view, update, and delete certificates.

### 3. Certificate Integrity Verification
Uses SHA-256 hashes to help detect changes to stored certificate files.

### 4. Controlled Sharing
Allows certificate owners to share certificates with other users and revoke access.

### 5. Expiry Management
Stores certificate issue dates and expiry dates.

### 6. Activity Tracking
Records certificate-related activities.

## Project Structure

```text
holden/
├── src/
│   ├── main/
│   │   ├── java/com/example/holden/
│   │   │   ├── certificate/
│   │   │   ├── config/
│   │   │   ├── controller/
│   │   │   ├── entity/
│   │   │   ├── repository/
│   │   │   └── service/
│   │   └── resources/
│   │       └── application.properties
│   └── test/
├── .gitignore
├── mvnw
├── mvnw.cmd
├── pom.xml
└── README.md
