package com.hcmute.lms.lmsapi.service;

/**
 * Nơi lưu tệp điểm gốc của lô nhập (ADR 0013, nháp chờ sinh viên xác nhận). Chỉ lưu và đọc theo
 * {@code fileRef}; đổi nơi lưu (thư mục, object storage) chỉ cần thay hiện thực.
 */
public interface ImportFileStorage {

  /**
   * Lưu nội dung tệp cho lô {@code batchId} và trả về {@code fileRef}.
   *
   * @param batchId mã lô (ObjectId 24 ký tự hex) do máy chủ sinh, dùng để đặt tên tệp
   */
  String save(String batchId, byte[] content);

  /** Đọc lại tệp đã lưu; ném {@code AppException} (500) nếu tệp không còn. */
  byte[] load(String fileRef);

  /** Xóa tệp; không lỗi nếu tệp không tồn tại. */
  void delete(String fileRef);
}
