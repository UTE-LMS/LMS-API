package com.hcmute.lms.lmsapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

/**
 * Document đại diện cho collection audit_logs trong MongoDB.
 * Cấu trúc và chỉ mục khớp 100% với $jsonSchema trong doc/database_import.js.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "audit_logs")
@CompoundIndexes({
    @CompoundIndex(name = "entity_collection_id_at_desc", def = "{'entity.collection': 1, 'entity.id': 1, 'at': -1}"),
    @CompoundIndex(name = "user_id_at_desc", def = "{'userId': 1, 'at': -1}")
})
public class AuditLog {

    @Id
    private String id;

    /** Thời điểm ghi nhận hành động (DATE trong Mongo) */
    @Field(name = "at")
    private Instant at;

    /**
     * ID người thực hiện.
     * Cấu hình FieldType.OBJECT_ID để Spring Data tự động chuyển String hex sang BSON ObjectId,
     * tránh vi phạm validator OID_N (mã lỗi 121 của MongoDB).
     */
    @Field(name = "userId", targetType = FieldType.OBJECT_ID)
    private String userId;

    /** Hành động kiểm toán */
    @Field(name = "action")
    private AuditAction action;

    /** Thực thể bị tác động { collection, id } */
    @Field(name = "entity")
    private AuditEntity entity;

    /** Dữ liệu trạng thái trước khi sửa (BSON Object hoặc null) */
    @Field(name = "before")
    private Object before;

    /** Dữ liệu trạng thái sau khi sửa (BSON Object hoặc null) */
    @Field(name = "after")
    private Object after;

    /** Lý do thực hiện (bắt buộc đối với SCORE_UPDATE) */
    @Field(name = "reason")
    private String reason;

    /** Địa chỉ IP máy trạm của người dùng */
    @Field(name = "ip")
    private String ip;

    public void setUserId(ObjectId userId) {
        this.userId = userId != null ? userId.toHexString() : null;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
