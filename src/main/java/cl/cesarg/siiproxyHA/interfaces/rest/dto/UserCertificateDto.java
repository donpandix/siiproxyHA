package cl.cesarg.siiproxyHA.interfaces.rest.dto;

import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public class UserCertificateDto {

    public UUID id;
    public UUID tenantId;
    public String rutUsuario;
    public String nombreUsuario;
    public String certSerialNumber;
    public String certIssuer;
    public String certSubject;
    public String certSubjectRut;
    public OffsetDateTime validFrom;
    public OffsetDateTime validUntil;
    public String status;
    public boolean isDefault;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
    public String createdBy;
    public OffsetDateTime lastUsedAt;
    public Integer usageCount;

    public static UserCertificateDto fromEntity(UserCertificateEntity entity) {
        UserCertificateDto dto = new UserCertificateDto();
        dto.id = entity.getId();
        dto.tenantId = entity.getTenantId();
        dto.rutUsuario = entity.getRutUsuario();
        dto.nombreUsuario = entity.getNombreUsuario();
        dto.certSerialNumber = entity.getCertSerialNumber();
        dto.certIssuer = entity.getCertIssuer();
        dto.certSubject = entity.getCertSubject();
        dto.certSubjectRut = entity.getCertSubjectRut();
        dto.validFrom = entity.getValidFrom();
        dto.validUntil = entity.getValidUntil();
        dto.status = entity.getStatus();
        dto.isDefault = entity.isDefault();
        dto.createdAt = entity.getCreatedAt();
        dto.updatedAt = entity.getUpdatedAt();
        dto.createdBy = entity.getCreatedBy();
        dto.lastUsedAt = entity.getLastUsedAt();
        dto.usageCount = entity.getUsageCount();
        return dto;
    }
}
