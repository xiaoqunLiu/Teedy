package com.sismics.docs.core.model.jpa;

import jakarta.persistence.*;
import java.util.Date;

/**
 * Registration request entity.
 */
@Entity
@Table(name = "T_REGISTRATION_REQUEST")
public class RegistrationRequest {
    @Id
    @Column(name = "RRE_ID_C", length = 36)
    private String id;

    @Column(name = "RRE_USERNAME_C", length = 50, nullable = false)
    private String username;

    @Column(name = "RRE_EMAIL_C", length = 100, nullable = false)
    private String email;

    @Column(name = "RRE_PASSWORD_C", length = 100, nullable = false)
    private String password;

    @Column(name = "RRE_STATUS_C", length = 20, nullable = false)
    private String status;

    @Column(name = "RRE_CREATEDATE_D", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createDate;

    @Column(name = "RRE_UPDATEDATE_D")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateDate;

    @Column(name = "RRE_ADMIN_ID_C", length = 36)
    private String adminId;

    @Column(name = "RRE_REJECTION_REASON_C", length = 500)
    private String rejectionReason;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    public Date getUpdateDate() {
        return updateDate;
    }

    public void setUpdateDate(Date updateDate) {
        this.updateDate = updateDate;
    }

    public String getAdminId() {
        return adminId;
    }

    public void setAdminId(String adminId) {
        this.adminId = adminId;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
} 