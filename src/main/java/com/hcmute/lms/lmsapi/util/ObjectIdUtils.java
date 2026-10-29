package com.hcmute.lms.lmsapi.util;

import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;

public final class ObjectIdUtils {

    private ObjectIdUtils() {
    }

    public static void requireValid(String id, String fieldName) {
        if (id == null || !ObjectId.isValid(id)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    String.format("Giá trị '%s' của trường '%s' không phải ObjectId hợp lệ", id, fieldName));
        }
    }
}
